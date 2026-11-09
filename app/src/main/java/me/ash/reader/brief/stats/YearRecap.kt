@file:OptIn(ExperimentalMaterial3Api::class)

package me.ash.reader.brief.stats

import me.ash.reader.brief.ui.rememberPressFeel
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle as UiTextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import me.ash.reader.R
import me.ash.reader.brief.ui.BriefYouMark
import me.ash.reader.brief.ui.OffscreenPicture
import me.ash.reader.brief.ui.sharePicture

/** How many days into January last year's recap is shown (the same week as a month's). */
private const val YEAR_RECAP_DAYS = 7

/** A year of reading, for the year in review. */
data class YearRecap(
    val year: Int,
    val articles: Int,
    val minutes: Long,
    /** Articles opened on each day, month by month (index 0 is January, then the day's index). */
    val perDay: List<List<Int>>,
    /** Minutes read on each day, month by month. */
    val minutesPerDay: List<List<Long>>,
    /** The month with the most articles. */
    val bestMonth: YearMonth?,
    val busiest: LocalDate?,
    val longestStreak: Int,
    val triaged: Int,
    val inboxZero: Int,
    val topFeed: Pair<String, Int>?,
    val filtered: Int,
    /** When the stats started partway through this year: the day they started. */
    val since: LocalDate?,
) {
    val perMonth: List<Int> get() = perDay.map { it.sum() }

    /** Whether [day] of [month] (0 is January, 0 is the 1st) came before the stats started. */
    fun untracked(month: Int, day: Int): Boolean =
        since != null && LocalDate.of(year, month + 1, day + 1).isBefore(since)
}

/** [year]'s recap, or null when nothing was read that year. */
fun yearRecap(data: ReadingStatsData, year: Int): YearRecap? {
    val months = (1..12).map { YearMonth.of(year, it) }
    val days = months.map { m -> (1..m.lengthOfMonth()).map { data.days[m.atDay(it).toString()] ?: StatsDay() } }
    val all = days.flatten()
    val articles = all.sumOf { it.articles }
    if (articles == 0) return null
    val started =
        data.since.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    val since = started?.takeIf { it.year == year && it.dayOfYear > 1 }
    var longest = 0
    var run = 0
    all.forEach {
        run = if (it.articles > 0) run + 1 else 0
        longest = maxOf(longest, run)
    }
    var busiest: LocalDate? = null
    var busiestN = 0
    days.forEachIndexed { m, list ->
        list.forEachIndexed { d, day ->
            if (day.articles > busiestN) {
                busiestN = day.articles
                busiest = LocalDate.of(year, m + 1, d + 1)
            }
        }
    }
    val feeds = HashMap<String, Int>()
    all.forEach { day -> day.feeds.forEach { (name, n) -> feeds[name] = (feeds[name] ?: 0) + n } }
    val perMonth = days.map { list -> list.sumOf { it.articles } }
    return YearRecap(
        year = year,
        articles = articles,
        minutes = all.sumOf { it.seconds } / 60,
        perDay = days.map { list -> list.map { it.articles } },
        minutesPerDay = days.map { list -> list.map { it.seconds / 60 } },
        bestMonth = perMonth.indices.maxByOrNull { perMonth[it] }?.let { months[it] },
        busiest = busiest,
        longestStreak = longest,
        triaged = all.sumOf { it.triaged },
        inboxZero = all.sumOf { it.inboxZero },
        topFeed = feeds.maxByOrNull { it.value }?.toPair(),
        filtered = all.sumOf { it.filtered },
        since = since,
    )
}

/**
 * Last year's recap during the first week of January (in place of December's), unless it was
 * hidden or nothing was read.
 */
fun currentYearRecap(data: ReadingStatsData, hidden: String, today: LocalDate = LocalDate.now()): YearRecap? {
    if (today.month != Month.JANUARY || today.dayOfMonth > YEAR_RECAP_DAYS) return null
    val year = today.year - 1
    if (hidden == year.toString()) return null
    return yearRecap(data, year)
}

