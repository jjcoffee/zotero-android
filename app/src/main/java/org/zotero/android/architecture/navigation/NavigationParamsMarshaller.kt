package org.zotero.android.architecture.navigation

import com.google.gson.Gson
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationParamsMarshaller @Inject constructor(val gson: Gson) {

    fun encodeObjectToBase64(data: Any, charset: Charset = StandardCharsets.US_ASCII): String {
        val json = gson.toJson(data)
        val encodedJson = encodeJsonToBase64(stringToEncode = json, charset = charset)
        val escaped = encodedJson.replace('/', '+').replace('_', '-')
        return escaped
    }

    private fun encodeJsonToBase64(
        stringToEncode: String,
        charset: Charset
    ): String {
        val bytes = IOUtils.toByteArray(stringToEncode);
        val encoded: ByteArray = Base64.encodeBase64(bytes)
        return String(encoded, charset)
    }

    inline fun <reified T> decodeObjectFromBase64(
        encodedJson: String,
        charset: Charset = StandardCharsets.US_ASCII
    ): T {
        val unescaped = encodedJson.replace('-', '_').replace('+', '/')
        val decodedJson = decodeJsonFromBase64Binary(encodedJson = unescaped, charset = charset)
        return unmarshal(decodedJson)
    }

    inline fun <reified T> unmarshal(data: String): T {
        return gson.fromJson(data, T::class.java)
    }

    fun decodeJsonFromBase64Binary(
        encodedJson: String,
        charset: Charset
    ): String {
        val bytes = IOUtils.toByteArray(encodedJson);
        val decodedJson: ByteArray = Base64.decodeBase64(bytes)
        return String(decodedJson, charset)
    }

}