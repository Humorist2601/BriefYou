package me.ash.reader.infrastructure.android

import me.ash.reader.ui.ext.dataStore
import me.ash.reader.infrastructure.preference.DarkThemePreference
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.lifecycleScope
import android.app.UiModeManager
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import me.ash.reader.ui.page.splash.BriefSplash
import android.Manifest
import android.content.Intent
import android.database.CursorWindow
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.app.NotificationManagerCompat
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.profileinstaller.ProfileInstallerInitializer
import androidx.work.WorkManager
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import java.lang.reflect.Field
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.WidgetUpdateWorker
import me.ash.reader.infrastructure.compose.ProvideCompositionLocals
import me.ash.reader.infrastructure.preference.AccountSettingsProvider
import me.ash.reader.infrastructure.preference.InitialPagePreference
import me.ash.reader.infrastructure.preference.LanguagesPreference
import me.ash.reader.infrastructure.preference.LocalDarkTheme
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.ui.ext.initialPage
import me.ash.reader.ui.ext.isFirstLaunch
import me.ash.reader.ui.ext.languages
import me.ash.reader.ui.page.common.ExtraName
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeViewModel
import me.ash.reader.ui.page.nav3.AppEntry
import me.ash.reader.ui.page.nav3.key.Route
import me.ash.reader.ui.page.nav3.key.Route.*
import me.ash.reader.ui.theme.AppTheme

/** The Single-Activity Architecture. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var imageLoader: ImageLoader

    @Inject lateinit var settingsProvider: SettingsProvider

    @Inject lateinit var accountService: AccountService

    @Inject lateinit var workManager: WorkManager

    @Inject lateinit var filterUseCase: FilterStateUseCase

    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("RLog", "onCreate: ${ProfileInstallerInitializer().create(this)}")

        enableEdgeToEdge()

        // The animated splash plays on fresh launches only (not when the activity is recreated).
        // Its first frame is the system splash's (the straight card), so the system splash is
        // removed at once and BriefSplash tips the card and carries on.
        val playSplash = savedInstanceState == null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && playSplash) {
            splashScreen.setOnExitAnimationListener { view -> view.remove() }
        }

        syncSplashNightMode()

        // Set the language
        if (Build.VERSION.SDK_INT < 33) {
            LanguagesPreference.fromValue(languages).let { LanguagesPreference.setLocale(it) }
        }

        // Workaround for https://github.com/ReadYouApp/ReadYou/issues/312: increase cursor window
        // size
        try {
            val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 100 * 1024 * 1024) // 100MB is the new cursor window size
        } catch (e: Exception) {
            Log.e("RLog", "Unable to increase cursor window size: ${e.printStackTrace()}")
        }

        val requestPermissionLauncher =
            this.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted
                ->
                if (isGranted) {} else { // Permission denied }
                }
            }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationManagerCompat.from(this).areNotificationsEnabled()
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AccountSettingsProvider(accountService = accountService) {
                settingsProvider.ProvidesSettings {
                    val subscribeViewModel: SubscribeViewModel = hiltViewModel()

                    ProvideCompositionLocals {
                        AppTheme(useDarkTheme = LocalDarkTheme.current.isDarkTheme()) {
                            val isFirstLaunch = remember { isFirstLaunch }
                            val initialPage = remember { initialPage }
                            val launchAction = intent.getLaunchAction()

                            val startDestination = remember {
                                if (isFirstLaunch) listOf(Route.Startup)
                                else
                                    when (launchAction) {
                                        is LaunchAction.OpenArticle -> {
                                            filterUseCase.init(
                                                launchAction.feedId,
                                                launchAction.groupId,
                                            )
                                            listOf(
                                                Route.Feeds,
                                                Route.Reading(launchAction.articleId),
                                            )
                                        }
                                        is LaunchAction.Subscribe -> {
                                            subscribeViewModel.handleSharedUrlFromIntent(
                                                launchAction.url
                                            )
                                            listOf(Route.Feeds)
                                        }
                                        else -> {
                                            if (
                                                initialPage == InitialPagePreference.FlowPage.value
                                            ) {
                                                listOf(Route.Feeds, Route.Reading(null))
                                            } else listOf(Route.Feeds)
                                        }
                                    }
                            }

                            val backStack = rememberNavBackStack(*startDestination.toTypedArray())

                            NewIntentHandlerEffect(backStack, subscribeViewModel)
                            var showSplash by remember { mutableStateOf(playSplash) }
                            Box(modifier = Modifier.fillMaxSize()) {
                                AppEntry(backStack)
                                if (showSplash) {
                                    BriefSplash(
                                        onFinished = { showSplash = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun NewIntentHandlerEffect(
        backStack: NavBackStack<NavKey>,
        subscribeViewModel: SubscribeViewModel,
    ) {
        val scope = rememberCoroutineScope()
        DisposableEffect(backStack) {
            val listener =
                Consumer<Intent> { intent ->
                    intent.getLaunchAction()?.let { action ->
                        when (action) {
                            is LaunchAction.OpenArticle -> {
                                val (articleId, feedId, groupId) = action
                                filterUseCase.init(feedId, groupId)
                                val readingIndex = backStack.indexOfFirst { it is Route.Reading }
                                if (readingIndex != -1) {
                                    repeat(backStack.size - readingIndex) {
                                        backStack.removeLastOrNull()
                                    }
                                }
                                scope.launch {
                                    delay(500L)
                                    backStack.add(Reading(articleId = articleId))
                                }
                            }

                            is LaunchAction.OpenArticleList -> {
                                val (accountId, feedId, groupId) = action
                                if (
                                    accountId != null &&
                                        accountId != accountService.getCurrentAccountId()
                                )
                                    return@Consumer
                                filterUseCase.init(feedId, groupId)
                                val readingIndex = backStack.indexOfFirst { it is Reading }
                                if (readingIndex != -1) {
                                    repeat(backStack.size - readingIndex) {
                                        backStack.removeLastOrNull()
                                    }
                                }
                                backStack.add(Reading(articleId = null))
                            }

                            is LaunchAction.Subscribe -> {
                                subscribeViewModel.handleSharedUrlFromIntent(action.url)
                                val feedsIndex = backStack.indexOf(Route.Feeds)
                                if (feedsIndex != -1) {
                                    repeat(backStack.size - (feedsIndex + 1)) {
                                        backStack.removeLastOrNull()
                                    }
                                } else {
                                    backStack.add(0, Route.Feeds)
                                    repeat(backStack.size - 1) { backStack.removeLastOrNull() }
                                }
                            }
                        }
                    }
                }
            listener.accept(intent) // consume the launch intent as well
            addOnNewIntentListener(listener)
            onDispose { removeOnNewIntentListener(listener) }
        }
    }

    override fun onResume() {
        WidgetUpdateWorker.enqueueOneTimeWork(workManager)
        notificationHelper.clearDigest()
        super.onResume()
    }

    /**
     * The system splash is drawn before the app runs, from the phone's light or dark mode. Tell
     * Android which one the app uses (its Dark theme setting), so the splash on the next launch
     * matches the app: a light splash for a light app on a dark phone, and the other way round.
     */
    private fun syncSplashNightMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = getSystemService(UiModeManager::class.java) ?: return
        lifecycleScope.launch {
            dataStore.data
                .map { DarkThemePreference.fromPreferences(it) }
                .distinctUntilChanged()
                .collect { preference ->
                    val mode =
                        when (preference) {
                            DarkThemePreference.ON -> UiModeManager.MODE_NIGHT_YES
                            DarkThemePreference.OFF -> UiModeManager.MODE_NIGHT_NO
                            else -> UiModeManager.MODE_NIGHT_AUTO
                        }
                    // Only when it changed: setting it can restart the screen.
                    val prefs = getSharedPreferences("splash_night_mode", MODE_PRIVATE)
                    if (prefs.getInt("mode", -1) != mode) {
                        prefs.edit().putInt("mode", mode).apply()
                        uiModeManager.setApplicationNightMode(mode)
                    }
                }
        }
    }
}

