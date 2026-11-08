@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import me.ash.reader.R
import me.ash.reader.brief.ui.GoogleSansWideFontFamily
import me.ash.reader.brief.ui.LocalBackStack
import me.ash.reader.brief.ui.OffscreenPicture
import me.ash.reader.brief.ui.sharePicture
import me.ash.reader.ui.page.nav3.key.Route

/** How many days into a new month last month's recap is shown at the top (and on home). */
private const val RECAP_DAYS = 7

/** How many past months Reading stats lists (about what it keeps: 400 days). */
private const val PAST_MONTHS = 13

/** A month of reading, for the monthly recap. */
data class MonthRecap(
    val month: YearMonth,
    val articles: Int,
    val minutes: Long,
    /** Articles more (or fewer) than the month before; null when there's nothing to compare. */
    val vsLast: Int?,
    /** Articles opened on each day of the month (index 0 is the 1st). */
    val perDay: List<Int>,
    /** Minutes read on each day of the month. */
    val minutesPerDay: List<Long>,
    val busiest: LocalDate?,
    val longestStreak: Int,
    val triaged: Int,
    val inboxZero: Int,
    /** The feed opened most that month (counted per day since the recap came in). */
    val topFeed: Pair<String, Int>?,
    /** Articles the filters hid (counted since the recap came in); 0 hides the line. */
    val filtered: Int,
    /** When the stats started partway through this month: the day they started. */
    val since: LocalDate?,
) {
    /** Whether [day] (0 is the 1st) came before the stats started, so there's nothing to show. */
    fun untracked(day: Int): Boolean = since != null && day < since.dayOfMonth - 1
}

/** [month]'s recap, or null when nothing was read that month. */
fun monthRecap(data: ReadingStatsData, month: YearMonth): MonthRecap? {
    val days = (1..month.lengthOfMonth()).map { data.days[month.atDay(it).toString()] ?: StatsDay() }
    val articles = days.sumOf { it.articles }
    if (articles == 0) return null
    val started =
        data.since.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    val since = started?.takeIf { YearMonth.from(it) == month && it.dayOfMonth > 1 }
    // Comparing makes sense only between two whole months of stats.
    val lastArticles =
        (1..month.minusMonths(1).lengthOfMonth()).sumOf {
            data.days[month.minusMonths(1).atDay(it).toString()]?.articles ?: 0
        }
    val lastWhole = started == null || !started.isAfter(month.minusMonths(1).atDay(1))
    var longest = 0
    var run = 0
    days.forEach {
        run = if (it.articles > 0) run + 1 else 0
        longest = maxOf(longest, run)
    }
    val busiestIndex = days.indices.maxByOrNull { days[it].articles }
    val feeds = HashMap<String, Int>()
    days.forEach { day -> day.feeds.forEach { (name, n) -> feeds[name] = (feeds[name] ?: 0) + n } }
    return MonthRecap(
        month = month,
        articles = articles,
        minutes = days.sumOf { it.seconds } / 60,
        vsLast = if (since == null && lastWhole && lastArticles > 0) articles - lastArticles else null,
        perDay = days.map { it.articles },
        minutesPerDay = days.map { it.seconds / 60 },
        busiest = busiestIndex?.let { month.atDay(it + 1) },
        longestStreak = longest,
        triaged = days.sumOf { it.triaged },
        inboxZero = days.sumOf { it.inboxZero },
        topFeed = feeds.maxByOrNull { it.value }?.toPair(),
        filtered = days.sumOf { it.filtered },
        since = since,
    )
}

/** Last month's recap during the first week of a month, unless it was hidden or nothing was read. */
fun currentRecap(data: ReadingStatsData, hidden: String, today: LocalDate = LocalDate.now()): MonthRecap? {
    if (today.dayOfMonth > RECAP_DAYS) return null
    val month = YearMonth.from(today).minusMonths(1)
    if (hidden == month.toString()) return null
    return monthRecap(data, month)
}

/** The months before this one that have a recap, newest first. */
fun pastRecaps(data: ReadingStatsData, today: LocalDate = LocalDate.now()): List<MonthRecap> =
    (1..PAST_MONTHS).mapNotNull { monthRecap(data, YearMonth.from(today).minusMonths(it.toLong())) }

