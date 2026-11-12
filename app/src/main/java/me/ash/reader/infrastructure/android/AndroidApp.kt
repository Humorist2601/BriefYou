package me.ash.reader.infrastructure.android

import me.ash.reader.brief.notifications.NotificationHelper // Brief You
import kotlinx.coroutines.flow.map // Brief You
import kotlinx.coroutines.flow.distinctUntilChanged // Brief You
import kotlinx.coroutines.flow.debounce // Brief You

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.BuildConfig
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.AppService
import me.ash.reader.domain.service.LocalRssService
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.db.AndroidDatabase
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.net.NetworkDataSource
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.rss.OPMLDataSource
import me.ash.reader.infrastructure.rss.RssHelper
import me.ash.reader.ui.ext.del
import me.ash.reader.ui.ext.getLatestApk
import me.ash.reader.ui.ext.isGitHub
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

    @Inject lateinit var briefArticleDao: me.ash.reader.brief.data.BriefArticleDao // Brief You

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
        me.ash.reader.brief.reading.EmailContent.load(this) // Brief You: the Tidy newsletters setting
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
        Coil.setImageLoader(imageLoader)
        // Brief You: offline copies of starred articles follow the stars (from anywhere: a tap,
        // Read later, Triage, a sync); the first look also downloads stars from before.
        applicationScope.launch(ioDispatcher) { // Brief You
            @OptIn(kotlinx.coroutines.FlowPreview::class) // Brief You
            briefArticleDao.starredIdsFlow().map { it.toSet() }.distinctUntilChanged().debounce(2_000).collect { // Brief You
                me.ash.reader.brief.offline.OfflineCopies.schedule(this@AndroidApp) // Brief You
            } // Brief You
        } // Brief You
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
            me.ash.reader.brief.update.UpdateDownload.readyApk(applicationContext) // Brief You
        }
        if (!me.ash.reader.brief.update.UpdateCheck.onStart(applicationContext)) return // Brief You
        appService.checkUpdate(showToast = false)
    }
}
