package com.ay.briefyou.ui.page.settings.color.lists

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.brief.ui.Group
import com.ay.briefyou.brief.ui.groupShape
import com.ay.briefyou.domain.model.article.Article
import com.ay.briefyou.domain.model.article.ArticleWithFeed
import com.ay.briefyou.domain.model.feed.Feed
import com.ay.briefyou.domain.model.group.Group as FeedGroup
import com.ay.briefyou.ui.ext.formatAsString
import com.ay.briefyou.ui.page.home.feeds.FeedItem
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
    val article = generateArticleWithFeedPreview()

    // Compact: a group (with its feed when expanded) and two article rows, which is what the
    // switches below change.
    Column(
        modifier = Modifier
            .animateContentSize()
            .padding(vertical = 8.dp)
    ) {
        // In the preview's own card, which is the same tone as a group's card, so the group (and its feed
        // when expanded) would vanish into it: a tone up.
        GroupWithFeedsContainer(shape = groupShape(0, 1), top = Group.Gap, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            GroupItem(isExpanded = { groupListExpand }, group = generateGroupPreview(), count = 3)
            FeedItem(feed = generateFeedPreview(), isLastItem = { true }, isExpanded = { groupListExpand })
        }
        Spacer(Modifier.height(8.dp))
        // The first is unread with a picture; the second is read (dimmed by the read indicator
        // switch below) and not starred, since the default indicator leaves starred ones as they are.
        ArticleItem(
            feedName = article.feed.name,
            title = article.article.title,
            shortDescription = article.article.shortDescription,
            timeString = article.article.dateString,
            imgData = R.drawable.preview_picture,
            isStarred = true,
            isUnread = true,
        )
        ArticleItem(
            feedName = article.feed.name,
            title = article.article.title,
            shortDescription = article.article.shortDescription,
            timeString = article.article.dateString,
            imgData = R.drawable.preview_picture,
            isStarred = false,
            isUnread = false,
        )
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