private fun monthName(month: YearMonth, style: TextStyle = TextStyle.FULL_STANDALONE) =
    month.month.getDisplayName(style, Locale.getDefault())

@Composable
private fun readingTime(minutes: Long): String =
    if (minutes >= 60) stringResource(R.string.recap_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
    else stringResource(R.string.recap_minutes, minutes.toInt())

@Composable
private fun vsText(recap: MonthRecap): String? {
    val diff = recap.vsLast ?: return null
    val last = monthName(recap.month.minusMonths(1), TextStyle.SHORT)
    return if (diff == 0) stringResource(R.string.stats_same_as_last_month, last)
    else stringResource(R.string.stats_vs_last_month, if (diff > 0) "+$diff" else "−${-diff}", last)
}

/** A day's square or bar: brighter the more you read that day ([most] is the month's busiest). */
private fun dayColor(n: Int, most: Int, accent: Color, empty: Color, untracked: Boolean = false): Color =
    when {
        // Before the stats started: barely there, so it doesn't read as a day without reading.
        untracked -> empty.copy(alpha = empty.alpha * 0.3f)
        n == 0 -> empty
        else -> accent.copy(alpha = 0.35f + 0.65f * n / most.coerceAtLeast(1))
    }

/**
 * The monthly recap, on the theme's primary container (light text on it, like the week card).
 * Closed: the month's articles in big wide type (and the change from the month before), a slim
 * row of daily bars and four highlight tiles. Tap it and it opens in place: each day's bar slides
 * to its spot in the calendar (Monday first) and becomes its square, and the tiles give way to
 * every highlight; tap a day for its numbers, tap the card again to fold it back. ✕ hides it
 * ([onHide]); Share makes a picture of the open card.
 */
@Composable
fun RecapCard(
    recap: MonthRecap,
    modifier: Modifier = Modifier,
    onHide: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable(recap.month.toString()) { mutableStateOf(false) }
    var picked by rememberSaveable(recap.month.toString()) { mutableStateOf(-1) }
    var capturing by remember { mutableStateOf(false) }
    val t by
        animateFloatAsState(
            if (open) 1f else 0f,
            animationSpec = tween(650, easing = FastOutSlowInEasing),
            label = "recap open",
        )
    Surface(
        onClick = {
            open = !open
            if (!open) picked = -1
        },
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier,
    ) {
        Box {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 4.dp)) { RecapHeader(recap, scheme.onPrimaryContainer) }
                    if (onHide != null) {
                        RoundIcon(Icons.Rounded.Close, stringResource(R.string.recap_hide), onHide)
                        Spacer(Modifier.width(6.dp))
                    }
                    RoundIcon(Icons.Rounded.Share, stringResource(R.string.recap_share)) { capturing = true }
                }
                Column(Modifier.padding(end = 8.dp)) {
                    Spacer(Modifier.height(12.dp))
                    CalendarMorph(
                        recap = recap,
                        t = t,
                        picked = picked,
                        accent = scheme.primary,
                        empty = scheme.onPrimaryContainer.copy(alpha = 0.13f),
                        outline = scheme.onPrimaryContainer,
                        onPick = if (open) ({ day: Int -> picked = if (picked == day) -1 else day }) else null,
                    )
                    // The tapped day's numbers, sliding in under the calendar.
                    Box(Modifier.animateContentSize()) {
                        if (open && picked >= 0) DayDetail(recap, picked)
                    }
                    Spacer(Modifier.height(12.dp))
                    // Tiles while closed, every highlight while open: each tile slides and stretches
                    // into its row, together with the calendar.
                    HighlightMorph(recap, t, scheme.onPrimaryContainer)
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier =
                            Modifier.align(Alignment.CenterHorizontally)
                                .padding(top = 2.dp)
                                .size(20.dp)
                                .graphicsLayer { rotationZ = 180f * t; alpha = 0.7f },
                    )
                }
            }
            OffscreenPicture(
                capturing = capturing,
                onPicture = { picture ->
                    capturing = false
                    if (picture != null) sharePicture(context, picture, "month-recap")
                },
            ) {
                RecapPicture(recap, open)
            }
        }
    }
}

/** A small round button on the card (✕ and Share). */
@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = LocalContentColor.current.copy(alpha = 0.12f),
        contentColor = LocalContentColor.current,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
        }
    }
}

