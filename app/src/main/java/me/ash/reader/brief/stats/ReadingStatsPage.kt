package me.ash.reader.brief.stats

import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.repository.FeedDao
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.EntryPoint
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import me.ash.reader.R
import me.ash.reader.brief.ui.LocalBackStack
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.page.nav3.key.Route
import me.ash.reader.ui.page.settings.SelectableSettingGroupItem
import me.ash.reader.ui.page.settings.SettingItem
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
 * Reading stats: time spent reading this week (with a bar per day), articles opened, the reading
 * streak, the feeds read most, and inbox-zero and Triage counts. Kept on the phone only.
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
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekDays = week.map { data.days[it.toString()] ?: StatsDay() }
    val weekMinutes = weekDays.sumOf { it.seconds } / 60
    val weekArticles = weekDays.sumOf { it.articles }
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
                    // This week at a glance, like the tiles at the top of Filters.
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
                    Spacer(Modifier.height(24.dp))
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
                    val values =
                        remember(data, chartStat) {
                            week.map { day ->
                                when (chartStat) {
                                    ChartStat.Minutes -> (data.days[day.toString()]?.seconds ?: 0) / 60
                                    ChartStat.Articles -> (data.days[day.toString()]?.articles ?: 0).toLong()
                                    ChartStat.Streak -> streakOn(data, day).toLong()
                                }
                            }
                        }
                    WeekChart(week = week, values = values)
                    Spacer(Modifier.height(24.dp))
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
                        topFeeds.forEach { (name, count) ->
                            FeedRow(name, feedIcons[name], count, count.toFloat() / most)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.stats_all_time),
                    )
                    SettingItem(
                        title = stringResource(R.string.stats_total_minutes, totalMinutes),
                        onClick = {},
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.stats_total_articles, totalArticles),
                        onClick = {},
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.stats_inbox_zero, data.inboxZero),
                        onClick = {},
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.stats_triaged, data.triaged),
                        onClick = {},
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.stats_reset),
                        desc = stringResource(R.string.stats_reset_desc),
                        onClick = { confirmReset = true },
                    ) {}
                    Text(
                        text = stringResource(R.string.stats_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
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

/** Days in a row with an article opened, up to today (or yesterday, if nothing yet today). */
private fun streakDays(data: ReadingStatsData, today: LocalDate): Int {
    fun read(day: LocalDate) = (data.days[day.toString()]?.articles ?: 0) > 0
    var day = if (read(today)) today else today.minusDays(1)
    var count = 0
    while (read(day)) {
        count++
        day = day.minusDays(1)
    }
    return count
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

/**
 * A big tonal tile with a number, like the tiles at the top of Filters. Tap to show its stat in
 * the weekly chart; the chosen one is tertiary, like the chart's bars.
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
            else MaterialTheme.colorScheme.primaryContainer,
            label = "statTile",
        )
    val content by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
            label = "statTileContent",
        )
    Surface(
        onClick = {
            if (!selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.semantics { this.selected = selected },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The chosen stat on each of the last seven days, as tertiary bars, today last and highlighted. */
@Composable
private fun WeekChart(week: List<LocalDate>, values: List<Long>) {
    val most = values.maxOf { it }.coerceAtLeast(1)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(180.dp).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEachIndexed { i, date ->
                val value = values[i]
                val isToday = i == week.lastIndex
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (value > 0) value.toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.weight(1f).width(22.dp), contentAlignment = Alignment.BottomCenter) {
                        val fraction by
                            animateFloatAsState(
                                (value.toFloat() / most).coerceIn(0.04f, 1f),
                                label = "bar",
                            )
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f),
                                        RoundedCornerShape(11.dp),
                                    )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
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

/** A feed with how many of its articles were opened, and a bar relative to the most-read one. */
@Composable
private fun FeedRow(name: String, iconUrl: String?, count: Int, fraction: Float) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeedIcon(feedName = name, iconUrl = iconUrl, size = 24.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            )
        }
    }
}
