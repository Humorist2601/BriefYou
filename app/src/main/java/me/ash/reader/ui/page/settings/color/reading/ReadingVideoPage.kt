package me.ash.reader.ui.page.settings.color.reading

import kotlin.math.roundToInt // Brief You: the number sheet
import me.ash.reader.infrastructure.preference.LocalReadingImageRoundedCorners
import me.ash.reader.infrastructure.preference.LocalReadingImageHorizontalPadding
import me.ash.reader.infrastructure.preference.ReadingImageRoundedCornersPreference
import me.ash.reader.infrastructure.preference.ReadingImageHorizontalPaddingPreference
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import me.ash.reader.infrastructure.preference.LocalReadingVideoHorizontalPadding // Brief You: the imports for the Videos settings
import me.ash.reader.infrastructure.preference.LocalReadingVideoMaximize
import me.ash.reader.infrastructure.preference.LocalReadingVideoRoundedCorners
import me.ash.reader.infrastructure.preference.ReadingThemePreference
import me.ash.reader.infrastructure.preference.ReadingVideoHorizontalPaddingPreference
import me.ash.reader.infrastructure.preference.ReadingVideoMaximizePreference
import me.ash.reader.infrastructure.preference.ReadingVideoRoundedCornersPreference
import me.ash.reader.infrastructure.preference.SAME_AS_IMAGES
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.TextFieldDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun ReadingVideoPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val corners = LocalReadingVideoRoundedCorners.current // Brief You: the Videos settings
    val padding = LocalReadingVideoHorizontalPadding.current
    val maximize = LocalReadingVideoMaximize.current
    var cornersDialogVisible by remember { mutableStateOf(false) }
    var paddingDialogVisible by remember { mutableStateOf(false) }
    var cornersValue: Int? by remember { mutableStateOf(corners.takeIf { it != SAME_AS_IMAGES }) }
    var paddingValue: Int? by remember { mutableStateOf(padding.takeIf { it != SAME_AS_IMAGES }) }
    val sameAsImages = stringResource(R.string.same_as_images)

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
                    DisplayText(text = stringResource(R.string.videos), desc = "")
                }

                // Preview
                item {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                    }

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

                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Videos
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.videos)
                    )
                    me.ash.reader.brief.ui.SettingGroup { // Brief You: the section as one joined card
                    SettingItem(
                        title = stringResource(R.string.rounded_corners),
                        desc = if (corners == SAME_AS_IMAGES) sameAsImages else "${corners}dp", // Brief You
                        onClick = { cornersDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.horizontal_padding),
                        desc = if (padding == SAME_AS_IMAGES) sameAsImages else "${padding}dp",
                        onClick = { paddingDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.maximize),
                        onClick = {
                            ReadingVideoMaximizePreference.put(context, scope, !maximize)
                            ReadingThemePreference.Custom.put(context, scope)
                        },
                    ) {
                        RYSwitch(activated = maximize) {
                            ReadingVideoMaximizePreference.put(context, scope, !maximize)
                            ReadingThemePreference.Custom.put(context, scope)
                        }
                    }
                    } // Brief You
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    // Brief You: empty means "same as images".
    // Brief You: number settings are a slider sheet (NumberSheet); Reset goes back to "same as images"
    if (cornersDialogVisible) me.ash.reader.brief.ui.NumberSheet(
        title = stringResource(R.string.rounded_corners),
        value = (if (corners == SAME_AS_IMAGES) LocalReadingImageRoundedCorners.current else corners).toFloat(),
        range = 0f..48f,
        step = 4f,
        default = ReadingImageRoundedCornersPreference.default.toFloat(),
        suffix = "dp",
        resetLabel = sameAsImages.lowercase(),
        onReset = {
            ReadingVideoRoundedCornersPreference.put(context, scope, SAME_AS_IMAGES)
            ReadingThemePreference.Custom.put(context, scope)
        },
        onSave = {
            ReadingVideoRoundedCornersPreference.put(context, scope, it.roundToInt())
            ReadingThemePreference.Custom.put(context, scope)
        },
        onDismiss = { cornersDialogVisible = false },
    )
    if (paddingDialogVisible) me.ash.reader.brief.ui.NumberSheet( // Brief You
        title = stringResource(R.string.horizontal_padding),
        value = (if (padding == SAME_AS_IMAGES) LocalReadingImageHorizontalPadding.current else padding).toFloat(),
        range = 0f..64f,
        step = 4f,
        default = ReadingImageHorizontalPaddingPreference.default.toFloat(),
        suffix = "dp",
        resetLabel = sameAsImages.lowercase(),
        onReset = {
            ReadingVideoHorizontalPaddingPreference.put(context, scope, SAME_AS_IMAGES)
            ReadingThemePreference.Custom.put(context, scope)
        },
        onSave = {
            ReadingVideoHorizontalPaddingPreference.put(context, scope, it.roundToInt())
            ReadingThemePreference.Custom.put(context, scope)
        },
        onDismiss = { paddingDialogVisible = false },
    )
}
