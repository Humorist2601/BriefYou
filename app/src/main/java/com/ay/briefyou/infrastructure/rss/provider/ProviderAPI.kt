package com.ay.briefyou.infrastructure.rss.provider

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ay.briefyou.infrastructure.di.UserAgentInterceptor
import com.ay.briefyou.infrastructure.di.cachingHttpClient
import okhttp3.OkHttpClient

abstract class ProviderAPI(context: Context, clientCertificateAlias: String?) {

    protected val client: OkHttpClient = cachingHttpClient(
        context = context,
        clientCertificateAlias = clientCertificateAlias,
    )
        .newBuilder()
        .addNetworkInterceptor(UserAgentInterceptor)
        .build()

    protected val gson: Gson = GsonBuilder().create()

    protected inline fun <reified T> toDTO(jsonStr: String): T =
        gson.fromJson(jsonStr, T::class.java)!!
}
