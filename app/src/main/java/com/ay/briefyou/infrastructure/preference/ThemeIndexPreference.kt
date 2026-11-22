package com.ay.briefyou.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.ay.briefyou.ui.ext.DataStoreKey
import com.ay.briefyou.ui.ext.DataStoreKey.Companion.themeIndex
import com.ay.briefyou.ui.ext.dataStore
import com.ay.briefyou.ui.ext.put

val LocalThemeIndex =
    compositionLocalOf { ThemeIndexPreference.default }

object ThemeIndexPreference {

    const val default = 5

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.themeIndex, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[DataStoreKey.keys[themeIndex]?.key as Preferences.Key<Int>] ?: default
}