/** Whether January's first week shows last year's recap instead of December's (hidden or not). */
fun yearRecapWeek(data: ReadingStatsData, today: LocalDate = LocalDate.now()): Boolean =
    today.month == Month.JANUARY && today.dayOfMonth <= YEAR_RECAP_DAYS && yearRecap(data, today.year - 1) != null

private fun shortMonth(month: Int) = Month.of(month + 1).getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())

/**
 * The year in review, on the primary container like the monthly recap. Closed: the year's
 * articles (no comparison with the year before: stats keep about 400 days), twelve month bars and four tiles. Tap it and it opens in place: each month's bar
 * breaks into its days, which fly to that month's little calendar (three across, Monday first),
 * and the tiles give way to every highlight; tap a day for its numbers. ✕ hides it ([onHide]);
 * Share makes a picture of the card as it is.
 */
@Composable
fun YearRecapCard(
    recap: YearRecap,
    modifier: Modifier = Modifier,
    onHide: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable(recap.year) { mutableStateOf(false) }
    // The tapped day as month * 32 + day, or -1.
    var picked by rememberSaveable(recap.year) { mutableStateOf(-1) }
    var capturing by remember { mutableStateOf(false) }
    val t by
        animateFloatAsState(
            if (open) 1f else 0f,
            animationSpec = tween(650, easing = FastOutSlowInEasing),
            label = "year open",
        )
    // A gentle squeeze and spring when pressed (Triage's press, softer for a big card).
    val press = rememberPressFeel(rest = 28.dp, held = 28.dp, squeezeTo = 0.98f)
    Surface(
        onClick = {
            open = !open
            if (!open) picked = -1
        },
        interactionSource = press.interaction,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = with(press) { modifier.squeeze() },
    ) {
        Box {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 4.dp)) {
                        YearHeader(recap, scheme.onPrimaryContainer)
                    }
                    if (onHide != null) {
                        RoundIcon(Icons.Rounded.Close, stringResource(R.string.recap_hide), onHide)
                        Spacer(Modifier.width(6.dp))
                    }
                    RoundIcon(Icons.Rounded.Share, stringResource(R.string.recap_share)) { capturing = true }
                }
                Column(Modifier.padding(end = 8.dp)) {
                    Spacer(Modifier.height(12.dp))
                    YearMorph(
                        recap = recap,
                        t = t,
                        picked = picked,
                        accent = scheme.primary,
                        empty = scheme.onPrimaryContainer.copy(alpha = 0.13f),
                        outline = scheme.onPrimaryContainer,
                        onPick = if (open) ({ key: Int -> picked = if (picked == key) -1 else key }) else null,
                    )
                    Box(Modifier.animateContentSize()) {
                        if (open && picked >= 0) YearDayDetail(recap, picked / 32, picked % 32)
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.animateContentSize(tween(650, easing = FastOutSlowInEasing))) {
                        if (open) {
                            YearRows(recap, scheme.onPrimaryContainer, Modifier.graphicsLayer { alpha = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f) })
                        } else {
                            YearTiles(recap, scheme.onPrimaryContainer, Modifier.graphicsLayer { alpha = (1f - t / 0.6f).coerceIn(0f, 1f) })
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
                    if (picture != null) sharePicture(context, picture, "year-recap")
                },
            ) {
                YearPicture(recap, open)
            }
        }
    }
}

@Composable
private fun YearHeader(recap: YearRecap, tint: Color) {
    RecapHeading(
        title = stringResource(R.string.recap_title, recap.year.toString()),
        since = recap.since,
        articles = recap.articles,
        // No "vs last year": stats keep about 400 days, so most of the year before is gone by
        // January and the comparison would be wrong.
        vs = null,
        tint = tint,
    )
}

/** "Sun 8 Mar · 22 articles · 6 min" for the tapped day. */
@Composable
private fun YearDayDetail(recap: YearRecap, month: Int, day: Int) {
    val n = recap.perDay[month][day]
    Text(
        text =
            pluralStringResource(
                R.plurals.recap_day_detail,
                n,
                LocalDate.of(recap.year, month + 1, day + 1).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                n,
                readingTime(recap.minutesPerDay[month][day]),
            ),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 10.dp, start = 2.dp),
    )
}

