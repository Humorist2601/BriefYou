package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.readingFonts
import me.ash.reader.ui.ext.ExternalFonts
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put
import me.ash.reader.ui.ext.restart
import me.ash.reader.ui.theme.GoogleSansReadingFontFamily
import me.ash.reader.ui.theme.LiterataFontFamily

val LocalReadingFonts =
    compositionLocalOf<ReadingFontsPreference> { ReadingFontsPreference.default }

sealed class ReadingFontsPreference(val value: Int) : Preference() {
    object GoogleSans : ReadingFontsPreference(6)

    object System : ReadingFontsPreference(0)

    object External : ReadingFontsPreference(5)

    object Literata : ReadingFontsPreference(7)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKey.readingFonts, value)
            if (this@ReadingFontsPreference == External) {
                context.restart()
            }
        }
    }

    fun toDesc(context: Context): String =
        when (this) {
            GoogleSans -> context.getString(R.string.google_sans)
            System -> context.getString(R.string.system_default)
            External -> context.getString(R.string.external_fonts)
            Literata -> "Literata"
        }

    fun asFontFamily(context: Context): FontFamily =
        when (this) {
            GoogleSans -> GoogleSansReadingFontFamily
            Literata -> LiterataFontFamily
            System -> FontFamily.Default
            External ->
                ExternalFonts.loadReadingTypography(context).displayLarge.fontFamily
                    ?: FontFamily.Default
        }

    companion object {

        val default = GoogleSans
        // Brief You: only Google Sans Flex, Literata, the system font and your own font.
        val values = listOf(GoogleSans, Literata, System, External)

        fun fromPreferences(preferences: Preferences): ReadingFontsPreference =
            when (preferences[DataStoreKey.keys[readingFonts]?.key as Preferences.Key<Int>]) {
                0 -> System
                // Serif, Sans-Serif, Monospace and Cursive were removed: back to Google Sans Flex.
                1, 2, 3, 4 -> GoogleSans
                5 -> External
                6 -> GoogleSans
                7 -> Literata
                else -> default
            }
    }
}
