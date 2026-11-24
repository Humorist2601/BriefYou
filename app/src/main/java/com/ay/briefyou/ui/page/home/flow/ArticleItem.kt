package com.ay.briefyou.ui.page.home.flow

// Brief You
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Color
import com.ay.briefyou.brief.feeds.rememberFeedAccent
import com.ay.briefyou.brief.reading.FeedContent
import com.ay.briefyou.brief.reading.estimateReadingMinutes
import com.ay.briefyou.ui.component.swipe.SwipeableActionsState
import com.ay.briefyou.ui.component.swipe.rememberSwipeableActionsState

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import com.ay.briefyou.ui.ext.openURL
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics // Brief You
import androidx.compose.ui.semantics.contentDescription // Brief You
import androidx.compose.ui.semantics.onClick // Brief You
import androidx.compose.ui.semantics.onLongClick // Brief You
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import coil3.size.Precision // Brief You: Coil 3
import coil3.size.Scale // Brief You: Coil 3
import com.ay.briefyou.R
import com.ay.briefyou.domain.model.article.ArticleWithFeed
import com.ay.briefyou.infrastructure.preference.FlowArticleListDescPreference
import com.ay.briefyou.infrastructure.preference.FlowArticleReadIndicatorPreference
import com.ay.briefyou.infrastructure.preference.LocalArticleListSwipeEndAction
import com.ay.briefyou.infrastructure.preference.LocalArticleListSwipeStartAction
import com.ay.briefyou.infrastructure.preference.LocalFlowArticleListDesc
import com.ay.briefyou.infrastructure.preference.LocalFlowArticleListFeedIcon
import com.ay.briefyou.infrastructure.preference.LocalFlowArticleListFeedName
import com.ay.briefyou.infrastructure.preference.LocalFlowArticleListImage
import com.ay.briefyou.infrastructure.preference.LocalFlowArticleListReadIndicator
import com.ay.briefyou.infrastructure.preference.LocalFlowArticleListTime
import com.ay.briefyou.infrastructure.preference.SwipeEndActionPreference
import com.ay.briefyou.infrastructure.preference.SwipeStartActionPreference
import com.ay.briefyou.ui.component.FeedIcon
import com.ay.briefyou.ui.component.base.BYAsyncImage
import com.ay.briefyou.ui.component.base.SIZE_1000
import com.ay.briefyou.ui.component.menu.AnimatedDropdownMenu
import com.ay.briefyou.ui.component.swipe.SwipeAction
import com.ay.briefyou.ui.component.swipe.SwipeableActionsBox
import com.ay.briefyou.ui.ext.requiresBidi
import com.ay.briefyou.ui.page.settings.color.lists.generateArticleWithFeedPreview
import com.ay.briefyou.ui.theme.Shape20
import com.ay.briefyou.brief.ui.applyTextDirection // Brief You

private const val TAG = "ArticleItem"

