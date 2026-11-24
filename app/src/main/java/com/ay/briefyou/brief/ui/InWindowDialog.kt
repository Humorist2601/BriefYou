package com.ay.briefyou.brief.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/*
 * The app's dialogs, and a host for things drawn over the whole app window (ScreenOverlay).
 *
 * A normal dialog window moves when the keyboard opens, and the text cursor's handle (placed from
 * the field's position inside that window) doesn't follow, so it showed far below the field, even
 * under the keyboard. Dialogs were first drawn in the app's own window to avoid that; now each gets
 * a full-screen window of its own that doesn't move, with the dialog moved above the keyboard inside
 * it, which keeps the handle in place without re-laying out the page behind on every keyboard
 * frame. They look and behave like Material dialogs: scrim, tap outside or back to close (unless
 * turned off), centred, moved up above the keyboard.
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

/** How a dialog is being closed: [closing] fades it out where it stands, then calls [onExited]. */
class DialogExit(val closing: Boolean, val onExited: () -> Unit)

private val NoDialogExit = DialogExit(false) {}
private val LocalDialogExit = compositionLocalOf { NoDialogExit }

/**
 * Keeps a dialog composed while it fades out (and its keyboard leaves), so closing is not a
 * cut: [content] is shown while [visible], and for a moment after. Its state starts fresh each
 * time the dialog opens. Inside another [LingerDialog] it does nothing (the outer one rules).
 */
@Composable
fun LingerDialog(visible: Boolean, content: @Composable () -> Unit) {
    if (LocalDialogExit.current !== NoDialogExit) {
        if (visible) content()
        return
    }
    var present by remember { mutableStateOf(visible) }
    var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible) { if (visible) present = true }
    if (visible || present) {
        key(round) {
            val exit = remember(visible) { DialogExit(!visible) { present = false; round++ } }
            CompositionLocalProvider(LocalDialogExit provides exit) { content() }
        }
    }
}

/**
 * A Material alert dialog that keeps the cursor's handle in place: drawn in a full-screen window of
 * its own that doesn't move for the keyboard (see the comment inside).
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
    /** A dialog that deletes or clears something: red icon, red main button (see [DialogConfirmButton]). */
    danger: Boolean = false,
    /** The icon is a picture of its own (a feed's icon): drawn as it is, not put on a cookie. */
    plainIcon: Boolean = false,
    /**
     * The text part scrolls when it doesn't fit (a tall dialog, or the keyboard up), with the same
     * edge fade as a sheet, for every dialog. So a dialog's content must not scroll by itself without
     * a height limit: a second scroll inside would crash. Off only for a content that must not scroll.
     */
    scrollContent: Boolean = true,
) {
    // Every dialog gets a window of its own that covers the screen and doesn't move for the
    // keyboard; the dialog moves above the keyboard inside it, so the cursor's handle stays under
    // the cursor (a normal dialog window moves, and the handle was left behind). Drawn in the
    // app's window instead (the host, as before), the keyboard's slide re-laid out the page
    // behind the dialog on every frame, and it showed under bottom sheets.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                // Back: the scaffold's predictive back when it reaches this window's dispatcher
                // (it's added later, so it goes first); the window's own dismiss otherwise.
                dismissOnBackPress = properties.dismissOnBackPress,
                dismissOnClickOutside = false, // and its scrim
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        // The scaffold draws its own scrim; no second dimming from the window.
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        DialogScaffold(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            properties = properties,
            danger = danger,
            plainIcon = plainIcon,
            scrollContent = scrollContent,
        )
    }
}

