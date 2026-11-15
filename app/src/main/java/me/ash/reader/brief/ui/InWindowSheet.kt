package me.ash.reader.brief.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scrim
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.ash.reader.R

/**
 * A modal bottom sheet drawn inside the app's own window, for a screen that fills the window
 * (Triage). It looks and behaves like Material's [androidx.compose.material3.ModalBottomSheet]:
 * the same sheet, dimmed background (a tap on it closes the sheet), drag to close, back gesture
 * and motion. The difference: Material's opens a separate window for it, and making and tearing
 * down that window on the main thread made 30–60 ms frames each time the sheet opened or closed
 * (the stutter log showed it when switching place in Triage). Brief You.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InWindowModalSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        scope.launch { sheetState.hide() }
            .invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
    }
    val scrimShown by remember { derivedStateOf { sheetState.targetValue != SheetValue.Hidden } }
    val scrimAlpha by
        animateFloatAsState(
            targetValue = if (scrimShown) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "sheet scrim",
        )
    Box(Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        Scrim(
            contentDescription = stringResource(R.string.close),
            onClick = dismiss,
            alpha = { scrimAlpha },
            color = BottomSheetDefaults.ScrimColor,
        )
        BottomSheet(
            modifier = Modifier.align(Alignment.TopCenter),
            state = sheetState,
            onDismissRequest = onDismissRequest,
            contentWindowInsets = { BottomSheetDefaults.modalWindowInsets },
            // As Material's modal sheet (the standalone one defaults to a tinted surface).
            tonalElevation = 0.dp,
            content = content,
        )
    }
    if (sheetState.hasExpandedState) {
        LaunchedEffect(sheetState) { sheetState.show() }
    }
}
