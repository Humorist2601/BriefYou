package me.ash.reader.ui.page.settings.color.reading

import kotlin.math.roundToInt // Brief You: the number sheet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalReadingTextLetterSpacing
import me.ash.reader.infrastructure.preference.LocalReadingTextAlign
import me.ash.reader.infrastructure.preference.LocalReadingTextBold
import me.ash.reader.infrastructure.preference.LocalReadingTextFontSize
import me.ash.reader.infrastructure.preference.LocalReadingTextHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.LocalReadingTheme
import me.ash.reader.infrastructure.preference.ReadingTextLetterSpacingPreference
import me.ash.reader.infrastructure.preference.ReadingTextAlignPreference
import me.ash.reader.infrastructure.preference.ReadingTextFontSizePreference
import me.ash.reader.infrastructure.preference.ReadingTextHorizontalPaddingPreference
import me.ash.reader.infrastructure.preference.ReadingTextLineHeightPreference
import me.ash.reader.infrastructure.preference.ReadingTextLineHeightPreference.coerceToRange
import me.ash.reader.infrastructure.preference.ReadingThemePreference
import me.ash.reader.infrastructure.preference.not
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun ReadingTextPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val readingTheme = LocalReadingTheme.current
    val fontSize = LocalReadingTextFontSize.current
    val lineHeight = LocalReadingTextLineHeight.current
    val letterSpacing = LocalReadingTextLetterSpacing.current
    val horizontalPadding = LocalReadingTextHorizontalPadding.current
    val align = LocalReadingTextAlign.current
    val bold = LocalReadingTextBold.current

    var fontSizeDialogVisible by remember { mutableStateOf(false) }
    var lineHeightDialogVisible by remember { mutableStateOf(false) }
    var letterSpacingDialogVisible by remember { mutableStateOf(false) }
    var horizontalPaddingDialogVisible by remember { mutableStateOf(false) }
    var alignDialogVisible by remember { mutableStateOf(false) }

    var fontSizeValue: Int? by remember { mutableStateOf(fontSize) }
    var letterSpacingValue: String? by remember { mutableStateOf(letterSpacing.toString()) }
    var lineHeightMultipleValue: String by remember(lineHeight) { mutableStateOf(lineHeight.toString()) }
    var horizontalPaddingValue: Int? by remember { mutableStateOf(horizontalPadding) }

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.text), desc = "")
                }

                // Preview
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                MaterialTheme.colorScheme.inverseOnSurface
                                        onLight MaterialTheme.colorScheme.surface.copy(0.7f)
                            )
                            .clickable { },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TitleAndTextPreview()
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Text
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.text)
                    )
                    me.ash.reader.brief.ui.SettingGroup { // Brief You: the section as one joined card
                    SettingItem(
                        title = stringResource(R.string.font_size),
                        desc = "${fontSize}sp",
                        onClick = { fontSizeDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.bold),
                        onClick = {
                            (!bold).put(context, scope)
                            ReadingThemePreference.Custom.put(context, scope)
                        },
                    ) {
                        RYSwitch(activated = bold.value) {
                            (!bold).put(context, scope)
                            ReadingThemePreference.Custom.put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.letter_spacing),
                        desc = "${letterSpacing}sp",
                        onClick = { letterSpacingDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.line_height_multiple),
                        desc = lineHeightMultipleValue,
                        onClick = { lineHeightDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.horizontal_padding),
                        desc = "${horizontalPadding}dp",
                        onClick = { horizontalPaddingDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.alignment),
                        desc = align.toDesc(context),
                        onClick = { alignDialogVisible = true },
                    ) {}
                    } // Brief You
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    // Brief You: number settings are a slider sheet (NumberSheet)
    if (fontSizeDialogVisible) me.ash.reader.brief.ui.NumberSheet(
        title = stringResource(R.string.font_size),
        value = fontSize.toFloat(),
        range = 12f..28f,
        step = 1f,
        default = ReadingTextFontSizePreference.default.toFloat(),
        suffix = "sp",
        onSave = {
            ReadingTextFontSizePreference.put(context, scope, it.roundToInt())
            ReadingThemePreference.Custom.put(context, scope)
        },
        onDismiss = { fontSizeDialogVisible = false },
    )

    if (lineHeightDialogVisible) me.ash.reader.brief.ui.NumberSheet( // Brief You
        title = stringResource(R.string.line_height_multiple),
        value = lineHeight,
        range = 0.8f..2f,
        step = 0.1f,
        default = ReadingTextLineHeightPreference.default,
        decimals = 1,
        onSave = {
            ReadingTextLineHeightPreference.put(context, scope, it.coerceToRange())
            ReadingThemePreference.Custom.put(context, scope)
        },
        onDismiss = { lineHeightDialogVisible = false },
    )

    if (letterSpacingDialogVisible) me.ash.reader.brief.ui.NumberSheet( // Brief You
        title = stringResource(R.string.letter_spacing),
        value = letterSpacing,
        range = 0f..2f,
        step = 0.1f,
        default = ReadingTextLetterSpacingPreference.default,
        decimals = 1,
        suffix = "sp",
        onSave = {
            ReadingTextLetterSpacingPreference.put(context, scope, it)
            ReadingThemePreference.Custom.put(context, scope)
        },
        onDismiss = { letterSpacingDialogVisible = false },
    )

    if (horizontalPaddingDialogVisible) me.ash.reader.brief.ui.NumberSheet( // Brief You
        title = stringResource(R.string.horizontal_padding),
        value = horizontalPadding.toFloat(),
        range = 0f..64f,
        step = 4f,
        default = ReadingTextHorizontalPaddingPreference.default.toFloat(),
        suffix = "dp",
        onSave = {
            ReadingTextHorizontalPaddingPreference.put(context, scope, it.roundToInt())
            ReadingThemePreference.Custom.put(context, scope)
        },
        onDismiss = { horizontalPaddingDialogVisible = false },
    )

    RadioDialog(
        visible = alignDialogVisible,
        title = stringResource(R.string.alignment),
        options = ReadingTextAlignPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == align,
            ) {
                it.put(context, scope)
                ReadingThemePreference.Custom.put(context, scope)
            }
        }
    ) {
        alignDialogVisible = false
    }
}
