package me.ash.reader.brief.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Whether an option sheet is open. [hide] slides it down first, like a swipe down does, instead of
 * it vanishing. Replaces Read You's Material 2 sheet state, with the same show / hide / isVisible.
 */
@Stable
class SheetSwitch {
    var isVisible by mutableStateOf(false)
        private set
    internal var closing by mutableStateOf(false)

    fun show() {
        closing = false
        isVisible = true
    }

    fun hide() {
        if (isVisible) closing = true
    }

    internal fun gone() {
        closing = false
        isVisible = false
    }
}

@Composable fun rememberSheetSwitch(): SheetSwitch = remember { SheetSwitch() }

/**
 * The app's bottom sheet (Material 3, like Triage's places, the gist and the summary) for a
 * [SheetSwitch]. Content gets 24 dp sides; dialogs opened from it should be drawn in their own
 * window (see [OptionSheetDialogs]) so they show above the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(switch: SheetSwitch, content: @Composable ColumnScope.() -> Unit) {
    if (!switch.isVisible) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(switch.closing) {
        if (switch.closing) {
            state.hide()
            switch.gone()
        }
    }
    ModalBottomSheet(onDismissRequest = { switch.gone() }, sheetState = state) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), content = content)
    }
}

/**
 * Dialogs opened from an [OptionSheet]: the sheet is its own window, so dialogs drawn in the app's
 * window would sit under it. Here they get their own window instead, above the sheet.
 */
@Composable
fun OptionSheetDialogs(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalDialogHost provides null, content = content)
}

/** A sheet's head: icon, title and a muted line under it, then the divider (the app's sheet design). */
@Composable
fun SheetHeader(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            title()
            subtitle?.invoke()
        }
    }
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(14.dp))
}

/** A small heading over a part of a sheet ("Reading", "Move to group"). */
@Composable
fun SheetSection(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/** One row of a sheet's joined list. */
class SheetRow(
    val icon: ImageVector,
    val title: String,
    /** A switch at the end, on or off; null for a plain row (an action). */
    val checked: Boolean? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/** Rows as one joined list: rounded big on the outside, small between rows, a small gap apart. */
@Composable
fun SheetRows(rows: List<SheetRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(Group.Gap)) {
        rows.forEachIndexed { index, row ->
            val tint = if (row.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            Surface(
                onClick = row.onClick,
                shape = groupShape(index, rows.size),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = tint,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        row.icon,
                        contentDescription = null,
                        tint = if (row.danger) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (row.checked != null) {
                        Spacer(Modifier.width(12.dp))
                        Box { me.ash.reader.ui.component.base.RYSwitch(activated = row.checked, onClick = row.onClick) }
                    }
                }
            }
        }
    }
}
