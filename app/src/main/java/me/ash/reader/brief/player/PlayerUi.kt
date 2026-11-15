package me.ash.reader.brief.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import me.ash.reader.R
import me.ash.reader.brief.media.ArticleExtras
import me.ash.reader.brief.media.ArticleMedia
import me.ash.reader.brief.media.PlaybackPosition
import me.ash.reader.brief.readaloud.TextToSpeechManager
import me.ash.reader.brief.readaloud.readProgress
import me.ash.reader.ui.component.base.RYAsyncImage

/** The open article's episode, video and comments link (provided by the reading page). */
val LocalArticleExtras = compositionLocalOf<ArticleExtras?> { null }

/** An article's extras, loaded once per article (see [ArticleMedia.of]). */
@Composable
fun rememberArticleExtras(articleId: String?, picture: String?): ArticleExtras? {
    val extras by
        produceState<ArticleExtras?>(null, articleId, picture) {
            value = articleId?.let { ArticleMedia.of(it, picture) }
        }
    return extras?.takeIf { it.articleId == articleId }
}

/** 1:05:09 or 12:04. */
fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * The article's podcast episode or video, under its title: an Expressive player card. Plays,
 * pauses, skips 30 seconds, and opens the speed and sleep timer sheet. A video plays in the
 * card (see [VideoCard]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArticleMediaCard(title: String, feedName: String, artwork: String?, modifier: Modifier = Modifier) {
    val extras = LocalArticleExtras.current ?: return
    val url = extras.mediaUrl ?: return
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val s by BriefPlayer.state.collectAsState()
    val current = s.isFor(extras.articleId)
    val now =
        remember(extras, title, feedName, artwork) {
            BriefPlayer.Now(
                articleId = extras.articleId,
                url = url,
                kind = extras.mediaKind ?: ArticleExtras.AUDIO,
                title = title,
                feedName = feedName,
                artwork = extras.image ?: artwork,
            )
        }
    val saved by
        produceState<PlaybackPosition?>(null, extras.articleId, current) {
            value = if (current) null else runCatching { ArticleMedia.dao?.position(extras.articleId) }.getOrNull()
        }
    var sheet by remember { mutableStateOf(false) }
    val position = if (current) s.positionMs else saved?.positionMs ?: 0L
    val duration = if (current && s.durationMs > 0) s.durationMs else saved?.durationMs?.takeIf { it > 0 } ?: extras.durationMs ?: 0L
    val playing = current && s.isPlaying
    val onPlay: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        if (current) BriefPlayer.toggle(context) else BriefPlayer.play(context, now, extras.durationMs)
    }

    if (extras.isVideo) {
        VideoCard(
            modifier = modifier.padding(top = 16.dp),
            now = now,
            current = current,
            playing = playing,
            buffering = current && s.buffering,
            position = position,
            duration = duration,
            onPlay = onPlay,
        )
    } else {
        Surface(
            modifier = modifier.padding(top = 16.dp).fillMaxWidth().padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(now.artwork, Modifier.size(64.dp), RoundedCornerShape(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = feedName,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text =
                                when {
                                    current && s.buffering -> stringResource(R.string.player_loading)
                                    !current && position > 0 -> stringResource(R.string.player_picks_up, formatTime(position))
                                    duration > 0 -> stringResource(R.string.player_episode_length, ((duration + 30_000) / 60_000).toInt())
                                    else -> stringResource(R.string.player_episode)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SeekBar(
                    progress = if (duration > 0) position.toFloat() / duration else 0f,
                    playing = playing,
                    enabled = current && duration > 0,
                    onSeek = { BriefPlayer.seekTo((it * duration).toLong()) },
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (duration > 0) {
                        Text("−" + formatTime(duration - position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TonalPill(text = speedLabel(s.speed), description = stringResource(R.string.player_speed)) { sheet = true }
                    IconButton(onClick = { BriefPlayer.seekBy(-PlayerService.SKIP_MS) }, enabled = current, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Replay30, stringResource(R.string.player_back_30), modifier = Modifier.size(28.dp))
                    }
                    PlayButton(playing = playing, onClick = onPlay, width = 76.dp, height = 64.dp)
                    IconButton(onClick = { BriefPlayer.seekBy(PlayerService.SKIP_MS) }, enabled = current, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Forward30, stringResource(R.string.player_forward_30), modifier = Modifier.size(28.dp))
                    }
                    TonalIcon(
                        icon = Icons.Outlined.Bedtime,
                        description = stringResource(R.string.player_sleep_timer),
                        active = s.sleep != BriefPlayer.Sleep.Off,
                    ) { sheet = true }
                }
            }
        }
    }
    if (sheet) PlayerSheet(onDismiss = { sheet = false })
}

private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) "%.0f".format(speed) else "%.1f".format(speed)) + "×"

/** The episode's picture, or a microphone on the tonal colour. */
@Composable
internal fun Artwork(url: String?, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    Box(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Mic, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(26.dp))
        if (url != null) {
            RYAsyncImage(modifier = Modifier.matchParentSize(), data = url, contentScale = ContentScale.Crop, contentDescription = null)
        }
    }
}

