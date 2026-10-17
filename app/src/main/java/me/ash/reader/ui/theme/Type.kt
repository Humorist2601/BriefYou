@file:OptIn(ExperimentalTextApi::class)

package me.ash.reader.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import java.text.Bidi
import me.ash.reader.R

// TODO: Rename file to Typography.kt and add @Stable

private val LabelSmallEmphasizedFont = FontFamily.SansSerif
private val LabelSmallEmphasizedLineHeight = 16.0.sp
private val LabelSmallEmphasizedSize = 11.sp
private val LabelSmallEmphasizedTracking = 0.5.sp
private val LabelSmallEmphasizedWeight = FontWeight.Bold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val SystemTypography =
    Typography(
        bodySmallEmphasized =
            TextStyle.Default.copy(
                fontFamily = LabelSmallEmphasizedFont,
                fontSize = LabelSmallEmphasizedSize,
                fontWeight = LabelSmallEmphasizedWeight,
                letterSpacing = LabelSmallEmphasizedTracking,
                lineHeight = LabelSmallEmphasizedLineHeight,
            )
    )

internal fun TextStyle.applyTextDirection(textDirection: TextDirection = TextDirection.Content) =
    this.copy(textDirection = textDirection)

/** Google Sans Flex's roundness axis: 0 is square-cornered, 100 is fully rounded. */
private fun roundness(value: Float) = FontVariation.Setting("ROND", value)

/** Google Sans Flex at the given [round]ness, with regular, medium, semibold and bold, upright and italic. */
private fun googleSansFlex(round: Float): FontFamily {
    fun font(weight: Int, italic: Boolean = false) =
        Font(
            R.font.google_sans_flex,
            weight = FontWeight(weight),
            style = if (italic) FontStyle.Italic else FontStyle.Normal,
            variationSettings =
                if (italic) {
                    FontVariation.Settings(
                        roundness(round),
                        FontVariation.weight(weight),
                        FontVariation.italic(.5f),
                    )
                } else {
                    FontVariation.Settings(roundness(round), FontVariation.weight(weight))
                },
        )
    return FontFamily(
        font(400),
        font(500),
        font(600),
        font(700),
        font(400, italic = true),
        font(500, italic = true),
        font(600, italic = true),
        font(700, italic = true),
    )
}

/** The app's font: Google Sans Flex Rounded, the bundled variable font fully rounded. */
val GoogleSansFontFamily = googleSansFlex(round = 100f)

/**
 * The display font's roundness (see [GoogleSansWideFontFamily]): half-rounded, a little crisper
 * than the app's fully rounded font.
 */
private const val DISPLAY_ROUNDNESS = 50f

/**
 * Google Sans Flex for reading articles: like the display font (its half-rounded letters) but at
 * the normal width and normal weights, which are easier to read in long text.
 */
val GoogleSansReadingFontFamily = googleSansFlex(round = DISPLAY_ROUNDNESS)

/**
 * The wide display font for a few short, big titles (Flow, Feeds and Settings headings, Triage),
 * with the same settings as PixelPlayer's large headings: Google Sans Flex at the widest its width
 * axis goes (151; 100 is normal), weight 636 and half-rounded. Text asking for SemiBold gets it.
 */
val GoogleSansWideFontFamily =
    FontFamily(
        Font(
            R.font.google_sans_flex,
            weight = FontWeight.W600,
            variationSettings =
                FontVariation.Settings(
                    roundness(DISPLAY_ROUNDNESS),
                    FontVariation.weight(636),
                    FontVariation.width(151f),
                ),
        )
    )

/**
 * Material 3 Expressive gives titles and headings more weight while body text stays regular:
 * display, headline and large titles go from 400 to 500, smaller titles and labels from 500 to 600.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun Typography.emphasizeHeadings(): Typography =
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.W500),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.W500),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.W500),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.W500),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.W500),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.W500),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.W500),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.W600),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.W600),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.W600),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.W600),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.W600),
    )

/**
 * Resolve the text to Rtl if the text requires BiDirectional
 *
 * @see [android.view.View.TEXT_DIRECTION_ANY_RTL]
 * @see [Bidi.requiresBidi]
 */
fun TextStyle.applyTextDirection(requiresBidi: Boolean) =
    this.applyTextDirection(
        textDirection = if (requiresBidi) TextDirection.Rtl else TextDirection.Ltr
    )

