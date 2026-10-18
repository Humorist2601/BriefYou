@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.stats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.brief.feeds.rememberFeedAccent
import me.ash.reader.brief.ui.GoogleSansWideFontFamily
import me.ash.reader.brief.ui.LocalBackStack
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.page.nav3.key.Route
import me.ash.reader.ui.page.settings.SelectableSettingGroupItem
import me.ash.reader.ui.theme.palette.onLight

/** Reading stats in the Settings list. Read You's SettingsPage shows it with a single line. */
@Composable
fun ReadingStatsSettingsItem() {
    val backStack = LocalBackStack.current
    SelectableSettingGroupItem(
        title = stringResource(R.string.stats_title),
        desc = stringResource(R.string.stats_settings_desc),
        icon = Icons.Outlined.Insights,
        onClick = {
            // The app's back stack (see BriefNavigationHost).
            @Suppress("UNCHECKED_CAST")
            (backStack as? MutableList<Any>)?.add(Route.ReadingStats)
        },
    )
}

/**
 * Reading stats, Material 3 Expressive: a hero card with this week's reading time and a wavy ring
 * for the days you read; three tiles (minutes, articles, streak), one of which the weekly chart
 * shows; the feeds you read most as ranked cards; and all-time numbers as tonal tiles. Kept on
 * the phone only.
 */
