package com.ay.briefyou.brief.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.brief.readaloud.openTtsSettings
import com.ay.briefyou.brief.ui.SettingGroup
import com.ay.briefyou.ui.component.base.RadioDialog
import com.ay.briefyou.ui.component.base.RadioDialogOption
import com.ay.briefyou.ui.component.base.Subtitle
import com.ay.briefyou.ui.page.settings.SettingItem

/**
 * Usage → Audio and video: when an episode or video counts as read, and read aloud's voice
 * (Android's speech settings). Put in Usage after the Reading page section. Brief You.
 */
@Composable
fun PlayerSettings() {
    val context = LocalContext.current
    var share by remember { mutableFloatStateOf(BriefPlayer.markReadAt(context)) }
    var dialog by remember { mutableStateOf(false) }
    val shares = listOf(0.25f, 0.5f, 0.75f, 0.9f, 1f)
    val label: @Composable (Float) -> String = {
        if (it >= 1f) stringResource(R.string.player_mark_read_finished)
        else stringResource(R.string.player_mark_read_heard, (it * 100).toInt())
    }

    Subtitle(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = stringResource(R.string.player_settings_title),
    )
    SettingGroup {
        SettingItem(
            title = stringResource(R.string.player_mark_read),
            desc = label(share),
            onClick = { dialog = true },
        ) {}
        SettingItem(
            title = stringResource(R.string.player_read_aloud_voice),
            desc = stringResource(R.string.player_read_aloud_voice_desc),
            onClick = { context.openTtsSettings() },
        ) {}
    }
    Spacer(modifier = Modifier.height(24.dp))

    RadioDialog(
        visible = dialog,
        title = stringResource(R.string.player_mark_read),
        icon = com.ay.briefyou.brief.ui.SettingIcons.PlayerMarkRead,
        options =
            shares.map {
                RadioDialogOption(text = label(it), selected = it == share) {
                    share = it
                    BriefPlayer.setMarkReadAt(context, it)
                }
            },
    ) {
        dialog = false
    }
}
