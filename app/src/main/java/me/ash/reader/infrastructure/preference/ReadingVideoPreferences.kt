package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

/**
 * Videos in the reading page (embedded players). Corners and padding start as [SAME_AS_IMAGES],
 * following the Images settings until you set them apart.
 */
const val SAME_AS_IMAGES = -1

val LocalReadingVideoRoundedCorners = compositionLocalOf { SAME_AS_IMAGES }
val LocalReadingVideoHorizontalPadding = compositionLocalOf { SAME_AS_IMAGES }
val LocalReadingVideoMaximize = compositionLocalOf { false }

object ReadingVideoRoundedCornersPreference {
    const val default = SAME_AS_IMAGES

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch { context.dataStore.put(DataStoreKey.readingVideoRoundedCorners, value) }
    }

    @Suppress("UNCHECKED_CAST")
    fun fromPreferences(preferences: Preferences): Int =
        preferences[DataStoreKey.keys[DataStoreKey.readingVideoRoundedCorners]?.key as Preferences.Key<Int>] ?: default
}

object ReadingVideoHorizontalPaddingPreference {
    const val default = SAME_AS_IMAGES

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch { context.dataStore.put(DataStoreKey.readingVideoHorizontalPadding, value) }
    }

    @Suppress("UNCHECKED_CAST")
    fun fromPreferences(preferences: Preferences): Int =
        preferences[DataStoreKey.keys[DataStoreKey.readingVideoHorizontalPadding]?.key as Preferences.Key<Int>] ?: default
}

object ReadingVideoMaximizePreference {
    const val default = false

    fun put(context: Context, scope: CoroutineScope, value: Boolean) {
        scope.launch { context.dataStore.put(DataStoreKey.readingVideoMaximize, value) }
    }

    @Suppress("UNCHECKED_CAST")
    fun fromPreferences(preferences: Preferences): Boolean =
        preferences[DataStoreKey.keys[DataStoreKey.readingVideoMaximize]?.key as Preferences.Key<Boolean>] ?: default
}
