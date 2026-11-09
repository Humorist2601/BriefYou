package me.ash.reader.brief.tour

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ireward.htmlcompose.HtmlText
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.brief.ai.PhoneCheck
import me.ash.reader.brief.ui.GoogleSansWideFontFamily
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

private const val PAGES = 4

/**
 * The first run: four pages on what's different about Brief You (Triage, the on-device AI,
 * calmer feeds, and the buttons that do more when held), in place of Read You's welcome page.
 * Its agreement (terms and privacy) is kept on the last page: Skip goes there, and "Agree and
 * start" is the only way on. "Set up AI" opens the AI page once you've agreed.
 */
@Composable
fun BriefFirstRun(onNavigateToFeeds: () -> Unit, onNavigateToAi: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BriefTour(firstRun = true) { setUpAi ->
        onNavigateToFeeds()
        if (setUpAi) onNavigateToAi()
        scope.launch { context.dataStore.put(DataStoreKey.isFirstLaunch, false) }
    }
}

/** The tour again, from Settings: the same pages, without the agreement. */
@Composable
fun BriefTourDialog(onDismiss: () -> Unit, onSetUpAi: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BriefTour(firstRun = false) { setUpAi ->
            onDismiss()
            if (setUpAi) onSetUpAi()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BriefTour(firstRun: Boolean, onDone: (setUpAi: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { PAGES }
    var setUpAi by rememberSaveable { mutableStateOf(false) }
    var termsVisible by remember { mutableStateOf(false) }
    val phone = remember { PhoneCheck.of(context) }
    val scheme = MaterialTheme.colorScheme
    val last = pager.currentPage == PAGES - 1

    Surface(color = scheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                ) {
                    Spacer(Modifier.height(48.dp))
                    when (page) {
                        0 -> {
                            CardsArt()
                            Title(stringResource(R.string.tour_triage_title))
                            Body(stringResource(R.string.tour_triage_text))
                        }
                        1 -> {
                            ShapeArt(MaterialShapes.Cookie9Sided.toShape(), scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Outlined.AutoAwesome)
                            Title(stringResource(R.string.tour_ai_title))
                            Body(stringResource(R.string.tour_ai_text))
                            Spacer(Modifier.height(16.dp))
                            PhoneLine(phone, setUpAi)
                        }
                        2 -> {
                            ShapeArt(MaterialShapes.Clover4Leaf.toShape(), scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Outlined.NotificationsActive)
                            Title(stringResource(R.string.tour_calm_title))
                            Body(stringResource(R.string.tour_calm_text))
                        }
                        else -> {
                            ShapeArt(MaterialShapes.SoftBurst.toShape(), scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Outlined.TouchApp)
                            Title(stringResource(R.string.tour_hold_title))
                            Body(stringResource(R.string.tour_hold_text))
                            Spacer(Modifier.height(10.dp))
                            listOf(
                                    R.string.tour_hold_sparkle,
                                    R.string.tour_hold_star,
                                    R.string.tour_hold_next,
                                    R.string.tour_hold_listen,
                                    R.string.tour_hold_triage,
                                )
                                .forEachIndexed { i, res ->
                                    HoldRow(stringResource(res), first = i == 0, last = i == 4)
                                }
                            if (firstRun) {
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    text = stringResource(R.string.brief_tos_tips),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { termsVisible = true }) {
                                    Text(stringResource(R.string.terms_of_service))
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
            Dots(pager.currentPage)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    last ->
                        Button(onClick = { onDone(setUpAi) }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(
                                stringResource(if (firstRun) R.string.tour_agree_start else R.string.tour_done),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    pager.currentPage == 1 -> {
                        FilledTonalButton(
                            onClick = { scope.launch { pager.animateScrollToPage(2) } },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text(stringResource(R.string.tour_later))
                        }
                        Button(
                            onClick = {
                                setUpAi = true
                                scope.launch { pager.animateScrollToPage(2) }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text(stringResource(R.string.tour_set_up_ai))
                        }
                    }
                    else -> {
                        TextButton(
                            onClick = { scope.launch { pager.animateScrollToPage(PAGES - 1) } },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text(stringResource(R.string.tour_skip))
                        }
                        Button(
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text(stringResource(R.string.tour_next))
                        }
                    }
                }
            }
        }
    }

    RYDialog(
        visible = termsVisible,
        onDismissRequest = { termsVisible = false },
        icon = { Icon(Icons.Rounded.Balance, contentDescription = null) },
        title = { Text(text = stringResource(R.string.terms_of_service)) },
        text = {
            SelectionContainer {
                HtmlText(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    text = stringResource(R.string.brief_tos_content),
                    style = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurfaceVariant),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { termsVisible = false }) { Text(text = stringResource(R.string.tour_close)) }
        },
    )
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.displaySmall.copy(
                fontFamily = GoogleSansWideFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 36.dp),
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/** An Expressive shape that turns slowly, with an icon that stays upright. */
@Composable
private fun ShapeArt(shape: Shape, container: Color, content: Color, icon: ImageVector) {
    val turn by
        rememberInfiniteTransition(label = "tour art")
            .animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "turn")
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(180.dp).graphicsLayer { rotationZ = turn }.clip(shape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(72.dp).graphicsLayer { rotationZ = -turn },
            )
        }
    }
}

/** Triage's art: a little stack of cards that sways, the front one with Triage's icon. */
@Composable
private fun CardsArt() {
    val scheme = MaterialTheme.colorScheme
    val sway by
        rememberInfiniteTransition(label = "tour cards")
            .animateFloat(-6f, 6f, infiniteRepeatable(tween(2_400), RepeatMode.Reverse), label = "sway")
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 130.dp, height = 176.dp).rotate(-10f).clip(RoundedCornerShape(26.dp)).background(scheme.secondaryContainer))
        Box(Modifier.size(width = 130.dp, height = 176.dp).rotate(7f).clip(RoundedCornerShape(26.dp)).background(scheme.primaryContainer))
        Box(
            Modifier.size(width = 130.dp, height = 176.dp).rotate(sway).clip(RoundedCornerShape(26.dp)).background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Style, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(56.dp))
        }
    }
}

