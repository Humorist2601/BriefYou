package me.ash.reader.infrastructure.android

import coil.ImageLoaderFactory
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

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var workManager: WorkManager

    @Inject lateinit var notificationHelper: NotificationHelper

    /** Used in the background only (update check), so it is built there. */
    @Inject lateinit var appService: dagger.Lazy<AppService>

    @Inject lateinit var androidStringsHelper: AndroidStringsHelper

    @Inject lateinit var accountService: AccountService

    /** Used in the background only (first account, sync set-up), so it is built there. */
    @Inject lateinit var rssService: dagger.Lazy<RssService>

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    @Inject @IODispatcher lateinit var ioDispatcher: CoroutineDispatcher

    /**
     * Built when the first picture is needed, not at launch (with the network client under it);
     * the other services the app used to build here at launch are also left to when they're used,
     * so the first screen shows sooner.
     */
    @Inject lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject lateinit var settingsProvider: SettingsProvider

    @Inject lateinit var diffMapHolder: DiffMapHolder

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
        Coil.setImageLoader(ImageLoaderFactory { imageLoader.get() })
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
                        rssService.get().get().doSyncOneTime(accountService.getCurrentAccountId())
                    }
            }
        }
    }

    private suspend fun workerInit() {
        rssService.get().get().initSync()
    }

    private suspend fun checkUpdate() {
        if (!isGitHub) return
        withContext(ioDispatcher) {
            applicationContext.getLatestApk().let { if (it.exists()) it.del() }
        }
        appService.get().checkUpdate(showToast = false)
    }
}