@Composable
fun ArticleItem(
    modifier: Modifier = Modifier,
    articleWithFeed: ArticleWithFeed,
    isUnread: Boolean = articleWithFeed.article.isUnread,
    onClick: (ArticleWithFeed) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val feed = articleWithFeed.feed
    val article = articleWithFeed.article

    ArticleItem(
        modifier = modifier,
        feedName = feed.name,
        feedIconUrl = feed.icon,
        title = article.title,
        shortDescription = article.shortDescription,
        timeString = article.dateString,
        imgData = FeedContent.listImage(article.img, article.link), // Brief You: better pictures
        isStarred = article.isStarred,
        isUnread = isUnread,
        readingMinutes = remember(article.id) { com.ay.briefyou.brief.reading.ListReadingTime.of(article.id) }, // Brief You: worked out in the background
        onClick = { onClick(articleWithFeed) },
        onLongClick = onLongClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleItem(
    modifier: Modifier = Modifier,
    feedName: String = "",
    feedIconUrl: String? = null,
    title: String = "",
    shortDescription: String = "",
    timeString: String? = null,
    imgData: Any? = null,
    isStarred: Boolean = false,
    isUnread: Boolean = false,
    readingMinutes: Int? = null, // Brief You
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    if (com.ay.briefyou.brief.perf.PerfLog.enabled) androidx.compose.runtime.SideEffect { com.ay.briefyou.brief.perf.PerfLog.note("row built") } // Brief You: Nightly stutter log
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListFeedName = LocalFlowArticleListFeedName.current
    val articleListImage = LocalFlowArticleListImage.current
    val articleListDesc = LocalFlowArticleListDesc.current
    val articleListDate = LocalFlowArticleListTime.current
    val articleListReadIndicator = LocalFlowArticleListReadIndicator.current
    // Brief You: reading time next to the time ("4 min · 2h").
    val readingLabel = readingMinutes?.let { stringResource(R.string.reading_time_min, it) }
    val timeLabel = listOfNotNull(readingLabel, timeString?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
    // Brief You: the row is one element for accessibility (read as one item, like Android's own
    // lists), so accessibility services don't walk its every text and icon after each change.
    val unreadLabel = stringResource(R.string.unread) // Brief You
    val starredLabel = stringResource(R.string.starred) // Brief You
    val rowDescription = // Brief You
        listOfNotNull( // Brief You
            feedName.takeIf { it.isNotBlank() }, title, shortDescription.takeIf { it.isNotBlank() }, // Brief You
            timeLabel.takeIf { it.isNotBlank() }, unreadLabel.takeIf { isUnread }, starredLabel.takeIf { isStarred }, // Brief You
        ).joinToString(", ") // Brief You
    val rowClick = onClick // Brief You
    val rowLongClick = onLongClick // Brief You

    Column(
        modifier =
            modifier
                .padding(horizontal = 12.dp)
                .clip(Shape20)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .alpha(
                    when (articleListReadIndicator) {
                        FlowArticleReadIndicatorPreference.None -> 1f

                        FlowArticleReadIndicatorPreference.AllRead -> {
                            if (isUnread) 1f else 0.5f
                        }

                        FlowArticleReadIndicatorPreference.ExcludingStarred -> {
                            if (isUnread || isStarred) 1f else 0.5f
                        }
                    }
                )
                .clearAndSetSemantics { // Brief You
                    contentDescription = rowDescription // Brief You
                    this.onClick(label = null) { rowClick(); true } // Brief You
                    if (rowLongClick != null) this.onLongClick(label = null) { rowLongClick(); true } // Brief You
                } // Brief You
    ) {
        // Top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Feed name, with the feed icon inline at its start (Brief You)
            if (articleListFeedName.value) {
                if (articleListFeedIcon.value) {
                    FeedIcon(feedName = feedName, iconUrl = feedIconUrl, size = 16.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                // Brief You: the feed name, then a watched word's chip right after it.
                Row(modifier = Modifier.weight(1f).padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = feedName,
                    // Brief You: a muted accent taken from the feed's icon.
                    color = rememberFeedAccent(feedIconUrl) ?: MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                com.ay.briefyou.brief.filters.WatchedChip(title, isUnread) // Brief You: watched words
                } // Brief You

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier) {
                    // Starred
                    if (isStarred) {
                        StarredIcon()
                    }

                    if (articleListDate.value) {
                        // Time
                        Text(
                            modifier = Modifier,
                            text = timeLabel, // Brief You
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (articleListFeedIcon.value) { // Brief You: the icon inline
                        FeedIcon(feedName = feedName, iconUrl = feedIconUrl, size = 16.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (articleListDate.value) {
                        // Time
                        Text(
                            modifier = Modifier.weight(1f),
                            text = timeLabel, // Brief You
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        // Starred
                        if (isStarred) {
                            StarredIcon()
                        }
                    }
                    com.ay.briefyou.brief.filters.WatchedChip(title, isUnread) // Brief You: watched words
                }
            }

            // Right

        }

        // Bottom
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            // (Brief You: the feed icon moved up, next to the feed name.)
            // Article
            Column(modifier = Modifier.weight(1f)) {

                // Title
                Row {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style =
                            MaterialTheme.typography.titleMedium
                                .applyTextDirection(title.requiresBidi())
                                .merge(lineHeight = 22.sp),
                        maxLines =
                            if (articleListDesc != FlowArticleListDescPreference.NONE) 2 else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!articleListFeedName.value && !articleListDate.value) {
                        if (isStarred) {
                            StarredIcon()
                        } else {
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                    }
                }

                // Description
                if (
                    articleListDesc != FlowArticleListDescPreference.NONE &&
                        shortDescription.isNotBlank()
                ) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = shortDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall.applyTextDirection(
                                shortDescription.requiresBidi()
                            ),
                        maxLines =
                            when (articleListDesc) {
                                FlowArticleListDescPreference.LONG -> 4
                                FlowArticleListDescPreference.SHORT -> 2
                            },
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Image
            if (imgData != null && articleListImage.value) {
                BYAsyncImage(
                    modifier = Modifier.padding(start = 10.dp).size(80.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(com.ay.briefyou.brief.ui.Corner.Small)), // Brief You: 80 dp, 16 dp corners
                    data = imgData,
                    scale = Scale.FILL,
                    precision = Precision.INEXACT,
                    // Brief You: decoded at the size shown (80 dp), not 1000 px, so scrolling keeps
                    // more thumbnails in memory and decodes less
                    size = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.roundToPx() }.let { coil3.size.Size(it, it) },
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
fun StarredIcon(modifier: Modifier = Modifier) {
    val fontSize = LocalTextStyle.current.fontSize
    val iconSize = with(LocalDensity.current) { fontSize.toDp() }

    Icon(
        modifier = modifier
            .size(iconSize)
            .padding(end = 2.dp),
        imageVector = Icons.Rounded.Star,
        contentDescription = stringResource(R.string.starred),
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

private const val PositionalThresholdFraction = 0.4f
private const val SwipeActionDelay = 300L

@Composable
fun SwipeableArticleItem(
    articleWithFeed: ArticleWithFeed,
    isUnread: Boolean = articleWithFeed.article.isUnread,
    onClick: (ArticleWithFeed) -> Unit = {},
    isSwipeEnabled: () -> Boolean = { false },
    isMenuEnabled: Boolean = true,
    onToggleStarred: (ArticleWithFeed) -> Unit = {},
    onToggleRead: (ArticleWithFeed) -> Unit = {},
    onMarkAboveAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onMarkBelowAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onShare: ((ArticleWithFeed) -> Unit)? = null,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val pressContext = androidx.compose.ui.platform.LocalContext.current // Brief You
    val pressedArticle by androidx.compose.runtime.rememberUpdatedState(articleWithFeed) // Brief You
    // Brief You: the menu is only built once it is first opened (every row built it, closed, as the
    // list appeared), and kept after that so it can animate closed.
    var menuUsed by remember { mutableStateOf(false) } // Brief You

    val onLongClick =
        if (isMenuEnabled) {
            {
                menuUsed = true // Brief You
                isMenuExpanded = true
            }
        } else {
            null
        }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }

    SwipeActionBox(
        articleWithFeed = articleWithFeed,
        isRead = !isUnread,
        isStarred = articleWithFeed.article.isStarred,
        onToggleStarred = onToggleStarred,
        onToggleRead = onToggleRead,
        onShare = onShare,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .pointerInput(isMenuExpanded) {
                        awaitEachGesture {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false).let {
                                    menuOffset = it.position.round()
                                    com.ay.briefyou.brief.reading.ArticlePress.pressed(pressContext, pressedArticle) // Brief You: the article is prepared on the press
                                }
                            }
                        }
                    }
                    .background(
                        MaterialTheme.colorScheme.surface
                    )
                    .wrapContentSize()
        ) {
            ArticleItem(
                articleWithFeed = articleWithFeed,
                isUnread = isUnread,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            with(articleWithFeed.article) {
                if (isMenuEnabled && menuUsed) { // Brief You
                    AnimatedDropdownMenu(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                        offset = menuOffset,
                        bare = true,
                    ) {
                        ArticleItemMenuContent(
                            articleWithFeed = articleWithFeed,
                            isStarred = isStarred,
                            isRead = !isUnread,
                            onToggleStarred = onToggleStarred,
                            onToggleRead = onToggleRead,
                            onMarkAboveAsRead = onMarkAboveAsRead,
                            onMarkBelowAsRead = onMarkBelowAsRead,
                            onShare = onShare,
                        ) {
                            isMenuExpanded = false
                        }
                    }
                }
            }
        }
    }
}

private enum class SwipeDirection {
    StartToEnd,
    EndToStart,
}

@Composable
private fun SwipeActionBox(
    modifier: Modifier = Modifier,
    articleWithFeed: ArticleWithFeed,
    isStarred: Boolean,
    isRead: Boolean,
    onToggleStarred: (ArticleWithFeed) -> Unit,
    onToggleRead: (ArticleWithFeed) -> Unit,
    onShare: ((ArticleWithFeed) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Brief You: the swipe's Open in browser, the way links open everywhere else.
    val swipeContext = androidx.compose.ui.platform.LocalContext.current
    val swipeOpenLink = com.ay.briefyou.infrastructure.preference.LocalOpenLink.current
    val swipeOpenBrowser = com.ay.briefyou.infrastructure.preference.LocalOpenLinkSpecificBrowser.current
    val onOpenInBrowser: (ArticleWithFeed) -> Unit = {
        swipeContext.openURL(it.article.link, swipeOpenLink, swipeOpenBrowser)
    }
    val containerColor = MaterialTheme.colorScheme.tertiaryContainer
    // Brief You: the icon sits on the page color until the swipe passes the threshold, then on the accent
    // container: it uses the accent there, and the container's own "on" color (which Material You
    // keeps readable on it, whatever the theme) after.
    // The color is worked out inside each icon, so only the icon redraws while it changes: if the
    // whole box redrew, the swipe would see "new" actions every frame and buzz again and again.
    val swipeState = rememberSwipeableActionsState()

    val swipeToStartAction = LocalArticleListSwipeStartAction.current
    val swipeToEndAction = LocalArticleListSwipeEndAction.current

    val onSwipeEndToStart =
        when (swipeToStartAction) {
            SwipeStartActionPreference.None -> null
            SwipeStartActionPreference.ToggleRead -> onToggleRead
            SwipeStartActionPreference.ToggleStarred -> onToggleStarred
            SwipeStartActionPreference.Share -> onShare
            SwipeStartActionPreference.OpenInBrowser -> onOpenInBrowser
        }

    val onSwipeStartToEnd =
        when (swipeToEndAction) {
            SwipeEndActionPreference.None -> null
            SwipeEndActionPreference.ToggleRead -> onToggleRead
            SwipeEndActionPreference.ToggleStarred -> onToggleStarred
            SwipeEndActionPreference.Share -> onShare
            SwipeEndActionPreference.OpenInBrowser -> onOpenInBrowser
        }

    if (onSwipeStartToEnd == null && onSwipeEndToStart == null) {
        content()
        return
    }

    val startAction =
        onSwipeStartToEnd?.let {
            SwipeAction(
                icon = {
                    swipeActionIcon(
                            direction = SwipeDirection.StartToEnd,
                            isStarred = isStarred,
                            isRead = isRead,
                        )
                        ?.let {
                            Icon(
                                imageVector = it,
                                contentDescription = null,
                                tint = swipeIconTint(swipeState), // Brief You
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                },
                background = containerColor,
                isUndo = false,
                onSwipe = { onSwipeStartToEnd.invoke(articleWithFeed) },
            )
        }

    val endAction =
        onSwipeEndToStart?.let {
            SwipeAction(
                icon = {
                    swipeActionIcon(
                            direction = SwipeDirection.EndToStart,
                            isStarred = isStarred,
                            isRead = isRead,
                        )
                        ?.let {
                            Icon(
                                imageVector = it,
                                contentDescription = null,
                                tint = swipeIconTint(swipeState), // Brief You
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                },
                background = containerColor,
                isUndo = false,
                onSwipe = { onSwipeEndToStart.invoke(articleWithFeed) },
            )
        }

    SwipeableActionsBox(
        modifier = modifier,
        state = swipeState, // Brief You
        startActions = listOfNotNull(startAction),
        endActions = listOfNotNull(endAction),
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surface,
    ) {
        content.invoke()
    }
}

@Composable
private fun swipeActionIcon(
    direction: SwipeDirection,
    isStarred: Boolean,
    isRead: Boolean,
): ImageVector? {
    val swipeToStartAction = LocalArticleListSwipeStartAction.current
    val swipeToEndAction = LocalArticleListSwipeEndAction.current

    val starImageVector =
        remember(isStarred) { if (isStarred) Icons.Outlined.StarOutline else Icons.Rounded.Star }

    val readImageVector =
        remember(isRead) { if (isRead) Icons.Outlined.Circle else Icons.Rounded.CheckCircleOutline }

    return remember(direction) {
        when (direction) {
            SwipeDirection.StartToEnd -> {

                when (swipeToEndAction) {
                    SwipeEndActionPreference.None -> null
                    SwipeEndActionPreference.ToggleRead -> readImageVector
                    SwipeEndActionPreference.ToggleStarred -> starImageVector
                    SwipeEndActionPreference.Share -> Icons.Outlined.Share
                    SwipeEndActionPreference.OpenInBrowser -> Icons.AutoMirrored.Outlined.OpenInNew
                }
            }

            SwipeDirection.EndToStart -> {
                when (swipeToStartAction) {
                    SwipeStartActionPreference.None -> null
                    SwipeStartActionPreference.ToggleRead -> readImageVector
                    SwipeStartActionPreference.ToggleStarred -> starImageVector
                    SwipeStartActionPreference.Share -> Icons.Outlined.Share
                    SwipeStartActionPreference.OpenInBrowser -> Icons.AutoMirrored.Outlined.OpenInNew
                }
            }
        }
    }
}

@Composable
fun ArticleItemMenuContent(
    articleWithFeed: ArticleWithFeed,
    isStarred: Boolean = false,
    isRead: Boolean = false,
    onToggleStarred: (ArticleWithFeed) -> Unit = {},
    onToggleRead: (ArticleWithFeed) -> Unit = {},
    onMarkAboveAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onMarkBelowAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onShare: ((ArticleWithFeed) -> Unit)? = null,
    onItemClick: (() -> Unit)? = null,
) {
    // Brief You: the menu is Mark above as read, a strip of round buttons, Mark below as read.
    // The strip shows what is on now (read, starred); a tap flips it.
    onMarkAboveAsRead?.let {
        com.ay.briefyou.brief.ui.MenuRow(
            icon = Icons.Rounded.ArrowUpward,
            text = stringResource(id = R.string.mark_above_as_read),
            onClick = {
                onMarkAboveAsRead(articleWithFeed)
                onItemClick?.invoke()
            },
        )
        com.ay.briefyou.brief.ui.MenuGap()
    }
    com.ay.briefyou.brief.ui.MenuStrip {
        com.ay.briefyou.brief.ui.MenuRoundButton(
            icon = if (isRead) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
            description = stringResource(if (isRead) R.string.mark_as_unread else R.string.mark_as_read),
            selected = isRead,
            onClick = {
                onToggleRead(articleWithFeed)
                onItemClick?.invoke()
            },
        )
        com.ay.briefyou.brief.ui.MenuRoundButton(
            icon = if (isStarred) Icons.Rounded.Star else Icons.Outlined.StarOutline,
            description = stringResource(if (isStarred) R.string.mark_as_unstar else R.string.mark_as_starred),
            selected = isStarred,
            onClick = {
                onToggleStarred(articleWithFeed)
                onItemClick?.invoke()
            },
        )
        onShare?.let {
            com.ay.briefyou.brief.ui.MenuRoundButton(
                icon = Icons.Rounded.Share,
                description = stringResource(R.string.share),
                onClick = {
                    onShare(articleWithFeed)
                    onItemClick?.invoke()
                },
            )
            com.ay.briefyou.brief.share.NewspaperMenuButton(articleWithFeed, onItemClick)
        }
    }
    onMarkBelowAsRead?.let {
        com.ay.briefyou.brief.ui.MenuGap()
        com.ay.briefyou.brief.ui.MenuRow(
            icon = Icons.Rounded.ArrowDownward,
            text = stringResource(id = R.string.mark_below_as_read),
            onClick = {
                onMarkBelowAsRead(articleWithFeed)
                onItemClick?.invoke()
            },
        )
    }
}

/** Brief You: the swipe icon's color: the accent on the page color, then the container's "on" color. */
@Composable
private fun swipeIconTint(state: SwipeableActionsState): Color {
    val tint by
        animateColorAsState(
            if (state.hasCrossedSwipeThreshold() || state.isResettingOnRelease) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.tertiary
            },
            label = "swipeIconTint",
        )
    return tint
}
