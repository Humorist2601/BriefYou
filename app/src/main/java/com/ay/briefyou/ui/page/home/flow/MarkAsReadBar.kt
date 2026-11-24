package com.ay.briefyou.ui.page.home.flow

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.domain.model.general.MarkAsReadConditions

/**
 * Mark as read, in the app's own style: a muted line, then one joined group of buttons (round on
 * the outside, small corners between, like the setting choices): 7 days, 3 days, 1 day and, filled
 * in the accent colour, Everything.
 */
@Composable
fun MarkAsReadBar(
    onItemClick: (MarkAsReadConditions) -> Unit = {},
) {
    val options =
        listOf(
            stringResource(R.string.mark_read_7_days) to MarkAsReadConditions.SevenDays,
            stringResource(R.string.mark_read_3_days) to MarkAsReadConditions.ThreeDays,
            stringResource(R.string.mark_read_1_day) to MarkAsReadConditions.OneDay,
            stringResource(R.string.mark_read_everything) to MarkAsReadConditions.All,
        )
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 12.dp)) {
        Text(
            text = stringResource(R.string.mark_read_older_than),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            options.forEachIndexed { index, (text, condition) ->
                val last = index == options.lastIndex
                val outer = 26.dp
                val inner = 9.dp
                MarkAsReadSegment(
                    modifier = Modifier.weight(if (last) 2.2f else 1.2f),
                    text = text,
                    shape =
                        RoundedCornerShape(
                            topStart = if (index == 0) outer else inner,
                            bottomStart = if (index == 0) outer else inner,
                            topEnd = if (last) outer else inner,
                            bottomEnd = if (last) outer else inner,
                        ),
                    container =
                        if (last) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    content =
                        if (last) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                ) {
                    onItemClick(condition)
                }
            }
        }
    }
}

@Composable
private fun MarkAsReadSegment(
    modifier: Modifier,
    text: String,
    shape: RoundedCornerShape,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Surface(
        modifier =
            modifier.height(52.dp).clip(shape).clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                view.playSoundEffect(SoundEffectConstants.CLICK)
                onClick()
            },
        shape = shape,
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
            )
        }
    }
}
