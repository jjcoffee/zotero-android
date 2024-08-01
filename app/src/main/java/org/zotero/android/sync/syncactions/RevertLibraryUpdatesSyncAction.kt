package org.zotero.android.sync.syncactions

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import io.realm.RealmObject
import org.zotero.android.api.network.CustomResult
import org.zotero.android.database.RealmDbCoordinator
import org.zotero.android.database.objects.RCollection
import org.zotero.android.database.objects.RItem
import org.zotero.android.database.objects.RSearch
import org.zotero.android.database.objects.Syncable
import org.zotero.android.database.requests.ReadAnyChangedObjectsInLibraryDbRequest
import org.zotero.android.database.requests.StoreCollectionsDbRequest
import org.zotero.android.database.requests.StoreItemsDbResponseRequest
import org.zotero.android.database.requests.StoreSearchesDbRequest
import org.zotero.android.files.FileStore
import org.zotero.android.sync.LibraryIdentifier
import org.zotero.android.sync.StoreItemsResponse
import org.zotero.android.sync.SyncObject
import org.zotero.android.sync.syncactions.architecture.SyncAction
import timber.log.Timber
import java.io.FileReader
import kotlin.reflect.KClass

class RevertLibraryUpdatesSyncAction(
    private val libraryId: LibraryIdentifier,

) : SyncAction() {

    companion object {
        fun <T : RealmObject, K> loadCachedJsonForObject(
            clazz: KClass<T>,
            objectType: SyncObject,
            libraryId: LibraryIdentifier,
            coordinator: RealmDbCoordinator,
            fileStorage: FileStore,
            gson: Gson,
            createResponse: (JsonObject) -> K
        ): Pair<List<K>, List<String>> {
            val request = ReadAnyChangedObjectsInLibraryDbRequest(libraryId = libraryId, clazz = clazz)
            val objects = coordinator.perform(request = request)

            val responses = mutableListOf<K>()
            val failed = mutableListOf<String>()

            for (objectS in objects) {
                objectS as Syncable
                try {
                    val file = fileStorage.jsonCacheFile(
                        objectS = objectType,
                        libraryId = libraryId,
                        key = objectS.key
                    )
                    val jsonData: JsonObject = gson.fromJson(
                        JsonReader( FileReader(file)), JsonObject::class.java)
                    val response = createResponse(jsonData)
                    responses.add(response)
                } catch (error: Throwable) {
                    Timber.e(error, "RevertLibraryUpdatesSyncAction: can't load cached file")
                    failed.add(objectS.key)
                }
            }

            return responses to failed
        }

    }

    suspend fun result(): CustomResult<Map<SyncObject, List<String>>> {
        try {
            var changes = mutableListOf<StoreItemsResponse.FilenameChange>()
            var failedCollections = mutableListOf<String>()
            var failedSearches = mutableListOf<String>()
            var failedItems = mutableListOf<String>()

            dbWrapperMain.realmDbStorage.perform { coordinator ->
                val collections = loadCachedJsonForObject(
                    clazz = RCollection::class,
                    objectType = SyncObject.collection,
                    this.libraryId,
                    coordinator = coordinator,
                    gson = gson,
                    fileStorage = fileStore,
                    createResponse = { collectionResponseMapper.fromJson(it) })
                val searches = loadCachedJsonForObject(clazz = RSearch::class,
                    objectType = SyncObject.search,
                    this.libraryId,
                    coordinator = coordinator,
                    fileStorage = fileStore,
                    gson = gson,
                    createResponse = { searchResponseMapper.fromJson(it) })
                val items = loadCachedJsonForObject(clazz = RItem::class,
                    objectType = SyncObject.item,
                    this.libraryId,
                    coordinator = coordinator,
                    fileStorage = fileStore,
                    gson = gson,
                    createResponse = { itemResponseMapper.fromJson(it, schemaController) })

                val storeCollectionsRequest =
                    StoreCollectionsDbRequest(response = collections.first)
                val storeSearchesRequest = StoreSearchesDbRequest(response = searches.first)
                coordinator.perform(
                    requests = listOf(
                        storeCollectionsRequest,
                        storeSearchesRequest
                    )
                )
                val storeItemsRequest = StoreItemsDbResponseRequest(
                    responses = items.first,
                    schemaController = this.schemaController,
                    dateParser = this.dateParser,
                    preferResponseData = true,
                    denyIncorrectCreator = true
                )
                changes =
                    coordinator.perform(request = storeItemsRequest).changedFilenames.toMutableList()

                failedCollections = collections.second.toMutableList()
                failedSearches = searches.second.toMutableList()
                failedItems = items.second.toMutableList()
                coordinator.invalidate()
            }

            renameExistingFiles(changes = changes, libraryId = this.libraryId)
            return CustomResult.GeneralSuccess(
                mapOf(
                    SyncObject.collection to failedCollections,
                    SyncObject.search to failedSearches,
                    SyncObject.item to failedItems
                )
            )
        } catch (error: Throwable) {
            return CustomResult.GeneralError.CodeError(error)
        }
    }


    private fun renameExistingFiles(
        changes: List<StoreItemsResponse.FilenameChange>,
        libraryId: LibraryIdentifier
    ) {
        for (change in changes) {
            val oldFile = fileStore.attachmentFile(
                libraryId,
                key = change.key,
                filename = change.oldName,
            )
            if (!oldFile.exists()) {
                return
            }

            val newFile = fileStore.attachmentFile(
                libraryId,
                key = change.key,
                filename = change.newName,
            )
            if (!oldFile.renameTo(newFile)) {
                Timber.w("RevertLibraryUpdatesSyncAction: can't rename file")
                oldFile.delete()
            }
        }
    }
}