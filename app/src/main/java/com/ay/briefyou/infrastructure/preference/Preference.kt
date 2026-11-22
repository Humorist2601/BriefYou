package com.ay.briefyou.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope

sealed class Preference {

    abstract fun put(context: Context, scope: CoroutineScope)
}

fun Preferences.toSettings(): Settings {
    return Settings(
        // Version
        newVersionNumber = NewVersionNumberPreference.fromPreferences(this),
        skipVersionNumber = SkipVersionNumberPreference.fromPreferences(this),
        newVersionPublishDate = NewVersionPublishDatePreference.fromPreferences(this),
        newVersionLog = NewVersionLogPreference.fromPreferences(this),
        newVersionSize = NewVersionSizePreference.fromPreferences(this),
        newVersionDownloadUrl = NewVersionDownloadUrlPreference.fromPreferences(this),

        // Theme
        themeIndex = ThemeIndexPreference.fromPreferences(this),
        customPrimaryColor = CustomPrimaryColorPreference.fromPreferences(this),
        darkTheme = DarkThemePreference.fromPreferences(this),
        amoledDarkTheme = AmoledDarkThemePreference.fromPreferences(this),
        basicFonts = BasicFontsPreference.fromPreferences(this),

        // Feeds page
        feedsGroupListExpand = FeedsGroupListExpandPreference.fromPreferences(this),

        // Flow page
        flowArticleListFeedIcon = FlowArticleListFeedIconPreference.fromPreferences(this),
        flowArticleListFeedName = FlowArticleListFeedNamePreference.fromPreferences(this),
        flowArticleListImage = FlowArticleListImagePreference.fromPreferences(this),
        flowArticleListDesc = FlowArticleListDescPreference.fromPreferences(this),
        flowArticleListTime = FlowArticleListTimePreference.fromPreferences(this),
        flowArticleListDateStickyHeader = FlowArticleListDateStickyHeaderPreference.fromPreferences(
            this
        ),
        flowArticleListReadIndicator = FlowArticleReadIndicatorPreference.fromPreferences(this),
        flowSortUnreadArticles = SortUnreadArticlesPreference.fromPreferences(this),

        // Reading page
        readingBoldCharacters = ReadingBoldCharactersPreference.fromPreferences(this),
        readingTheme = ReadingThemePreference.fromPreferences(this),
        readingAutoHideToolbar = ReadingAutoHideToolbarPreference.fromPreferences(this),
        readingTextFontSize = ReadingTextFontSizePreference.fromPreferences(this),
        readingTextLineHeight = ReadingTextLineHeightPreference.fromPreferences(this),
        readingLetterSpacing = ReadingTextLetterSpacingPreference.fromPreferences(this),
        readingTextHorizontalPadding = ReadingTextHorizontalPaddingPreference.fromPreferences(this),
        readingTextAlign = ReadingTextAlignPreference.fromPreferences(this),
        readingTextBold = ReadingTextBoldPreference.fromPreferences(this),
        readingTitleAlign = ReadingTitleAlignPreference.fromPreferences(this),
        readingSubheadAlign = ReadingSubheadAlignPreference.fromPreferences(this),
        readingFonts = ReadingFontsPreference.fromPreferences(this),
        readingTitleBold = ReadingTitleBoldPreference.fromPreferences(this),
        readingSubheadBold = ReadingSubheadBoldPreference.fromPreferences(this),
        readingTitleUpperCase = ReadingTitleUpperCasePreference.fromPreferences(this),
        readingSubheadUpperCase = ReadingSubheadUpperCasePreference.fromPreferences(this),
        readingImageHorizontalPadding = ReadingImageHorizontalPaddingPreference.fromPreferences(this),
        readingImageRoundedCorners = ReadingImageRoundedCornersPreference.fromPreferences(this),
        readingImageMaximize = ReadingImageMaximizePreference.fromPreferences(this),
        readingVideoRoundedCorners = ReadingVideoRoundedCornersPreference.fromPreferences(this), // Brief You
        readingVideoHorizontalPadding = ReadingVideoHorizontalPaddingPreference.fromPreferences(this), // Brief You
        readingVideoMaximize = ReadingVideoMaximizePreference.fromPreferences(this), // Brief You

        // Interaction
        initialPage = InitialPagePreference.fromPreferences(this),
        initialFilter = InitialFilterPreference.fromPreferences(this),
        swipeStartAction = SwipeStartActionPreference.fromPreferences(this),
        swipeEndAction = SwipeEndActionPreference.fromPreferences(this),
        markAsReadOnScroll = MarkAsReadOnScrollPreference.fromPreferences(this),
        hideEmptyGroups = HideEmptyGroupsPreference.fromPreferences(this),
        pullToSwitchFeed = PullToLoadNextFeedPreference.fromPreference(this),
        pullToSwitchArticle = PullToSwitchArticlePreference.fromPreference(this),
        openLink = OpenLinkPreference.fromPreferences(this),
        openLinkSpecificBrowser = OpenLinkSpecificBrowserPreference.fromPreferences(this),
        sharedContent = SharedContentPreference.fromPreferences(this),

        // Languages
        languages = LanguagesPreference.fromPreferences(this),
    )
}
