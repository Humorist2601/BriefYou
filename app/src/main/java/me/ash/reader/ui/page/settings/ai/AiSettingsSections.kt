package me.ash.reader.ui.page.settings.ai

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.text.DateFormat
import java.util.Calendar
import me.ash.reader.R
import me.ash.reader.infrastructure.ai.MorningBriefing
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.page.settings.SettingItem

private const val SHERPA_TTS_PAGE = "https://f-droid.org/packages/org.woheller69.ttsengine/"
private val RATES = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f)

@Composable
fun VoiceSection(viewModel: AiSettingsViewModel) {
    val context = LocalContext.current
    val engine = viewModel.engine.collectAsStateValue()
    val rate = viewModel.rate.collectAsStateValue()
    var showEngines by rememberSaveable { mutableStateOf(false) }
    var showRates by rememberSaveable { mutableStateOf(false) }
    val engines = viewModel.engines()
    val engineLabel =
        engines.firstOrNull { it.first == engine }?.second
            ?: stringResource(R.string.voice_engine_default)
    val testSentence = stringResource(R.string.voice_test_sentence)

    Subtitle(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = stringResource(R.string.voice_settings),
    )
    SettingItem(
        title = stringResource(R.string.voice_engine),
        desc = engineLabel,
        icon = Icons.Outlined.RecordVoiceOver,
        onClick = { showEngines = true },
    ) {}
    SettingItem(
        title = stringResource(R.string.voice_rate),
        desc = "${rate}×",
        icon = Icons.Outlined.Speed,
        onClick = { showRates = true },
    ) {}
    SettingItem(
        title = stringResource(R.string.voice_download),
        desc = stringResource(R.string.voice_download_desc),
        icon = Icons.Outlined.CloudDownload,
        onClick = {
            val intent =
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                    engine?.let { setPackage(it) }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // Fall back to the system text-to-speech settings.
                runCatching {
                    context.startActivity(
                        Intent("com.android.settings.TTS_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        },
    ) {}
    SettingItem(
        title = stringResource(R.string.voice_sherpa),
        desc = stringResource(R.string.voice_sherpa_desc),
        icon = Icons.Outlined.GraphicEq,
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, SHERPA_TTS_PAGE.toUri()))
        },
    ) {}
    SettingItem(
        title = stringResource(R.string.voice_test),
        icon = Icons.Outlined.PlayCircle,
        onClick = { viewModel.testVoice(testSentence) },
    ) {}
    Spacer(modifier = Modifier.height(24.dp))

    RadioDialog(
        visible = showEngines,
        title = stringResource(R.string.voice_engine),
        options =
            listOf(
                RadioDialogOption(
                    text = stringResource(R.string.voice_engine_default),
                    selected = false,
                ) {
                    viewModel.setEngine(null)
                    showEngines = false
                }
            ) +
                engines.map { (pkg, label) ->
                    RadioDialogOption(text = label, selected = pkg == engine) {
                        viewModel.setEngine(pkg)
                        showEngines = false
                    }
                },
        onDismissRequest = { showEngines = false },
    )
    RadioDialog(
        visible = showRates,
        title = stringResource(R.string.voice_rate),
        options =
            RATES.map { r ->
                RadioDialogOption(text = "${r}×", selected = r == rate) {
                    viewModel.setRate(r)
                    showRates = false
                }
            },
        onDismissRequest = { showRates = false },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefingSection(viewModel: AiSettingsViewModel) {
    val context = LocalContext.current
    val settings = viewModel.briefing.settings.collectAsStateValue()
    val groups = viewModel.groups.collectAsStateValue()
    var showTime by rememberSaveable { mutableStateOf(false) }
    var showSource by rememberSaveable { mutableStateOf(false) }
    val timeText =
        DateFormat.getTimeInstance(DateFormat.SHORT)
            .format(
                Calendar.getInstance()
                    .apply {
                        set(Calendar.HOUR_OF_DAY, settings.hour)
                        set(Calendar.MINUTE, settings.minute)
                    }
                    .time
            )

    Subtitle(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = stringResource(R.string.briefing_settings),
    )
    SettingItem(
        title = stringResource(R.string.briefing_settings_enabled),
        desc = stringResource(R.string.briefing_settings_enabled_desc),
        icon = Icons.Outlined.WbSunny,
        onClick = { viewModel.updateBriefing(settings.copy(enabled = !settings.enabled)) },
    ) {
        RYSwitch(activated = settings.enabled) {
            viewModel.updateBriefing(settings.copy(enabled = !settings.enabled))
        }
    }
    SettingItem(
        enabled = settings.enabled,
        title = stringResource(R.string.briefing_settings_time),
        desc = timeText,
        icon = Icons.Outlined.Schedule,
        onClick = { showTime = true },
    ) {}
    SettingItem(
        title = stringResource(R.string.briefing_settings_source),
        desc = settings.groupName ?: stringResource(R.string.briefing_settings_all),
        icon = Icons.Outlined.Folder,
        onClick = { showSource = true },
    ) {}
    SettingItem(
        title = stringResource(R.string.briefing_settings_open),
        icon = Icons.Outlined.PlayCircle,
        onClick = { context.startActivity(MorningBriefing.openBriefingIntent(context)) },
    ) {}
    Spacer(modifier = Modifier.height(24.dp))

    if (showTime) {
        val state =
            rememberTimePickerState(initialHour = settings.hour, initialMinute = settings.minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.briefing_settings_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateBriefing(
                            settings.copy(hour = state.hour, minute = state.minute)
                        )
                        showTime = false
                    }
                ) {
                    Text(stringResource(R.string.ai_settings_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text(stringResource(R.string.ai_settings_cancel))
                }
            },
        )
    }
    RadioDialog(
        visible = showSource,
        title = stringResource(R.string.briefing_settings_source),
        options =
            listOf(
                RadioDialogOption(
                    text = stringResource(R.string.briefing_settings_all),
                    selected = settings.groupId == null,
                ) {
                    viewModel.updateBriefing(settings.copy(groupId = null, groupName = null))
                    showSource = false
                }
            ) +
                groups.map { g ->
                    RadioDialogOption(text = g.name, selected = g.id == settings.groupId) {
                        viewModel.updateBriefing(settings.copy(groupId = g.id, groupName = g.name))
                        showSource = false
                    }
                },
        onDismissRequest = { showSource = false },
    )
}
