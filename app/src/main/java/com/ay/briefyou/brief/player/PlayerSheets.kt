package com.ay.briefyou.brief.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import com.ay.briefyou.R
import com.ay.briefyou.brief.media.ArticleExtras
import com.ay.briefyou.brief.media.ArticleMedia
import com.ay.briefyou.brief.media.PlaybackPosition
import com.ay.briefyou.brief.readaloud.TextToSpeechManager
import com.ay.briefyou.brief.readaloud.readProgress
import com.ay.briefyou.brief.ui.rememberPressFeel
import com.ay.briefyou.ui.component.base.RYAsyncImage

/* The player's sheets: speed and the sleep timer. */
/** The speed sheet (from the speed pill): the speed, large, and a slider of the speeds offered. */
@Composable
fun SpeedSheet(onDismiss: () -> Unit) {
    // Picture-in-picture shows only the video: the sheet (its own window) closes.
    LaunchedEffect(VideoWindow.inPip) { if (VideoWindow.inPip) onDismiss() }
    val context = LocalContext.current
    val s by BriefPlayer.state.collectAsState()
    // The shared speed sheet (read aloud's too); the player follows each step.
    com.ay.briefyou.brief.ui.SpeedSheet(
        title = stringResource(R.string.player_speed_title),
        speeds = BriefPlayer.SPEEDS,
        speed = s.speed,
        onDismiss = onDismiss,
        onStep = { BriefPlayer.setSpeed(context, it) },
    )
}

/** The sleep timer sheet (from the moon): off, 15 to 60 minutes, or the end of the episode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    // Picture-in-picture shows only the video: the sheet (its own window) closes.
    LaunchedEffect(VideoWindow.inPip) { if (VideoWindow.inPip) onDismiss() }
    val haptic = LocalHapticFeedback.current
    val s by BriefPlayer.state.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = com.ay.briefyou.brief.ui.rememberFullSheetState()) {
        // The app's sheet design: title, divider, then the choices.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.player_sleep_timer), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 6.dp))
            val options =
                listOf(
                    0 to stringResource(R.string.player_sleep_off),
                    15 to stringResource(R.string.player_minutes, 15),
                    30 to stringResource(R.string.player_minutes, 30),
                    45 to stringResource(R.string.player_minutes, 45),
                    60 to stringResource(R.string.player_hour),
                    -1 to stringResource(R.string.player_end_of_episode),
                )
            val selected =
                when (val sleep = s.sleep) {
                    BriefPlayer.Sleep.Off -> 0
                    BriefPlayer.Sleep.EndOfEpisode -> -1
                    is BriefPlayer.Sleep.Minutes -> sleep.minutes
                }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.chunked(3).forEachIndexed { row, items ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEachIndexed { col, (minutes, label) ->
                            val outer = 26.dp
                            val inner = 10.dp
                            val shape =
                                RoundedCornerShape(
                                    topStart = if (row == 0 && col == 0) outer else inner,
                                    topEnd = if (row == 0 && col == 2) outer else inner,
                                    bottomStart = if (row == 1 && col == 0) outer else inner,
                                    bottomEnd = if (row == 1 && col == 2) outer else inner,
                                )
                            val on = minutes == selected
                            val scheme = MaterialTheme.colorScheme
                            val transition = updateTransition(on, label = "sleep option")
                            val container by transition.animateColor(label = "container") { if (it) scheme.primary else scheme.secondaryContainer }
                            val content by transition.animateColor(label = "content") { if (it) scheme.onPrimary else scheme.onSecondaryContainer }
                            val press = rememberPressFeel(rest = 0.dp, held = 0.dp, squeezeTo = 0.94f)
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    BriefPlayer.setSleep(minutes)
                                },
                                shape = shape,
                                color = container,
                                contentColor = content,
                                interactionSource = press.interaction,
                                modifier = with(press) { Modifier.weight(1f).height(52.dp).squeeze() },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            val sleep = s.sleep
            AnimatedVisibility(visible = sleep is BriefPlayer.Sleep.Minutes) {
                val endsAt = (sleep as? BriefPlayer.Sleep.Minutes)?.endsAt ?: 0L
                val left by produceState(endsAt - System.currentTimeMillis(), endsAt) {
                    while (true) {
                        value = endsAt - System.currentTimeMillis()
                        kotlinx.coroutines.delay(1_000)
                    }
                }
                Text(
                    stringResource(R.string.player_sleep_left, formatTime(left)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
