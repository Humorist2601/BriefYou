package com.ay.briefyou.infrastructure.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import com.ay.briefyou.domain.data.ArticlePagingListUseCase
import com.ay.briefyou.domain.data.DiffMapHolder
import com.ay.briefyou.domain.data.FilterStateUseCase
import com.ay.briefyou.domain.data.GroupWithFeedsListUseCase
import com.ay.briefyou.domain.service.AccountService
import com.ay.briefyou.brief.feeds.FeedOrdering // Brief You
import com.ay.briefyou.domain.service.RssService
import com.ay.briefyou.infrastructure.android.AndroidStringsHelper
import com.ay.briefyou.infrastructure.preference.SettingsProvider

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun providesArticlePagingList(
        rssService: RssService,
        androidStringsHelper: AndroidStringsHelper,
        @ApplicationScope applicationScope: CoroutineScope,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
        settingsProvider: SettingsProvider,
        filterStateUseCase: FilterStateUseCase,
        accountService: AccountService,
    ): ArticlePagingListUseCase {
        return ArticlePagingListUseCase(
            rssService,
            androidStringsHelper,
            applicationScope,
            ioDispatcher,
            settingsProvider,
            filterStateUseCase,
            accountService,
        )
    }

    @Provides
    @Singleton
    fun providesGroupWithFeedsList(
        @ApplicationScope applicationScope: CoroutineScope,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
        settingsProvider: SettingsProvider,
        rssService: RssService,
        filterStateUseCase: FilterStateUseCase,
        diffMapHolder: DiffMapHolder,
        accountService: AccountService,
        feedOrdering: FeedOrdering, // Brief You
    ): GroupWithFeedsListUseCase {
        return GroupWithFeedsListUseCase(
            applicationScope,
            ioDispatcher,
            settingsProvider,
            rssService,
            filterStateUseCase,
            diffMapHolder,
            accountService,
            feedOrdering, // Brief You
        )
    }
}
