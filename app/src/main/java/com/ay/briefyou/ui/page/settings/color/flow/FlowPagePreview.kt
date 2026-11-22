package com.ay.briefyou.ui.page.settings.color.flow

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ay.briefyou.R
import com.ay.briefyou.domain.model.article.Article
import com.ay.briefyou.domain.model.article.ArticleWithFeed
import com.ay.briefyou.domain.model.feed.Feed
import com.ay.briefyou.domain.model.general.Filter
import com.ay.briefyou.infrastructure.preference.FlowArticleListTonalElevationPreference
import com.ay.briefyou.infrastructure.preference.FlowTopBarTonalElevationPreference
import com.ay.briefyou.brief.ui.BriefFilterBar // Brief You
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.ext.formatAsString
import com.ay.briefyou.ui.ext.surfaceColorAtElevation
import com.ay.briefyou.ui.page.home.flow.ArticleItem
import com.ay.briefyou.ui.theme.palette.onDark
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowPagePreview(
    topBarTonalElevation: FlowTopBarTonalElevationPreference,
    articleListTonalElevation: FlowArticleListTonalElevationPreference,
) {
    var filter by remember { mutableStateOf(Filter.Unread) }

    Column(
        modifier = Modifier
            .animateContentSize()
            .background(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    articleListTonalElevation.value.dp
                ) onDark MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        val preview = generateArticleWithFeedPreview()
        val feed = preview.feed
        val article = preview.article

        TopAppBar(
            title = {
                Text(
                    text = feed.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                )
            },
            navigationIcon = {
                FeedbackIconButton(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface
                ) {}
            },
            actions = {
                FeedbackIconButton(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = stringResource(R.string.mark_all_as_read),
                    tint = MaterialTheme.colorScheme.onSurface,
                ) {}
                FeedbackIconButton(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = MaterialTheme.colorScheme.onSurface,
                ) {}
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    topBarTonalElevation.value.dp
                ),
            )
        )
        Spacer(modifier = Modifier.height(12.dp))

        ArticleItem(
            modifier = Modifier,
            feedName = feed.name,
            feedIconUrl = feed.icon,
            title = article.title,
            shortDescription = article.shortDescription,
            timeString = article.dateString,
            imgData = R.drawable.animation,
            isStarred = article.isStarred,
            isUnread = article.isUnread,
            onClick = {},
            onLongClick = null
        )

        Spacer(modifier = Modifier.height(12.dp))
        BriefFilterBar( // Brief You: the preview shows Brief You's bar
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
            filter = filter,
        ) {
            filter = it
        }
    }
}

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
            dateString = date.formatAsString(
                context = LocalContext.current,
                onlyHourMinute = true
            )
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
