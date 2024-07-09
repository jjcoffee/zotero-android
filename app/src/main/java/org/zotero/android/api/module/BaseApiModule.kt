package org.zotero.android.api.module

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.migration.DisableInstallInCheck
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.zotero.android.BuildConfig
import org.zotero.android.api.ClientInfoNetworkInterceptor
import org.zotero.android.api.NetworkConfiguration
import org.zotero.android.api.annotations.ForBaseApi
import org.zotero.android.ktx.setNetworkTimeout
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton


@Module
@DisableInstallInCheck
object BaseApiModule {

    @Provides
    @Singleton
    @ForBaseApi
    fun provideBaseOkHttp(
        clientInfoNetworkInterceptor: ClientInfoNetworkInterceptor,
        configuration: NetworkConfiguration
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
            .setNetworkTimeout(configuration.networkTimeout)
            .addInterceptor(clientInfoNetworkInterceptor)
//            .addInterceptor(interceptor)
            .build()
    }

    @Provides
    @ForBaseApi
    fun provideBaseRetrofitBuilder(gson: Gson): Retrofit.Builder {
        val stringConverter = ScalarsConverterFactory.create()
        val gsonConverter = GsonConverterFactory.create(gson)
        return Retrofit.Builder()
            .addConverterFactory(stringConverter)
            .addConverterFactory(gsonConverter)
    }

    @Provides
    @Singleton
    @ForBaseApi
    fun provideRetrofit(
        @ForBaseApi baseBuilder: Retrofit.Builder,
        @ForBaseApi okHttpClient: OkHttpClient
    ): Retrofit {
        return baseBuilder
            .baseUrl(BuildConfig.BASE_API_URL)
            .client(okHttpClient)
            .build()
    }
}