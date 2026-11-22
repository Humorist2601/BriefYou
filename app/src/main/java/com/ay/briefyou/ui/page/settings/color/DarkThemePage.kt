package com.ay.briefyou.ui.page.settings.color

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.infrastructure.preference.DarkThemePreference
import com.ay.briefyou.infrastructure.preference.LocalAmoledDarkTheme
import com.ay.briefyou.infrastructure.preference.LocalDarkTheme
import com.ay.briefyou.infrastructure.preference.not
import com.ay.briefyou.ui.component.base.*
import com.ay.briefyou.ui.page.settings.SettingItem
import com.ay.briefyou.ui.theme.palette.onLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkThemePage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = LocalDarkTheme.current
    val amoledDarkTheme = LocalAmoledDarkTheme.current
    val scope = rememberCoroutineScope()

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
                    DisplayText(text = stringResource(R.string.dark_theme), desc = "")
                }
                item {
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You: the choices as one joined card
                    DarkThemePreference.values.map {
                        SettingItem(
                            title = it.toDesc(context),
                            onClick = {
                                it.put(context, scope)
                            },
                        ) {
                            RadioButton(selected = it == darkTheme, onClick = {
                                it.put(context, scope)
                            })
                        }
                    }
                    } // Brief You
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.other),
                    )
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You: the section as one joined card
                    SettingItem(
                        title = stringResource(R.string.amoled_dark_theme),
                        onClick = {
                            (!amoledDarkTheme).put(context, scope)
                        },
                    ) {
                        BYSwitch(activated = amoledDarkTheme.value) {
                            (!amoledDarkTheme).put(context, scope)
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
}
