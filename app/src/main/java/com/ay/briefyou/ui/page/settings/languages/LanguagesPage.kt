package com.ay.briefyou.ui.page.settings.languages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.infrastructure.preference.LanguagesPreference
import com.ay.briefyou.infrastructure.preference.LocalLanguages
import com.ay.briefyou.infrastructure.preference.OpenLinkPreference
import com.ay.briefyou.ui.component.base.Banner
import com.ay.briefyou.ui.component.base.DisplayText
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.component.base.RYScaffold
import com.ay.briefyou.ui.ext.openURL
import com.ay.briefyou.ui.page.settings.SettingItem
import com.ay.briefyou.ui.theme.palette.onLight
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagesPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val currentLocale = Locale.getDefault()

    val languages = LocalLanguages.current.run {
        if (toLocale() == currentLocale) this
        else LanguagesPreference.default
    }

    val scope = rememberCoroutineScope()

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
                item(key = languages.value) {
                    DisplayText(text = stringResource(R.string.languages), desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                    Banner(
                        title = stringResource(R.string.help_translate),
                        icon = Icons.Outlined.Lightbulb,
                        action = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.go_to),
                            )
                        },
                    ) {
                        context.openURL(
                            context.getString(R.string.translatable_url),
                            OpenLinkPreference.AutoPreferCustomTabs
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You: the choices as one joined card
                    LanguagesPreference.values.map {
                        SettingItem(
                            title = it.toDesc(),
                            onClick = {
                                it.put(context, scope)
                            },
                        ) {
                            RadioButton(selected = it == languages, onClick = {
                                it.put(context, scope)
                            })
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