/** The month's name, "since" when the stats started partway through, and the big number. */
@Composable
private fun RecapHeader(recap: MonthRecap, tint: Color) {
    Text(
        text = stringResource(R.string.recap_title, monthName(recap.month)).uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
        fontWeight = FontWeight.Bold,
        color = tint,
    )
    recap.since?.let {
        Text(
            text = stringResource(R.string.recap_since, it.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))),
            style = MaterialTheme.typography.labelMedium,
            color = tint.copy(alpha = 0.8f),
        )
    }
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = recap.articles.toString(),
            style =
                MaterialTheme.typography.displayMedium.copy(
                    fontFamily = GoogleSansWideFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 44.sp,
                    lineHeight = 44.sp,
                ),
            color = tint,
        )
        Text(
            text = stringResource(R.string.recap_articles),
            style = MaterialTheme.typography.titleSmall,
            color = tint.copy(alpha = 0.85f),
            modifier = Modifier.padding(start = 6.dp, bottom = 5.dp),
        )
        vsText(recap)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                modifier =
                    Modifier.padding(start = 8.dp, bottom = 6.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** "Sun 8 · 22 articles · 6 min" for the tapped day. */
@Composable
private fun DayDetail(recap: MonthRecap, day: Int) {
    val date = recap.month.atDay(day + 1)
    Text(
        text =
            pluralStringResource(
                R.plurals.recap_day_detail,
                recap.perDay[day],
                date.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())),
                recap.perDay[day],
                readingTime(recap.minutesPerDay[day]),
            ),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 10.dp, start = 2.dp),
    )
}

/** Bar height while closed, and the gap between calendar squares while open. */
private val BarsHeight = 40.dp
private val SquareGap = 5.dp

/**
 * The month's days, drawn between two layouts as [t] goes from 0 to 1: a row of thin bars (one per
 * day, as tall as its reading) and the calendar (Monday first, across the card). Each day moves, grows and
 * rounds from its bar into its square. While open ([onPick]), tapping a square picks that day.
 */
