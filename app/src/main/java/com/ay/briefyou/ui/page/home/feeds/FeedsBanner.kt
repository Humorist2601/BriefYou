package com.ay.briefyou.ui.page.home.feeds

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ay.briefyou.domain.model.general.Filter
import com.ay.briefyou.ui.component.base.Banner
import com.ay.briefyou.brief.ui.pressNudge // Brief You

@Composable
fun FeedsBanner(
    modifier: Modifier = Modifier,
    filter: Filter,
    desc: String? = null,
    onClick: () -> Unit = {},
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current // Brief You
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
        onClick = { // Brief You: a tap you can feel, like the app's other openers
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick) // Brief You
            onClick() // Brief You
        } // Brief You
    )
}