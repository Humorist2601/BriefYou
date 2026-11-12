package me.ash.reader.brief.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.ash.reader.R
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.page.settings.SettingItem

/**
 * Account → Syncing: starred articles' offline copies download on Wi-Fi only (the default), or on
 * any connection.
 */
@Composable
fun OfflineSettingsItem() {
    val context = LocalContext.current
    var wifiOnly by remember { mutableStateOf(OfflineCopies.wifiOnly(context)) }
    val toggle = {
        wifiOnly = !wifiOnly
        OfflineCopies.setWifiOnly(context, wifiOnly)
    }
    SettingItem(title = stringResource(R.string.offline_copies_wifi), onClick = toggle) {
        RYSwitch(activated = wifiOnly) { toggle() }
    }
}
