package me.ash.reader.brief.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekDays = week.map { data.days[it.toString()] ?: StatsDay() }
    val weekMinutes = weekDays.sumOf { it.seconds } / 60
    val weekArticles = weekDays.sumOf { it.articles }
    val streak = remember(data) { streakDays(data, today) }
    val totalMinutes = data.days.values.sumOf { it.seconds } / 60
    val totalArticles = data.days.values.sumOf { it.articles }
    val topFeeds = remember(data) { data.feeds.entries.sortedByDescending { it.value }.take(5) }

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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatTile(
                            Modifier.weight(1f),
                            Icons.Outlined.Schedule,
                            weekMinutes.toString(),
                            stringResource(R.string.stats_minutes_week),
                        )
                        StatTile(
                            Modifier.weight(1f),
                            Icons.AutoMirrored.Outlined.Article,
                            weekArticles.toString(),
                            stringResource(R.string.stats_articles_week),
                        )
                        StatTile(
                            Modifier.weight(1f),
                            Icons.Outlined.LocalFireDepartment,
                            streak.toString(),
                            stringResource(R.string.stats_streak),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.stats_this_week),
                    )
                    WeekChart(week = week, days = weekDays)
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
                        topFeeds.forEach { (name, count) -> FeedRow(name, count, count.toFloat() / most) }
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

/** A big tonal tile with a number, like the tiles at the top of Filters. */
@Composable
private fun StatTile(modifier: Modifier, icon: ImageVector, value: String, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
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

/** Minutes read on each of the last seven days, as rounded bars, today last and highlighted. */
@Composable
private fun WeekChart(week: List<LocalDate>, days: List<StatsDay>) {
    val most = days.maxOf { it.seconds }.coerceAtLeast(60)
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
                val minutes = days[i].seconds / 60
                val isToday = i == week.lastIndex
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (minutes > 0) minutes.toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.weight(1f).width(22.dp), contentAlignment = Alignment.BottomCenter) {
                        val fraction = (days[i].seconds.toFloat() / most).coerceIn(0.04f, 1f)
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
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
private fun FeedRow(name: String, count: Int, fraction: Float) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
