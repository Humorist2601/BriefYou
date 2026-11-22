package com.ay.briefyou.ui.page.settings.color.reading

import com.ay.briefyou.infrastructure.preference.isOn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Segment
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Title
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.infrastructure.preference.LocalPullToSwitchArticle
import com.ay.briefyou.infrastructure.preference.LocalReadingAutoHideToolbar
import com.ay.briefyou.infrastructure.preference.LocalReadingBoldCharacters
import com.ay.briefyou.infrastructure.preference.LocalReadingFonts
import com.ay.briefyou.infrastructure.preference.LocalReadingTheme
import com.ay.briefyou.infrastructure.preference.ReadingFontsPreference
import com.ay.briefyou.infrastructure.preference.ReadingThemePreference
import com.ay.briefyou.infrastructure.preference.not
import com.ay.briefyou.ui.component.ReadingThemePrev
import com.ay.briefyou.ui.component.base.DisplayText
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.component.base.BYScaffold
import com.ay.briefyou.ui.component.base.BYSwitch
import com.ay.briefyou.ui.component.base.RadioDialog
import com.ay.briefyou.ui.component.base.RadioDialogOption
import com.ay.briefyou.ui.component.base.Subtitle
import com.ay.briefyou.ui.ext.ExternalFonts
import com.ay.briefyou.ui.ext.MimeType
import com.ay.briefyou.ui.ext.showToast
import com.ay.briefyou.ui.page.settings.SettingItem
import com.ay.briefyou.ui.theme.palette.onLight

@Composable
fun ReadingStylePage(
    onBack: () -> Unit,
    navigateToReadingBoldCharacters: () -> Unit,
    navigateToReadingPageTitle: () -> Unit,
    navigateToReadingPageText: () -> Unit,
    navigateToReadingPageMedia: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val readingTheme = LocalReadingTheme.current
    val fonts = LocalReadingFonts.current
    val autoHideToolbar = LocalReadingAutoHideToolbar.current
    val boldCharacters = LocalReadingBoldCharacters.current
    val keepScreenOn = com.ay.briefyou.infrastructure.preference.BriefSwitch.KeepScreenOn.isOn()

    var fontsDialogVisible by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                ExternalFonts(
                    context,
                    it,
                    ExternalFonts.FontType.ReadingFont
                ).copyToInternalStorage()
                ReadingFontsPreference.External.put(context, scope)
            } ?: context.showToast("Cannot get activity result with launcher")
        }

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
                item {
                    DisplayText(text = stringResource(R.string.reading_page), desc = "")
                }

                // Preview
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.width(24.dp))
                        ReadingThemePreference.values.map {
                            if (readingTheme == ReadingThemePreference.Custom || it != ReadingThemePreference.Custom) {
                                ReadingThemePrev(selected = readingTheme, theme = it) {
                                    it.put(context, scope)
                                    it.applyTheme(context, scope)
                                }
                            } else {
                                Spacer(modifier = Modifier.width(150.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Spacer(modifier = Modifier.width((24 - 8).dp))
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

                // General
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.general)
                    )
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You: the section as one joined card
                    SettingItem(
                        title = stringResource(R.string.bold_characters),
                        separatedActions = true,
                        onClick = navigateToReadingBoldCharacters,
                    ) {
                        BYSwitch(activated = boldCharacters.value) {
                            (!boldCharacters).put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.reading_fonts),
                        desc = fonts.toDesc(context),
                        onClick = { fontsDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.auto_hide_toolbars),
                        onClick = {
                            (!autoHideToolbar).put(context, scope)
                        },
                    ) {
                        BYSwitch(activated = autoHideToolbar.value) {
                            (!autoHideToolbar).put(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.keep_screen_on),
                        onClick = {
                            com.ay.briefyou.infrastructure.preference.BriefSwitch.KeepScreenOn.put(context, scope, !keepScreenOn)
                        },
                    ) {
                        BYSwitch(activated = keepScreenOn) {
                            com.ay.briefyou.infrastructure.preference.BriefSwitch.KeepScreenOn.put(context, scope, !keepScreenOn)
                        }
                    }
                    com.ay.briefyou.brief.reading.NewsletterSettingItem() // Brief You
                    // Brief You: "Rearrange buttons" is hidden until it works

                    } // Brief You

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Advanced
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.advanced)
                    )
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You: the section as one joined card
                    SettingItem(
                        title = stringResource(R.string.title),
                        desc = stringResource(R.string.title_desc),
                        icon = Icons.Rounded.Title,
                        onClick = navigateToReadingPageTitle,
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.text),
                        desc = stringResource(R.string.text_desc),
                        icon = Icons.AutoMirrored.Rounded.Segment,
                        onClick = navigateToReadingPageText,
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.images_and_videos),
                        desc = stringResource(R.string.images_and_videos_desc),
                        icon = Icons.Outlined.Image,
                        onClick = navigateToReadingPageMedia,
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

    RadioDialog(
        visible = fontsDialogVisible,
        title = stringResource(R.string.reading_fonts),
        options = ReadingFontsPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                style = TextStyle(fontFamily = it.asFontFamily(context)),
                selected = it == fonts,
            ) {
                if (it.value == ReadingFontsPreference.External.value) {
                    launcher.launch(arrayOf(MimeType.FONT))
                } else {
                    it.put(context, scope)
                }
            }
        }
    ) {
        fontsDialogVisible = false
    }
}
