@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.ay.briefyou.brief.feeds

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.view.SoundEffectConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.toPath
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.State
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ay.briefyou.R
import com.ay.briefyou.brief.ui.GoogleSansWideFontFamily
import com.ay.briefyou.brief.ui.LocalPressFeel
import com.ay.briefyou.brief.ui.bannerContainer
import com.ay.briefyou.brief.ui.rememberPressFeel
import com.ay.briefyou.domain.model.general.Filter
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date

/**
 * The hero card at the top of the Home screen: a greeting and when the account last synced, then
 * the count in a cookie with a short sentence beside it. It has the press feel of the app's other
 * openers (squeeze, tighter corners, the cookie turns one bump). Only the faint cookies behind it
 * move (a clover, a pill and a rounded square turning slowly); the main cookie keeps still.
 */
@Composable
fun HomeHero(
    filter: Filter,
    count: Int,
    groupCount: Int,
    feedCount: Int,
    topGroup: String?,
    updatedAt: Date?,
    hasFeeds: Boolean?,
    loading: Boolean,
    newCount: Int,
    oldestDays: Int?,
    topFeed: String?,
    syncing: Boolean,
    progressDone: Int,
    progressTotal: Int,
    problem: com.ay.briefyou.brief.app.SyncStatus.Problem,
    online: Boolean,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onClick: () -> Unit,
    onAddFeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val container = bannerContainer()
    val ink = MaterialTheme.colorScheme.contentColorFor(container)
    // As on Trainable's membership card: the colour runs diagonally into a deeper tone.
    val gradientEnd =
        if (com.ay.briefyou.infrastructure.preference.LocalDarkTheme.current.isDarkTheme()) {
            androidx.compose.ui.graphics.lerp(container, MaterialTheme.colorScheme.surface, 0.35f)
        } else {
            androidx.compose.ui.graphics.lerp(container, MaterialTheme.colorScheme.primary, 0.22f)
        }
    val press = rememberPressFeel(rest = 32.dp, held = 18.dp, bumps = 9, squeezeTo = 0.98f)

    val noFeeds = hasFeeds == false
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val (greeting, greetingIcon) =
        when (hour) {
            in 5..11 -> stringResource(R.string.hero_greeting_morning) to Icons.Outlined.WbSunny
            in 12..16 -> stringResource(R.string.hero_greeting_afternoon) to Icons.Outlined.WbCloudy
            in 17..21 -> stringResource(R.string.hero_greeting_evening) to Icons.Outlined.WbTwilight
            else -> stringResource(R.string.hero_greeting_night) to Icons.Outlined.Bedtime
        }

    val groups = pluralStringResource(R.plurals.hero_groups, groupCount, groupCount)
    val feedsText = pluralStringResource(R.plurals.hero_feeds, feedCount, feedCount)
    val newMode = !noFeeds && !loading && filter.isUnread() && count > 0 && newCount > 0
    val shownCount = if (newMode) newCount else count
    val baseName: String?
    val baseSentence: String
    val big: String
    val cookieIcon: ImageVector?
    when {
        noFeeds -> {
            big = stringResource(R.string.hero_add_feed)
            baseSentence = stringResource(R.string.hero_add_feed_desc)
            cookieIcon = Icons.Outlined.Add
            baseName = null
        }
        loading -> {
            big = stringResource(R.string.hero_counting)
            baseSentence = ""
            cookieIcon = null
            baseName = null
        }
        filter == Filter.All -> {
            big = stringResource(R.string.hero_articles)
            baseSentence = stringResource(R.string.hero_from, feedsText)
            cookieIcon = null
            baseName = null
        }
        count == 0 && filter.isStarred() -> {
            big = stringResource(R.string.hero_no_stars)
            baseSentence = stringResource(R.string.hero_no_stars_desc)
            cookieIcon = Icons.Outlined.Star
            baseName = null
        }
        count == 0 -> {
            big = stringResource(R.string.hero_caught_up)
            baseSentence = stringResource(R.string.hero_caught_up_desc)
            cookieIcon = Icons.Outlined.DoneAll
            baseName = null
        }
        filter.isStarred() -> {
            big = stringResource(R.string.hero_starred)
            baseSentence = stringResource(R.string.hero_in, groups)
            cookieIcon = null
            baseName = null
        }
        newMode -> {
            big = stringResource(R.string.hero_new)
            baseSentence = stringResource(R.string.hero_new_desc)
            cookieIcon = null
            baseName = null
        }
        filter.isUnread() && oldestDays != null && oldestDays >= 30 -> {
            big = stringResource(R.string.hero_unread)
            baseSentence = pluralStringResource(R.plurals.hero_oldest_days, oldestDays, oldestDays)
            cookieIcon = null
            baseName = null
        }
        groupCount < 2 && topFeed != null && feedCount > 1 -> {
            big = stringResource(R.string.hero_unread)
            baseSentence = stringResource(R.string.hero_mostly_from, topFeed)
            cookieIcon = null
            baseName = topFeed
        }
        groupCount >= 2 && topGroup != null -> {
            big = stringResource(R.string.hero_unread)
            baseSentence = stringResource(R.string.hero_across, groups, topGroup)
            cookieIcon = null
            baseName = topGroup
        }
        else -> {
            big = stringResource(R.string.hero_unread)
            baseSentence = stringResource(R.string.hero_in, topGroup ?: groups)
            cookieIcon = null
            baseName = topGroup
        }
    }
    // A running sync or a problem with the last one says so in the sentence and the chip.
    val statusSentence: String? =
        when {
            noFeeds || loading -> null
            syncing -> stringResource(R.string.hero_syncing_desc)
            problem == com.ay.briefyou.brief.app.SyncStatus.Problem.SignIn -> stringResource(R.string.hero_signin_desc)
            !online -> stringResource(R.string.hero_offline_desc)
            problem == com.ay.briefyou.brief.app.SyncStatus.Problem.Failed -> stringResource(R.string.hero_failed_desc)
            else -> null
        }
    val sentence = statusSentence ?: baseSentence
    val name = if (statusSentence != null) null else baseName
    val number = remember(shownCount) { NumberFormat.getIntegerInstance().format(shownCount) }

    // When the account last synced: a quiet chip, greyed with a "synced" word when it is old.
    val age = updatedAt?.let { ((System.currentTimeMillis() - it.time) / 60_000L).coerceAtLeast(0L) }
    val agoText =
        when {
            age == null -> null
            age < 1 -> stringResource(R.string.hero_just_now)
            age < 60 -> stringResource(R.string.hero_minutes_ago, age.toInt())
            age < 24 * 60 -> stringResource(R.string.hero_hours_ago, (age / 60).toInt())
            else -> stringResource(R.string.hero_days_ago, (age / (24 * 60)).toInt())
        }
    // Old = more than two sync intervals late (at least an hour); on manual sync it is never old.
    val interval = com.ay.briefyou.infrastructure.preference.LocalSyncInterval.current.value
    val stale = age != null && interval > 0L && age >= maxOf(60L, 2 * interval)
    val signInProblem = problem == com.ay.briefyou.brief.app.SyncStatus.Problem.SignIn
    val chip: HeroChip? =
        when {
            noFeeds || loading -> null
            syncing ->
                HeroChip(
                    if (progressTotal > 0) stringResource(R.string.hero_chip_progress, progressDone, progressTotal) else stringResource(R.string.hero_chip_syncing),
                    Icons.Outlined.Sync,
                    null,
                )
            signInProblem -> HeroChip(stringResource(R.string.hero_chip_signin), Icons.Outlined.Key, onSignIn)
            !online -> HeroChip(stringResource(R.string.hero_chip_offline), Icons.Outlined.CloudOff, null)
            problem == com.ay.briefyou.brief.app.SyncStatus.Problem.Failed ->
                HeroChip(stringResource(R.string.hero_chip_failed), Icons.Outlined.ErrorOutline, onRetry)
            agoText == null -> null
            stale -> HeroChip(stringResource(R.string.hero_synced_ago, agoText), Icons.Outlined.CloudOff, null)
            else -> HeroChip(agoText, Icons.Outlined.Sync, null)
        }

    Surface(
        modifier = with(press) { modifier.padding(horizontal = 16.dp).squeeze().fillMaxWidth() },
        color = Color.Transparent,
        shape = RoundedCornerShape(press.corner),
        contentColor = ink,
        interactionSource = press.interaction,
        onClick = {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            if (noFeeds) onAddFeed() else onClick()
        },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPressFeel provides press) {
            Box(Modifier.background(Brush.linearGradient(listOf(container, gradientEnd)))) {
                FaintCookies(Modifier.matchParentSize())
                if (syncing && progressTotal > 0) {
                    // How far the sync is: a thin bar along the bottom edge.
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(ink.copy(alpha = 0.13f)))
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth((progressDone.toFloat() / progressTotal).coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(ink)
                    )
                }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(28.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(greetingIcon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                            Text(
                                text = greeting.uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (chip != null) {
                            Row(
                                Modifier.clip(CircleShape)
                                    .background(ink.copy(alpha = 0.12f))
                                    .then(if (chip.action != null) Modifier.clickable { chip.action.invoke() } else Modifier)
                                    .height(28.dp)
                                    .padding(start = 8.dp, end = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(chip.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(chip.text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                            }
                        }
                    }
                    Row(
                        Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val cookieSize = if (number.length >= 5 && cookieIcon == null && !loading) 104.dp else 92.dp
                        Box(Modifier.size(cookieSize), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.matchParentSize()
                                    .graphicsLayer { rotationZ = press.turn }
                                    .clip(MaterialShapes.Cookie9Sided.toShape())
                                    .background(ink)
                            )
                            if (cookieIcon != null) {
                                Icon(cookieIcon, contentDescription = null, tint = container, modifier = Modifier.size(44.dp))
                            } else {
                                val text = if (loading) "…" else number
                                val top = when { loading -> 40.sp; text.length <= 1 -> 46.sp; text.length == 2 -> 40.sp; else -> 34.sp }
                                // Fits inside the cookie's round middle, however many digits.
                                androidx.compose.foundation.text.BasicText(
                                    text = text,
                                    modifier = Modifier.width(cookieSize * 0.62f),
                                    color = { container },
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontWeight = FontWeight.Bold, // the app's normal font, plain bold
                                        letterSpacing = (-0.5).sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    ),
                                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = top),
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = big,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = GoogleSansWideFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                ),
                            )
                            if (sentence.isNotEmpty()) HeroSentence(sentence, name, ink)
                        }
                    }
                }
            }
        }
    }
}

