package org.zotero.android.screens.itemdetails.data

class ItemDetailField(
    var key: String,
    var baseField: String?,
    var name: String,
    var value: String,
    var isTitle: Boolean,
    var isTappable: Boolean,
    var additionalInfo: Map<AdditionalInfoKey, String>? = null
) {
    enum class AdditionalInfoKey {
        dateOrder, formattedDate, formattedEditDate
    }

    val id: String get() { return this.key }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ItemDetailField

        if (key != other.key) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    fun deepClone(
        key: String = this.key,
        baseField: String? = this.baseField,
        name: String = this.name,
        value: String = this.value,
        isTitle: Boolean = this.isTitle,
        isTappable: Boolean = this.isTappable,
        additionalInfo: Map<AdditionalInfoKey, String>? = this.additionalInfo
    ): ItemDetailField {
        val clonedAdditionalInfo = mutableMapOf<AdditionalInfoKey, String>()
        for (ca in additionalInfo ?: emptyMap()) {
            clonedAdditionalInfo[ca.key] = ca.value
        }
        return ItemDetailField(
            key = key,
            baseField = baseField,
            name = name,
            value = value,
            isTitle = isTitle,
            isTappable = isTappable,
            additionalInfo = clonedAdditionalInfo
        )
    }

    val valueOrAdditionalInfo: String get() {
        return additionalInfo?.get(AdditionalInfoKey.formattedDate)
            ?: value
    }

}