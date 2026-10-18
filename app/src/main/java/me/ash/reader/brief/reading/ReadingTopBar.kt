package me.ash.reader.brief.reading

// Brief You's copy of Read You's reading-page top bar (ui/page/home/reading/TopBar.kt), which it
// rewrote: tonal buttons, the article title and time left once scrolled, and a wavy progress bar.
// Read You's original stays untouched; see .github/fork/copies.txt.

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import me.ash.reader.brief.ui.TonalCircleButton
import me.ash.reader.brief.ui.TopBarAction
import me.ash.reader.brief.ui.TopBarButtonGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalReadingPageTonalElevation
import me.ash.reader.infrastructure.preference.LocalSharedContent
import me.ash.reader.infrastructure.preference.ReadingPageTonalElevationPreference
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.page.adaptive.NavigationAction

private val topBarSizeSpec = spring<IntSize>(stiffness = 700f)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BriefTopBar(
    isShow: Boolean,
    isScrolled: Boolean = false,
    title: String? = "",
    /** How far the article has been scrolled, 0 to 1 (a thin wavy bar under the top bar). */
    progress: () -> Float = { 0f },
    /** "3 min left", under the small title once you scrolled past the header. */
    timeLeft: String? = null,
    link: String? = "",
    navigationAction: NavigationAction,
    onClick: (() -> Unit)? = null,
    onNavButtonClick: (NavigationAction) -> Unit = {},
    onNavigateToStylePage: () -> Unit,
    onSummarize: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val sharedContent = LocalSharedContent.current
    val isOutlined =
        LocalReadingPageTonalElevation.current == ReadingPageTonalElevationPreference.Outlined

    val containerColor by
        animateColorAsState(
            with(MaterialTheme.colorScheme) {
                if (isOutlined || !isScrolled) surface else surfaceContainer
            },
            label = "",
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        )

    Box(modifier = Modifier.fillMaxSize().zIndex(1f), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.drawBehind { drawRect(containerColor) }) {
            Spacer(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            )
            AnimatedVisibility(
                visible = isShow,
                enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = topBarSizeSpec),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = topBarSizeSpec),
            ) {
                TopAppBar(
                    // Past the header: the article's title, small, with the time left under it.
                    title = {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isScrolled && !title.isNullOrBlank(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Column {
                                Text(
                                    text = title.orEmpty(),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                timeLeft?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    },
                    modifier =
                        if (onClick == null) Modifier
                        else
                            Modifier.clickable(
                                onClick = onClick,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ),
                    windowInsets = WindowInsets(0.dp),
                    navigationIcon = {
                        val imageVector =
                            when (navigationAction) {
                                NavigationAction.Close -> Icons.Rounded.Close
                                NavigationAction.HideList -> Icons.AutoMirrored.Rounded.MenuOpen
                                NavigationAction.ExpandList -> Icons.Rounded.Menu
                            }
                        val contentDescription =
                            when (navigationAction) {
                                NavigationAction.Close -> stringResource(R.string.close)
                                NavigationAction.HideList -> "Hide list"
                                NavigationAction.ExpandList -> "Expand list"
                            }
                        // Material 3 Expressive: tonal buttons, like the list's top bar.
                        TonalCircleButton(icon = imageVector, description = contentDescription) {
                            onNavButtonClick(navigationAction)
                        }
                    },
                    actions = {
                        TopBarButtonGroup(
                            buildList {
                                if (onSummarize != null) {
                                    add(
                                        TopBarAction(
                                            Icons.Outlined.AutoAwesome,
                                            stringResource(R.string.ai_summarize),
                                        ) {
                                            onSummarize()
                                        }
                                    )
                                }
                                add(
                                    TopBarAction(Icons.Outlined.Palette, stringResource(R.string.style)) {
                                        onNavigateToStylePage()
                                    }
                                )
                                add(
                                    TopBarAction(Icons.Outlined.Share, stringResource(R.string.share)) {
                                        sharedContent.share(context, title, link)
                                    }
                                )
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
            // Reading progress: exact, from how far the article is scrolled.
            val started by remember { derivedStateOf { progress() > 0.01f } }
            if (started) {
                LinearWavyProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp),
                )
            }
            // No divider line under the bar here: the progress bar already marks its edge.
        }
    }
}
