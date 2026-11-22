package com.ay.briefyou.infrastructure.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import com.ay.briefyou.domain.repository.AccountDao
import com.ay.briefyou.domain.repository.ArticleDao
import com.ay.briefyou.domain.repository.FeedDao
import com.ay.briefyou.domain.repository.GroupDao
import com.ay.briefyou.domain.service.AccountService
import com.ay.briefyou.domain.service.RssService
import com.ay.briefyou.infrastructure.preference.SettingsProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AccountServiceModule {
    @Provides
    @Singleton
    fun provideAccountService(
        @ApplicationContext context: Context,
        accountDao: AccountDao,
        groupDao: GroupDao,
        feedDao: FeedDao,
        articleDao: ArticleDao,
        @ApplicationScope coroutineScope: CoroutineScope,
        settingsProvider: SettingsProvider,
    ): AccountService {
        return AccountService(
            context = context,
            accountDao = accountDao,
            groupDao = groupDao,
            feedDao = feedDao,
            articleDao = articleDao,
            coroutineScope = coroutineScope,
            settingsProvider = settingsProvider,
        )
    }
}