package me.ash.reader.ui.page.settings.color.reading

import kotlin.math.roundToInt // Brief You: the number sheet
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalReadingImageHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalReadingImageMaximize
import me.ash.reader.infrastructure.preference.LocalReadingImageRoundedCorners
import me.ash.reader.infrastructure.preference.LocalReadingRenderer
import me.ash.reader.infrastructure.preference.LocalReadingTheme
import me.ash.reader.infrastructure.preference.ReadingImageHorizontalPaddingPreference
import me.ash.reader.infrastructure.preference.ReadingImageRoundedCornersPreference
import me.ash.reader.infrastructure.preference.ReadingRendererPreference
import me.ash.reader.infrastructure.preference.ReadingThemePreference
import me.ash.reader.infrastructure.preference.not
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun ReadingImagePage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val readingTheme = LocalReadingTheme.current
    val roundedCorners = LocalReadingImageRoundedCorners.current
    val horizontalPadding = LocalReadingImageHorizontalPadding.current
    val maximize = LocalReadingImageMaximize.current
    val renderer = LocalReadingRenderer.current

    var roundedCornersDialogVisible by remember { mutableStateOf(false) }
    var horizontalPaddingDialogVisible by remember { mutableStateOf(false) }

    var roundedCornersValue: Int? by remember { mutableStateOf(roundedCorners) }
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
                    DisplayText(text = stringResource(R.string.images), desc = "")
                }

                // Preview
                item {
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(horizontal = 24.dp)
//                            .clip(RoundedCornerShape(24.dp))
//                            .background(
//                                MaterialTheme.colorScheme.inverseOnSurface
//                                        onLight MaterialTheme.colorScheme.surface.copy(0.7f)
//                            )
//                            .clickable { },
//                        horizontalArrangement = Arrangement.Center,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        RYAsyncImage(
//                            modifier = Modifier
//                                .fillMaxSize()
//                                .padding(24.dp)
//                                .padding(imageHorizontalPadding().dp)
//                                .clip(imageShape()),
//                            data = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1yZWxhdGVkfDJ8fHxlbnwwfHx8fA%3D%3D&auto=format&fit=crop&w=800&q=60",
//                            contentDescription = stringResource(R.string.images),
//                            contentScale = ContentScale.Inside,
//                        )
//                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }


                // Images
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.images)
                    )
                    me.ash.reader.brief.ui.SettingGroup { // Brief You: the section as one joined card
                    SettingItem(
                        title = stringResource(R.string.rounded_corners),
                        desc = "${roundedCorners}dp",
                        onClick = { roundedCornersDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.horizontal_padding), // Brief You: works in the web view too
                        desc = "${horizontalPadding}dp",
                        onClick = { horizontalPaddingDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.maximize), // Brief You: works in the web view too
                        onClick = {
                            (!maximize).put(context, scope)
                            ReadingThemePreference.Custom.put(context, scope)
                        },
                    ) {
                        RYSwitch(activated = maximize.value) {
                            (!maximize).put(context, scope)
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

    // Brief You: number settings are a slider sheet (NumberSheet)
    if (roundedCornersDialogVisible) me.ash.reader.brief.ui.NumberSheet(
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

    if (horizontalPaddingDialogVisible) me.ash.reader.brief.ui.NumberSheet( // Brief You
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
}
