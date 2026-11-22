package com.ay.briefyou.ui.page.settings.color.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.infrastructure.preference.*
import com.ay.briefyou.ui.component.base.*
import com.ay.briefyou.ui.page.settings.SettingItem
import com.ay.briefyou.ui.theme.palette.onLight

@Composable
fun FeedsPageStylePage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val topBarTonalElevation = LocalFeedsTopBarTonalElevation.current
    val groupListExpand = LocalFeedsGroupListExpand.current
    val groupListTonalElevation = LocalFeedsGroupListTonalElevation.current

    val scope = rememberCoroutineScope()

    var topBarTonalElevationDialogVisible by remember { mutableStateOf(false) }
    var groupListTonalElevationDialogVisible by remember { mutableStateOf(false) }

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
                    DisplayText(text = stringResource(R.string.feeds_page), desc = "")
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
                        FeedsPagePreview(
                            topBarTonalElevation = topBarTonalElevation,
                            groupListExpand = groupListExpand,
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Top Bar
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.top_bar)
                    )
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You: the section as one joined card
                    SettingItem(
                        title = stringResource(R.string.tonal_elevation),
                        desc = "${topBarTonalElevation.value}dp",
                        onClick = {
                            topBarTonalElevationDialogVisible = true
                        },
                    ) {}
                    } // Brief You
//                    Tips(text = stringResource(R.string.tips_top_bar_tonal_elevation))
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Group List
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.group_list)
                    )
                    com.ay.briefyou.brief.ui.SettingGroup { // Brief You: the section as one joined card
                    SettingItem(
                        title = stringResource(R.string.always_expand),
                        onClick = {
                            (!groupListExpand).put(context, scope)
                        },
                    ) {
                        RYSwitch(activated = groupListExpand.value) {
                            (!groupListExpand).put(context, scope)
                        }
                    }
                    } // Brief You
/*                    SettingItem(
                        title = stringResource(R.string.tonal_elevation),
                        desc = "${groupListTonalElevation.value}dp",
                        onClick = {
                            groupListTonalElevationDialogVisible = true
                        },
                    ) {}
                    Tips(text = stringResource(R.string.tips_group_list_tonal_elevation))
                    Spacer(modifier = Modifier.height(24.dp))*/
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    RadioDialog(
        visible = topBarTonalElevationDialogVisible,
        title = stringResource(R.string.tonal_elevation),
        options = FeedsTopBarTonalElevationPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == topBarTonalElevation,
            ) {
                it.put(context, scope)
            }
        }
    ) {
        topBarTonalElevationDialogVisible = false
    }

/*    RadioDialog(
        visible = groupListTonalElevationDialogVisible,
        title = stringResource(R.string.tonal_elevation),
        options = FeedsGroupListTonalElevationPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == groupListTonalElevation,
            ) {
                it.put(context, scope)
            }
        }
    ) {
        groupListTonalElevationDialogVisible = false
    }*/
}