private val YearBarsHeight = 40.dp
private const val YEAR_COLUMNS = 3

/**
 * The year drawn between two layouts as [t] goes from 0 to 1: twelve month bars (as tall as the
 * month's reading) and twelve little calendars, three across. Opening, each bar breaks into its
 * days, which move and grow into their squares while the bar fades; the month names fade in last.
 * While open ([onPick]), tapping a square picks that day (as month * 32 + day).
 */
@Composable
private fun YearMorph(
    recap: YearRecap,
    t: Float,
    picked: Int,
    accent: Color,
    empty: Color,
    outline: Color,
    onPick: ((Int) -> Unit)?,
) {
    val perMonth = recap.perMonth
    val mostMonth = (perMonth.maxOrNull() ?: 0).coerceAtLeast(1)
    val mostDay = (recap.perDay.maxOf { it.maxOrNull() ?: 0 }).coerceAtLeast(1)
    val offsets = (1..12).map { YearMonth.of(recap.year, it).atDay(1).dayOfWeek.value - 1 }
    val names = remember { (0 until 12).map { shortMonth(it) } }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val px = { d: Float -> with(density) { d.dp.toPx() } }
        // Closed: the bars.
        val barsHeight = with(density) { YearBarsHeight.toPx() }
        val barGap = px(4f)
        val barWidth = (width - barGap * 11) / 12
        val minBar = px(3f)
        // Open: the calendars, three across, six weeks tall so the rows line up.
        val colGap = px(16f)
        val rowGap = px(12f)
        val label = px(20f)
        val gap = px(2.5f)
        val calWidth = (width - colGap * (YEAR_COLUMNS - 1)) / YEAR_COLUMNS
        val square = (calWidth - gap * 6) / 7
        val calHeight = square * 6 + gap * 5
        val rows = 12 / YEAR_COLUMNS
        val openHeight = rows * (label + calHeight) + (rows - 1) * rowGap
        val height = barsHeight + (openHeight - barsHeight) * t
        fun bar(m: Int): Rect {
            val h = maxOf(minBar, perMonth[m].toFloat() / mostMonth * barsHeight)
            val left = m * (barWidth + barGap)
            return Rect(left, barsHeight - h, left + barWidth, barsHeight)
        }
        fun calendarAt(m: Int) =
            Offset((m % YEAR_COLUMNS) * (calWidth + colGap), (m / YEAR_COLUMNS) * (label + calHeight + rowGap))
        fun squareAt(m: Int, day: Int): Rect {
            val k = day + offsets[m]
            val origin = calendarAt(m)
            val left = origin.x + (k % 7) * (square + gap)
            val top = origin.y + label + (k / 7) * (square + gap)
            return Rect(left, top, left + square, top + square)
        }
        val pick =
            if (onPick != null)
                Modifier.pointerInput(recap.year, width) {
                    detectTapGestures { at ->
                        for (m in 0 until 12) {
                            // A little slack around each square so the small ones are easy to hit.
                            val day =
                                recap.perDay[m].indices.firstOrNull {
                                    !recap.untracked(m, it) && squareAt(m, it).inflate(gap / 2).contains(at)
                                }
                            if (day != null) {
                                onPick(m * 32 + day)
                                break
                            }
                        }
                    }
                }
            else Modifier
        Canvas(Modifier.fillMaxWidth().height(with(density) { height.toDp() }).then(pick)) {
            val pieces = (t / 0.3f).coerceIn(0f, 1f) // the days take over from the bars early on
            val barRadius = minOf(barWidth / 2, px(6f))
            for (m in 0 until 12) {
                val b = bar(m)
                if (pieces < 1f) {
                    drawRoundRect(
                        color = dayColor(perMonth[m], mostMonth, accent, empty),
                        topLeft = b.topLeft,
                        size = b.size,
                        cornerRadius = CornerRadius(barRadius),
                        alpha = 1f - pieces,
                    )
                }
                if (pieces > 0f) {
                    val days = recap.perDay[m]
                    val slice = b.height / days.size
                    days.forEachIndexed { d, n ->
                        val from = Rect(b.left, b.top + d * slice, b.right, b.top + (d + 1) * slice)
                        val rect = lerp(from, squareAt(m, d), t)
                        val r = square * 0.22f * t
                        drawRoundRect(
                            color = dayColor(n, mostDay, accent, empty, recap.untracked(m, d)),
                            topLeft = rect.topLeft,
                            size = rect.size,
                            cornerRadius = CornerRadius(r),
                            alpha = pieces,
                        )
                        if (m * 32 + d == picked && t > 0.99f) {
                            drawRoundRect(
                                color = outline,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                cornerRadius = CornerRadius(r),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                }
            }
            val labels = ((t - 0.6f) / 0.4f).coerceIn(0f, 1f)
            if (labels > 0f) {
                for (m in 0 until 12) {
                    val origin = calendarAt(m)
                    drawText(
                        textMeasurer = measurer,
                        text = names[m],
                        topLeft = Offset(origin.x, origin.y),
                        style = labelStyle.merge(UiTextStyle(color = outline.copy(alpha = 0.85f * labels))),
                    )
                }
            }
        }
    }
}

/** The closed card's tiles: best month, longest streak, reading time, Triage (or top feed). */
@Composable
private fun YearTiles(recap: YearRecap, tint: Color, modifier: Modifier = Modifier) {
    val tiles = buildList {
        recap.bestMonth?.let { m ->
            add(
                stringResource(R.string.recap_busiest_value, shortMonth(m.monthValue - 1), recap.perMonth[m.monthValue - 1]) to
                    stringResource(R.string.recap_tile_best_month)
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
    TileGrid(tiles, tint, modifier)
}

/** Every highlight of the year, as one connected list. */
@Composable
private fun YearRows(recap: YearRecap, tint: Color, modifier: Modifier = Modifier) {
    val rows = buildList {
        recap.bestMonth?.let { m ->
            add(
                stringResource(R.string.recap_best_month) to
                    stringResource(R.string.recap_busiest_value, monthName(m), recap.perMonth[m.monthValue - 1])
            )
        }
        recap.busiest?.let { day ->
            add(
                stringResource(R.string.recap_busiest) to
                    stringResource(
                        R.string.recap_busiest_value,
                        day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                        recap.perDay[day.monthValue - 1][day.dayOfMonth - 1],
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
    RowList(rows, tint, modifier)
}

/** The shared picture: the card as it is on screen, on a soft backdrop with the Brief You mark. */
@Composable
private fun YearPicture(recap: YearRecap, open: Boolean) {
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
            YearHeader(recap, scheme.onSurface)
            Spacer(Modifier.height(14.dp))
            YearMorph(
                recap = recap,
                t = if (open) 1f else 0f,
                picked = -1,
                accent = scheme.primary,
                empty = scheme.onSurface.copy(alpha = 0.1f),
                outline = scheme.onSurface,
                onPick = null,
            )
            Spacer(Modifier.height(14.dp))
            if (open) YearRows(recap, scheme.onSurface) else YearTiles(recap, scheme.onSurface)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                BriefYouMark(scheme.onSurfaceVariant)
            }
        }
    }
}

/** A tiny copy of the twelve month bars, for the home row. */
@Composable
internal fun MiniYear(recap: YearRecap) {
    val scheme = MaterialTheme.colorScheme
    val perMonth = recap.perMonth
    val most = (perMonth.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(30.dp)) {
        perMonth.forEach { n ->
            Box(
                Modifier.width(3.5.dp)
                    .height((30f * n / most).coerceAtLeast(3f).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(dayColor(n, most, scheme.primary, scheme.onPrimaryContainer.copy(alpha = 0.13f)))
            )
        }
    }
}
