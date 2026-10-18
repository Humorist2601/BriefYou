@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.home.reading

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.ash.reader.R
import me.ash.reader.ui.component.FloatingBarGap

private val PillOrigin = TransformOrigin(.5f, 1f)

/** The reading bar's height (48 dp buttons and 4 dp above and below), for things placed above it. */
val ReadingBarHeight = 56.dp

/**
 * The reading page's actions: a floating pill like the one on the Unread list (accent colors, fully
 * rounded, a soft shadow), with icons only. From left to right: star, AI summary, next article,
 * read aloud, and full content. Long presses: star marks as read or unread, AI opens the AI
 * settings, next article goes to the previous one, full content opens the browser. It shrinks
 * away while you scroll down and comes back when you scroll up.
 *
 * [isUnread], [isBoldCharacters], [onUnread], [onBoldCharacters] and [onReadAloud] are kept for the
 * callers but no longer have a button here.
 */
@Composable
fun BottomBar(
    isShow: Boolean,
    isUnread: Boolean,
    isStarred: Boolean,
    isNextArticleAvailable: Boolean,
    isPreviousArticleAvailable: Boolean = false,
    isFullContent: Boolean,
    isBoldCharacters: Boolean,
    ttsButton: @Composable () -> Unit,
    onUnread: (isUnread: Boolean) -> Unit = {},
    onStarred: (isStarred: Boolean) -> Unit = {},
    onNextArticle: () -> Unit = {},
    /** Long press on next article: the previous one. */
    onPreviousArticle: () -> Unit = {},
    /** Long press on the AI summary button: the AI settings. */
    onOpenAiSettings: () -> Unit = {},
    onFullContent: (isFullContent: Boolean) -> Unit = {},
    /** Long press on the full content button: open the article in the browser. */
    onOpenInBrowser: () -> Unit = {},
    onBoldCharacters: () -> Unit = {},
    onReadAloud: () -> Unit = {},
    onSummarize: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier.fillMaxSize().zIndex(1f),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = isShow,
            modifier = Modifier.navigationBarsPadding().padding(bottom = FloatingBarGap),
            enter =
                scaleIn(
                    transformOrigin = PillOrigin,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ) + fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
            exit =
                scaleOut(
                    transformOrigin = PillOrigin,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ) + fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            val content = MaterialTheme.colorScheme.onPrimaryContainer
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = content,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton(
                        icon = if (isStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        description =
                            stringResource(
                                if (isStarred) R.string.mark_as_unstar else R.string.mark_as_starred
                            ),
                        // Long press: mark as read or unread.
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUnread(!isUnread)
                        },
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onStarred(!isStarred)
                    }
                    PillButton(
                        icon = Icons.Outlined.AutoAwesome,
                        description = stringResource(R.string.ai_summarize),
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenAiSettings()
                        },
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onSummarize()
                    }
                    PillButton(
                        icon = Icons.Rounded.ExpandMore,
                        description = stringResource(R.string.next_article),
                        enabled = isNextArticleAvailable,
                        // Long press: the previous article (works even at the end of the list).
                        onLongClick =
                            if (isPreviousArticleAvailable) {
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPreviousArticle()
                                }
                            } else null,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onNextArticle()
                    }
                    // Read aloud keeps its own button, which shows the progress while reading.
                    // (It grows to the left with the speed chip while reading.)
                    Box(
                        modifier = Modifier.height(48.dp).widthIn(min = 48.dp).animateContentSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides content) { ttsButton() }
                    }
                    PillButton(
                        icon =
                            if (isFullContent) Icons.AutoMirrored.Rounded.Article
                            else Icons.AutoMirrored.Outlined.Article,
                        description = stringResource(R.string.parse_full_content),
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenInBrowser()
                        },
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onFullContent(!isFullContent)
                    }
                }
            }
        }
    }
}

/** One icon in the pill: no label, the pill's content color, dimmed when it can't be used. */
@Composable
private fun PillButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier.size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    // Dimmed buttons can still have a long press (next article: previous one).
                    enabled = enabled || onLongClick != null,
                    role = Role.Button,
                    onClickLabel = description,
                    onLongClick = onLongClick,
                    onClick = { if (enabled) onClick() },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            tint =
                if (enabled) LocalContentColor.current
                else LocalContentColor.current.copy(alpha = 0.38f),
        )
    }
}
