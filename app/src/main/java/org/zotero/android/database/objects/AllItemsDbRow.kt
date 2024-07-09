package org.zotero.android.database.objects

import io.realm.RealmObject
import io.realm.annotations.RealmClass

@RealmClass(embedded = true)
open class AllItemsDbRow : RealmObject() {
    var typeIconName: String = ""
    var title: String = ""
    var subtitle: String = ""
    var hasNote: Boolean = false
}