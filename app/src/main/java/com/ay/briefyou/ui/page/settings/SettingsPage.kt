package com.ay.briefyou.ui.page.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding // Brief You
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome // Brief You
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ay.briefyou.R
import com.ay.briefyou.infrastructure.preference.LocalNewVersionNumber
import com.ay.briefyou.infrastructure.preference.LocalSkipVersionNumber
import com.ay.briefyou.infrastructure.preference.toDisplayName
import com.ay.briefyou.ui.component.base.Banner
import com.ay.briefyou.ui.component.base.DisplayText
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.component.base.RYScaffold
import com.ay.briefyou.ui.ext.getCurrentVersion
import com.ay.briefyou.ui.page.settings.tips.UpdateDialog
import com.ay.briefyou.ui.page.settings.tips.UpdateViewModel
import com.ay.briefyou.ui.theme.palette.onLight
import java.util.Locale

@Composable
fun SettingsPage(
    updateViewModel: UpdateViewModel = hiltViewModel(),
    onBack: () -> Unit,
    navigateToAccounts: () -> Unit,
    navigateToColorAndStyle: () -> Unit,
    navigateToInteraction: () -> Unit,
    navigateToLanguages: () -> Unit,
    navigateToAiModel: () -> Unit, // Brief You
    navigateToFilters: () -> Unit,
    navigateToTroubleshooting: () -> Unit,
    navigateToTipsAndSupport: () -> Unit,
) {
    val context = LocalContext.current
    val newVersion = LocalNewVersionNumber.current
    val skipVersion = LocalSkipVersionNumber.current
    val currentVersion by remember { mutableStateOf(context.getCurrentVersion()) }

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
                    DisplayText(text = stringResource(R.string.settings), desc = "")
                }
                item {
                    Box {
                        if (newVersion.whetherNeedUpdate(currentVersion, skipVersion)) {
                            Banner(
                                modifier = Modifier.zIndex(1f),
                                title = stringResource(R.string.get_new_updates),
                                desc = stringResource(
                                    R.string.get_new_updates_desc,
                                    newVersion.toString(),
                                ),
                                icon = Icons.Outlined.Lightbulb,
                                action = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.close),
                                    )
                                },
                            ) {
                                updateViewModel.showDialog()
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        // Banner(
                        //     title = stringResource(R.string.in_coding),
                        //     desc = stringResource(R.string.coming_soon),
                        //     icon = Icons.Outlined.Lightbulb,
                        // )
                    }
                }
                // Brief You: three sections, each one joined card (was one long column).
                item { // Brief You
                    com.ay.briefyou.ui.component.base.Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = stringResource(R.string.settings_section_reading)) // Brief You
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.color_and_style),
                        desc = stringResource(R.string.color_and_style_desc),
                        icon = Icons.Outlined.Palette,
                        onClick = navigateToColorAndStyle
                    )
                    // Brief You: the same card
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.interaction),
                        desc = stringResource(R.string.interaction_desc),
                        icon = Icons.Outlined.TouchApp,
                        onClick = navigateToInteraction
                    )
                    // Brief You: no Languages entry: the app ships in English only (smaller app).
                    } // Brief You
                    Spacer(modifier = Modifier.height(16.dp)) // Brief You
                    com.ay.briefyou.ui.component.base.Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = stringResource(R.string.settings_section_brief)) // Brief You
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.ai_settings),
                        desc = stringResource(R.string.ai_settings_desc),
                        icon = Icons.Outlined.AutoAwesome,
                        onClick = navigateToAiModel
                    )
                    // Brief You: the same card
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.filters_settings),
                        desc = stringResource(R.string.filters_settings_desc),
                        icon = Icons.Outlined.FilterAlt,
                        onClick = navigateToFilters
                    )
                    // Brief You: the same card
                    com.ay.briefyou.brief.stats.ReadingStatsSettingsItem()
                    } // Brief You
                    Spacer(modifier = Modifier.height(16.dp)) // Brief You
                    com.ay.briefyou.ui.component.base.Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = stringResource(R.string.settings_section_app)) // Brief You
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.accounts),
                        desc = stringResource(R.string.accounts_desc),
                        icon = Icons.Outlined.AccountCircle,
                        onClick = navigateToAccounts
                    )
                    // Brief You: the same card
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.troubleshooting),
                        desc = stringResource(R.string.troubleshooting_desc),
                        icon = Icons.Outlined.BugReport,
                        onClick = navigateToTroubleshooting
                    )
                    // Brief You: the same card
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.tips_and_support),
                        desc = stringResource(R.string.tips_and_support_desc),
                        icon = Icons.Outlined.TipsAndUpdates,
                        onClick = navigateToTipsAndSupport
                    )
                    } // Brief You
                } // Brief You
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    UpdateDialog()
}
