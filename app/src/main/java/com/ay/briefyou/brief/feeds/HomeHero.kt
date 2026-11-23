@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.ay.briefyou.brief.feeds

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.view.SoundEffectConstants
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.toPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * move (the same Gem and Sunny turning as Trainable's membership card); the main cookie keeps still.
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
    val name: String?
    val sentence: String
    val big: String
    val cookieIcon: ImageVector?
    when {
        noFeeds -> {
            big = stringResource(R.string.hero_add_feed)
            sentence = stringResource(R.string.hero_add_feed_desc)
            cookieIcon = Icons.Outlined.Add
            name = null
        }
        loading -> {
            big = stringResource(R.string.hero_counting)
            sentence = ""
            cookieIcon = null
            name = null
        }
        filter == Filter.All -> {
            big = stringResource(R.string.hero_articles)
            sentence = stringResource(R.string.hero_from, feedsText)
            cookieIcon = null
            name = null
        }
        count == 0 && filter.isStarred() -> {
            big = stringResource(R.string.hero_no_stars)
            sentence = stringResource(R.string.hero_no_stars_desc)
            cookieIcon = Icons.Outlined.Star
            name = null
        }
        count == 0 -> {
            big = stringResource(R.string.hero_caught_up)
            sentence = stringResource(R.string.hero_caught_up_desc)
            cookieIcon = Icons.Outlined.DoneAll
            name = null
        }
        filter.isStarred() -> {
            big = stringResource(R.string.hero_starred)
            sentence = stringResource(R.string.hero_in, groups)
            cookieIcon = null
            name = null
        }
        groupCount >= 2 && topGroup != null -> {
            big = stringResource(R.string.hero_unread)
            sentence = stringResource(R.string.hero_across, groups, topGroup)
            cookieIcon = null
            name = topGroup
        }
        else -> {
            big = stringResource(R.string.hero_unread)
            sentence = stringResource(R.string.hero_in, topGroup ?: groups)
            cookieIcon = null
            name = topGroup
        }
    }
    val number = remember(count) { NumberFormat.getIntegerInstance().format(count) }

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
    val chipText = if (agoText == null || noFeeds || loading) null else if (stale) stringResource(R.string.hero_synced_ago, agoText) else agoText

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
                        if (chipText != null) {
                            Row(
                                Modifier.clip(CircleShape).background(ink.copy(alpha = 0.12f)).height(28.dp).padding(start = 8.dp, end = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(if (stale) Icons.Outlined.CloudOff else Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(chipText, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
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
                                val size = when {
                                    loading -> 40.sp
                                    text.length <= 1 -> 46.sp
                                    text.length == 2 -> 40.sp
                                    text.length == 3 -> 30.sp
                                    text.length <= 5 -> 22.sp
                                    else -> 19.sp
                                }
                                Text(
                                    text = text,
                                    color = container,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontFamily = GoogleSansWideFontFamily,
                                        fontWeight = FontWeight.Black,
                                        fontSize = size,
                                        letterSpacing = (-1).sp,
                                    ),
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
 * The card's background, as on Trainable's gym membership card (GPL-3.0, https://github.com/Emanuel5014/Trainable):
 * a big Gem turning once every 15 s with its centre on the top right corner, and a Sunny turning once
 * every 20 s near the bottom left, both very faint.
 */
@Composable
private fun FaintCookies(modifier: Modifier = Modifier) {
    val gem = remember { MaterialShapes.Gem.toPath() }
    val sunny = remember { MaterialShapes.Sunny.toPath() }
    val transition = rememberInfiniteTransition(label = "hero background")
    val gemTurn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(15_000, easing = LinearEasing)), label = "gem")
    val sunnyTurn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "sunny")
    val dark = com.ay.briefyou.infrastructure.preference.LocalDarkTheme.current.isDarkTheme()
    val gemColor = Color.White.copy(alpha = if (dark) 0.1f else 0.3f) // lighter than the card, so it needs more on a light card
    val sunnyColor = Color.Black.copy(alpha = 0.05f)
    Canvas(modifier) {
        fun place(base: Path, scale: Float, degrees: Float, x: Float, y: Float): Path =
            Path().apply {
                addPath(base)
                transform(Matrix().apply { scale(scale, scale) })
                val c = getBounds().center
                transform(Matrix().apply {
                    translate(c.x, c.y)
                    rotateZ(degrees)
                    translate(-c.x, -c.y)
                })
                translate(Offset(x - c.x, y - c.y))
            }
        drawPath(place(gem, size.width * 0.6f, gemTurn, size.width, 0f), gemColor)
        drawPath(place(sunny, size.width * 0.5f, sunnyTurn, size.width * 0.06f, size.height * 0.88f), sunnyColor)
    }
}
