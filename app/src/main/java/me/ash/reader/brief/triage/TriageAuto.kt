package me.ash.reader.brief.triage

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.brief.ui.SettingGroup
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.page.settings.SettingItem

/**
 * Triage's auto-advance settings (Usage): what happens to a card when its time is up (skip, mark
 * as read or save) and how long that takes. The delay is the one the play button's long press
 * changes, kept in the same place.
 */
internal object TriageAuto {
    private const val PREFS = "triage"
    private const val KEY_SECONDS = "auto_seconds"
    private const val KEY_ACTION = "auto_action"
    val DELAYS = listOf(3, 5, 8, 10, 15, 30)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun seconds(context: Context): Int = prefs(context).getInt(KEY_SECONDS, 5)

    fun setSeconds(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_SECONDS, value).apply()
    }

    /** Skip (the default), mark as read or save. */
    fun action(context: Context): TriageAction =
        when (prefs(context).getInt(KEY_ACTION, 0)) {
            1 -> TriageAction.Read
            2 -> TriageAction.Save
            else -> TriageAction.Skip
        }

    fun setAction(context: Context, action: TriageAction) {
        val v = when (action) {
            TriageAction.Read -> 1
            TriageAction.Save -> 2
            else -> 0
        }
        prefs(context).edit().putInt(KEY_ACTION, v).apply()
    }

    val ACTIONS = listOf(TriageAction.Skip, TriageAction.Read, TriageAction.Save)

    fun actionLabel(context: Context, action: TriageAction): String =
        context.getString(
            when (action) {
                TriageAction.Read -> R.string.triage_auto_action_read
                TriageAction.Save -> R.string.triage_auto_action_save
                else -> R.string.triage_auto_action_skip
            }
        )
}

/** The Triage section of the Usage page. */
@Composable
fun TriageSettings() {
    val context = LocalContext.current
    var action by remember { mutableStateOf(TriageAuto.action(context)) }
    var seconds by remember { mutableIntStateOf(TriageAuto.seconds(context)) }
    var actionDialog by remember { mutableStateOf(false) }
    var delayDialog by remember { mutableStateOf(false) }

    Subtitle(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = stringResource(R.string.triage_settings_title),
    )
    SettingGroup {
        SettingItem(
            title = stringResource(R.string.triage_auto_action),
            desc = TriageAuto.actionLabel(context, action),
            onClick = { actionDialog = true },
        ) {}
        SettingItem(
            title = stringResource(R.string.triage_auto_delay_setting),
            desc = stringResource(R.string.triage_auto_seconds, seconds),
            onClick = { delayDialog = true },
        ) {}
    }
    Spacer(modifier = Modifier.height(24.dp))

    RadioDialog(
        visible = actionDialog,
        title = stringResource(R.string.triage_auto_action),
        options =
            TriageAuto.ACTIONS.map {
                RadioDialogOption(text = TriageAuto.actionLabel(context, it), selected = it == action) {
                    action = it
                    TriageAuto.setAction(context, it)
                }
            },
    ) {
        actionDialog = false
    }
    RadioDialog(
        visible = delayDialog,
        title = stringResource(R.string.triage_auto_delay_setting),
        options =
            TriageAuto.DELAYS.map {
                RadioDialogOption(text = stringResource(R.string.triage_auto_seconds, it), selected = it == seconds) {
                    seconds = it
                    TriageAuto.setSeconds(context, it)
                }
            },
    ) {
        delayDialog = false
    }
}
