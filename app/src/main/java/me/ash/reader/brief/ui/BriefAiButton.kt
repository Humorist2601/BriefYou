@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.brief.tour.BriefHint
import me.ash.reader.brief.tour.BriefHints
import me.ash.reader.brief.tour.hintText

/** What the AI button at the bottom of the Unread list opens. */
class AiActions(val onGist: () -> Unit, val onTriage: () -> Unit)

/** What a long press on the AI button opens: Triage (the default) or AI Gist. */
enum class AiLongPress {
    Triage,
    Gist;

    /** Its name: "Triage" or "AI Gist". */
    @Composable
    fun label(): String = stringResource(if (this == Triage) R.string.triage else R.string.flow_fab_gist)

    companion object {
        private const val PREFS = "ai_button"
        private const val KEY = "long_press"
        @Volatile private var cached: AiLongPress? = null
        /** Bumped on a change, so the button and the setting (which read it) update at once. */
        private var changes by mutableIntStateOf(0)

        /** The chosen one (read from the phone once, then kept). */
        fun get(context: Context): AiLongPress {
            check(changes >= 0) // read, so a change is seen
            return cached
                ?: runCatching {
                        valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: Triage.name)
                    }
                    .getOrDefault(Triage)
                    .also { cached = it }
        }

        fun set(context: Context, value: AiLongPress) {
            cached = value
            changes++
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value.name).apply()
        }
    }
}

/**
 * The AI button beside the filter toolbar: a tap opens a small menu (AI Gist and Triage, Material
 * 3's floating action button menu), a long press opens the one chosen in Settings straight away.
 * Open, it turns round and shows a close icon. The menu keeps 16 dp around the button
 * ([AiButtonMenuMargin]); the bar takes that back so the button lines up with the toolbar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BriefAiButton(
    actions: AiActions,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    longPress: AiLongPress,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        button = {
            val corner by animateDpAsState(
                if (expanded) BottomBarHeight / 2 else 20.dp,
                MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "ai button shape",
            )
            val shape = RoundedCornerShape(corner)
            val colors = MaterialTheme.colorScheme
            val dark = colors.surface.luminance() < 0.5f
            val name = stringResource(R.string.ai_button)
            val hold = stringResource(if (longPress == AiLongPress.Triage) R.string.triage else R.string.flow_fab_gist)
            Box(contentAlignment = Alignment.Center) {
                // The first Unread list: what the button does (once).
                BriefHint(
                    key = BriefHints.UNREAD_AI_BUTTON,
                    text = hintText(name, stringResource(R.string.hint_unread_ai_button)),
                    enabled = !expanded,
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier.size(BottomBarHeight)
                            .dropShadow(shape, Shadow(radius = 16.dp, color = Color.Black, offset = DpOffset(0.dp, 4.dp), alpha = if (dark) 0.3f else 0.16f))
                            .clip(shape)
                            .background(colors.primary)
                            .combinedClickable(
                                role = Role.Button,
                                onClickLabel = name,
                                onLongClickLabel = hold,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onExpandedChange(!expanded)
                                },
                                // (The long press buzzes by itself.)
                                onLongClick = {
                                    onExpandedChange(false)
                                    if (longPress == AiLongPress.Triage) actions.onTriage() else actions.onGist()
                                },
                            ),
                ) {
                    AnimatedContent(
                        targetState = expanded,
                        transitionSpec = {
                            (scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()))
                                .togetherWith(scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()))
                        },
                        label = "ai button icon",
                    ) { open ->
                        Icon(
                            imageVector = if (open) Icons.Rounded.Close else Icons.Rounded.AutoAwesome,
                            contentDescription = if (open) stringResource(R.string.close) else name,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                actions.onGist()
            },
            text = { Text(stringResource(R.string.flow_fab_gist)) },
            icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                actions.onTriage()
            },
            text = { Text(stringResource(R.string.triage)) },
            icon = { Icon(Icons.Rounded.Style, contentDescription = null) },
        )
    }
}

/** Takes back the FAB menu's own 16 dp margins (at the side and under the button). */
internal val AiButtonMenuMargin = 16.dp
