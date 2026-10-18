package me.ash.reader.brief.readaloud

import kotlin.math.roundToInt
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import android.content.Context
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.brief.readaloud.TextToSpeechManager
import me.ash.reader.ui.motion.Direction
import me.ash.reader.ui.motion.sharedYAxisTransitionSlow

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TtsButton(
    modifier: Modifier = Modifier,
    onClick: (TextToSpeechManager.State) -> Unit,
    state: TextToSpeechManager.State,
    /** Long press while paused: stop. */
    onStop: () -> Unit = {},
    /** Long press while reading: the speed. */
    onSpeed: () -> Unit = {},
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        contentKey = { it.isActive() },
        transitionSpec = {
            sharedYAxisTransitionSlow(
                direction =
                    if (targetState.isActive()) Direction.Forward
                    else Direction.Backward,
                motionScheme = motionScheme
            )
        }) { state ->
        when (state) {
            // Reading or paused: a wavy ring around the button shows how far it has read (from
            // the words being spoken), with Pause or Play in the middle. Tap to pause or play;
            // long press: the speed while reading, stop while paused.
            is TextToSpeechManager.State.Reading, is TextToSpeechManager.State.Paused -> {
                val playing = state is TextToSpeechManager.State.Reading
                Box(
                    modifier = modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onClick(state)
                            },
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (playing) onSpeed() else onStop()
                            },
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    ReadAloudRing(
                        progress = state.readProgress(),
                        playing = playing,
                        modifier = Modifier.size(40.dp),
                    )
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(
                            if (playing) R.string.read_aloud_pause else R.string.read_aloud_play
                        ),
                        modifier = Modifier.size(20.dp),
                        tint = LocalContentColor.current
                    )
                }
            }

            else -> {
                Box(
                    modifier = modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onClick(state)
                        }, onLongClick = { context.openTtsSettings() }),
                    contentAlignment = Alignment.Center
                ) {
                    // Same color as the other icons around it (the reading bar's content color).
                    Icon(
                        Icons.AutoMirrored.Outlined.VolumeUp,
                        stringResource(R.string.read_aloud),
                        modifier = Modifier.size(24.dp),
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TtsProgressIndicator(progress: (() -> Float), modifier: Modifier = Modifier) {

    val animatedProgress by animateFloatAsState(
        targetValue = progress(),
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )
    CircularProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier.size(24.dp),
        gapSize = 0.dp,
        strokeCap = StrokeCap.Butt,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        strokeWidth = 3.dp
    )
}

@Preview
@Composable
private fun Preview() {
    Column {
        TtsProgressIndicator({ .5f })
//        TtsButton(onClick = {}, state = TextToSpeechManager.State.Idle)
//        TtsButton(onClick = {}, state = TextToSpeechManager.State.Reading(200, 400))
        var state: TextToSpeechManager.State by remember { mutableStateOf(TextToSpeechManager.State.Idle) }
        TtsButton(onClick = {
            state = if (state == TextToSpeechManager.State.Idle) {
                TextToSpeechManager.State.Reading(200, 400)
            } else {
                TextToSpeechManager.State.Idle
            }
        }, state = state)
    }

}

private fun TextToSpeechManager.State.isActive() =
    this is TextToSpeechManager.State.Reading || this is TextToSpeechManager.State.Paused

/** How far read aloud has got, 0 to 1 (0 when it isn't reading). */
fun TextToSpeechManager.State.readProgress(): Float =
    when (this) {
        is TextToSpeechManager.State.Reading -> progress
        is TextToSpeechManager.State.Paused -> progress
        else -> 0f
    }

/**
 * Read aloud's progress as an Expressive wavy ring (still while paused), in the content colour
 * over a faint track. Used by the reading bar and the AI summary sheets.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadAloudRing(progress: Float, playing: Boolean, modifier: Modifier = Modifier) {
    val animated by
        animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "readAloudProgress",
        )
    val color = LocalContentColor.current
    if (playing) {
        CircularWavyProgressIndicator(
            progress = { animated },
            modifier = modifier,
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    } else {
        CircularProgressIndicator(
            progress = { animated },
            modifier = modifier,
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            strokeWidth = 4.dp,
        )
    }
}

/** Opens Android's text-to-speech settings (voice, speed, engine). */
fun Context.openTtsSettings() {
    try {
        startActivity(
            Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "TTS settings screen not found.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Read aloud's speed, 0.8× to 2× in steps of 0.2, as a slider in a small sheet (long press on read
 * aloud while reading). The new speed applies from the current word, and is kept for next time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAloudSpeedSheet(speed: Float, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember(speed) { mutableFloatStateOf(speed) }
    val haptic = LocalHapticFeedback.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()
        ) {
            Text(
                stringResource(R.string.read_aloud_speed_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TextToSpeechManager.speedLabel(TextToSpeechManager.SPEEDS.first()),
                    style = MaterialTheme.typography.labelLarge,
                )
                // Steps of 0.2× (0.8× to 2×), with a tick on each one.
                Slider(
                    value = value,
                    onValueChange = {
                        val step = TextToSpeechManager.snap(it)
                        if (step != value) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            value = step
                        }
                    },
                    onValueChangeFinished = { onSpeed(value) },
                    steps = TextToSpeechManager.SPEEDS.size - 2,
                    valueRange = TextToSpeechManager.SPEEDS.first()..TextToSpeechManager.SPEEDS.last(),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(
                    TextToSpeechManager.speedLabel(TextToSpeechManager.SPEEDS.last()),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                TextToSpeechManager.speedLabel(value),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
