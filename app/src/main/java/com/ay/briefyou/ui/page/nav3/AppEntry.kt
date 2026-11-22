package com.ay.briefyou.ui.page.nav3

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import com.ay.briefyou.brief.app.NavEntry // Brief You: every screen gets the back preview
// Brief You
import com.ay.briefyou.brief.ai.AiSettingsPage
import com.ay.briefyou.brief.app.BriefNavigationHost
import com.ay.briefyou.brief.app.backPreviewTransition
import com.ay.briefyou.brief.filters.FiltersPage
import com.ay.briefyou.brief.ui.rememberBackPreviewRunning
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.delay
import com.ay.briefyou.ui.motion.materialSharedAxisXIn
import com.ay.briefyou.ui.motion.materialSharedAxisXOut
import com.ay.briefyou.ui.page.adaptive.ArticleData
import com.ay.briefyou.ui.page.adaptive.ArticleListReaderPage
import com.ay.briefyou.ui.page.adaptive.ArticleListReaderViewModel
import com.ay.briefyou.ui.page.home.feeds.FeedsPage
import com.ay.briefyou.ui.page.home.feeds.subscribe.SubscribeViewModel
import com.ay.briefyou.ui.page.nav3.key.Route
import com.ay.briefyou.ui.page.settings.SettingsPage
import com.ay.briefyou.ui.page.settings.accounts.AccountDetailsPage
import com.ay.briefyou.ui.page.settings.accounts.AccountViewModel
import com.ay.briefyou.ui.page.settings.accounts.AccountsPage
import com.ay.briefyou.ui.page.settings.accounts.AddAccountsPage
import com.ay.briefyou.ui.page.settings.color.ColorAndStylePage
import com.ay.briefyou.ui.page.settings.color.DarkThemePage
import com.ay.briefyou.ui.page.settings.color.lists.ListsStylePage
import com.ay.briefyou.ui.page.settings.color.reading.BoldCharactersPage
import com.ay.briefyou.ui.page.settings.color.reading.ReadingMediaPage
import com.ay.briefyou.ui.page.settings.color.reading.ReadingStylePage
import com.ay.briefyou.ui.page.settings.color.reading.ReadingTextPage
import com.ay.briefyou.ui.page.settings.color.reading.ReadingTitlePage
import com.ay.briefyou.ui.page.settings.interaction.InteractionPage
import com.ay.briefyou.ui.page.settings.tips.LicenseListPage
import com.ay.briefyou.ui.page.settings.tips.TipsAndSupportPage
import com.ay.briefyou.ui.page.settings.troubleshooting.TroubleshootingPage
import com.ay.briefyou.ui.page.startup.StartupPage

