package org.zotero.android.screens.collections

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.OrderedCollectionChangeSet
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.RealmModel
import io.realm.RealmResults
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.Defaults
import org.zotero.android.architecture.LCE2
import org.zotero.android.architecture.ScreenArguments
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import org.zotero.android.architecture.coroutines.Dispatchers
import org.zotero.android.architecture.ifFailure
import org.zotero.android.database.DbWrapperMain
import org.zotero.android.database.objects.RCollection
import org.zotero.android.database.objects.RCustomLibraryType
import org.zotero.android.database.objects.RItem
import org.zotero.android.database.requests.MarkObjectsAsDeletedDbRequest
import org.zotero.android.database.requests.ReadCollectionDbRequest
import org.zotero.android.database.requests.ReadCollectionsDbRequest
import org.zotero.android.database.requests.ReadItemsDbRequest
import org.zotero.android.database.requests.ReadLibraryDbRequest
import org.zotero.android.database.requests.SetCollectionCollapsedDbRequest
import org.zotero.android.files.FileStore
import org.zotero.android.screens.allitems.data.AllItemsArgs
import org.zotero.android.screens.collectionedit.data.CollectionEditArgs
import org.zotero.android.screens.collections.data.CollectionItemWithChildren
import org.zotero.android.screens.collections.data.CollectionTree
import org.zotero.android.screens.collections.data.CollectionTreeBuilder
import org.zotero.android.screens.collections.data.CollectionsArgs
import org.zotero.android.screens.collections.data.CollectionsError
import org.zotero.android.screens.dashboard.data.ShowDashboardLongPressBottomSheet
import org.zotero.android.sync.Collection
import org.zotero.android.sync.CollectionIdentifier
import org.zotero.android.sync.Library
import org.zotero.android.sync.LibraryIdentifier
import org.zotero.android.uicomponents.bottomsheet.LongPressOptionItem
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.reflect.KClass