@Composable
private fun CalendarMorph(
    recap: MonthRecap,
    t: Float,
    picked: Int,
    accent: Color,
    empty: Color,
    outline: Color,
    onPick: ((Int) -> Unit)?,
) {
    val most = (recap.perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val offset = recap.month.atDay(1).dayOfWeek.value - 1
    val weeks = (offset + recap.perDay.size + 6) / 7
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val gap = with(density) { SquareGap.toPx() }
        val square = (width - gap * 6) / 7 // the calendar spans the card
        val calWidth = square * 7 + gap * 6
        val calHeight = square * weeks + gap * (weeks - 1)
        val barsHeight = with(density) { BarsHeight.toPx() }
        val n = recap.perDay.size
        val barGap = with(density) { 2.dp.toPx() }
        val barWidth = (width - barGap * (n - 1)) / n
        val minBar = with(density) { 3.dp.toPx() }
        val height = barsHeight + (calHeight - barsHeight) * t
        fun squareAt(day: Int): androidx.compose.ui.geometry.Rect {
            val k = day + offset
            val left = (width - calWidth) / 2 + (k % 7) * (square + gap)
            val top = (k / 7) * (square + gap)
            return androidx.compose.ui.geometry.Rect(left, top, left + square, top + square)
        }
        val pick =
            if (onPick != null)
                Modifier.pointerInput(recap.month, width) {
                    detectTapGestures { at ->
                        recap.perDay.indices.firstOrNull { !recap.untracked(it) && squareAt(it).contains(at) }?.let(onPick)
                    }
                }
            else Modifier
        Canvas(Modifier.fillMaxWidth().height(with(density) { height.toDp() }).then(pick)) {
            recap.perDay.forEachIndexed { day, count ->
                val barH = maxOf(minBar, count.toFloat() / most * barsHeight)
                val bar =
                    androidx.compose.ui.geometry.Rect(
                        day * (barWidth + barGap), barsHeight - barH, day * (barWidth + barGap) + barWidth, barsHeight
                    )
                val sq = squareAt(day)
                val rect = androidx.compose.ui.geometry.lerp(bar, sq, t)
                val barRadius = minOf(barWidth / 2, minBar)
                val radius = barRadius + (square * 0.22f - barRadius) * t
                drawRoundRect(
                    color = dayColor(count, most, accent, empty, recap.untracked(day)),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                if (day == picked && t > 0.99f) {
                    drawRoundRect(
                        color = outline,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

/** One highlight: its row (label, value) and, for the four shown closed, its tile. */
private class Highlight(
    val label: String,
    val value: String,
    val tileValue: String? = null,
    val tileLabel: String? = null,
)

/** Every highlight in list order; the busiest day, streak, time and Triage (or top feed) are tiles. */
@Composable
private fun highlights(recap: MonthRecap): List<Highlight> {
    val busiest =
        recap.busiest?.let { day ->
            stringResource(
                R.string.recap_busiest_value,
                day.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())),
                recap.perDay[day.dayOfMonth - 1],
            )
        }
    val streak = pluralStringResource(R.plurals.recap_days, recap.longestStreak, recap.longestStreak)
    val time = readingTime(recap.minutes)
    val triageTile = recap.triaged > 0
    return buildList {
        busiest?.let { add(Highlight(stringResource(R.string.recap_busiest), it, it, stringResource(R.string.recap_tile_busiest))) }
        recap.topFeed?.let { (name, n) ->
            add(
                Highlight(
                    stringResource(R.string.recap_top_feed),
                    stringResource(R.string.recap_top_feed_value, name, n),
                    if (triageTile) null else name,
                    if (triageTile) null else stringResource(R.string.recap_tile_top_feed),
                )
            )
        }
        add(Highlight(stringResource(R.string.recap_streak), streak, streak, stringResource(R.string.recap_tile_streak)))
        add(Highlight(stringResource(R.string.recap_time), time, time, stringResource(R.string.recap_tile_time)))
        if (recap.triaged > 0 || recap.inboxZero > 0) {
            add(
                Highlight(
                    stringResource(R.string.recap_triage),
                    pluralStringResource(R.plurals.recap_triage_value, recap.inboxZero, recap.triaged, recap.inboxZero),
                    if (triageTile) recap.triaged.toString() else null,
                    if (triageTile) pluralStringResource(R.plurals.recap_tile_triage, recap.inboxZero, recap.inboxZero) else null,
                )
            )
        }
        if (recap.filtered > 0) add(Highlight(stringResource(R.string.recap_filtered), recap.filtered.toString()))
    }
}

private val TileHeight = 58.dp
private val RowHeight = 42.dp
private val BoxGap = 3.dp

private fun ease(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

/**
 * The highlights between their two layouts as [t] goes from 0 (closed) to 1 (open): the four
 * tiles in a 2×2 grid, and every highlight as one connected list. The tiles go one after another
 * (busiest first, so they don't pile onto each other), each sliding to its row and stretching to
 * full width while its corners reshape and its text swaps from "value over label" to "label …
 * value". Rows without a tile (top feed, filters) grow open in their gaps near the end.
 */
@Composable
private fun HighlightMorph(recap: MonthRecap, t: Float, tint: Color) {
    val items = highlights(recap)
    val tiles = items.withIndex().filter { it.value.tileValue != null }.map { it.index }
    // How far along each box is: tiles staggered, the other rows at the end.
    val progress =
        items.indices.map { i ->
            val k = tiles.indexOf(i)
            if (k >= 0) ease((t - k * 0.12f) / 0.64f) else ease((t - 0.55f) / 0.45f)
        }
    val big = 16.dp
    val small = 4.dp
    fun lerpDp(a: androidx.compose.ui.unit.Dp, b: androidx.compose.ui.unit.Dp, f: Float) = a + (b - a) * f
    androidx.compose.ui.layout.Layout(
        content = {
            items.forEachIndexed { i, item ->
                val p = progress[i]
                val k = tiles.indexOf(i)
                // Tile corners: big at the grid's outer corners (a lone last tile spans the row).
                val col = if (k >= 0) k % 2 else 0
                val row = if (k >= 0) k / 2 else 0
                val lastRow = (tiles.size - 1) / 2
                val spans = k == tiles.size - 1 && tiles.size % 2 == 1
                val tTopStart = if (row == 0 && col == 0) big else small
                val tTopEnd = if (row == 0 && (col == 1 || spans)) big else small
                val tBottomStart = if (row == lastRow && col == 0) big else small
                val tBottomEnd = if (row == lastRow && (col == 1 || spans)) big else small
                val rTop = if (i == 0) big else small
                val rBottom = if (i == items.lastIndex) big else small
                val shape =
                    if (k >= 0)
                        RoundedCornerShape(
                            topStart = lerpDp(tTopStart, rTop, p),
                            topEnd = lerpDp(tTopEnd, rTop, p),
                            bottomStart = lerpDp(tBottomStart, rBottom, p),
                            bottomEnd = lerpDp(tBottomEnd, rBottom, p),
                        )
                    else RoundedCornerShape(rTop, rTop, rBottom, rBottom)
                Box(
                    Modifier.clip(shape)
                        .background(tint.copy(alpha = 0.1f))
                        .graphicsLayer { alpha = if (k >= 0) 1f else p }
                ) {
                    if (k >= 0) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                .graphicsLayer { alpha = (1f - 2.5f * p).coerceIn(0f, 1f) }
                        ) {
                            Text(
                                text = item.tileValue.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tint,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.tileLabel.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = tint.copy(alpha = 0.8f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(RowHeight)
                                .align(Alignment.Center)
                                .padding(horizontal = 14.dp)
                                .graphicsLayer {
                                    alpha = if (k >= 0) (2.5f * p - 1.5f).coerceIn(0f, 1f) else p
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tint.copy(alpha = 0.8f),
                            maxLines = 1,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth.toFloat()
        val gap = BoxGap.toPx()
        val tileW = (width - gap) / 2
        val tileH = TileHeight.toPx()
        val rowH = RowHeight.toPx()
        val rects =
            items.indices.map { i ->
                val p = progress[i]
                val rowY = i * (rowH + gap)
                val k = tiles.indexOf(i)
                if (k >= 0) {
                    val spans = k == tiles.size - 1 && tiles.size % 2 == 1
                    val tx = if (spans) 0f else (k % 2) * (tileW + gap)
                    val ty = (k / 2) * (tileH + gap)
                    val tw = if (spans) width else tileW
                    androidx.compose.ui.geometry.Rect(
                        tx + (0f - tx) * p,
                        ty + (rowY - ty) * p,
                        tx + (0f - tx) * p + tw + (width - tw) * p,
                        ty + (rowY - ty) * p + tileH + (rowH - tileH) * p,
                    )
                } else {
                    // Grows open from the middle of its slot.
                    val h = rowH * p
                    androidx.compose.ui.geometry.Rect(0f, rowY + (rowH - h) / 2, width, rowY + (rowH + h) / 2)
                }
            }
        val placeables =
            measurables.mapIndexed { i, m ->
                val r = rects[i]
                m.measure(
                    androidx.compose.ui.unit.Constraints.fixed(
                        r.width.roundToInt().coerceAtLeast(0),
                        r.height.roundToInt().coerceAtLeast(0),
                    )
                )
            }
        val height = rects.maxOfOrNull { it.bottom }?.roundToInt() ?: 0
        layout(constraints.maxWidth, height) {
            // Earlier tiles on top while they cross the later ones.
            placeables.forEachIndexed { i, pl ->
                val k = tiles.indexOf(i)
                pl.place(rects[i].left.roundToInt(), rects[i].top.roundToInt(), zIndex = if (k >= 0) (tiles.size - k).toFloat() else 0f)
            }
        }
    }
}

/**
 * The shared picture: the card as it is on screen ([open]: calendar and every highlight; closed:
 * bars and the four tiles) on a soft backdrop, with a small Brief You mark, like the Triage card's
 * picture. Nothing to tap, no buttons.
 */
@Composable
private fun RecapPicture(recap: MonthRecap, open: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(scheme.primaryContainer.copy(alpha = 0.75f), scheme.surface, scheme.surface)))
                .padding(22.dp)
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(20.dp)
        ) {
            RecapHeader(recap, scheme.onSurface)
            Spacer(Modifier.height(14.dp))
            CalendarMorph(
                recap = recap,
                t = if (open) 1f else 0f,
                picked = -1,
                accent = scheme.primary,
                empty = scheme.onSurface.copy(alpha = 0.1f),
                outline = scheme.onSurface,
                onPick = null,
            )
            Spacer(Modifier.height(14.dp))
            HighlightMorph(recap, if (open) 1f else 0f, scheme.onSurface)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                me.ash.reader.brief.ui.BriefYouMark(scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Last month's recap at the top of Reading stats, for the first week of a month. Hide hides it
 * here and on home (it stays under Past months).
 */
@Composable
fun RecapAtTop(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ReadingStats.loadRecap(context) }
    val data = ReadingStats.data
    val hidden = ReadingStats.recapHidden
    val recap = remember(data, hidden) { currentRecap(data, hidden) }
    // Kept while it animates away after Hide.
    var shown by remember { mutableStateOf(recap) }
    if (recap != null) shown = recap
    AnimatedVisibility(
        visible = recap != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        shown?.let { r ->
            RecapCard(
                recap = r,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                onHide = { ReadingStats.hideRecap(context, r.month.toString()) },
            )
        }
    }
}

/**
 * Past months on Reading stats: one row per month with its articles; tapping one opens its recap
 * in a sheet, with Share.
 */
@Composable
fun PastMonths(data: ReadingStatsData, modifier: Modifier = Modifier) {
    val past = remember(data) { pastRecaps(data) }
    if (past.isEmpty()) return
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        me.ash.reader.ui.component.base.Subtitle(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(R.string.recap_past),
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            past.forEachIndexed { i, recap ->
                val top = if (i == 0) 24.dp else 6.dp
                val bottom = if (i == past.lastIndex) 24.dp else 6.dp
                Surface(
                    onClick = { open = recap.month.toString() },
                    color = scheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(top, top, bottom, bottom),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${monthName(recap.month)} ${recap.month.year}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.recap_teaser, recap.articles),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                        )
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
    val chosen = past.firstOrNull { it.month.toString() == open }
    if (chosen != null) {
        ModalBottomSheet(
            onDismissRequest = { open = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            RecapCard(
                recap = chosen,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            )
        }
    }
}

/**
 * Last month's recap on home, above the feeds, for the first week of a month: a slim row with a
 * tiny copy of the calendar and the month's articles. Tap opens Reading stats (the full card is
 * at the top); × hides it everywhere. Off with "Monthly recap on home" on Reading stats.
 */
@Composable
fun HomeRecapTeaser(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        ReadingStats.load(context)
        ReadingStats.loadRecap(context)
    }
    val data = ReadingStats.data
    val hidden = ReadingStats.recapHidden
    val recap = remember(data, hidden) { currentRecap(data, hidden) }
    var shown by remember { mutableStateOf(recap) }
    if (recap != null) shown = recap
    val backStack = LocalBackStack.current
    AnimatedVisibility(
        visible = recap != null && ReadingStats.recapOnHome,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val r = shown ?: return@AnimatedVisibility
        val scheme = MaterialTheme.colorScheme
        Surface(
            onClick = {
                @Suppress("UNCHECKED_CAST")
                (backStack as? MutableList<Any>)?.add(Route.ReadingStats)
            },
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniCalendar(r)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = stringResource(R.string.recap_title, monthName(r.month)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = listOfNotNull(stringResource(R.string.recap_teaser, r.articles), vsText(r)).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // ✕ in a small circle, like the count badge on each feed (a full-size touch area).
                IconButton(onClick = { ReadingStats.hideRecap(context, r.month.toString()) }) {
                    Box(
                        modifier =
                            Modifier.size(22.dp)
                                .clip(CircleShape)
                                .background(scheme.onPrimaryContainer.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.recap_hide),
                            tint = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A tiny copy of the recap's calendar, for the home row. */
@Composable
private fun MiniCalendar(recap: MonthRecap) {
    val scheme = MaterialTheme.colorScheme
    val most = (recap.perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val offset = recap.month.atDay(1).dayOfWeek.value - 1
    val weeks = (offset + recap.perDay.size + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(weeks) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { col ->
                    val day = week * 7 + col - offset
                    val n = recap.perDay.getOrNull(day)
                    Box(
                        Modifier.size(5.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                if (n == null) Color.Transparent
                                else dayColor(n, most, scheme.primary, scheme.onPrimaryContainer.copy(alpha = 0.13f), recap.untracked(day))
                            )
                    )
                }
            }
        }
    }
}
