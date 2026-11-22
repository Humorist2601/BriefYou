package com.ay.briefyou.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ay.briefyou.infrastructure.datastore.get
import com.ay.briefyou.infrastructure.datastore.getOrDefault
import com.ay.briefyou.infrastructure.di.ApplicationScope
import com.ay.briefyou.infrastructure.di.IODispatcher
import com.ay.briefyou.ui.ext.DataStoreKey
import com.ay.briefyou.ui.ext.collectAsStateValue
import com.ay.briefyou.ui.ext.dataStore
import javax.inject.Inject

class SettingsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope coroutineScope: CoroutineScope,
    @IODispatcher ioDispatcher: CoroutineDispatcher
) {
    private val _settingsFlow = MutableStateFlow(Settings())
    val settingsFlow: StateFlow<Settings> = _settingsFlow
    val settings: Settings get() = settingsFlow.value

    val dataStore = context.dataStore.data

    val preferencesFlow: StateFlow<Preferences> =
        dataStore.stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesOf()
        )

    val preferences get() = preferencesFlow.value

    inline fun <reified T> get(key: Preferences.Key<T>): T? = preferences[key]

    inline fun <reified T> get(key: String): T? = preferences.get(key)

    inline fun <reified T> getOrDefault(key: String, default: T): T =
        preferences.getOrDefault(key, default) ?: default


    init {
        coroutineScope.launch(ioDispatcher) {
            preferencesFlow.collect {
                _settingsFlow.value = it.toSettings()
            }
        }
    }

    @Composable
    fun ProvidesSettings(content: @Composable () -> Unit) {
        val settings = settingsFlow.collectAsStateValue()
        // Brief You: the master Haptic feedback switch. Everything that vibrates through Compose
        // asks this one; with the switch off it does nothing.
        val systemHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        val hapticsOn = settings.isOn(BriefSwitch.Haptics)
        // The buttons and lists that ask the window's view directly are silenced there.
        val rootView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            rootView.isHapticFeedbackEnabled = hapticsOn
            com.ay.briefyou.brief.stats.ReadingStats.collecting = settings.isOn(BriefSwitch.CollectStats)
        }
        val haptics =
            androidx.compose.runtime.remember(systemHaptics, hapticsOn) {
                object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                    override fun performHapticFeedback(
                        hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType
                    ) {
                        if (hapticsOn) systemHaptics.performHapticFeedback(hapticFeedbackType)
                    }
                }
            }
        CompositionLocalProvider(
            LocalSettings provides settings,

            // Version
            NewVersionNumberPreference.provide(settings),
            LocalSkipVersionNumber provides settings.skipVersionNumber,
            LocalNewVersionPublishDate provides settings.newVersionPublishDate,
            LocalNewVersionLog provides settings.newVersionLog,
            LocalNewVersionSize provides settings.newVersionSize,
            LocalNewVersionDownloadUrl provides settings.newVersionDownloadUrl,
            LocalBasicFonts provides settings.basicFonts,

            // Theme
            LocalThemeIndex provides settings.themeIndex,
            LocalCustomPrimaryColor provides settings.customPrimaryColor,
            LocalDarkTheme provides settings.darkTheme,
            LocalAmoledDarkTheme provides settings.amoledDarkTheme,
            LocalBasicFonts provides settings.basicFonts,

            // Feeds page
            LocalFeedsGroupListExpand provides settings.feedsGroupListExpand,

            // Flow page
            LocalFlowArticleListFeedIcon provides settings.flowArticleListFeedIcon,
            LocalFlowArticleListFeedName provides settings.flowArticleListFeedName,
            LocalFlowArticleListImage provides settings.flowArticleListImage,
            LocalFlowArticleListDesc provides settings.flowArticleListDesc,
            LocalFlowArticleListTime provides settings.flowArticleListTime,
            LocalFlowArticleListDateStickyHeader provides settings.flowArticleListDateStickyHeader,
            LocalFlowArticleListReadIndicator provides settings.flowArticleListReadIndicator,
            LocalSortUnreadArticles provides settings.flowSortUnreadArticles,

            // Reading page
            LocalReadingBoldCharacters provides settings.readingBoldCharacters,
            LocalReadingTheme provides settings.readingTheme,
            LocalReadingAutoHideToolbar provides settings.readingAutoHideToolbar,
            LocalReadingTextFontSize provides settings.readingTextFontSize,
            LocalReadingTextLineHeight provides settings.readingTextLineHeight,
            LocalReadingTextLetterSpacing provides settings.readingLetterSpacing,
            LocalReadingTextHorizontalPadding provides settings.readingTextHorizontalPadding,
            LocalReadingTextAlign provides settings.readingTextAlign,
            LocalReadingTextBold provides settings.readingTextBold,
            LocalReadingTitleAlign provides settings.readingTitleAlign,
            LocalReadingSubheadAlign provides settings.readingSubheadAlign,
            LocalReadingFonts provides settings.readingFonts,
            LocalReadingTitleBold provides settings.readingTitleBold,
            LocalReadingSubheadBold provides settings.readingSubheadBold,
            LocalReadingTitleUpperCase provides settings.readingTitleUpperCase,
            LocalReadingSubheadUpperCase provides settings.readingSubheadUpperCase,
            LocalReadingImageHorizontalPadding provides settings.readingImageHorizontalPadding,
            LocalReadingImageRoundedCorners provides settings.readingImageRoundedCorners,
            LocalReadingImageMaximize provides settings.readingImageMaximize,
            LocalReadingVideoRoundedCorners provides settings.readingVideoRoundedCorners, // Brief You
            LocalReadingVideoHorizontalPadding provides settings.readingVideoHorizontalPadding, // Brief You
            LocalReadingVideoMaximize provides settings.readingVideoMaximize, // Brief You

            // Interaction
            LocalInitialPage provides settings.initialPage,
            LocalInitialFilter provides settings.initialFilter,
            LocalArticleListSwipeStartAction provides settings.swipeStartAction,
            LocalArticleListSwipeEndAction provides settings.swipeEndAction,
            LocalMarkAsReadOnScroll provides settings.markAsReadOnScroll,
            LocalHideEmptyGroups provides settings.hideEmptyGroups,
            LocalPullToSwitchArticle provides settings.pullToSwitchArticle,
            LocalOpenLink provides settings.openLink,
            LocalOpenLinkSpecificBrowser provides settings.openLinkSpecificBrowser,
            LocalSharedContent provides settings.sharedContent,

            // Languages
            LocalLanguages provides settings.languages,

            // Brief You
            LocalBriefSwitches provides settings.switches,
            androidx.compose.ui.platform.LocalHapticFeedback provides haptics,
        ) {
            content()
        }
    }
}