private const val INITIAL_OFFSET_FACTOR = 0.10f

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun AppEntry(backStack: NavBackStack<NavKey>) {
    val subscribeViewModel = hiltViewModel<SubscribeViewModel>()

    val onBack: () -> Unit = {
        if (backStack.size == 1) backStack[0] = Route.Feeds else backStack.removeLastOrNull()
    }

    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())

    val navigator =
        rememberListDetailPaneScaffoldNavigator<ArticleData>(
            scaffoldDirective = scaffoldDirective,
            isDestinationHistoryAware = false,
        )

    val backPreviewRunning = rememberBackPreviewRunning() // Brief You
    BriefNavigationHost(backStack) { // Brief You: back preview, shared filter bar
    SharedTransitionLayout {
        NavDisplay(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            backStack = backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            transitionSpec = {
                backPreviewTransition(backPreviewRunning, forward = true, readingPage = backStack.getOrNull(backStack.lastIndex - 1) is Route.Reading) ?: // Brief You: the article screen being left
                materialSharedAxisXIn(
                    initialOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() }
                ) togetherWith
                    materialSharedAxisXOut(
                        targetOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                    )
            },
            popTransitionSpec = {
                backPreviewTransition(backPreviewRunning, readingPage = backStack.lastOrNull() is Route.Reading) ?: // Brief You: the article screen coming back
                materialSharedAxisXIn(
                    initialOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                ) togetherWith
                    materialSharedAxisXOut(targetOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() })
            },
            predictivePopTransitionSpec = {
                // Brief You: the back preview, Android's own back animation between screens.
                backPreviewTransition(running = { true }) ?:
                materialSharedAxisXIn(
                    initialOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                ) togetherWith
                    materialSharedAxisXOut(targetOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() })
            },
            onBack = { backStack.removeLastOrNull() },
            entryProvider = { key ->
                when (key) {
                    Route.Feeds -> {
                        NavEntry(key) {
                            FeedsPage(
                                subscribeViewModel = subscribeViewModel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                navigateToSettings = { backStack.add(Route.Settings) },
                                navigationToFlow = { backStack.add(Route.Reading(null)) },
                                navigateToAccountList = { backStack.add(Route.Accounts) },
                                navigateToAccountDetail = {
                                    backStack.add(Route.AccountDetails(it))
                                },
                                navigateToFilters = { backStack.add(Route.Filters) }, // Brief You
                            )
                        }
                    }
                    is Route.Reading -> {
                        NavEntry(key) {
                            val key = rememberSaveable(saver = Route.Reading.Saver) { key }

                            // Brief You: once only; coming back from the reading style pages, the article
                            // is already open (opening it again restarted the pane).
                            var opened by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                            LaunchedEffect(key) {
                                if (key.articleId != null && !opened) {
                                    opened = true
                                    delay(50L)
                                    navigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail,
                                        ArticleData(key.articleId),
                                    )
                                }
                            }

                            val viewModel = hiltViewModel<ArticleListReaderViewModel>()

                            // Brief You: the article's page is loaded once the screen has slid in.
                            val animatedScope = LocalNavAnimatedContentScope.current
                            androidx.compose.runtime.CompositionLocalProvider(
                                com.ay.briefyou.brief.webview.LocalScreenSettled provides {
                                    animatedScope.transition.currentState == animatedScope.transition.targetState
                                }
                            ) {
                            ArticleListReaderPage(
                                scaffoldDirective = scaffoldDirective,
                                navigator = navigator,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                viewModel = viewModel,
                                onBack = onBack,
                                onNavigateToStylePage = {
                                    com.ay.briefyou.brief.perf.PerfLog.action("Article to Reading style", windowMs = 3_000) // Nightly stutter log
                                    backStack.add(Route.ReadingPageStyle)
                                },
                                onNavigateToAiSettings = { backStack.add(Route.AiModel) }, // Brief You
                            )
                            }
                        }
                    }
                    //                    is Route.Reading -> {
                    //                        NavEntry(key) {
                    //                            val articleId = key.articleId
                    //
                    //                            val readingViewModel: ReadingViewModel =
                    //                                hiltViewModel<
                    //                                    ReadingViewModel,
                    //                                    ReadingViewModel.ReadingViewModelFactory,
                    //                                > { factory ->
                    //                                    factory.create(articleId.toString(), null)
                    //                                }
                    //
                    //                            ReadingPage(
                    //                                readingViewModel = readingViewModel,
                    //                                onBack = onBack,
                    //                                onNavigateToStylePage = {
                    // backStack.add(Route.ReadingPageStyle) },
                    //                            )
                    //                        }
                    //                    }
                    Route.Startup -> {
                        NavEntry(key) {
                            // Brief You: the tour, with the original agreement on its last page
                            com.ay.briefyou.brief.tour.BriefFirstRun(
                                // Brief You: the tour is left behind, so the back gesture on Feeds
                                // closes the app instead of opening the tour again.
                                onNavigateToFeeds = {
                                    backStack.add(Route.Feeds)
                                    backStack.remove(Route.Startup)
                                },
                                onNavigateToAi = { backStack.add(Route.AiModel) },
                            )
                        }
                    }
                    Route.Settings ->
                        NavEntry(key) {
                            SettingsPage(
                                onBack = onBack,
                                navigateToAccounts = { backStack.add(Route.Accounts) },
                                navigateToColorAndStyle = { backStack.add(Route.ColorAndStyle) },
                                navigateToInteraction = { backStack.add(Route.Interaction) },
                                navigateToAiModel = { backStack.add(Route.AiModel) }, // Brief You
                                navigateToFilters = { backStack.add(Route.Filters) }, // Brief You
                                navigateToTroubleshooting = {
                                    backStack.add(Route.Troubleshooting)
                                },
                                navigateToTipsAndSupport = { backStack.add(Route.TipsAndSupport) },
                            )
                        }
                    Route.Accounts ->
                        NavEntry(key) {
                            AccountsPage(
                                onBack = onBack,
                                navigateToAddAccount = { backStack.add(Route.AddAccounts) },
                                navigateToAccountDetails = {
                                    backStack.add(Route.AccountDetails(it))
                                },
                            )
                        }
                    is Route.AccountDetails ->
                        NavEntry(key) {
                            AccountDetailsPage(
                                viewModel =
                                    hiltViewModel<AccountViewModel>().also {
                                        it.initData(key.accountId)
                                    },
                                onBack = onBack,
                                navigateToFeeds = { backStack.add(Route.Feeds) },
                            )
                        }
                    Route.AddAccounts ->
                        NavEntry(key) {
                            AddAccountsPage(
                                onBack = onBack,
                                navigateToAccountDetails = {
                                    backStack.add(Route.AccountDetails(it))
                                },
                            )
                        }
                    Route.ColorAndStyle ->
                        NavEntry(key) {
                            ColorAndStylePage(
                                onBack = onBack,
                                navigateToDarkTheme = { backStack.add(Route.DarkTheme) },
                                navigateToListsStyle = { backStack.add(Route.ListsStyle) },
                                navigateToReadingPageStyle = {
                                    backStack.add(Route.ReadingPageStyle)
                                },
                            )
                        }
                    Route.DarkTheme -> NavEntry(key) { DarkThemePage(onBack = onBack) }
                    Route.ListsStyle -> NavEntry(key) { ListsStylePage(onBack = onBack) }
                    Route.ReadingPageStyle ->
                        NavEntry(key) {
                            ReadingStylePage(
                                onBack = {
                                    com.ay.briefyou.brief.perf.PerfLog.action("Reading style back to the article", windowMs = 3_000) // Nightly stutter log
                                    onBack()
                                },
                                navigateToReadingBoldCharacters = {
                                    backStack.add(Route.ReadingBoldCharacters)
                                },
                                navigateToReadingPageTitle = {
                                    backStack.add(Route.ReadingPageTitle)
                                },
                                navigateToReadingPageText = {
                                    backStack.add(Route.ReadingPageText)
                                },
                                navigateToReadingPageMedia = {
                                    backStack.add(Route.ReadingPageMedia)
                                },
                            )
                        }
                    Route.ReadingBoldCharacters ->
                        NavEntry(key) { BoldCharactersPage(onBack = onBack) }
                    Route.ReadingPageTitle -> NavEntry(key) { ReadingTitlePage(onBack = onBack) }
                    Route.ReadingPageText -> NavEntry(key) { ReadingTextPage(onBack = onBack) }
                    Route.ReadingPageMedia -> NavEntry(key) { ReadingMediaPage(onBack = onBack) }
                    Route.Interaction -> NavEntry(key) { InteractionPage(onBack = onBack, navigateToNotifications = { backStack.add(Route.Notifications) }) } // Brief You: Notifications sits under Usage
                    Route.AiModel -> NavEntry(key) { AiSettingsPage(onBack = onBack) } // Brief You
                    Route.Filters -> NavEntry(key) { FiltersPage(onBack = onBack) } // Brief You
                    Route.Notifications -> NavEntry(key) { com.ay.briefyou.brief.notifications.NotificationsPage(onBack = onBack) } // Brief You
                    Route.ReadingStats -> NavEntry(key) { com.ay.briefyou.brief.stats.ReadingStatsPage(onBack = onBack) } // Brief You
                    Route.Troubleshooting -> NavEntry(key) { TroubleshootingPage(onBack = onBack) }
                    Route.TipsAndSupport ->
                        NavEntry(key) {
                            TipsAndSupportPage(
                                onBack = onBack,
                                navigateToLicenseList = { backStack.add(Route.LicenseList) },
                                navigateToAiModel = { backStack.add(Route.AiModel) }, // Brief You
                            )
                        }
                    Route.LicenseList -> NavEntry(key) { LicenseListPage(onBack = onBack) }
                    else -> NavEntry(key) { throw Exception("Unknown destination") }
                }
            },
        )
    }
    } // Brief You
}
