package com.ay.briefyou.brief.notifications

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.ay.briefyou.R
import com.ay.briefyou.domain.model.feed.Feed
import com.ay.briefyou.domain.service.AccountService
import com.ay.briefyou.domain.service.RssService
import com.ay.briefyou.infrastructure.di.IODispatcher
import com.ay.briefyou.ui.component.base.Banner
import com.ay.briefyou.ui.component.base.DisplayText
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.component.base.BYScaffold
import com.ay.briefyou.ui.component.base.BYSwitch
import com.ay.briefyou.ui.component.base.Subtitle
import com.ay.briefyou.ui.component.base.Tips
import com.ay.briefyou.ui.ext.collectAsStateValue
import com.ay.briefyou.ui.page.settings.SettingItem
import com.ay.briefyou.ui.theme.palette.onLight
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A feed with the name of its group. */
data class NotifyRow(val feed: Feed, val group: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel
@Inject
constructor(
    accountService: AccountService,
    private val rssService: RssService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** Every feed of the account (null while it is read). */
    val rows: StateFlow<List<NotifyRow>?> =
        accountService.currentAccountFlow
            .filterNotNull()
            .flatMapLatest { rssService.get(it.type.id).pullFeeds() }
            .map<List<com.ay.briefyou.domain.model.group.GroupWithFeed>, List<NotifyRow>?> { groups ->
                groups.flatMap { g -> g.feeds.map { NotifyRow(it, g.group.name) } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun set(feed: Feed, on: Boolean) {
        viewModelScope.launch(ioDispatcher) { rssService.get().updateFeed(feed.copy(isNotification = on)) }
    }
}

/**
 * Settings → Notifications: the feeds that notify, each with its switch, and a way into Android's
 * own settings for the app's notifications (sound, lock screen). A feed switched off stays in the
 * list until you leave, so it can be switched on again.
 */
@Composable
fun NotificationsPage(onBack: () -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val rows = viewModel.rows.collectAsStateValue()
    // The feeds that notified when the page opened, kept in the list while it is open.
    var openedWith by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(rows != null) {
        if (openedWith == null && rows != null) {
            openedWith = rows.filter { it.feed.isNotification }.map { it.feed.id }.toSet()
        }
    }
    val shown =
        rows.orEmpty().filter { it.feed.isNotification || it.feed.id in (openedWith ?: emptySet()) }

    // Whether Android lets the app notify at all, checked again when coming back from its settings.
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    BYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
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
                    DisplayText(text = stringResource(R.string.notifications), desc = "")
                    Spacer(Modifier.height(8.dp))
                    if (!allowed) {
                        Banner(
                            modifier = Modifier.padding(bottom = 12.dp),
                            title = stringResource(R.string.notifications_off_title),
                            desc = stringResource(R.string.notifications_off_desc),
                            icon = Icons.Outlined.NotificationsOff,
                        ) {
                            openAndroidSettings(context)
                        }
                    }
                    com.ay.briefyou.brief.ui.SettingGroup {
                        SettingItem(
                            title = stringResource(R.string.notifications_android),
                            desc = stringResource(R.string.notifications_android_desc),
                            icon = Icons.Outlined.Settings,
                            onClick = { openAndroidSettings(context) },
                        ) {}
                    }
                    Spacer(Modifier.height(24.dp))
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.notifications_feeds),
                    )
                }
                if (rows != null && shown.isEmpty()) {
                    item { Tips(text = stringResource(R.string.notifications_none)) }
                } else if (shown.isNotEmpty()) {
                    item {
                        com.ay.briefyou.brief.ui.SettingGroup {
                            shown.forEach { row ->
                                SettingItem(
                                    title = row.feed.name,
                                    desc = row.group,
                                    onClick = { viewModel.set(row.feed, !row.feed.isNotification) },
                                ) {
                                    BYSwitch(activated = row.feed.isNotification) {
                                        viewModel.set(row.feed, !row.feed.isNotification)
                                    }
                                }
                            }
                        }
                        Tips(text = stringResource(R.string.notifications_tips))
                    }
                }
                item {
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )
}

private fun openAndroidSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
