
package com.ay.briefyou.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ay.briefyou.ui.ext.dataStore
import com.ay.briefyou.ui.ext.put

/**
 * Brief You's on/off settings, kept together: each one is a boolean in the settings store (so it
 * is part of a backup), registered once in `DataStoreExt.kt` through [entries].
 */
enum class BriefSwitch(val key: String, val default: Boolean) {
    /** Opening an article marks it read (podcasts and videos are marked by the player instead). */
    MarkAsReadWhenOpened("markAsReadWhenOpened", true),

    /** The screen stays on while an article is open. */
    KeepScreenOn("keepScreenOn", false),

    /** Every vibration the app makes (a master switch above Android's own touch feedback). */
    Haptics("hapticFeedback", true),

    /** Reading stats are counted (and the recap is made). */
    CollectStats("collectReadingStats", true),

    /** The AI model is loaded when the app opens, so the first summary is quick. */
    PrepareAiModel("prepareAiModelAtStart", true);

    fun put(context: Context, scope: CoroutineScope, value: Boolean) {
        scope.launch { context.dataStore.put(key, value) }
    }

    companion object {
        val defaults: Map<BriefSwitch, Boolean> = entries.associateWith { it.default }

        fun fromPreferences(preferences: Preferences): Map<BriefSwitch, Boolean> =
            entries.associateWith { preferences[booleanPreferencesKey(it.key)] ?: it.default }
    }
}

val LocalBriefSwitches = compositionLocalOf { BriefSwitch.defaults }
/** Whether the switch is on, read where the app draws. */
@Composable
fun BriefSwitch.isOn(): Boolean = LocalBriefSwitches.current[this] ?: default

/** The same, read outside of the screens (a worker, a view model). */
fun Settings.isOn(switch: BriefSwitch): Boolean = switches[switch] ?: switch.default
