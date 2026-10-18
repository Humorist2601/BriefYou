package me.ash.reader.brief.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ash.reader.R

private val FabOrigin = TransformOrigin(.5f, 1f)

/**
 * The floating pill at the bottom of the Unread list: AI gist and Triage. It follows the app's
 * accent colors (primary container), is fully rounded, and slides away while you scroll down.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlowActionsFab(
    visible: Boolean,
    onGist: () -> Unit,
    onTriage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter =
            scaleIn(
                transformOrigin = FabOrigin,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ) + fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        exit =
            scaleOut(
                transformOrigin = FabOrigin,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ) + fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val haptic = LocalHapticFeedback.current
        val content = MaterialTheme.colorScheme.onPrimaryContainer
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = content,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FabSegment(
                    icon = Icons.Outlined.AutoAwesome,
                    label = stringResource(R.string.flow_fab_gist),
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onGist()
                }
                VerticalDivider(modifier = Modifier.height(24.dp), color = content.copy(alpha = 0.25f))
                FabSegment(icon = Icons.Rounded.Style, label = stringResource(R.string.triage)) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onTriage()
                }
            }
        }
    }
}

@Composable
private fun FabSegment(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}