sealed interface LaunchAction {
    data class Subscribe(val url: String) : LaunchAction

    data class OpenArticle(val articleId: String, val feedId: String?, val groupId: String?) :
        LaunchAction

    data class OpenArticleList(val accountId: Int?, val feedId: String?, val groupId: String?) :
        LaunchAction
}

private fun Intent.getLaunchAction(): LaunchAction? {
    return when (action) {
        Intent.ACTION_VIEW -> {
            dataString?.let { LaunchAction.Subscribe(it) }
        }

        Intent.ACTION_SEND -> {
            getStringExtra(Intent.EXTRA_TEXT)
                ?.also { removeExtra(Intent.EXTRA_TEXT) }
                ?.let { LaunchAction.Subscribe(it) }
        }

        else -> {
            val articleId =
                getStringExtra(ExtraName.ARTICLE_ID)?.also { removeExtra(ExtraName.ARTICLE_ID) }
            val feedId = getStringExtra(ExtraName.FEED_ID)?.also { removeExtra(ExtraName.FEED_ID) }
            val groupId =
                getStringExtra(ExtraName.GROUP_ID)?.also { removeExtra(ExtraName.GROUP_ID) }
            val accountId = getIntExtra(ExtraName.ACCOUNT_ID, -1)

            if (accountId != -1) {
                removeExtra(ExtraName.ACCOUNT_ID)
            }

            if (articleId != null) {
                LaunchAction.OpenArticle(articleId, feedId, groupId)
            } else if (feedId != null || groupId != null || accountId != -1) {
                LaunchAction.OpenArticleList(
                    accountId = if (accountId != -1) accountId else null,
                    feedId = feedId,
                    groupId = groupId,
                )
            } else {
                null
            }
        }
    }
}

/** How long the system splash takes to fade out over the identical first frame of BriefSplash. */
