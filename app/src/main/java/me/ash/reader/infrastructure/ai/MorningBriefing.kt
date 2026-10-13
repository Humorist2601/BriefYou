package me.ash.reader.infrastructure.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.infrastructure.android.MainActivity
import me.ash.reader.ui.widget.BriefingWidget
import timber.log.Timber

/** The most recent briefing. */
data class Briefing(
    val title: String,
    val text: String,
    val createdAt: Long,
    val articleCount: Int,
)

/** User settings for the scheduled briefing. */
data class BriefingSettings(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 30,
    /** Group to brief on; null means all unread articles. */
    val groupId: String? = null,
    val groupName: String? = null,
)

/**
 * The morning briefing: at a chosen time, an on-device gist of the newest unread articles (all,
 * or one group) is generated and posted as a single notification that can be read or listened
 * to. The latest briefing is also shown in the app and on the Briefing widget.
 */
@Singleton
class MorningBriefing
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val summarizer: GemmaSummarizer,
    private val articleDao: ArticleDao,
    private val accountService: AccountService,
    private val workManager: WorkManager,
) {
    private val prefs = context.getSharedPreferences("morning_briefing", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<BriefingSettings> = _settings.asStateFlow()

    private val _latest = MutableStateFlow(readLatest())
    val latest: StateFlow<Briefing?> = _latest.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    fun update(settings: BriefingSettings) {
        prefs.edit {
            putBoolean("enabled", settings.enabled)
            putInt("hour", settings.hour)
            putInt("minute", settings.minute)
            putString("groupId", settings.groupId)
            putString("groupName", settings.groupName)
        }
        _settings.value = settings
        reschedule()
    }

    /** Schedules (or cancels) the next run based on the settings. */
    fun reschedule() {
        val s = _settings.value
        if (!s.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = Calendar.getInstance()
        val next =
            (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, s.hour)
                set(Calendar.MINUTE, s.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
        val delay = next.timeInMillis - now.timeInMillis
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BriefingWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
        Timber.d("Morning briefing scheduled in ${delay / 60000} min")
    }

    /**
     * Generates a briefing now. Returns it, or null when there was nothing to brief on or no
     * model. When [notify] is true, posts the briefing notification.
     */
    suspend fun generate(notify: Boolean): Briefing? {
        if (!summarizer.hasModel()) return null
        if (_generating.value) return null
        _generating.value = true
        try {
            val s = _settings.value
            val items =
                withContext(Dispatchers.IO) {
                    val accountId = accountService.getCurrentAccountId()
                    val rows =
                        if (s.groupId != null)
                            articleDao.queryForGistByGroupId(accountId, s.groupId, true, false, LIMIT)
                        else articleDao.queryForGist(accountId, true, false, LIMIT)
                    rows.map {
                        GistItem(it.article.id, it.feed.name, it.article.title, it.article.shortDescription)
                    }
                }
            if (items.isEmpty()) return null
            var text = ""
            summarizer.gist(s.groupName, items).collect { text = it }
            if (text.isBlank()) return null
            val title = context.getString(R.string.briefing_title)
            val briefing = Briefing(title, text, System.currentTimeMillis(), items.size)
            save(briefing)
            runCatching { BriefingWidget().updateAll(context) }
            if (notify) postNotification(briefing)
            return briefing
        } finally {
            _generating.value = false
        }
    }

    private fun postNotification(b: Briefing) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.briefing_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val open =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                openBriefingIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val listen =
            PendingIntent.getForegroundService(
                context,
                NOTIFICATION_ID + 1,
                BriefingPlaybackService.playIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val firstLine = b.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val notification =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(b.title)
                .setContentText(firstLine)
                .setStyle(NotificationCompat.BigTextStyle().bigText(b.text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.briefing_listen), listen)
                .build()
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "notification permission missing")
        }
    }

    private fun save(b: Briefing) {
        prefs.edit {
            putString("latest_title", b.title)
            putString("latest_text", b.text)
            putLong("latest_time", b.createdAt)
            putInt("latest_count", b.articleCount)
        }
        _latest.value = b
    }

    private fun readSettings() =
        BriefingSettings(
            enabled = prefs.getBoolean("enabled", false),
            hour = prefs.getInt("hour", 7),
            minute = prefs.getInt("minute", 30),
            groupId = prefs.getString("groupId", null),
            groupName = prefs.getString("groupName", null),
        )

    private fun readLatest(): Briefing? {
        val text = prefs.getString("latest_text", null) ?: return null
        return Briefing(
            prefs.getString("latest_title", null) ?: context.getString(R.string.briefing_title),
            text,
            prefs.getLong("latest_time", 0),
            prefs.getInt("latest_count", 0),
        )
    }

    companion object {
        private const val WORK_NAME = "brief_you_morning_briefing"
        private const val CHANNEL = "morning_briefing"
        private const val NOTIFICATION_ID = 0xB41E
        private const val LIMIT = 40
        const val EXTRA_OPEN_BRIEFING = "brief_you.open_briefing"

        fun openBriefingIntent(context: Context) =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_BRIEFING, true)
            }
    }
}

@HiltWorker
class BriefingWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val briefing: MorningBriefing,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            briefing.generate(notify = true)
        } catch (e: Exception) {
            Timber.w(e, "morning briefing failed")
        } finally {
            briefing.reschedule() // tomorrow
        }
        return Result.success()
    }
}