/** What this phone can expect from the model (memory), and what Set up AI will do. */
@Composable
private fun PhoneLine(phone: PhoneCheck, setUpAi: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val text =
        when {
            setUpAi -> stringResource(R.string.tour_ai_after)
            else ->
                when (phone.fit) {
                    PhoneCheck.Fit.Good -> stringResource(R.string.ai_check_memory_good, phone.nominalGb)
                    PhoneCheck.Fit.Okay -> stringResource(R.string.ai_check_memory_okay, phone.nominalGb)
                    PhoneCheck.Fit.Poor -> stringResource(R.string.ai_check_memory_poor, phone.nominalGb)
                }
        }
    val poor = !setUpAi && phone.fit == PhoneCheck.Fit.Poor
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (poor) scheme.errorContainer else scheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = if (poor) scheme.onErrorContainer else scheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (poor) scheme.onErrorContainer else scheme.onSurface)
    }
}

/** One long press, in a connected list. */
@Composable
private fun HoldRow(text: String, first: Boolean, last: Boolean) {
    val big = 20.dp
    val small = 4.dp
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (first) big else small,
                        topEnd = if (first) big else small,
                        bottomStart = if (last) big else small,
                        bottomEnd = if (last) big else small,
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Page dots: the current one is a wider pill. */
@Composable
private fun Dots(current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(PAGES) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, label = "dot")
            Box(
                Modifier.padding(horizontal = 3.dp)
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

/** "Tour" in Settings: see the tour again (and its hints). */
@Composable
fun TourSettingsItem(navigateToAiModel: () -> Unit) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    me.ash.reader.ui.page.settings.SelectableSettingGroupItem(
        title = stringResource(R.string.tour_settings),
        desc = stringResource(R.string.tour_settings_desc),
        icon = Icons.Outlined.TouchApp,
        onClick = {
            BriefHints.reset(context)
            open = true
        },
    )
    if (open) BriefTourDialog(onDismiss = { open = false }, onSetUpAi = navigateToAiModel)
}
