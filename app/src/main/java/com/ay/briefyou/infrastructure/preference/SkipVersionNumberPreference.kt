@file:Suppress("UNCHECKED_CAST")

package com.ay.briefyou.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.ay.briefyou.domain.model.general.Version
import com.ay.briefyou.domain.model.general.toVersion
import com.ay.briefyou.ui.ext.DataStoreKey
import com.ay.briefyou.ui.ext.DataStoreKey.Companion.skipVersionNumber
import com.ay.briefyou.ui.ext.dataStore
import com.ay.briefyou.ui.ext.put

val LocalSkipVersionNumber = compositionLocalOf { SkipVersionNumberPreference.default }

object SkipVersionNumberPreference {

    val default = Version()

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.skipVersionNumber, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[DataStoreKey.keys[skipVersionNumber]?.key as Preferences.Key<String>].toVersion()
}
