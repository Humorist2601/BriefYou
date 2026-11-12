package me.ash.reader.brief.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.ash.reader.R
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.page.settings.SettingItem

private fun OfflineCopies.Mode.label(): Int =
    when (this) {
        OfflineCopies.Mode.Off -> R.string.offline_mode_off
        OfflineCopies.Mode.Mobile -> R.string.offline_mode_mobile
        OfflineCopies.Mode.Wifi -> R.string.offline_mode_wifi
        OfflineCopies.Mode.Both -> R.string.offline_mode_both
    }

/**
 * Account → Syncing: when starred articles' offline copies download: off, on mobile data only, on
 * Wi-Fi only (the default), or on both.
 */
@Composable
fun OfflineSettingsItem() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(OfflineCopies.mode(context)) }
    var dialog by remember { mutableStateOf(false) }
    SettingItem(
        title = stringResource(R.string.offline_copies_title),
        desc = stringResource(mode.label()),
        onClick = { dialog = true },
    ) {}
    RadioDialog(
        visible = dialog,
        title = stringResource(R.string.offline_copies_title),
        options =
            OfflineCopies.Mode.values().map {
                RadioDialogOption(text = stringResource(it.label()), selected = it == mode) {
                    mode = it
                    OfflineCopies.setMode(context, it)
                }
            },
    ) {
        dialog = false
    }
}
