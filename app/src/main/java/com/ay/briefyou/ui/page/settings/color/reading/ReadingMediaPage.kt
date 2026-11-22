package com.ay.briefyou.ui.page.settings.color.reading

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.infrastructure.preference.*
import com.ay.briefyou.ui.component.base.DisplayText
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.component.base.BYScaffold
import com.ay.briefyou.ui.component.base.BYSwitch
import com.ay.briefyou.ui.component.base.Subtitle
import com.ay.briefyou.ui.page.settings.SettingItem
import com.ay.briefyou.ui.theme.palette.onLight
import kotlin.math.roundToInt

/** Brief You: the pictures' and the videos' look on one page (they were two pages). */
@Composable
fun ReadingMediaPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val roundedCorners = LocalReadingImageRoundedCorners.current
    val horizontalPadding = LocalReadingImageHorizontalPadding.current
    val maximize = LocalReadingImageMaximize.current
    val videoCorners = LocalReadingVideoRoundedCorners.current
    val videoPadding = LocalReadingVideoHorizontalPadding.current
    val videoMaximize = LocalReadingVideoMaximize.current
    val sameAsImages = stringResource(R.string.same_as_images)

    var roundedCornersDialogVisible by remember { mutableStateOf(false) }
    var horizontalPaddingDialogVisible by remember { mutableStateOf(false) }
    var videoCornersDialogVisible by remember { mutableStateOf(false) }
    var videoPaddingDialogVisible by remember { mutableStateOf(false) }

    BYScaffold(
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
                item { DisplayText(text = stringResource(R.string.images_and_videos), desc = "") }

                // Images
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.images)
                    )
                    com.ay.briefyou.brief.ui.SettingGroup {
                        SettingItem(
                            title = stringResource(R.string.rounded_corners),
                            desc = "${roundedCorners}dp",
                            onClick = { roundedCornersDialogVisible = true },
                        ) {}
                        SettingItem(
                            title = stringResource(R.string.horizontal_padding),
                            desc = "${horizontalPadding}dp",
                            onClick = { horizontalPaddingDialogVisible = true },
                        ) {}
                        SettingItem(
                            title = stringResource(R.string.maximize),
                            onClick = {
                                (!maximize).put(context, scope)
                                ReadingThemePreference.Custom.put(context, scope)
                            },
                        ) {
                            BYSwitch(activated = maximize.value) {
                                (!maximize).put(context, scope)
                                ReadingThemePreference.Custom.put(context, scope)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Videos
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.videos)
                    )
                    com.ay.briefyou.brief.ui.SettingGroup {
                        SettingItem(
                            title = stringResource(R.string.rounded_corners),
                            desc = if (videoCorners == SAME_AS_IMAGES) sameAsImages else "${videoCorners}dp",
                            onClick = { videoCornersDialogVisible = true },
                        ) {}
                        SettingItem(
                            title = stringResource(R.string.horizontal_padding),
                            desc = if (videoPadding == SAME_AS_IMAGES) sameAsImages else "${videoPadding}dp",
                            onClick = { videoPaddingDialogVisible = true },
                        ) {}
                        SettingItem(
                            title = stringResource(R.string.maximize),
                            onClick = {
                                ReadingVideoMaximizePreference.put(context, scope, !videoMaximize)
                                ReadingThemePreference.Custom.put(context, scope)
                            },
                        ) {
                            BYSwitch(activated = videoMaximize) {
                                ReadingVideoMaximizePreference.put(context, scope, !videoMaximize)
                                ReadingThemePreference.Custom.put(context, scope)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    if (roundedCornersDialogVisible) com.ay.briefyou.brief.ui.NumberSheet(
        title = stringResource(R.string.rounded_corners),
        value = roundedCorners.toFloat(),
        range = 0f..48f,
        step = 4f,
        default = ReadingImageRoundedCornersPreference.default.toFloat(),
        suffix = "dp",
        onSave = {
            ReadingImageRoundedCornersPreference.put(context, scope, it.roundToInt())
            ReadingThemePreference.Custom.put(context, scope)
        },
        onDismiss = { roundedCornersDialogVisible = false },
    )
    if (horizontalPaddingDialogVisible) com.ay.briefyou.brief.ui.NumberSheet(
        title = stringResource(R.string.horizontal_padding),
        value = horizontalPadding.toFloat(),
        range = 0f..64f,
        step = 4f,
        default = ReadingImageHorizontalPaddingPreference.default.toFloat(),
        suffix = "dp",
        onSave = {
            ReadingImageHorizontalPaddingPreference.put(context, scope, it.roundToInt())
            ReadingThemePreference.Custom.put(context, scope)
        },
        onDismiss = { horizontalPaddingDialogVisible = false },
    )

    // Empty means "same as images": Reset goes back to that.
    if (videoCornersDialogVisible) com.ay.briefyou.brief.ui.NumberSheet(
        title = stringResource(R.string.rounded_corners),
        value = (if (videoCorners == SAME_AS_IMAGES) roundedCorners else videoCorners).toFloat(),
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
        onDismiss = { videoCornersDialogVisible = false },
    )
    if (videoPaddingDialogVisible) com.ay.briefyou.brief.ui.NumberSheet(
        title = stringResource(R.string.horizontal_padding),
        value = (if (videoPadding == SAME_AS_IMAGES) horizontalPadding else videoPadding).toFloat(),
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
        onDismiss = { videoPaddingDialogVisible = false },
    )
}