/** The big play/pause: a rounded square in the accent colour. */
@Composable
internal fun PlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width, height),
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (playing) R.string.player_pause else R.string.player_play),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun TonalPill(text: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.height(40.dp).semantics { contentDescription = "$description $text" },
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TonalIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(width = 44.dp, height = 40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, modifier = Modifier.size(20.dp)) }
    }
}

/**
 * The progress: Expressive's wavy line while playing, a flat one while paused. Tap or drag to
 * move; the player only jumps when the finger lifts (seeking on every move fought the player).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SeekBar(progress: Float, playing: Boolean, enabled: Boolean, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown by animateFloatAsState(drag ?: progress.coerceIn(0f, 1f), label = "seek")
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { drag?.let(onSeek); drag = null },
                        onDragCancel = { drag = null },
                    ) { change, _ ->
                        drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (playing && drag == null) {
            LinearWavyProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The speed and the sleep timer, in one sheet (from the speed chip or the moon). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s by BriefPlayer.state.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.player_speed), style = MaterialTheme.typography.titleMedium)
                Text(speedLabel(s.speed), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            val speeds = BriefPlayer.SPEEDS
            val index = speeds.indexOfFirst { it >= s.speed - 0.01f }.coerceAtLeast(0)
            Slider(
                value = index.toFloat(),
                onValueChange = { BriefPlayer.setSpeed(context, speeds[it.toInt().coerceIn(0, speeds.lastIndex)]) },
                valueRange = 0f..speeds.lastIndex.toFloat(),
                steps = speeds.size - 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.player_sleep_timer), style = MaterialTheme.typography.titleMedium)
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
                        Surface(
                            onClick = { BriefPlayer.setSleep(minutes) },
                            shape = shape,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
            val sleep = s.sleep
            if (sleep is BriefPlayer.Sleep.Minutes) {
                val left by produceState(sleep.endsAt - System.currentTimeMillis(), sleep) {
                    while (true) {
                        value = sleep.endsAt - System.currentTimeMillis()
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

/**
 * The reading bar's play button for an article with an episode: its progress as a ring, play or
 * pause in the middle (in place of read aloud). Long press: the speed and sleep timer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EpisodeBarButton(title: String, feedName: String, artwork: String?) {
    val extras = LocalArticleExtras.current ?: return
    val url = extras.mediaUrl ?: return
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val s by BriefPlayer.state.collectAsState()
    val current = s.isFor(extras.articleId)
    var sheet by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier.size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (current) BriefPlayer.toggle(context)
                        else BriefPlayer.play(context, BriefPlayer.Now(extras.articleId, url, extras.mediaKind ?: ArticleExtras.AUDIO, title, feedName, extras.image ?: artwork), extras.durationMs)
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        sheet = true
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (current) {
            me.ash.reader.brief.readaloud.ReadAloudRing(progress = s.progress, playing = s.isPlaying, modifier = Modifier.size(40.dp))
        }
        Icon(
            if (current && s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (current && s.isPlaying) R.string.player_pause else R.string.player_play),
            modifier = Modifier.size(if (current) 20.dp else 24.dp),
            tint = LocalContentColor.current,
        )
    }
    if (sheet) PlayerSheet(onDismiss = { sheet = false })
}

/**
 * The "now playing" pill above the bottom bar (on Feeds and the article lists): the episode's
 * picture with its progress as a ring, title and time left, and play/pause. Tap: the full
 * controls. Read aloud shows here the same way while it reads (with its sound-wave icon).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val s by BriefPlayer.state.collectAsState()
    val tts = remember { runCatching { EntryPointAccessors.fromApplication(context.applicationContext, BriefPlayer.Deps::class.java).tts() }.getOrNull() }
    val ttsState = tts?.stateFlow?.collectAsState()?.value
    val reading = ttsState is TextToSpeechManager.State.Reading || ttsState is TextToSpeechManager.State.Paused
    val now = s.now
    var sheet by remember { mutableStateOf(false) }
    if (now == null && !reading) return
    val showEpisode = now != null && (s.isPlaying || !reading || ttsState !is TextToSpeechManager.State.Reading)
    Surface(
        onClick = { if (showEpisode) sheet = true },
        modifier = modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = 24.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                val progress = if (showEpisode) s.progress else ttsState?.readProgress() ?: 0f
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(52.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    strokeWidth = 3.dp,
                )
                if (showEpisode) {
                    Artwork(now!!.artwork, Modifier.size(40.dp), CircleShape)
                } else {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.GraphicEq, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (showEpisode) now!!.title else stringResource(R.string.player_reading_aloud, tts?.title.orEmpty()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        if (showEpisode) {
                            if (s.durationMs > 0) stringResource(R.string.player_feed_left, now!!.feedName, formatTime(s.durationMs - s.positionMs))
                            else now!!.feedName
                        } else stringResource(R.string.read_aloud),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val playing = if (showEpisode) s.isPlaying else ttsState is TextToSpeechManager.State.Reading
            PlayButton(
                playing = playing,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (showEpisode) BriefPlayer.toggle(context)
                    else if (ttsState is TextToSpeechManager.State.Reading) tts?.pause() else tts?.resume()
                },
                width = 48.dp,
                height = 48.dp,
                corner = 18.dp,
            )
            IconButton(
                onClick = { if (showEpisode) BriefPlayer.stop() else tts?.stop() },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Rounded.Close, stringResource(R.string.player_stop), modifier = Modifier.size(20.dp))
            }
        }
    }
    if (sheet && now != null) NowPlayingSheet(onDismiss = { sheet = false })
}

/** The pill's full controls: picture, title, progress, skip, speed and sleep timer. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s by BriefPlayer.state.collectAsState()
    val now = s.now ?: return
    var options by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Artwork(now.artwork, Modifier.size(168.dp), RoundedCornerShape(32.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(now.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(now.feedName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SeekBar(
                progress = s.progress,
                playing = s.isPlaying,
                enabled = s.durationMs > 0,
                onSeek = { BriefPlayer.seekTo((it * s.durationMs).toLong()) },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(s.positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.durationMs > 0) Text("−" + formatTime(s.durationMs - s.positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TonalPill(text = speedLabel(s.speed), description = stringResource(R.string.player_speed)) { options = true }
                IconButton(onClick = { BriefPlayer.seekBy(-PlayerService.SKIP_MS) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.Replay30, stringResource(R.string.player_back_30), modifier = Modifier.size(28.dp))
                }
                PlayButton(playing = s.isPlaying, onClick = { BriefPlayer.toggle(context) }, width = 76.dp, height = 64.dp)
                IconButton(onClick = { BriefPlayer.seekBy(PlayerService.SKIP_MS) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.Forward30, stringResource(R.string.player_forward_30), modifier = Modifier.size(28.dp))
                }
                TonalIcon(icon = Icons.Outlined.Bedtime, description = stringResource(R.string.player_sleep_timer), active = s.sleep != BriefPlayer.Sleep.Off) { options = true }
            }
        }
    }
    if (options) PlayerSheet(onDismiss = { options = false })
}
