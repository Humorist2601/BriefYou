@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
)

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

/** The recap card's soft backdrop: the theme's primary container fading into the card colour. */
@Composable
private fun recapBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(
        listOf(scheme.primaryContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHigh)
    )
}

/**
 * The monthly recap: the month's articles in big wide type (and the change from the month
 * before), the month as a calendar (Monday first; brighter squares are days you read more; tap
 * one for its numbers), then the month's highlights as one connected group. [onHide] and
 * [showShare] add the buttons at the bottom; Share makes a picture of the card.
 */
@Composable
fun RecapCard(
    recap: MonthRecap,
    modifier: Modifier = Modifier,
    onHide: (() -> Unit)? = null,
    showShare: Boolean = true,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    var capturing by remember { mutableStateOf(false) }
    Box(modifier = modifier.clip(RoundedCornerShape(28.dp)).background(recapBrush())) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            RecapContent(recap, interactive)
            if (onHide != null || showShare) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onHide != null) {
                        FilledTonalButton(onClick = onHide, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.recap_hide))
                        }
                    }
                    if (showShare) {
                        Button(onClick = { capturing = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.recap_share))
                        }
                    }
                }
            }
        }
        OffscreenPicture(
            capturing = capturing,
            onPicture = { picture ->
                capturing = false
                if (picture != null) sharePicture(context, picture, "month-recap")
            },
        ) {
            RecapPicture(recap)
        }
    }
}

/** The card's top part: title, big number, calendar and highlights. */
@Composable
private fun RecapContent(recap: MonthRecap, interactive: Boolean) {
    val scheme = MaterialTheme.colorScheme
    var picked by rememberSaveable(recap.month.toString()) { mutableStateOf(-1) }
    Text(
        text = stringResource(R.string.recap_title, monthName(recap.month)).uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
        fontWeight = FontWeight.SemiBold,
        color = scheme.primary,
    )
    recap.since?.let {
        Text(
            text = stringResource(R.string.recap_since, it.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = recap.articles.toString(),
            style =
                MaterialTheme.typography.displayLarge.copy(
                    fontFamily = GoogleSansWideFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                ),
            color = scheme.onSurface,
        )
        Text(
            text = stringResource(R.string.recap_articles),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).weight(1f),
        )
        vsText(recap)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                modifier =
                    Modifier.padding(bottom = 8.dp)
                        .clip(CircleShape)
                        .background(scheme.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    val pick: ((Int) -> Unit)? =
        if (interactive) ({ day: Int -> picked = if (picked == day) -1 else day }) else null
    RecapCalendar(recap, picked, pick)
    // The tapped day's numbers, sliding in under the calendar.
    Box(Modifier.animateContentSize()) {
        if (picked >= 0) {
            val date = recap.month.atDay(picked + 1)
            Text(
                text =
                    pluralStringResource(
                        R.plurals.recap_day_detail,
                        recap.perDay[picked],
                        date.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())),
                        recap.perDay[picked],
                        readingTime(recap.minutesPerDay[picked]),
                    ),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
        }
    }
    Spacer(Modifier.height(14.dp))
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
        add(
            stringResource(R.string.recap_streak) to
                pluralStringResource(R.plurals.recap_days, recap.longestStreak, recap.longestStreak)
        )
        add(stringResource(R.string.recap_time) to readingTime(recap.minutes))
        if (recap.triaged > 0 || recap.inboxZero > 0) {
            add(
                stringResource(R.string.recap_triage) to
                    pluralStringResource(R.plurals.recap_triage_value, recap.inboxZero, recap.triaged, recap.inboxZero)
            )
        }
        if (recap.filtered > 0) add(stringResource(R.string.recap_filtered) to recap.filtered.toString())
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, (label, value) ->
            val top = if (i == 0) 16.dp else 4.dp
            val bottom = if (i == rows.lastIndex) 16.dp else 4.dp
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(top, top, bottom, bottom))
                        .background(scheme.surfaceContainerHighest.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The month as weeks, Monday first: each day a square, brighter the more you read that day. */
@Composable
private fun RecapCalendar(recap: MonthRecap, picked: Int, onPick: ((Int) -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val most = (recap.perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val offset = recap.month.atDay(1).dayOfWeek.value - 1
    val cells = offset + recap.perDay.size
    val weeks = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(weeks) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { col ->
                    val day = week * 7 + col - offset
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day in recap.perDay.indices) {
                            val n = recap.perDay[day]
                            val color by
                                animateColorAsState(
                                    if (n == 0) scheme.surfaceContainerHighest
                                    else scheme.primary.copy(alpha = 0.25f + 0.75f * n / most),
                                    label = "recap day",
                                )
                            Box(
                                Modifier.matchParentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color)
                                    .then(
                                        if (day == picked) Modifier.border(2.dp, scheme.onSurface, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .then(if (onPick != null) Modifier.clickable { onPick(day) } else Modifier)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The shared picture: the card on a soft backdrop (no buttons, nothing to tap), with a small
 * Brief You mark, like the Triage card's picture.
 */
@Composable
private fun RecapPicture(recap: MonthRecap) {
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
            RecapContent(recap, interactive = false)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(scheme.primary))
                Text(
                    text = stringResource(R.string.triage_share_brand),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
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
            color = Color.Transparent,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(recapBrush()).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniCalendar(r)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = stringResource(R.string.recap_title, monthName(r.month)),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = listOfNotNull(stringResource(R.string.recap_teaser, r.articles), vsText(r)).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { ReadingStats.hideRecap(context, r.month.toString()) }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.recap_hide),
                        tint = scheme.onSurfaceVariant,
                    )
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
                                when {
                                    n == null -> Color.Transparent
                                    n == 0 -> scheme.onSurface.copy(alpha = 0.1f)
                                    else -> scheme.primary.copy(alpha = 0.3f + 0.7f * n / most)
                                }
                            )
                    )
                }
            }
        }
    }
}
