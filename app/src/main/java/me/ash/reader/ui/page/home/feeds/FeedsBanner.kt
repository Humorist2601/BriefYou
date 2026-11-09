package me.ash.reader.ui.page.home.feeds

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.ui.component.base.Banner
import me.ash.reader.brief.ui.pressNudge // Brief You

@Composable
fun FeedsBanner(
    modifier: Modifier = Modifier,
    filter: Filter,
    desc: String? = null,
    onClick: () -> Unit = {},
) {
    Banner(
        modifier = modifier,
        title = filter.toName(),
        desc = desc,
        icon = filter.iconOutline,
        action = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.pressNudge(), // Brief You: nudges toward the page while held
            )
        },
        onClick = onClick
    )
}