@Composable
fun ReadingStatsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ReadingStats.load(context) }
    val data = ReadingStats.data
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    // Which stat the weekly chart shows: tap a tile to choose (it turns tertiary, like the bars).
    var chartStat by rememberSaveable { mutableStateOf(ChartStat.Minutes) }

    val today = LocalDate.now()
    val week = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val weekDays = week.map { data.days[it.toString()] ?: StatsDay() }
    val weekMinutes = weekDays.sumOf { it.seconds } / 60
    val weekArticles = weekDays.sumOf { it.articles }
    val daysRead = weekDays.count { it.articles > 0 }
    val streak = remember(data) { streakDays(data, today) }
    val totalMinutes = data.days.values.sumOf { it.seconds } / 60
    val totalArticles = data.days.values.sumOf { it.articles }
    val topFeeds = remember(data) { data.feeds.entries.sortedByDescending { it.value }.take(5) }
    // The feeds' current icons (stats keep feed names; the icons are looked up when shown).
    val feedIcons by
        produceState(emptyMap<String, String?>(), topFeeds) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            val deps =
                                EntryPointAccessors.fromApplication(
                                    context.applicationContext,
                                    StatsDependencies::class.java,
                                )
                            deps.feedDao()
                                .queryAll(deps.accountService().getCurrentAccountId())
                                .associate { it.name to it.icon }
                        }
                        .getOrDefault(emptyMap())
                }
        }
    val chartValues =
        remember(data, chartStat, week) {
            week.map { day ->
                when (chartStat) {
                    ChartStat.Minutes -> (data.days[day.toString()]?.seconds ?: 0) / 60
                    ChartStat.Articles -> (data.days[day.toString()]?.articles ?: 0).toLong()
                    ChartStat.Streak -> streakOn(data, day).toLong()
                }
            }
        }

    // Read You's settings-page scaffold (deprecated by Read You, but all of Read You's settings
    // pages still use it), so this page looks like the others.
    @Suppress("DEPRECATION")
    RYScaffold(
        containerColor =
            MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.stats_title), desc = "")
                    Spacer(Modifier.height(8.dp))
                    HeroCard(minutes = weekMinutes, daysRead = daysRead)
                    Spacer(Modifier.height(12.dp))
                    // All three as tall as the tallest (a label may take two lines).
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatTile(
                            Modifier.weight(1f).fillMaxHeight(),
                            Icons.Outlined.Schedule,
                            weekMinutes.toString(),
                            stringResource(R.string.stats_minutes_week),
                            selected = chartStat == ChartStat.Minutes,
                            onClick = { chartStat = ChartStat.Minutes },
                        )
                        StatTile(
                            Modifier.weight(1f).fillMaxHeight(),
                            Icons.AutoMirrored.Outlined.Article,
                            weekArticles.toString(),
                            stringResource(R.string.stats_articles_week),
                            selected = chartStat == ChartStat.Articles,
                            onClick = { chartStat = ChartStat.Articles },
                        )
                        StatTile(
                            Modifier.weight(1f).fillMaxHeight(),
                            Icons.Outlined.LocalFireDepartment,
                            streak.toString(),
                            stringResource(R.string.stats_streak),
                            selected = chartStat == ChartStat.Streak,
                            onClick = { chartStat = ChartStat.Streak },
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text =
                            stringResource(
                                when (chartStat) {
                                    ChartStat.Minutes -> R.string.stats_this_week
                                    ChartStat.Articles -> R.string.stats_this_week_articles
                                    ChartStat.Streak -> R.string.stats_this_week_streak
                                }
                            ),
                    )
                    WeekChart(week = week, values = chartValues)
                    Spacer(Modifier.height(28.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.stats_top_feeds),
                    )
                    if (topFeeds.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_nothing_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    } else {
                        val most = topFeeds.first().value.coerceAtLeast(1)
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            topFeeds.forEachIndexed { i, (name, count) ->
                                FeedCard(
                                    rank = i + 1,
                                    name = name,
                                    iconUrl = feedIcons[name],
                                    count = count,
                                    fraction = count.toFloat() / most,
                                    shape = connectedShape(i, topFeeds.size),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.stats_all_time),
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmallTile(
                                Modifier.weight(1f),
                                Icons.Outlined.Schedule,
                                totalMinutes.toString(),
                                stringResource(R.string.stats_tile_minutes),
                            )
                            SmallTile(
                                Modifier.weight(1f),
                                Icons.AutoMirrored.Outlined.Article,
                                totalArticles.toString(),
                                stringResource(R.string.stats_tile_articles),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmallTile(
                                Modifier.weight(1f),
                                Icons.Outlined.DoneAll,
                                data.inboxZero.toString(),
                                stringResource(R.string.stats_tile_inbox_zero),
                            )
                            SmallTile(
                                Modifier.weight(1f),
                                Icons.Outlined.Style,
                                data.triaged.toString(),
                                stringResource(R.string.stats_tile_triage),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.stats_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                    )
                    TextButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.stats_reset))
                    }
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    RYDialog(
        visible = confirmReset,
        title = { Text(stringResource(R.string.stats_reset)) },
        text = { Text(stringResource(R.string.stats_reset_confirm)) },
        confirmButton = {
            TextButton(
                onClick = {
                    ReadingStats.reset(context)
                    confirmReset = false
                }
            ) {
                Text(stringResource(R.string.stats_reset_action))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
        },
        onDismissRequest = { confirmReset = false },
    )
}

/** For looking up the feeds' icons. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface StatsDependencies {
    fun feedDao(): FeedDao

    fun accountService(): AccountService
}

/** What the weekly chart shows. */
private enum class ChartStat {
    Minutes,
    Articles,
    Streak,
}

/** Days in a row with an article opened, up to today (or yesterday, if nothing yet today). */
private fun streakDays(data: ReadingStatsData, today: LocalDate): Int {
    val read = (data.days[today.toString()]?.articles ?: 0) > 0
    return streakOn(data, if (read) today else today.minusDays(1))
}

/** The streak on [day]: days in a row with an article opened, ending that day (0 if none). */
private fun streakOn(data: ReadingStatsData, day: LocalDate): Int {
    var d = day
    var count = 0
    while ((data.days[d.toString()]?.articles ?: 0) > 0) {
        count++
        d = d.minusDays(1)
    }
    return count
}

/** Rounded at the outer ends only, so the cards read as one connected group. */
private fun connectedShape(index: Int, count: Int): RoundedCornerShape {
    val big = 24.dp
    val small = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
}