/** The card's sentence: numbers in bold, and a group's name under a wavy line. */
@Composable
private fun HeroSentence(text: String, name: String?, ink: Color) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val start = if (name.isNullOrEmpty()) -1 else text.indexOf(name)
    val annotated: AnnotatedString =
        remember(text) {
            buildAnnotatedString {
                var i = 0
                while (i < text.length) {
                    if (text[i].isDigit()) {
                        var j = i
                        while (j < text.length && (text[j].isDigit() || text[j] == ',' || text[j] == '.')) j++
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(text.substring(i, j)) }
                        i = j
                    } else {
                        append(text[i])
                        i++
                    }
                }
            }
        }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = ink.copy(alpha = 0.8f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier =
            Modifier.padding(top = 3.dp).drawBehind {
                val l = layout ?: return@drawBehind
                if (start < 0) return@drawBehind
                val end = (start + name!!.length).coerceAtMost(l.layoutInput.text.length)
                if (end <= start) return@drawBehind
                // Under each line of the name, a wavy line.
                for (line in l.getLineForOffset(start)..l.getLineForOffset(end - 1)) {
                    val from = if (line == l.getLineForOffset(start)) l.getHorizontalPosition(start, true) else l.getLineLeft(line)
                    val to = if (line == l.getLineForOffset(end - 1)) l.getHorizontalPosition(end, true) else l.getLineRight(line)
                    val y = l.getLineBottom(line) + 1.dp.toPx()
                    val half = 3.dp.toPx()
                    val amp = 1.6.dp.toPx()
                    val path = Path().apply {
                        moveTo(from, y)
                        var x = from
                        var up = true
                        while (x < to) {
                            quadraticTo(x + half / 2, y + if (up) -amp * 2 else amp * 2, x + half, y)
                            x += half
                            up = !up
                        }
                    }
                    drawPath(path, ink.copy(alpha = 0.7f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                }
            },
    )
}

/**
 * The card's background: three faint Material shapes (a clover top right, a pill bottom left and a
 * rounded square between) turning slowly at their own speeds. Each shape is drawn once and only its
 * layer turns, so the animation costs the main thread nothing (rebuilding paths on every frame
 * kept sheets from opening smoothly).
 */
@Composable
private fun FaintCookies(modifier: Modifier = Modifier) {
    val clover = MaterialShapes.Clover4Leaf.toPath()
    val pill = MaterialShapes.Pill.toPath()
    val square = MaterialShapes.Square.toPath()
    val transition = rememberInfiniteTransition(label = "hero background")
    val cloverTurn = transition.animateFloat(0f, 360f, infiniteRepeatable(tween(36_000, easing = LinearEasing)), label = "clover")
    val pillTurn = transition.animateFloat(360f, 0f, infiniteRepeatable(tween(28_000, easing = LinearEasing)), label = "pill")
    val squareTurn = transition.animateFloat(0f, 360f, infiniteRepeatable(tween(44_000, easing = LinearEasing)), label = "square")
    val dark = com.ay.briefyou.infrastructure.preference.LocalDarkTheme.current.isDarkTheme()
    val light = Color.White.copy(alpha = if (dark) 0.11f else 0.34f) // lighter than the card, so it needs more on a light card
    val shade = Color.Black.copy(alpha = if (dark) 0.12f else 0.08f)
    Box(modifier) {
        TurningShape(clover, 0.5f, 0.92f, 0.19f, cloverTurn, light)
        TurningShape(pill, 0.34f, 0.22f, 1f, pillTurn, shade)
        TurningShape(square, 0.19f, 0.65f, 0.97f, squareTurn, light)
    }
}

/** [base] at [widthFraction] of the card's width, centred on ([cx], [cy]) (fractions of the card), turning about that centre. */
@Composable
private fun TurningShape(base: Path, widthFraction: Float, cx: Float, cy: Float, turn: State<Float>, color: Color) {
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(cx, cy)
                rotationZ = turn.value
            }
            .drawWithCache {
                val side = size.width * widthFraction
                val path =
                    Path().apply {
                        addPath(base)
                        transform(Matrix().apply { scale(side, side) })
                        val c = getBounds().center
                        translate(Offset(cx * size.width - c.x, cy * size.height - c.y))
                    }
                onDrawBehind { drawPath(path, color) }
            }
    )
}

private class HeroChip(val text: String, val icon: ImageVector, val action: (() -> Unit)?)
