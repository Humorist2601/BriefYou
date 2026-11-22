package com.ay.briefyou.ui.page.settings.color.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.infrastructure.preference.*
import com.ay.briefyou.ui.component.base.DisplayText
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.component.base.RYScaffold
import com.ay.briefyou.ui.component.base.RYSwitch
import com.ay.briefyou.ui.component.base.RadioDialog
import com.ay.briefyou.ui.component.base.RadioDialogOption
import com.ay.briefyou.ui.component.base.Subtitle
import com.ay.briefyou.ui.component.base.Tips
import com.ay.briefyou.ui.page.settings.SettingItem
import com.ay.briefyou.ui.theme.palette.onLight

/** Brief You: one page for how the Feeds list and the article lists look (was Feeds page + Flow page). */
@Composable
fun ListsStylePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val groupListExpand = LocalFeedsGroupListExpand.current
    val hideEmptyGroups = LocalHideEmptyGroups.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListFeedName = LocalFlowArticleListFeedName.current
    val articleListImage = LocalFlowArticleListImage.current
    val articleListDesc = LocalFlowArticleListDesc.current
    val articleListTime = LocalFlowArticleListTime.current
    val articleListStickyDate = LocalFlowArticleListDateStickyHeader.current
    val articleListReadIndicator = LocalFlowArticleListReadIndicator.current

    val scope = rememberCoroutineScope()

    var articleListReadIndicatorDialogVisible by remember { mutableStateOf(false) }
    var showArticleListDescDialog by remember { mutableStateOf(false) }

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
                item { DisplayText(text = stringResource(R.string.lists_page), desc = "") }

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
                        ListsPagePreview(groupListExpand = groupListExpand.value)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Feeds list
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.group_list)
                    )
                    com.ay.briefyou.brief.ui.SettingGroup {
                        SettingItem(
                            title = stringResource(R.string.always_expand),
                            onClick = { (!groupListExpand).put(context, scope) },
                        ) {
                            RYSwitch(activated = groupListExpand.value) {
                                (!groupListExpand).put(context, scope)
                            }
                        }
                        SettingItem(
                            title = stringResource(R.string.hide_empty_groups),
                            onClick = { hideEmptyGroups.toggle(context, scope) },
                        ) {
                            RYSwitch(activated = hideEmptyGroups.value) {
                                hideEmptyGroups.toggle(context, scope)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Article lists
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.article_list)
                    )
                    com.ay.briefyou.brief.ui.SettingGroup {
                        SettingItem(
                            title = stringResource(R.string.feed_favicons),
                            onClick = { (!articleListFeedIcon).put(context, scope) },
                        ) {
                            RYSwitch(activated = articleListFeedIcon.value) {
                                (!articleListFeedIcon).put(context, scope)
                            }
                        }
                        SettingItem(
                            title = stringResource(R.string.feed_names),
                            onClick = { (!articleListFeedName).put(context, scope) },
                        ) {
                            RYSwitch(activated = articleListFeedName.value) {
                                (!articleListFeedName).put(context, scope)
                            }
                        }
                        SettingItem(
                            title = stringResource(R.string.article_images),
                            onClick = { (!articleListImage).put(context, scope) },
                        ) {
                            RYSwitch(activated = articleListImage.value) {
                                (!articleListImage).put(context, scope)
                            }
                        }
                        SettingItem(
                            title = stringResource(R.string.article_desc),
                            desc = articleListDesc.description(),
                            onClick = { showArticleListDescDialog = true },
                        )
                        SettingItem(
                            title = stringResource(R.string.article_date),
                            onClick = { (!articleListTime).put(context, scope) },
                        ) {
                            RYSwitch(activated = articleListTime.value) {
                                (!articleListTime).put(context, scope)
                            }
                        }
                        SettingItem(
                            title = stringResource(R.string.article_date_sticky_header),
                            onClick = { (!articleListStickyDate).put(context, scope) },
                        ) {
                            RYSwitch(activated = articleListStickyDate.value) {
                                (!articleListStickyDate).put(context, scope)
                            }
                        }
                        SettingItem(
                            title = stringResource(R.string.grey_out_articles),
                            desc = articleListReadIndicator.description,
                            onClick = { articleListReadIndicatorDialogVisible = true }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

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
        onDismissRequest = { showArticleListDescDialog = false }
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
}
