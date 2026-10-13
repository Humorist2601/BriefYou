/**
 * Copyright (C) 2021 Kyant0
 *
 * @link https://github.com/Kyant0/MusicYou
 * @author Kyant0
 * @modifier Ashinch
 */

package me.ash.reader.ui.page.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import android.view.SoundEffectConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val settingShapes =
    listOf(
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.SoftBurst,
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectableSettingGroupItem(
    modifier: Modifier = Modifier,
    enable: Boolean = true,
    selected: Boolean = false,
    title: String,
    desc: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val view = LocalView.current

    Surface(
        modifier = modifier
            .clickable {
                view.playSoundEffect(SoundEffectConstants.CLICK)
                onClick()
            }
            .alpha(if (enable) 1f else 0.5f),
        color = Color.Unspecified,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(8.dp, 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                // Material 3 Expressive: each group icon sits on its own shape and tonal color.
                val index = (title.hashCode() and Int.MAX_VALUE)
                val shape = settingShapes[index % settingShapes.size].toShape()
                val (container, content) =
                    with(MaterialTheme.colorScheme) {
                        when {
                            selected -> surfaceVariant to onSurfaceVariant
                            index % 3 == 0 -> primaryContainer to onPrimaryContainer
                            index % 3 == 1 -> secondaryContainer to onSecondaryContainer
                            else -> tertiaryContainer to onTertiaryContainer
                        }
                    }
                Box(
                    modifier =
                        Modifier.padding(start = 4.dp, end = 16.dp)
                            .size(40.dp)
                            .clip(shape)
                            .background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = it,
                        contentDescription = title,
                        modifier = Modifier.size(22.dp),
                        tint = content,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = if (desc == null) 2 else 1,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                )
                desc?.let {
                    Text(
                        text = it,
                        color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
