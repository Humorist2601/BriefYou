package com.ay.briefyou.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ay.briefyou.ui.ext.DataStoreKey
import com.ay.briefyou.ui.ext.DataStoreKey.Companion.readingTextLetterSpacing
import com.ay.briefyou.ui.ext.dataStore
import com.ay.briefyou.ui.ext.put

val LocalReadingTextLetterSpacing = compositionLocalOf { ReadingTextLetterSpacingPreference.default }

object ReadingTextLetterSpacingPreference {

    const val default = 0.5F

    fun put(context: Context, scope: CoroutineScope, value: Float) {
        scope.launch {
            context.dataStore.put(DataStoreKey.readingTextLetterSpacing, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[DataStoreKey.keys[readingTextLetterSpacing]?.key as Preferences.Key<Float>] ?: default
}
