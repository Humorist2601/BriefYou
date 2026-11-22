package com.ay.briefyou.ui.page.settings.color.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.infrastructure.preference.*
import com.ay.briefyou.ui.component.base.*
import com.ay.briefyou.ui.page.settings.SettingItem
import com.ay.briefyou.ui.theme.palette.onLight

@Composable
fun FlowPageStylePage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val topBarTonalElevation = LocalFlowTopBarTonalElevation.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListFeedName = LocalFlowArticleListFeedName.current
    val articleListImage = LocalFlowArticleListImage.current
    val articleListDesc = LocalFlowArticleListDesc.current
    val articleListTime = LocalFlowArticleListTime.current
    val articleListStickyDate = LocalFlowArticleListDateStickyHeader.current
    val articleListTonalElevation = LocalFlowArticleListTonalElevation.current
    val articleListReadIndicator = LocalFlowArticleListReadIndicator.current
    val sortUnreadArticles = LocalSortUnreadArticles.current
    val aiLongPress = com.ay.briefyou.brief.ui.AiLongPress.get(context) // Brief You

    val settings = LocalSettings.current
    val pullToSwitchFeed = settings.pullToSwitchFeed

    val scope = rememberCoroutineScope()

    var topBarTonalElevationDialogVisible by remember { mutableStateOf(false) }
    var aiLongPressDialogVisible by remember { mutableStateOf(false) } // Brief You
    var articleListTonalElevationDialogVisible by remember { mutableStateOf(false) }
    var articleListReadIndicatorDialogVisible by remember { mutableStateOf(false) }
    var showArticleListDescDialog by remember { mutableStateOf(false) }
    var showPullToLoadDialog by remember { mutableStateOf(false) }

    var showSortUnreadArticlesDialog by remember { mutableStateOf(false) }

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.flow_page), desc = "")
                }

                // Preview
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                MaterialTheme.colorScheme.inverseOnSurface
                                        onLight MaterialTheme.colorScheme.surface.copy(0.7f)
                            )
                            .clickable { },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlowPagePreview(
                            topBarTonalElevation = topBarTonalElevation,
                            articleListTonalElevation = articleListTonalElevation,
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Top Bar
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.top_bar)
                    )
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You: the section as one joined card
                    // Brief You: "Mark as read button position" is hidden until it works
                    SettingItem(
                        title = stringResource(R.string.tonal_elevation),
                        desc = "${topBarTonalElevation.value}dp",
                        onClick = {
                            topBarTonalElevationDialogVisible = true
                        },
                    ) {}
                    } // Brief You
                    Tips(text = stringResource(R.string.tips_top_bar_tonal_elevation))
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Article List
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.article_list)
                    )
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You: the section as one joined card
                    SettingItem(
                        title = stringResource(R.string.feed_favicons),
                        onClick = {
                            (!articleListFeedIcon).put(context, scope)
                        },
                    ) {
                        RYSwitch(activated = articleListFeedIcon.value) {
                            (!articleListFeedIcon).put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.feed_names),
                        onClick = {
                            (!articleListFeedName).put(context, scope)
                        },
                    ) {
                        RYSwitch(activated = articleListFeedName.value) {
                            (!articleListFeedName).put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.article_images),
                        onClick = {
                            (!articleListImage).put(context, scope)
                        },
                    ) {
                        RYSwitch(activated = articleListImage.value) {
                            (!articleListImage).put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.article_desc),
                        desc = articleListDesc.description(),
                        onClick = {
                            showArticleListDescDialog = true
                        },
                    )
                    SettingItem(
                        title = stringResource(R.string.article_date),
                        onClick = {
                            (!articleListTime).put(context, scope)
                        },
                    ) {
                        RYSwitch(activated = articleListTime.value) {
                            (!articleListTime).put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.article_date_sticky_header),
                        onClick = {
                            (!articleListStickyDate).put(context, scope)
                        },
                    ) {
                        RYSwitch(activated = articleListStickyDate.value) {
                            (!articleListStickyDate).put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.grey_out_articles),
                        desc = articleListReadIndicator.description,
                        onClick = {
                            articleListReadIndicatorDialogVisible = true
                        }
                    )
                    SettingItem(
                        title = stringResource(R.string.sort_unread_articles),
                        onClick = {
                            showSortUnreadArticlesDialog = true
                        },
                        desc = sortUnreadArticles.description()
                    ) {
                    }

                    SettingItem(
                        title = stringResource(R.string.pull_from_bottom),
                        desc = pullToSwitchFeed.description(),
                        onClick = {
                            showPullToLoadDialog = true
                        },
                    )

                    SettingItem(
                        title = stringResource(R.string.tonal_elevation),
                        desc = "${articleListTonalElevation.value}dp",
                        onClick = {
                            articleListTonalElevationDialogVisible = true
                        },
                    ) {}
                    } // Brief You
                    Tips(text = stringResource(R.string.tips_article_list_tonal_elevation))
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Brief You: the AI button beside the bottom bar (Read You's filter bar settings are
                // gone: Brief You's bar has one look).
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.ai_button),
                    )
                    com.ay.briefyou.brief.ui.SettingGroup {
                    SettingItem(
                        title = stringResource(R.string.ai_button_long_press),
                        desc = stringResource(R.string.ai_button_opens, aiLongPress.label()),
                        onClick = { aiLongPressDialogVisible = true },
                    ) {}
                    }
                    Tips(text = stringResource(R.string.ai_button_tips))
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    RadioDialog(
        visible = aiLongPressDialogVisible,
        title = stringResource(R.string.ai_button_long_press),
        options = com.ay.briefyou.brief.ui.AiLongPress.entries.map {
            RadioDialogOption(
                text = it.label(),
                selected = it == aiLongPress,
            ) {
                com.ay.briefyou.brief.ui.AiLongPress.set(context, it)
            }
        }
    ) {
        aiLongPressDialogVisible = false
    }

    RadioDialog(
        visible = topBarTonalElevationDialogVisible,
        title = stringResource(R.string.tonal_elevation),
        options = FlowTopBarTonalElevationPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == topBarTonalElevation,
            ) {
                it.put(context, scope)
            }
        }
    ) {
        topBarTonalElevationDialogVisible = false
    }

    RadioDialog(
        visible = articleListTonalElevationDialogVisible,
        title = stringResource(R.string.tonal_elevation),
        options = FlowArticleListTonalElevationPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == articleListTonalElevation,
            ) {
                it.put(context, scope)
            }
        }
    ) {
        articleListTonalElevationDialogVisible = false
    }

    RadioDialog(
        visible = showArticleListDescDialog,
        title = stringResource(R.string.article_desc),
        options = FlowArticleListDescPreference.values.map {
            RadioDialogOption(
                text = it.description(),
                selected = it == articleListDesc,
            ) {
                it.put(context, scope)
            }
        },
        onDismissRequest = {
            showArticleListDescDialog = false
        }
    )

    RadioDialog(
        visible = articleListReadIndicatorDialogVisible,
        title = stringResource(id = R.string.grey_out_articles),
        options = FlowArticleReadIndicatorPreference.values.map {
            RadioDialogOption(
                text = it.description,
                selected = it == articleListReadIndicator
            ) {
                it.put(context, scope)
            }
        }
    ) {
        articleListReadIndicatorDialogVisible = false
    }

    RadioDialog(
        visible = showSortUnreadArticlesDialog,
        title = stringResource(R.string.sort_unread_articles),
        options = SortUnreadArticlesPreference.values.map {
            RadioDialogOption(
                text = it.description(),
                selected = it == sortUnreadArticles,
            ) {
                it.put(context, scope)
            }
        },
        onDismissRequest = {
            showSortUnreadArticlesDialog = false
        }
    )

    RadioDialog(
        visible = showPullToLoadDialog,
        title = stringResource(R.string.pull_from_bottom),
        options = PullToLoadNextFeedPreference.values.map {
            RadioDialogOption(
                text = it.description(),
                selected = it == pullToSwitchFeed,
            ) {
                it.put(context, scope)
            }
        },
        onDismissRequest = {
            showPullToLoadDialog = false
        }
    )
}
