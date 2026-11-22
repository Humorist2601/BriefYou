package com.ay.briefyou.infrastructure.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import com.ay.briefyou.domain.service.RssService
import com.ay.briefyou.domain.data.DiffMapHolder
import com.ay.briefyou.domain.service.AccountService
import com.ay.briefyou.infrastructure.preference.SettingsProvider
import com.ay.briefyou.infrastructure.rss.ReaderCacheHelper
import com.ay.briefyou.infrastructure.rss.RssHelper
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheHolderModule {
    @Provides
    @Singleton
    fun provideDiffMapHolder(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
        accountService: AccountService,
        rssService: RssService,
    ): DiffMapHolder {
        return DiffMapHolder(
            context = context, applicationScope, ioDispatcher, accountService, rssService
        )
    }

    @Provides
    @Singleton
    fun provideCacheHelper(
        @ApplicationContext context: Context,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
        rssHelper: RssHelper,
        accountService: AccountService,
    ): ReaderCacheHelper = ReaderCacheHelper(
        context = context, ioDispatcher = ioDispatcher,
        rssHelper = rssHelper,
        accountService = accountService,
    )
}