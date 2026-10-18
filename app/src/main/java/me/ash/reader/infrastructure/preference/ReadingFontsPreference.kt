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
import me.ash.reader.brief.ui.GoogleSansReadingFontFamily // Brief You
import me.ash.reader.brief.ui.LiterataFontFamily // Brief You

val LocalReadingFonts =
    compositionLocalOf<ReadingFontsPreference> { ReadingFontsPreference.default }

sealed class ReadingFontsPreference(val value: Int) : Preference() {
    object GoogleSans : ReadingFontsPreference(6)

    object System : ReadingFontsPreference(0)

    object Serif : ReadingFontsPreference(1)

    object SansSerif : ReadingFontsPreference(2)

    object Monospace : ReadingFontsPreference(3)

    object Cursive : ReadingFontsPreference(4)

    object External : ReadingFontsPreference(5)

    // Brief You: Google's serif for long reading.
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
            Serif -> "Serif"
            SansSerif -> "Sans-Serif"
            Monospace -> "Monospace"
            Cursive -> "Cursive"
            External -> context.getString(R.string.external_fonts)
            Literata -> "Literata" // Brief You
        }

    fun asFontFamily(context: Context): FontFamily =
        when (this) {
            GoogleSans -> GoogleSansReadingFontFamily // Brief You: the half-rounded reading cut
            Literata -> LiterataFontFamily // Brief You
            System -> FontFamily.Default
            Serif -> FontFamily.Serif
            SansSerif -> FontFamily.SansSerif
            Monospace -> FontFamily.Monospace
            Cursive -> FontFamily.Cursive
            External ->
                ExternalFonts.loadReadingTypography(context).displayLarge.fontFamily
                    ?: FontFamily.Default
        }

    companion object {

        val default = GoogleSans
        // Brief You: offers Google Sans Flex, Literata, the system font and your own font.
        val values = listOf(GoogleSans, Literata, System, External)

        fun fromPreferences(preferences: Preferences): ReadingFontsPreference =
            when (preferences[DataStoreKey.keys[readingFonts]?.key as Preferences.Key<Int>]) {
                0 -> System
                1 -> Serif
                2 -> SansSerif
                3 -> Monospace
                4 -> Cursive
                5 -> External
                6 -> GoogleSans
                7 -> Literata // Brief You
                else -> default
            }
    }
}
