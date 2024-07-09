package org.zotero.android.api.module

import dagger.Module
import dagger.Provides
import dagger.hilt.migration.DisableInstallInCheck
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.zotero.android.api.NetworkConfiguration
import org.zotero.android.api.WebDavAuthNetworkInterceptor
import org.zotero.android.api.annotations.ForWebDav
import org.zotero.android.ktx.setNetworkTimeout
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton


@Module
@DisableInstallInCheck
object WebDavModule {

    @Provides
    @Singleton
    @ForWebDav
    fun provideWebDavOkHttpClient(
        configuration: NetworkConfiguration,
        webDavAuthNetworkInterceptor: WebDavAuthNetworkInterceptor,
    ): OkHttpClient {
        val connectionPool = ConnectionPool(
            maxIdleConnections = 10,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        )
        val dispatcher = Dispatcher()
        dispatcher.maxRequests = 30
        dispatcher.maxRequestsPerHost = 30

//        val interceptor = HttpLoggingInterceptor()
//        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)

        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .setNetworkTimeout(15L)
            .addInterceptor(webDavAuthNetworkInterceptor)
//            .addInterceptor(interceptor)
            .build()
    }

    @Provides
    @ForWebDav
    fun provideWebDavRetrofitBuilder(): Retrofit.Builder {
        return Retrofit.Builder()
    }

    @Provides
    @Singleton
    @ForWebDav
    fun provideWebDavRetrofit(
        @ForWebDav retrofitBuilder: Retrofit.Builder,
        @ForWebDav okHttpClient: OkHttpClient
    ): Retrofit {
        return retrofitBuilder
            .baseUrl("https://dummyurl.com") //no-op as all URLs for webdav are absolute
            .client(okHttpClient)
            .build()
    }
}