/**
 * This week at a glance: the reading time in large wide type, and a wavy ring filling with the
 * days you read (an Expressive shape in the middle, like a badge).
 */
@Composable
private fun HeroCard(minutes: Long, daysRead: Int) {
    val ring = remember { Animatable(0f) }
    LaunchedEffect(daysRead) { ring.animateTo(daysRead / 7f) }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = minutes.toString(),
                    style =
                        MaterialTheme.typography.displayLarge.copy(
                            fontFamily = GoogleSansWideFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 64.sp,
                            lineHeight = 64.sp,
                        ),
                )
                Text(
                    text = stringResource(R.string.stats_hero_label),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
                CircularWavyProgressIndicator(
                    progress = { ring.value },
                    modifier = Modifier.size(104.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                )
                Box(
                    modifier =
                        Modifier.size(62.dp)
                            .clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$daysRead/7",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = stringResource(R.string.stats_days),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A big tonal tile with a number. Tap to show its stat in the weekly chart; the chosen one is
 * tertiary, like the chart's bars.
 */
@Composable
private fun StatTile(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val container by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            label = "statTile",
        )
    val content by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurface,
            label = "statTileContent",
        )
    // The chosen tile's corners grow rounder, the Expressive way of showing a selection.
    val corner by animateFloatAsState(if (selected) 36f else 20f, label = "statTileCorner")
    Surface(
        onClick = {
            if (!selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(corner.dp),
        modifier = modifier.semantics { this.selected = selected },
    ) {
        // Centred, so it stays clear of the rounder corners when the tile is chosen.
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The chosen stat on each of the last seven days, as pill-shaped tertiary bars that grow in when
 * the page opens and change smoothly when you pick another stat. Today's value sits in an
 * Expressive shape on top of its bar.
 */
@Composable
private fun WeekChart(week: List<LocalDate>, values: List<Long>) {
    val most = values.maxOf { it }.coerceAtLeast(1)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(208.dp).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEachIndexed { i, date ->
                val value = values[i]
                val isToday = i == week.lastIndex
                val target = (value.toFloat() / most).coerceIn(0.06f, 1f)
                val fraction by animateFloatAsState(target, label = "bar")
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (isToday) {
                        Box(
                            modifier =
                                Modifier.size(34.dp)
                                    .clip(MaterialShapes.Cookie7Sided.toShape())
                                    .background(MaterialTheme.colorScheme.tertiary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = value.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary,
                                maxLines = 1,
                            )
                        }
                    } else {
                        Text(
                            text = if (value > 0) value.toString() else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.weight(1f).width(26.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .fillMaxHeight((fraction * grow.value).coerceAtLeast(0.06f))
                                    .clip(CircleShape)
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                                    )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color =
                            if (isToday) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * One of the feeds you read most: its rank, icon and name, how many of its articles you opened,
 * and a thick rounded bar in the feed's own colour (from its icon), relative to the top feed.
 */
@Composable
private fun FeedCard(
    rank: Int,
    name: String,
    iconUrl: String?,
    count: Int,
    fraction: Float,
    shape: RoundedCornerShape,
) {
    val accent = rememberFeedAccent(iconUrl) ?: MaterialTheme.colorScheme.primary
    val fill by animateFloatAsState(fraction.coerceIn(0.04f, 1f), label = "feedBar")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier.size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (rank == 1) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (rank == 1) MaterialTheme.colorScheme.onTertiary
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                FeedIcon(feedName = name, iconUrl = iconUrl, size = 24.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth(fill).height(12.dp).clip(CircleShape).background(accent)
                )
            }
        }
    }
}

/** A small tonal tile for the all-time numbers. */
@Composable
private fun SmallTile(modifier: Modifier, icon: ImageVector, value: String, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(MaterialShapes.Cookie4Sided.toShape())
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
