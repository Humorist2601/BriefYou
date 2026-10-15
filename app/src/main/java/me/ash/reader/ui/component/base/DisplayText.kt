package me.ash.reader.ui.component.base

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ash.reader.ui.interaction.alphaIndicationClickable
import me.ash.reader.ui.theme.GoogleSansWideFontFamily

@Composable
fun DisplayText(
    modifier: Modifier = Modifier,
    text: String,
    desc: String,
    onTextClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .alphaIndicationClickable(enabled = onTextClick != null) { onTextClick?.invoke() }
                .padding(start = 24.dp, top = 48.dp, end = 24.dp, bottom = 24.dp)
    ) {
        // Wide, expressive heading. Wide letters take room, so long text (an account name, a
        // translation) shrinks a little instead of being cut off.
        val style =
            MaterialTheme.typography.displaySmall.copy(
                fontFamily = GoogleSansWideFontFamily,
                fontWeight = FontWeight.SemiBold,
            )
        val color = MaterialTheme.colorScheme.onSurface
        BasicText(
            modifier = Modifier,
            text = text,
            style = style,
            color = { color },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize =
                TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = style.fontSize),
        )
        RYExtensibleVisibility(visible = desc.isNotEmpty()) {
            Text(
                text = desc,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