/** The tallest keyboard seen so far (pixels), so a dialog can make room for it before it is up. */
private object KeyboardMemory {
    var px = 0
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    danger: Boolean,
    plainIcon: Boolean,
    scrollContent: Boolean,
) = DangerDialog(danger) {
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
    // Closing (see LingerDialog): fade out where it stands; the keyboard leaving doesn't move it.
    val exit = LocalDialogExit.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val imeNow = androidx.compose.runtime.rememberUpdatedState(imeBottom)
    // A dialog with a text field makes room for the keyboard as soon as the field has focus: it
    // glides once to where it will stand with the keyboard up (the keyboard's height as last seen)
    // instead of chasing the keyboard frame by frame, which looked like it jumped.
    var fieldFocused by remember { mutableStateOf(false) }
    // The keyboard's height is kept between launches too: the first dialog after a fresh start used to
    // guess (300 dp), glide there and then move again when the real keyboard was taller.
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("brief_keyboard", 0)
    if (KeyboardMemory.px == 0) KeyboardMemory.px = prefs.getInt("px", 0)
    SideEffect {
        if (imeBottom > KeyboardMemory.px) {
            KeyboardMemory.px = imeBottom
            prefs.edit().putInt("px", imeBottom).apply()
        }
    }
    val reserve = if (KeyboardMemory.px > 0) KeyboardMemory.px else with(density) { 380.dp.roundToPx() }
    val bottomTarget = if (fieldFocused) maxOf(reserve, imeBottom, navBottom) else maxOf(imeBottom, navBottom)
    val bottomPx by androidx.compose.animation.core.animateIntAsState(
        bottomTarget,
        androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 380f),
        label = "dialogBottom",
    )
    var frozenBottom by remember { mutableStateOf<Int?>(null) }
    SideEffect {
        // Freeze where the dialog stands now, not where the keyboard is as it starts to leave (it would drop).
        if (exit.closing && frozenBottom == null) frozenBottom = bottomPx
        if (!exit.closing) frozenBottom = null
    }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(exit.closing) {
        if (exit.closing) {
            // The dialog leaves WITH its keyboard: it stays where it stands, and fades as far as the
            // keyboard has slid away, so the keyboard is never left on screen alone over the page
            // (that was the "flash": the dialog faded in 160 ms, the keyboard took longer to go).
            val startIme = imeNow.value
            val keyboardUp = startIme > 40
            focus.clearFocus()
            keyboard?.hide()
            if (keyboardUp) {
                kotlinx.coroutines.withTimeoutOrNull(700) {
                    androidx.compose.runtime.snapshotFlow { imeNow.value }
                        .first { v ->
                            leave.snapTo(minOf(leave.value, (v.toFloat() / startIme).coerceIn(0f, 1f)))
                            v <= 0
                        }
                }
            }
            leave.animateTo(0f, tween(if (keyboardUp) 90 else 160))
            exit.onExited()
        } else if (leave.value < 1f) {
            leave.animateTo(1f, tween(120))
        }
    }
    // The dim fades in with the dialog (and out with it, through [leave]).
    val dimIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) { dimIn.animateTo(1f, tween(220)) }
    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = leave.value }) {
        // The scrim, over the whole screen (bars included); tapping it closes the dialog.
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * dimIn.value * (1f - 0.4f * p)))
                    .pointerInput(properties.dismissOnClickOutside) {
                        detectTapGestures {
                            if (properties.dismissOnClickOutside) onDismissRequest()
                        }
                    }
        )
        // Centred in the space the bars and the keyboard leave.
        Box(
            modifier =
                Modifier.fillMaxSize().then(
                    frozenBottom?.let { frozen ->
                        Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                )
                            )
                            .padding(bottom = with(density) { frozen.toDp() })
                    } ?: Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        )
                        .padding(bottom = with(density) { bottomPx.toDp() })
                ),
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
                                val scale = (1f - 0.1f * p) * (0.94f + 0.06f * leave.value)
                                scaleX = scale
                                scaleY = scale
                                translationX = (if (fromLeft) 1f else -1f) * 8.dp.toPx() * p
                            }
                            .onFocusChanged { if (it.hasFocus) fieldFocused = true }
                            // Taps on the dialog itself don't reach the scrim.
                            .pointerInput(Unit) { detectTapGestures { } },
                    // The Banner corner and the card colour, as Home's hero and groups.
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Corner.Banner),
                    // The sheet's base (surfaceContainerLow); what sits on it is surfaceContainerHigh, as in a sheet.
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    CompositionLocalProvider(LocalQuietColor provides MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        // The icon and the title share one line: the icon on a cookie (red for a dialog that
                        // deletes), or a picture of its own, then the title beside it.
                        if (icon != null || title != null) {
                            Row(
                                modifier = Modifier.padding(bottom = 16.dp).align(Alignment.Start),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                if (icon != null && plainIcon) {
                                    icon()
                                } else icon?.let {
                                    val scheme = MaterialTheme.colorScheme
                                    CompositionLocalProvider(
                                        LocalContentColor provides
                                            (if (danger) scheme.onErrorContainer else scheme.onPrimaryContainer)
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier.size(48.dp)
                                                    .clip(MaterialShapes.Cookie9Sided.toShape())
                                                    .background(if (danger) scheme.errorContainer else scheme.primaryContainer),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            it()
                                        }
                                    }
                                }
                                title?.let {
                                    CompositionLocalProvider(
                                        LocalContentColor provides AlertDialogDefaults.titleContentColor
                                    ) {
                                        ProvideTextStyle(
                                            MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                        ) {
                                            Box(modifier = Modifier.weight(1f, fill = false)) { it() }
                                        }
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
                                                .then(
                                                    if (scrollContent) {
                                                        // Fades at the edge where there is more to scroll to, as in a sheet.
                                                        val textScroll = rememberScrollState()
                                                        Modifier.sheetScrollFade(textScroll).verticalScroll(textScroll)
                                                    }
                                                    else Modifier
                                                )
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
}
