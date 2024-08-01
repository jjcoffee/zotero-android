package org.zotero.android.database

import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.RealmConfiguration
import io.realm.annotations.RealmModule
import org.zotero.android.database.objects.AllItemsDbRow
import org.zotero.android.database.objects.FieldKeys
import org.zotero.android.database.objects.ItemTypes
import org.zotero.android.database.objects.RCollection
import org.zotero.android.database.objects.RCondition
import org.zotero.android.database.objects.RCreator
import org.zotero.android.database.objects.RCustomLibrary
import org.zotero.android.database.objects.RGroup
import org.zotero.android.database.objects.RItem
import org.zotero.android.database.objects.RItemField
import org.zotero.android.database.objects.RLink
import org.zotero.android.database.objects.RObjectChange
import org.zotero.android.database.objects.RPageIndex
import org.zotero.android.database.objects.RPath
import org.zotero.android.database.objects.RPathCoordinate
import org.zotero.android.database.objects.RRect
import org.zotero.android.database.objects.RRelation
import org.zotero.android.database.objects.RSearch
import org.zotero.android.database.objects.RTag
import org.zotero.android.database.objects.RTranslatorMetadata
import org.zotero.android.database.objects.RTypedTag
import org.zotero.android.database.objects.RUser
import org.zotero.android.database.objects.RVersions
import org.zotero.android.database.objects.RWebDavDeletion
import org.zotero.android.database.requests.key
import org.zotero.android.files.FileStore
import java.io.File

class Database {
    companion object {
        private const val schemaVersion = 2L //From now on must only increase by 1 whenever db schema changes

        fun mainConfiguration(dbFile: File): RealmConfiguration {
            val builder = RealmConfiguration.Builder()
                .directory(dbFile.parentFile!!)
                .name(dbFile.name)
                .modules(MainConfigurationDbModule())
                .schemaVersion(schemaVersion)
                .allowWritesOnUiThread(true)
                .migration { dynamicRealm, oldVersion, newVersion ->
                    if (oldVersion < 2) {
                        migrateAllItemsDbRowTypeIconNameTypeChange(dynamicRealm)
                    }
                }

            return builder.build()
        }

        private fun migrateAllItemsDbRowTypeIconNameTypeChange(dynamicRealm: DynamicRealm) {
            val realmSchema = dynamicRealm.schema

            val allItemsDbRowSchema = realmSchema.get(AllItemsDbRow::class.java.simpleName)

            allItemsDbRowSchema?.run {
                removeField("typeIconName")
                addField("typeIconName", String::class.java, FieldAttribute.REQUIRED)
                transform {
                    it.set("typeIconName", "")
                }
            }
            val allItems = dynamicRealm.where(RItem::class.java.simpleName).findAll()
            for (item in allItems) {
                val dbRow = item.getObject("allItemsDbRow")
                if (dbRow == null) {
                    println()
                    continue
                }

                val rawType = item.getString("rawType")
                val fields = item.getList("fields")

                val contentType = if (rawType == ItemTypes.attachment) fields.where().key(
                    FieldKeys.Item.Attachment.contentType
                ).findFirst()?.getString("value") else null
                dbRow.setString(
                    "typeIconName", ItemTypes.iconName(
                        rawType = rawType,
                        contentType = contentType
                    )
                )
            }

        }

        fun bundledDataConfiguration(dbFile: File): RealmConfiguration {
            val builder = RealmConfiguration.Builder()
                .directory(dbFile.parentFile!!)
                .name(dbFile.name)
                .modules(BundledDataConfigurationDbModule())
                .schemaVersion(schemaVersion)
                .allowWritesOnUiThread(true)
                .migration { dynamicRealm, oldVersion, newVersion ->
                    //no-op for bundle migration for now
                }
            return builder.build()
        }
    }
}

@RealmModule(
    library = false, classes = [
        RCollection::class,
        RCreator::class,
        RCustomLibrary::class,
        RGroup::class,
        RItem::class,
        RItemField::class,
        RLink::class,
        RPageIndex::class,
        RPath::class,
        RPathCoordinate::class,
        RRect::class,
        RRelation::class,
        RSearch::class,
        RCondition::class,
        RTag::class,
        RTypedTag::class,
        RUser::class,
        RWebDavDeletion::class,
        RVersions::class,
        RObjectChange::class,
        AllItemsDbRow::class,
    ]
)
data class MainConfigurationDbModule(val placeholder: String) { // empty data class for equals/hashcode
    constructor(): this("")
}

@RealmModule(library = false, classes=[
    RTranslatorMetadata::class
])
data class BundledDataConfigurationDbModule(val placeholder: String) { // empty data class for equals/hashcode
    constructor(): this("")
}