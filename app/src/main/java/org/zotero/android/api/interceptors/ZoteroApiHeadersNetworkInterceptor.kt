package org.zotero.android.api.interceptors

import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import org.zotero.android.sync.SchemaController
import javax.inject.Inject

class ZoteroApiHeadersNetworkInterceptor @Inject constructor(
    private val schemaController: SchemaController
) : Interceptor {

    override fun intercept(chain: Chain): Response {
        val request = chain.request()
        val clientInfoRequest = request.newBuilder()
            .header("Zotero-API-Version", 3.toString())
            .header("Zotero-Schema-Version", schemaController.version.toString())
            .build()
        return chain.proceed(clientInfoRequest)
    }
}