@HiltViewModel
internal class CollectionsViewModel @Inject constructor(
    private val defaults: Defaults,
    private val dbWrapperMain: DbWrapperMain,
    private val fileStore: FileStore,
    dispatchers: Dispatchers,
) : BaseViewModel2<CollectionsViewState, CollectionsViewEffect>(CollectionsViewState()) {

    var allItems: RealmResults<RItem>? = null
    var unfiledItems: RealmResults<RItem>? = null
    var trashItems: RealmResults<RItem>? = null
    var collections: RealmResults<RCollection>? = null

    var isTablet: Boolean = false

    private var coroutineScope = CoroutineScope(dispatchers.default)
    private var loadJob: Job? = null

    private var collectionTree: CollectionTree = CollectionTree(
        mutableListOf(),
        ConcurrentHashMap(),
        ConcurrentHashMap()
    )

    private var libraryId: LibraryIdentifier = LibraryIdentifier.group(0)
    private var library: Library = Library(
        identifier = LibraryIdentifier.group(0),
        name = "",
        metadataEditable = false,
        filesEditable = false
    )


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: LongPressOptionItem) {
        onLongPressOptionsItemSelected(event)
    }

    fun init(isTablet: Boolean) = initOnce {
        EventBus.getDefault().register(this)
        this.isTablet = isTablet
        viewModelScope.launch {
            val args = ScreenArguments.collectionsArgs
            initViewState(args)
            loadData()
        }
    }

    private fun maybeRecreateItemsScreen(shouldRecreateItemsScreen: Boolean) {
        if (shouldRecreateItemsScreen) {
            val collectionTree = this.collectionTree
            onItemTapped(collectionTree.collections[viewState.selectedCollectionId]!!)
        }
    }

    private fun initViewState(args: CollectionsArgs) {
        this.collectionTree = CollectionTree(
            nodes = mutableListOf(),
            collections = ConcurrentHashMap(),
            collapsed = ConcurrentHashMap()
        )
        this.libraryId = args.libraryId
        this.library = Library(
            identifier = LibraryIdentifier.custom(RCustomLibraryType.myLibrary),
            name = "",
            metadataEditable = false,
            filesEditable = false
        )

        updateState {
            copy(
                selectedCollectionId = args.selectedCollectionId,
            )
        }

    }

    private fun loadData() {
        val libraryId = this.libraryId
        val includeItemCounts = defaults.showCollectionItemCounts()

        try {
            dbWrapperMain.realmDbStorage.perform { coordinator ->
                this.library =
                    coordinator.perform(request = ReadLibraryDbRequest(libraryId = libraryId))
                collections =
                    coordinator.perform(request = ReadCollectionsDbRequest(libraryId = libraryId))
                viewModelScope.launch {
                    updateState {
                        copy(
                            libraryName = this@CollectionsViewModel.library.name,
                            lce = LCE2.Content
                        )
                    }
                }

                var allItemCount = 0
                var unfiledItemCount = 0
                var trashItemCount = 0

                if (includeItemCounts) {
                    allItems = coordinator.perform(
                        request = ReadItemsDbRequest(
                            collectionId = CollectionIdentifier.custom(
                                CollectionIdentifier.CustomType.all
                            ),
                            libraryId = libraryId,
                            defaults = defaults,
                            isAsync = false,
                        )
                    )
                    allItemCount = allItems!!.size

                    unfiledItems = coordinator.perform(
                        request = ReadItemsDbRequest(
                            collectionId = CollectionIdentifier.custom(
                                CollectionIdentifier.CustomType.unfiled
                            ),
                            libraryId = libraryId,
                            defaults = defaults,
                            isAsync = false,
                        )
                    )
                    unfiledItemCount = unfiledItems!!.size

                    trashItems = coordinator.perform(
                        request = ReadItemsDbRequest(
                            collectionId = CollectionIdentifier.custom(
                                CollectionIdentifier.CustomType.trash
                            ),
                            libraryId = libraryId,
                            defaults = defaults,
                            isAsync = false,
                        )
                    )
                    trashItemCount = trashItems!!.size
                    observeItemCount(
                        results = allItems!!,
                        customType = CollectionIdentifier.CustomType.all
                    )
                    observeItemCount(
                        results = unfiledItems!!,
                        customType = CollectionIdentifier.CustomType.unfiled
                    )
                    observeItemCount(
                        results = trashItems!!,
                        customType = CollectionIdentifier.CustomType.trash
                    )
                }
                collections?.addChangeListener(OrderedRealmCollectionChangeListener<RealmResults<RCollection>> { objects, changeSet ->
                    when (changeSet.state) {
                        OrderedCollectionChangeSet.State.INITIAL -> {
                            //no-op
                        }

                        OrderedCollectionChangeSet.State.UPDATE -> {
                            update(collections = objects, includeItemCounts = includeItemCounts)
                        }

                        OrderedCollectionChangeSet.State.ERROR -> {
                            Timber.e(
                                changeSet.error,
                                "CollectionsViewModel: could not load results"
                            )
                        }

                        else -> {
                            //no-op
                        }
                    }
                })
                val frozenCollections = collections!!.freeze()
                loadJob = coroutineScope.launch {

                    val collectionTree = CollectionTreeBuilder.collections(
                        rCollections = frozenCollections,
                        libraryId = libraryId,
                        includeItemCounts = includeItemCounts
                    )

                    collectionTree.sortNodes()

                    collectionTree.insert(
                        collection = Collection.initWithCustomType(
                            type = CollectionIdentifier.CustomType.all,
                            itemCount = allItemCount
                        ), index = 0
                    )
                    collectionTree.append(
                        collection = Collection.initWithCustomType(
                            type = CollectionIdentifier.CustomType.unfiled,
                            itemCount = unfiledItemCount
                        )
                    )
                    collectionTree.append(
                        collection = Collection.initWithCustomType(
                            type = CollectionIdentifier.CustomType.trash,
                            itemCount = trashItemCount
                        )
                    )
                    val snapshot = collectionTree.createSnapshot()
                    viewModelScope.launch {
                        updateCollectionTree(collectionTree, snapshot)
                        maybeRecreateItemsScreen(ScreenArguments.collectionsArgs.shouldRecreateItemsScreen)
                    }

                }
            }
        } catch (error: Exception) {
            Timber.e(error, "CollectionsActionHandlers: can't load data")
            updateState {
                copy(error = CollectionsError.dataLoading)
            }
        }
    }

    private fun updateCollectionTree(
        collectionTree: CollectionTree,
        snapshot: List<CollectionItemWithChildren>
    ) {
        this.collectionTree = collectionTree
        updateState {
            copy(
                collectionItemsToDisplay = snapshot.toImmutableList()
            )
        }
        expandCollectionsIfNeeded()
        triggerEffect(CollectionsViewEffect.ScreenRefresh)
    }

    private fun expandCollectionsIfNeeded() {
        if (!isTablet) {
            return
        }
        val listOfParentsToExpand = traverseCollectionTreeForSelectedCollection(
            items = viewState.collectionItemsToDisplay,
            listOfParents = listOf()
        )
        for (parent in listOfParentsToExpand.second) {
            this.collectionTree.set(false, parent)
        }
    }

    private fun traverseCollectionTreeForSelectedCollection(
        items: List<CollectionItemWithChildren>,
        listOfParents: List<CollectionIdentifier>
    ): Pair<Boolean, List<CollectionIdentifier>> {
        for (item in items) {
            if (item.collection.identifier == viewState.selectedCollectionId) {
                return true to listOfParents
            }
            val traverseResult = traverseCollectionTreeForSelectedCollection(
                items = item.children,
                listOfParents = listOfParents + item.collection.identifier
            )
            if (traverseResult.first) {
                return traverseResult
            }
        }
        return false to emptyList()
    }

    private fun observeItemCount(
        results: RealmResults<RItem>,
        customType: CollectionIdentifier.CustomType
    ) {
        results.addChangeListener(OrderedRealmCollectionChangeListener<RealmResults<RItem>> { items, changeSet ->
            when (changeSet.state) {
                OrderedCollectionChangeSet.State.INITIAL -> {
                    //no-op
                }

                OrderedCollectionChangeSet.State.UPDATE -> {
                    update(itemsCount = items.size, customType = customType)
                }

                OrderedCollectionChangeSet.State.ERROR -> {
                    Timber.e(changeSet.error, "CollectionsViewModel: could not load results")
                }

                else -> {
                    //no-op
                }
            }
        })
    }

    private fun update(itemsCount: Int, customType: CollectionIdentifier.CustomType) {
        val collectionTree = this.collectionTree
        collectionTree.update(
            collection = Collection.initWithCustomType(
                type = customType,
                itemCount = itemsCount
            )
        )
        updateCollectionTree(collectionTree, collectionTree.createSnapshot())
    }

    private fun update(collections: RealmResults<RCollection>, includeItemCounts: Boolean) {
        val tree = CollectionTreeBuilder.collections(
            collections,
            libraryId = this.libraryId,
            includeItemCounts = includeItemCounts
        )
        tree.sortNodes()
        val collectionTree = this.collectionTree
        collectionTree.replace(matching = { it.isCollection }, tree = tree)
        updateCollectionTree(collectionTree, collectionTree.createSnapshot())

        if (this.collectionTree.collection(viewState.selectedCollectionId) == null) {
            val collection =
                collectionTree.collections[CollectionIdentifier.custom(CollectionIdentifier.CustomType.all)]!!
            onItemTapped(collection)
        }
//        triggerEffect(CollectionsViewEffect.ScreenRefresh)
    }

    fun onItemTapped(collection: Collection) {
        updateState {
            copy(selectedCollectionId = collection.identifier)
        }
        fileStore.setSelectedCollectionId(collection.identifier)
        ScreenArguments.allItemsArgs = AllItemsArgs(
            collection = collection,
            library = this.library,
            searchTerm = null,
            error = null
        )
        triggerEffect(CollectionsViewEffect.NavigateToAllItemsScreen)
    }

    fun onItemChevronTapped(collection: Collection) {
        val tree = this.collectionTree
        val libraryId = this.libraryId
        val collapsed = tree.collapsed[collection.identifier] ?: return
        tree.set(
            collapsed = !collapsed, collection.identifier
        )
        this.collectionTree = tree

        val request = SetCollectionCollapsedDbRequest(
            collapsed = !collapsed,
            identifier = collection.identifier,
            libraryId = libraryId
        )
        viewModelScope.launch {
            perform(dbWrapperMain, request)
                .ifFailure {
                    Timber.e(it, "CollectionsActionHandler: can't change collapsed")
                    return@launch
                }
        }
        triggerEffect(CollectionsViewEffect.ScreenRefresh)
    }

    override fun onCleared() {
        loadJob?.cancel()
        EventBus.getDefault().unregister(this)
        allItems?.removeAllChangeListeners()
        unfiledItems?.removeAllChangeListeners()
        trashItems?.removeAllChangeListeners()
        collections?.removeAllChangeListeners()
        super.onCleared()
    }

    fun onAdd() {
        ScreenArguments.collectionEditArgs = CollectionEditArgs(
            library = this.library,
            key = null,
            name = "",
            parent = null,
        )
        triggerEffect(CollectionsViewEffect.ShowCollectionEditEffect)
    }

    fun onItemLongTapped(collection: Collection) {
        if (!this.library.metadataEditable) {
            return
        }
        val actions = mutableListOf<LongPressOptionItem>()

        when (collection.identifier) {
            is CollectionIdentifier.collection -> {
                actions.add(LongPressOptionItem.CollectionEdit(collection))
                actions.add(LongPressOptionItem.CollectionNewSubCollection(collection))
                actions.add(LongPressOptionItem.CollectionDelete(collection))
            }

            is CollectionIdentifier.custom -> {
                //TODO
                return
            }

            is CollectionIdentifier.search -> {
                return
            }
        }
        EventBus.getDefault().post(
            ShowDashboardLongPressBottomSheet(
                title = collection.name,
                longPressOptionItems = actions
            )
        )

    }

    private fun onLongPressOptionsItemSelected(longPressOptionItem: LongPressOptionItem) {
        viewModelScope.launch {
            when (longPressOptionItem) {
                is LongPressOptionItem.CollectionEdit -> {
                    onEdit(longPressOptionItem.collection)
                }

                is LongPressOptionItem.CollectionNewSubCollection -> {
                    onAddSubcollection(longPressOptionItem.collection)
                }

                is LongPressOptionItem.CollectionDelete -> {
                    deleteCollection(
                        clazz = RCollection::class,
                        listOf(longPressOptionItem.collection.identifier.keyGet!!)
                    )
                }

                else -> {
                    //no-op
                }
            }
        }
    }

    private suspend fun deleteCollection(clazz: KClass<out RealmModel>, keys: List<String>) {
        val request = MarkObjectsAsDeletedDbRequest(
            clazz = clazz,
            keys = keys,
            libraryId = this.library.identifier
        )
        perform(
            dbWrapper = dbWrapperMain,
            request = request
        ).ifFailure {
            Timber.e(it, "CollectionsActionHandler: can't delete object")
            updateState {
                copy(error = CollectionsError.deletion)
            }
            return
        }
    }

    private fun onEdit(collection: Collection) {
        val parentKey = this.collectionTree.parent(collection.identifier)?.keyGet
        val parent: Collection?
        if (parentKey != null) {
            val request =
                ReadCollectionDbRequest(libraryId = this.library.identifier, key = parentKey)
            val rCollection = dbWrapperMain.realmDbStorage.perform(request = request)
            parent = Collection.initWithCollection(objectS = rCollection, itemCount = 0)
        } else {
            parent = null
        }
        ScreenArguments.collectionEditArgs = CollectionEditArgs(
            library = this.library,
            key = collection.identifier.keyGet,
            name = collection.name,
            parent = parent,
        )
        triggerEffect(CollectionsViewEffect.ShowCollectionEditEffect)
    }

    private fun onAddSubcollection(collection: Collection) {
        ScreenArguments.collectionEditArgs = CollectionEditArgs(
            library = this.library,
            key = null,
            name = "",
            parent = collection,
        )
        triggerEffect(CollectionsViewEffect.ShowCollectionEditEffect)
    }

    fun navigateToLibraries() {
        ScreenArguments.collectionsArgs = CollectionsArgs(
            libraryId = fileStore.getSelectedLibrary(),
            fileStore.getSelectedCollectionId()
        )
        triggerEffect(CollectionsViewEffect.NavigateToLibrariesScreen)
    }

    fun isCollapsed(snapshot: CollectionItemWithChildren): Boolean {
        return collectionTree.collapsed[snapshot.collection.identifier]!!
    }

    fun showCollectionItemCounts(): Boolean {
        return defaults.showCollectionItemCounts()
    }

}

internal data class CollectionsViewState(
    val libraryName: String = "",
    val collectionItemsToDisplay: ImmutableList<CollectionItemWithChildren> = persistentListOf(),
    val selectedCollectionId: CollectionIdentifier = CollectionIdentifier.custom(
        CollectionIdentifier.CustomType.all
    ),
    val editingData: Triple<String?, String, Collection?>? = null,
    val error: CollectionsError? = null,
    val lce: LCE2 = LCE2.Loading,
) : ViewState {
}

internal sealed class CollectionsViewEffect : ViewEffect {
    object NavigateBack : CollectionsViewEffect()
    object NavigateToAllItemsScreen : CollectionsViewEffect()
    object NavigateToLibrariesScreen : CollectionsViewEffect()
    object ShowCollectionEditEffect : CollectionsViewEffect()
    object ScreenRefresh : CollectionsViewEffect()
}