fun TextStyle.applyFontFamily(fontFamily: FontFamily) = this.merge(fontFamily = fontFamily)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun Typography.applyTextDirection() =
    this.copy(
        displayLarge = displayLarge.applyTextDirection(),
        displayMedium = displayMedium.applyTextDirection(),
        displaySmall = displaySmall.applyTextDirection(),
        headlineLarge = headlineLarge.applyTextDirection(),
        headlineMedium = headlineMedium.applyTextDirection(),
        headlineSmall = headlineSmall.applyTextDirection(),
        titleLarge = titleLarge.applyTextDirection(),
        titleMedium = titleMedium.applyTextDirection(),
        titleSmall = titleSmall.applyTextDirection(),
        bodyLarge = bodyLarge.applyTextDirection(),
        bodyMedium = bodyMedium.applyTextDirection(),
        bodySmall = bodySmall.applyTextDirection(),
        labelLarge = labelLarge.applyTextDirection(),
        labelMedium = labelMedium.applyTextDirection(),
        labelSmall = labelSmall.applyTextDirection(),
        bodyLargeEmphasized = bodyLargeEmphasized.applyTextDirection(),
        bodyMediumEmphasized = bodyMediumEmphasized.applyTextDirection(),
        bodySmallEmphasized = bodySmallEmphasized.applyTextDirection(),
        displayLargeEmphasized = displayLargeEmphasized.applyTextDirection(),
        displayMediumEmphasized = displayMediumEmphasized.applyTextDirection(),
        displaySmallEmphasized = displaySmallEmphasized.applyTextDirection(),
        headlineLargeEmphasized = headlineLargeEmphasized.applyTextDirection(),
        headlineMediumEmphasized = headlineMediumEmphasized.applyTextDirection(),
        headlineSmallEmphasized = headlineSmallEmphasized.applyTextDirection(),
        titleLargeEmphasized = titleLargeEmphasized.applyTextDirection(),
        titleMediumEmphasized = titleMediumEmphasized.applyTextDirection(),
        titleSmallEmphasized = titleSmallEmphasized.applyTextDirection(),
        labelLargeEmphasized = labelLargeEmphasized.applyTextDirection(),
        labelMediumEmphasized = labelMediumEmphasized.applyTextDirection(),
        labelSmallEmphasized = labelSmallEmphasized.applyTextDirection(),
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun Typography.applyFontFamily(fontFamily: FontFamily) =
    this.copy(
        displayLarge = displayLarge.applyFontFamily(fontFamily),
        displayMedium = displayMedium.applyFontFamily(fontFamily),
        displaySmall = displaySmall.applyFontFamily(fontFamily),
        headlineLarge = headlineLarge.applyFontFamily(fontFamily),
        headlineMedium = headlineMedium.applyFontFamily(fontFamily),
        headlineSmall = headlineSmall.applyFontFamily(fontFamily),
        titleLarge = titleLarge.applyFontFamily(fontFamily),
        titleMedium = titleMedium.applyFontFamily(fontFamily),
        titleSmall = titleSmall.applyFontFamily(fontFamily),
        bodyLarge = bodyLarge.applyFontFamily(fontFamily),
        bodyMedium = bodyMedium.applyFontFamily(fontFamily),
        bodySmall = bodySmall.applyFontFamily(fontFamily),
        labelLarge = labelLarge.applyFontFamily(fontFamily),
        labelMedium = labelMedium.applyFontFamily(fontFamily),
        labelSmall = labelSmall.applyFontFamily(fontFamily),
        bodyLargeEmphasized = bodyLargeEmphasized.applyFontFamily(fontFamily),
        bodyMediumEmphasized = bodyMediumEmphasized.applyFontFamily(fontFamily),
        bodySmallEmphasized = bodySmallEmphasized.applyFontFamily(fontFamily),
        displayLargeEmphasized = displayLargeEmphasized.applyFontFamily(fontFamily),
        displayMediumEmphasized = displayMediumEmphasized.applyFontFamily(fontFamily),
        displaySmallEmphasized = displaySmallEmphasized.applyFontFamily(fontFamily),
        headlineLargeEmphasized = headlineLargeEmphasized.applyFontFamily(fontFamily),
        headlineMediumEmphasized = headlineMediumEmphasized.applyFontFamily(fontFamily),
        headlineSmallEmphasized = headlineSmallEmphasized.applyFontFamily(fontFamily),
        titleLargeEmphasized = titleLargeEmphasized.applyFontFamily(fontFamily),
        titleMediumEmphasized = titleMediumEmphasized.applyFontFamily(fontFamily),
        titleSmallEmphasized = titleSmallEmphasized.applyFontFamily(fontFamily),
        labelLargeEmphasized = labelLargeEmphasized.applyFontFamily(fontFamily),
        labelMediumEmphasized = labelMediumEmphasized.applyFontFamily(fontFamily),
        labelSmallEmphasized = labelSmallEmphasized.applyFontFamily(fontFamily),
    )


// Brief You: Literata, Google's serif designed for long reading on screens (SIL Open Font License).
private fun literata(weight: Int, italic: Boolean) =
    Font(
        if (italic) R.font.literata_italic else R.font.literata,
        weight = FontWeight(weight),
        style = if (italic) FontStyle.Italic else FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

val LiterataFontFamily =
    FontFamily(
        literata(400, false),
        literata(500, false),
        literata(600, false),
        literata(700, false),
        literata(400, true),
        literata(500, true),
        literata(600, true),
        literata(700, true),
    )
