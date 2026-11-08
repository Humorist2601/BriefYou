@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.readlater

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.service.AccountService
import me.ash.reader.infrastructure.android.MainActivity
import me.ash.reader.infrastructure.compose.ProvideCompositionLocals
import me.ash.reader.infrastructure.preference.AccountSettingsProvider
import me.ash.reader.infrastructure.preference.LocalDarkTheme
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.page.common.ExtraName
import me.ash.reader.ui.theme.AppTheme

/**
 * Read later's sheet: Brief You in the Share menu as "Read later". Sharing a link saves the page
 * (see [ReadLater]) and a small sheet says so over the app it came from, with Read now and Done;
 * it closes by itself after a few seconds, leaving you where you were.
 */
@AndroidEntryPoint
class ReadLaterActivity : ComponentActivity() {

    @Inject lateinit var readLater: ReadLater
    @Inject lateinit var settingsProvider: SettingsProvider
    @Inject lateinit var accountService: AccountService

    private sealed interface State {
        data object Saving : State

        data class Saved(val saved: ReadLater.Saved) : State

        data class Failed(val message: Int) : State
    }

    private var state by mutableStateOf<State>(State.Saving)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = ReadLater.findUrl(intent?.getStringExtra(Intent.EXTRA_TEXT))
        if (url == null) {
            state = State.Failed(R.string.read_later_no_link)
        } else {
            lifecycleScope.launch {
                state =
                    try {
                        State.Saved(readLater.save(url, intent?.getStringExtra(Intent.EXTRA_SUBJECT)))
                    } catch (_: ReadLater.NotLocalAccount) {
                        State.Failed(R.string.read_later_not_local)
                    } catch (e: Exception) {
                        State.Failed(R.string.read_later_failed)
                    }
            }
        }
        setContent {
            AccountSettingsProvider(accountService = accountService) {
                settingsProvider.ProvidesSettings {
                    ProvideCompositionLocals {
                        AppTheme(useDarkTheme = LocalDarkTheme.current.isDarkTheme()) {
                            Sheet(state = state, onReadNow = ::readNow, onClose = ::finishQuietly)
                        }
                    }
                }
            }
        }
    }

    private fun readNow(saved: ReadLater.Saved) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(ExtraName.ARTICLE_ID, saved.articleId)
                putExtra(ExtraName.FEED_ID, saved.feedId)
            }
        )
        finishQuietly()
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    @Composable
    private fun Sheet(
        state: State,
        onReadNow: (ReadLater.Saved) -> Unit,
        onClose: () -> Unit,
    ) {
        val haptics = LocalHapticFeedback.current
        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
        val scope = rememberCoroutineScope()
        fun close() {
            visible.targetState = false
            scope.launch {
                delay(250)
                onClose()
            }
        }
        BackHandler { close() }

        // Saved: a tick of feedback, then a countdown; touching the sheet keeps it open.
        var secondsLeft by remember { mutableIntStateOf(AUTO_CLOSE_SECONDS) }
        var held by remember { mutableStateOf(false) }
        LaunchedEffect(state is State.Saved) {
            if (state is State.Saved) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                while (secondsLeft > 0) {
                    delay(1_000)
                    if (held) return@LaunchedEffect
                    secondsLeft--
                }
                close()
            }
        }

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        close()
                    },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    modifier =
                        Modifier.fillMaxWidth().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            held = true
                        },
                ) {
                    Column(
                        modifier =
                            Modifier.navigationBarsPadding()
                                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                        when (state) {
                            State.Saving -> Saving()
                            is State.Saved -> SavedContent(state.saved, secondsLeft, held, { onReadNow(state.saved) }, ::close)
                            is State.Failed -> FailedContent(state.message, ::close)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ColumnScope.Saving() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(vertical = 18.dp),
        ) {
            LoadingIndicator(modifier = Modifier.size(40.dp))
            Text(
                text = stringResource(R.string.read_later_saving),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    @Composable
    private fun ColumnScope.SavedContent(
        saved: ReadLater.Saved,
        secondsLeft: Int,
        held: Boolean,
        onReadNow: () -> Unit,
        onDone: () -> Unit,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column {
                Text(
                    text = stringResource(if (saved.alreadySaved) R.string.read_later_already else R.string.read_later_saved),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(if (saved.fetched) R.string.read_later_where else R.string.read_later_link_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (saved.image != null) {
                    RYAsyncImage(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                        data = saved.image,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = saved.site,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = saved.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    saved.minutes?.let {
                        Text(
                            text = stringResource(R.string.reading_time_min, it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onReadNow, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.read_later_read_now))
            }
            Button(onClick = onDone, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.read_later_done))
            }
        }
        if (!held && secondsLeft > 0) {
            Text(
                text = stringResource(R.string.read_later_closes, secondsLeft),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    @Composable
    private fun ColumnScope.FailedContent(message: Int, onDone: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.read_later_done))
        }
    }

    private companion object {
        const val AUTO_CLOSE_SECONDS = 3
    }
}
