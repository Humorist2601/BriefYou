package com.ay.briefyou.infrastructure.android

import com.ay.briefyou.brief.notifications.NotificationHelper // Brief You
import kotlinx.coroutines.flow.map // Brief You
import kotlinx.coroutines.flow.distinctUntilChanged // Brief You
import kotlinx.coroutines.flow.debounce // Brief You

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.SingletonImageLoader // Brief You: Coil 3
import coil3.ImageLoader // Brief You: Coil 3
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ay.briefyou.BuildConfig
import com.ay.briefyou.domain.data.DiffMapHolder
import com.ay.briefyou.domain.service.AccountService
import com.ay.briefyou.domain.service.AppService
import com.ay.briefyou.domain.service.LocalRssService
import com.ay.briefyou.domain.service.OpmlService
import com.ay.briefyou.domain.service.RssService
import com.ay.briefyou.infrastructure.db.AndroidDatabase
import com.ay.briefyou.infrastructure.di.ApplicationScope
import com.ay.briefyou.infrastructure.di.IODispatcher
import com.ay.briefyou.infrastructure.net.NetworkDataSource
import com.ay.briefyou.infrastructure.preference.SettingsProvider
import com.ay.briefyou.infrastructure.rss.OPMLDataSource
import com.ay.briefyou.infrastructure.rss.RssHelper
import com.ay.briefyou.ui.ext.del
import com.ay.briefyou.ui.ext.getLatestApk
import com.ay.briefyou.ui.ext.isGitHub
import okhttp3.OkHttpClient
import timber.log.Timber

/** The Application class, where the Dagger components is generated. */
@HiltAndroidApp
class AndroidApp : Application(), Configuration.Provider {

    /**
     * From: [Feeder](https://gitlab.com/spacecowboy/Feeder).
     *
     * Install Conscrypt to handle TLSv1.3 pre Android10.
     */
    init {
        // Cancel TLSv1.3 support pre Android10
        // Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }

    @Inject lateinit var androidDatabase: AndroidDatabase

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var workManager: WorkManager

    @Inject lateinit var networkDataSource: NetworkDataSource

    @Inject lateinit var OPMLDataSource: OPMLDataSource

    @Inject lateinit var rssHelper: RssHelper

    @Inject lateinit var notificationHelper: NotificationHelper

    @Inject lateinit var appService: AppService

    @Inject lateinit var androidStringsHelper: AndroidStringsHelper

    @Inject lateinit var accountService: AccountService

    @Inject lateinit var localRssService: LocalRssService

    @Inject lateinit var opmlService: OpmlService

    @Inject lateinit var rssService: RssService

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    @Inject @IODispatcher lateinit var ioDispatcher: CoroutineDispatcher

    @Inject lateinit var okHttpClient: OkHttpClient

    @Inject lateinit var imageLoader: ImageLoader

    @Inject lateinit var imageDownloader: AndroidImageDownloader

    @Inject lateinit var settingsProvider: SettingsProvider

    @Inject lateinit var diffMapHolder: DiffMapHolder

    @Inject lateinit var briefArticleDao: com.ay.briefyou.brief.data.BriefArticleDao // Brief You

    /**
     * When the application startup.
     * 1. Set the uncaught exception handler
     * 2. Initialize the default account if there is none
     * 3. Synchronize once
     * 4. Check for new version
     */
    override fun onCreate() {
        super.onCreate()
        CrashHandler(this)
        com.ay.briefyou.brief.reading.EmailContent.load(this) // Brief You: the Tidy newsletters setting
        com.ay.briefyou.brief.reading.ListReadingTime.start(this) { briefArticleDao.textOf(it) } // Brief You: the lists' reading times
        com.ay.briefyou.brief.feeds.FeedAccent.start(this) // Brief You: the feeds' colours, ready for the first list
        com.ay.briefyou.brief.media.ArticleMedia.start(this, briefArticleDao) // Brief You: articles' episodes, videos and comments links
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        applicationScope.launch {
            accountInit()
            workerInit()
            checkUpdate()
        }
        // Brief You: AI summaries are no longer saved; remove any an older version saved.
        applicationScope.launch(ioDispatcher) {
            runCatching { java.io.File(filesDir, "ai_cache").deleteRecursively() }
        }
        SingletonImageLoader.setSafe { imageLoader } // Brief You: Coil 3 (the app's loader, made by Hilt)
        // Brief You: offline copies of starred articles follow the stars (from anywhere: a tap,
        // Read later, Triage, a sync); the first look also downloads stars from before.
        applicationScope.launch(ioDispatcher) { // Brief You
            @OptIn(kotlinx.coroutines.FlowPreview::class) // Brief You
            briefArticleDao.starredIdsFlow().map { it.toSet() }.distinctUntilChanged().debounce(2_000).collect { // Brief You
                com.ay.briefyou.brief.offline.OfflineCopies.schedule(this@AndroidApp) // Brief You
            } // Brief You
        } // Brief You
        com.ay.briefyou.brief.backup.AutoBackup.schedule(this) // Brief You
    }

    /** Override the [Configuration.Builder] to provide the [HiltWorkerFactory]. */
    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setWorkerCoroutineContext(Dispatchers.IO)
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()

    private suspend fun accountInit() {
        withContext(ioDispatcher) {
            if (accountService.isNoAccount()) {
                launch { accountService.initWithDefaultAccount() }
                    .invokeOnCompletion {
                        rssService.get().doSyncOneTime(accountService.getCurrentAccountId())
                    }
            }
        }
    }

    private suspend fun workerInit() {
        rssService.get().initSync()
    }

    private suspend fun checkUpdate() {
        if (!isGitHub) return
        // Brief You: a downloaded update waits for Install; only one that's no longer newer (or
        // isn't this app) is removed. Off in Tips & support: no check when the app starts.
        withContext(ioDispatcher) {
            com.ay.briefyou.brief.update.UpdateDownload.readyApk(applicationContext) // Brief You
        }
        if (!com.ay.briefyou.brief.update.UpdateCheck.onStart(applicationContext)) return // Brief You
        appService.checkUpdate(showToast = false)
    }
}
