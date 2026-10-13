package me.ash.reader.ui.widget

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import me.ash.reader.R
import me.ash.reader.infrastructure.ai.Briefing
import me.ash.reader.infrastructure.ai.MorningBriefing

class BriefingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BriefingWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BriefingEntryPoint {
    fun morningBriefing(): MorningBriefing
}

/** Home-screen widget showing the latest morning briefing. Tapping it opens the full briefing. */
class BriefingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val briefing =
            EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BriefingEntryPoint::class.java,
                )
                .morningBriefing()
                .latest
                .value
        val open = MorningBriefing.openBriefingIntent(context)
        provideContent {
            GlanceTheme {
                BriefingContent(context, briefing, GlanceModifier.clickable(actionStartActivity(open)))
            }
        }
    }
}

@Composable
private fun BriefingContent(context: Context, briefing: Briefing?, clickModifier: GlanceModifier) {
    Column(
        modifier =
            GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(16.dp)
                .then(clickModifier)
    ) {
        Text(
            text = "✦ " + (briefing?.title ?: context.getString(R.string.briefing_title)),
            style =
                TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
        )
        if (briefing != null) {
            Text(
                text =
                    DateUtils.getRelativeTimeSpanString(
                            briefing.createdAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        )
                        .toString(),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (briefing == null) {
            Text(
                text = context.getString(R.string.briefing_widget_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
            )
        } else {
            val lines =
                briefing.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                items(lines.size) { i ->
                    Text(
                        text = lines[i],
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                        modifier = GlanceModifier.padding(bottom = 6.dp).then(clickModifier),
                    )
                }
            }
        }
    }
}
