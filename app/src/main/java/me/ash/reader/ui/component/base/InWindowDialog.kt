package me.ash.reader.ui.component.base

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/*
 * Dialogs drawn inside the app's own window instead of a separate dialog window.
 *
 * A separate dialog window moves when the keyboard opens, and the text cursor's handle (placed
 * from the field's position inside that window) doesn't follow, so it showed far below the field,
 * even under the keyboard. In the app's window the keyboard is handled the same way as on every
 * other screen, so the handle sits under the cursor. They look and behave like Material dialogs:
 * scrim, tap outside or back to close (unless turned off), centred, moved up above the keyboard.
 */

/** Where in-window dialogs are drawn: one host at the root of the app, above everything. */
class DialogHostState {
    internal val entries = mutableStateListOf<DialogEntry>()
}

internal class DialogEntry {
    var content by mutableStateOf<@Composable () -> Unit>({})
}

val LocalDialogHost = staticCompositionLocalOf<DialogHostState?> { null }

/** Put at the root of the app; dialogs from anywhere below show on top of [content]. */
@Composable
fun DialogHost(state: DialogHostState = remember { DialogHostState() }, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDialogHost provides state) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            state.entries.forEach { entry -> key(entry) { entry.content() } }
        }
    }
}

/**
 * Something shown over the whole screen, centred on it, above everything (a celebration, say).
 * It doesn't take touches. Shown while this is in the composition.
 */
@Composable
fun ScreenOverlay(content: @Composable () -> Unit) {
    val host = LocalDialogHost.current ?: return
    val entry = remember { DialogEntry() }
    SideEffect {
        entry.content = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/**
 * A Material alert dialog in the app's window (falls back to the normal [AlertDialog] where there
 * is no [DialogHost], such as in a widget's settings).
 */
@Composable
fun InWindowAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val host = LocalDialogHost.current
    if (host == null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            properties = properties,
        )
        return
    }
    val entry = remember { DialogEntry() }
    SideEffect {
        entry.content = {
            DialogScaffold(
                onDismissRequest = onDismissRequest,
                confirmButton = confirmButton,
                modifier = modifier,
                dismissButton = dismissButton,
                icon = icon,
                title = title,
                text = text,
                properties = properties,
            )
        }
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogScaffold(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier,
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    properties: DialogProperties,
) {
    // Predictive back: while you swipe back, the dialog shrinks a little and drifts toward the
    // edge you swipe from, and the scrim lightens; let go and it fades away and closes, change
    // your mind and it settles back.
    val scope = rememberCoroutineScope()
    val back = remember { Animatable(0f) }
    val leave = remember { Animatable(1f) }
    var fromLeft by remember { mutableStateOf(true) }
    PredictiveBackHandler(enabled = properties.dismissOnBackPress) { events ->
        try {
            events.collect { event ->
                fromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                back.snapTo(event.progress)
            }
            leave.animateTo(0f, tween(150))
            onDismissRequest()
        } catch (e: CancellationException) {
            scope.launch { back.animateTo(0f, spring()) }
        }
    }
    val p = back.value
    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = leave.value }) {
        // The scrim, over the whole screen (bars included); tapping it closes the dialog.
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * (1f - 0.4f * p)))
                    .pointerInput(properties.dismissOnClickOutside) {
                        detectTapGestures {
                            if (properties.dismissOnClickOutside) onDismissRequest()
                        }
                    }
        )
        // Centred in the space the bars and the keyboard leave.
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            val appear = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(
                visibleState = appear,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
            ) {
                Surface(
                    modifier =
                        (if (properties.usePlatformDefaultWidth) Modifier.padding(horizontal = 40.dp)
                            else Modifier)
                            .then(modifier)
                            .sizeIn(minWidth = 280.dp, maxWidth = 560.dp)
                            .graphicsLayer {
                                val scale = 1f - 0.1f * p
                                scaleX = scale
                                scaleY = scale
                                translationX = (if (fromLeft) 1f else -1f) * 8.dp.toPx() * p
                            }
                            // Taps on the dialog itself don't reach the scrim.
                            .pointerInput(Unit) { detectTapGestures { } },
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        icon?.let {
                            CompositionLocalProvider(
                                LocalContentColor provides AlertDialogDefaults.iconContentColor
                            ) {
                                Box(
                                    modifier =
                                        Modifier.padding(bottom = 16.dp)
                                            .align(Alignment.CenterHorizontally)
                                ) {
                                    it()
                                }
                            }
                        }
                        title?.let {
                            CompositionLocalProvider(
                                LocalContentColor provides AlertDialogDefaults.titleContentColor
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                                    Box(
                                        modifier =
                                            Modifier.padding(bottom = 16.dp)
                                                .align(
                                                    if (icon == null) Alignment.Start
                                                    else Alignment.CenterHorizontally
                                                )
                                    ) {
                                        it()
                                    }
                                }
                            }
                        }
                        text?.let {
                            CompositionLocalProvider(
                                LocalContentColor provides AlertDialogDefaults.textContentColor
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                    Box(
                                        modifier =
                                            Modifier.weight(1f, fill = false)
                                                .padding(bottom = 24.dp)
                                                .align(Alignment.Start)
                                    ) {
                                        it()
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.align(Alignment.End)) {
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.primary
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                                    FlowRow(
                                        horizontalArrangement =
                                            Arrangement.spacedBy(8.dp, Alignment.End),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        dismissButton?.invoke()
                                        confirmButton()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
