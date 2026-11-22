package com.ay.briefyou.ui.page.settings.color.lists

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.brief.ui.BriefFilterBar
import com.ay.briefyou.brief.ui.Group
import com.ay.briefyou.brief.ui.TonalCircleButton
import com.ay.briefyou.brief.ui.TopBarAction
import com.ay.briefyou.brief.ui.TopBarButtonGroup
import com.ay.briefyou.brief.ui.groupShape
import com.ay.briefyou.domain.model.article.Article
import com.ay.briefyou.domain.model.article.ArticleWithFeed
import com.ay.briefyou.domain.model.feed.Feed
import com.ay.briefyou.domain.model.general.Filter
import com.ay.briefyou.domain.model.group.Group as FeedGroup
import com.ay.briefyou.ui.component.base.DisplayText
import com.ay.briefyou.ui.ext.formatAsString
import com.ay.briefyou.ui.page.home.feeds.FeedItem
import com.ay.briefyou.ui.page.home.feeds.FeedsBanner
import com.ay.briefyou.ui.page.home.feeds.GroupItem
import com.ay.briefyou.ui.page.home.feeds.GroupWithFeedsContainer
import com.ay.briefyou.ui.page.home.flow.ArticleItem
import java.util.Date

/**
 * The Lists page's preview, built from the app's own components so it looks like the real Feeds
 * and Articles lists and follows the switches below it.
 */
@Composable
fun ListsPagePreview(groupListExpand: Boolean) {
    var filter by remember { mutableStateOf(Filter.Unread) }
    val article = generateArticleWithFeedPreview()

    Column(
        modifier = Modifier
            .animateContentSize()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .padding(bottom = 12.dp)
    ) {
        // The Feeds page: the round Settings button and the joined actions.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TonalCircleButton(Icons.Outlined.Settings, stringResource(R.string.settings)) {}
            TopBarButtonGroup(
                listOf(
                    TopBarAction(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort_title)) {},
                    TopBarAction(Icons.Outlined.FilterAlt, stringResource(R.string.filters_settings)) {},
                    TopBarAction(Icons.Rounded.Add, stringResource(R.string.subscribe)) {},
                )
            )
        }
        FeedsBanner(filter = Filter.Unread, desc = pluralStringResource(R.plurals.unread_desc, 3, 3))
        Spacer(Modifier.height(4.dp))
        GroupWithFeedsContainer(shape = groupShape(0, 1), top = Group.Gap) {
            GroupItem(isExpanded = { groupListExpand }, group = generateGroupPreview(), count = 3)
            FeedItem(feed = generateFeedPreview(), isLastItem = { true }, isExpanded = { groupListExpand })
        }

        // The Articles list: the large heading and the rows.
        Spacer(Modifier.height(20.dp))
        DisplayText(text = stringResource(R.string.unread), desc = "")
        ArticleItem(articleWithFeed = article, isUnread = true)
        ArticleItem(articleWithFeed = article, isUnread = false)

        // The bottom bar with the AI button beside it.
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BriefFilterBar(filter = filter) { filter = it }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = stringResource(R.string.ai_button),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Stable
@Composable
fun generateFeedPreview(): Feed =
    Feed(
        id = "",
        name = stringResource(R.string.preview_feed_name),
        icon = "",
        accountId = 0,
        groupId = "",
        url = "",
        important = 100,
    )

@Stable
@Composable
fun generateGroupPreview(): FeedGroup =
    FeedGroup(id = "", name = stringResource(R.string.defaults), accountId = 0)

@Stable
@Composable
fun generateArticleWithFeedPreview(): ArticleWithFeed =
    ArticleWithFeed(
        Article(
            id = "",
            title = stringResource(R.string.preview_article_title),
            shortDescription = stringResource(R.string.preview_article_desc),
            rawDescription = stringResource(R.string.preview_article_desc),
            link = "",
            feedId = "",
            accountId = 0,
            date = Date(1654053729L),
            isStarred = true,
            img = null,
        ).apply {
            dateString = date.formatAsString(context = LocalContext.current, onlyHourMinute = true)
        },
        feed = Feed(
            id = "",
            name = stringResource(R.string.preview_feed_name),
            icon = "",
            accountId = 0,
            groupId = "",
            url = "",
        ),
    )
