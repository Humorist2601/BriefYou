@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
            animationSpec = tween(550, easing = FastOutSlowInEasing),
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
                    // Tiles while closed, every highlight while open: one fades into the other as
                    // the card grows or shrinks.
                    Box(Modifier.animateContentSize(tween(550, easing = FastOutSlowInEasing))) {
                        if (open) {
                            HighlightRows(recap, scheme.onPrimaryContainer, Modifier.graphicsLayer { alpha = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f) })
                        } else {
                            HighlightTiles(recap, scheme.onPrimaryContainer, Modifier.graphicsLayer { alpha = (1f - t / 0.6f).coerceIn(0f, 1f) })
                        }
                    }
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

/** The closed card's highlights: two by two, rounded at the group's outer corners. */
@Composable
private fun HighlightTiles(recap: MonthRecap, tint: Color, modifier: Modifier = Modifier) {
    val tiles = buildList {
        recap.busiest?.let { day ->
            add(
                stringResource(
                    R.string.recap_busiest_value,
                    day.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())),
                    recap.perDay[day.dayOfMonth - 1],
                ) to stringResource(R.string.recap_tile_busiest)
            )
        }
        add(pluralStringResource(R.plurals.recap_days, recap.longestStreak, recap.longestStreak) to stringResource(R.string.recap_tile_streak))
        add(readingTime(recap.minutes) to stringResource(R.string.recap_tile_time))
        if (recap.triaged > 0) {
            add(recap.triaged.toString() to pluralStringResource(R.plurals.recap_tile_triage, recap.inboxZero, recap.inboxZero))
        } else {
            recap.topFeed?.let { (name, _) -> add(name to stringResource(R.string.recap_tile_top_feed)) }
        }
    }
    val rows = tiles.chunked(2)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                row.forEachIndexed { c, (value, label) ->
                    val big = 16.dp
                    val small = 4.dp
                    val firstRow = r == 0
                    val lastRow = r == rows.lastIndex
                    val left = c == 0
                    val right = c == row.lastIndex
                    Column(
                        modifier =
                            Modifier.weight(1f)
                                .fillMaxHeight()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (firstRow && left) big else small,
                                        topEnd = if (firstRow && right) big else small,
                                        bottomStart = if (lastRow && left) big else small,
                                        bottomEnd = if (lastRow && right) big else small,
                                    )
                                )
                                .background(tint.copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = tint.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Every highlight as one connected list (the open card and the shared picture). */
@Composable
private fun HighlightRows(recap: MonthRecap, tint: Color, modifier: Modifier = Modifier) {
    val rows = buildList {
        recap.busiest?.let { day ->
            add(
                stringResource(R.string.recap_busiest) to
                    stringResource(
                        R.string.recap_busiest_value,
                        day.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())),
                        recap.perDay[day.dayOfMonth - 1],
                    )
            )
        }
        recap.topFeed?.let { (name, n) ->
            add(stringResource(R.string.recap_top_feed) to stringResource(R.string.recap_top_feed_value, name, n))
        }
        add(stringResource(R.string.recap_streak) to pluralStringResource(R.plurals.recap_days, recap.longestStreak, recap.longestStreak))
        add(stringResource(R.string.recap_time) to readingTime(recap.minutes))
        if (recap.triaged > 0 || recap.inboxZero > 0) {
            add(
                stringResource(R.string.recap_triage) to
                    pluralStringResource(R.plurals.recap_triage_value, recap.inboxZero, recap.triaged, recap.inboxZero)
            )
        }
        if (recap.filtered > 0) add(stringResource(R.string.recap_filtered) to recap.filtered.toString())
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, (label, value) ->
            val top = if (i == 0) 16.dp else 4.dp
            val bottom = if (i == rows.lastIndex) 16.dp else 4.dp
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(top, top, bottom, bottom))
                        .background(tint.copy(alpha = 0.1f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tint.copy(alpha = 0.8f),
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = value,
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
            if (open) HighlightRows(recap, scheme.onSurface) else HighlightTiles(recap, scheme.onSurface)
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
