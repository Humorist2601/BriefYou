@file:Suppress("UNCHECKED_CAST")

package com.ay.briefyou.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.style.TextAlign
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ay.briefyou.R
import com.ay.briefyou.ui.ext.DataStoreKey
import com.ay.briefyou.ui.ext.DataStoreKey.Companion.readingSubheadAlign
import com.ay.briefyou.ui.ext.dataStore
import com.ay.briefyou.ui.ext.put

val LocalReadingSubheadAlign =
    compositionLocalOf<ReadingSubheadAlignPreference> { ReadingSubheadAlignPreference.default }

sealed class ReadingSubheadAlignPreference(val value: Int) : Preference() {
    object Start : ReadingSubheadAlignPreference(0)
    object End : ReadingSubheadAlignPreference(1)
    object Center : ReadingSubheadAlignPreference(2)
    object Justify : ReadingSubheadAlignPreference(3)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKey.readingSubheadAlign,
                value
            )
        }
    }

    fun toDesc(context: Context): String =
        when (this) {
            Start -> context.getString(R.string.align_start)
            End -> context.getString(R.string.align_end)
            Center -> context.getString(R.string.center_text)
            Justify -> context.getString(R.string.justify)
        }

    fun toTextAlign(): TextAlign =
        when (this) {
            Start -> TextAlign.Start
            End -> TextAlign.End
            Center -> TextAlign.Center
            Justify -> TextAlign.Justify
        }

    fun toTextAlignCSS(): String =
        when (this) {
            Start -> "left"
            End -> "right"
            Center -> "center"
            Justify -> "justify"
        }

    companion object {

        val default = Start
        val values = listOf(Start, End, Center, Justify)

        fun fromPreferences(preferences: Preferences): ReadingSubheadAlignPreference =
            when (preferences[DataStoreKey.keys[readingSubheadAlign]?.key as Preferences.Key<Int>]) {
                0 -> Start
                1 -> End
                2 -> Center
                3 -> Justify
                else -> default
            }
    }
}
