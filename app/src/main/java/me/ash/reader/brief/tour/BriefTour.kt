@file:Suppress("DEPRECATION") // Read You's DataStoreKey is deprecated; every preference here must still use it

package me.ash.reader.brief.tour

import me.ash.reader.brief.ui.groupShape
import me.ash.reader.brief.ui.Group
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import me.ash.reader.brief.ui.rememberPressFeel
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
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
import me.ash.reader.ui.ext.fromJSONStringToDataStore
import me.ash.reader.ui.ext.put

// The tour's pages. Restore is on the first run only (when the tour is opened from Settings,
// there is nothing to restore into).
private const val RESTORE = 0
private const val TRIAGE = 1
private const val AI = 2
private const val CALM = 3
private const val HOLD = 4

/**
 * The first run: restoring a settings backup (for someone coming from another phone), then four
 * pages on what's different about Brief You (Triage, the on-device AI, calmer feeds, and the
 * buttons that do more when held), in place of Read You's welcome page.
 * Its agreement (terms and privacy) is kept on the last page: Skip goes there, and "Agree and
 * start" is the only way on. "Set up AI" opens the AI page once you've agreed.
 */
@Composable
fun BriefFirstRun(onNavigateToFeeds: () -> Unit, onNavigateToAi: () -> Unit) {
    val context = LocalContext.current
    BriefTour(firstRun = true) { setUpAi ->
        // Saved outside this screen: the tour leaves the screen stack next, which would cancel
        // its own coroutines.
        val appContext = context.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            appContext.dataStore.put(DataStoreKey.isFirstLaunch, false)
        }
        onNavigateToFeeds()
        if (setUpAi) onNavigateToAi()
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

/** A page's art: its Expressive shape, colours and icon (the shape morphs into the next one). */
private class TourArt(val polygon: RoundedPolygon, val container: Color, val content: Color, val icon: ImageVector)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BriefTour(firstRun: Boolean, onDone: (setUpAi: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pages = remember(firstRun) { if (firstRun) listOf(RESTORE, TRIAGE, AI, CALM, HOLD) else listOf(TRIAGE, AI, CALM, HOLD) }
    val pager = rememberPagerState { pages.size }
    val aiPage = pages.indexOf(AI)
    var setUpAi by rememberSaveable { mutableStateOf(false) }
    var restored by rememberSaveable { mutableStateOf<Boolean?>(null) } // null: not tried; false: the file couldn't be read
    // Restore: your backup file (Settings, Troubleshooting, Export as JSON on the other phone),
    // read into the app's settings as Troubleshooting's Import does.
    val restoreLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val appContext = context.applicationContext
            scope.launch {
                val ok =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                                val text =
                                    appContext.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
                                importSettings(appContext, text)
                            }
                            .isSuccess
                    }
                restored = ok
                if (ok) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    pager.animateScrollToPage(pages.indexOf(RESTORE) + 1)
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                }
            }
        }
    var termsVisible by remember { mutableStateOf(false) }
    val phone = remember { PhoneCheck.of(context) }
    val scheme = MaterialTheme.colorScheme
    val last = pager.currentPage == pages.lastIndex
    val kind = pages[pager.currentPage.coerceIn(0, pages.lastIndex)]
    val arts =
        pages.map { page ->
            when (page) {
                RESTORE -> TourArt(MaterialShapes.Sunny, scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Outlined.SettingsBackupRestore)
                TRIAGE -> TourArt(MaterialShapes.Cookie4Sided, scheme.primary, scheme.onPrimary, Icons.Rounded.Style)
                AI -> TourArt(MaterialShapes.Cookie9Sided, scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Outlined.AutoAwesome)
                CALM -> TourArt(MaterialShapes.Clover4Leaf, scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Outlined.NotificationsActive)
                else -> TourArt(MaterialShapes.SoftBurst, scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Outlined.TouchApp)
            }
        }
    fun go(page: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        scope.launch { pager.animateScrollToPage(page) }
    }

    Surface(color = scheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Spacer(Modifier.height(40.dp))
            // One shape for the whole tour: as you swipe it morphs into the next page's shape,
            // its colour and icon following your finger.
            MorphingArt(arts, position = { pager.currentPage + pager.currentPageOffsetFraction })
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                // Scrolls only if the page is taller than the screen; otherwise dragging up or
                // down does nothing (no stretch).
                val scroll = rememberScrollState()
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                // The text moves with its page and fades out quickly as it
                                // leaves, so the last page is gone before the next one settles.
                                val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                                alpha = 1f - (kotlin.math.abs(offset) * 1.6f).coerceIn(0f, 1f)
                            }
                            .verticalScroll(scroll, enabled = scroll.maxValue > 0)
                            .padding(horizontal = 28.dp),
                ) {
                    when (pages[page]) {
                        RESTORE -> {
                            Title(stringResource(R.string.tour_restore_title))
                            Body(stringResource(R.string.tour_restore_text))
                            restored?.let { ok ->
                                Spacer(Modifier.height(16.dp))
                                ResultLine(
                                    stringResource(if (ok) R.string.tour_restore_done else R.string.tour_restore_failed),
                                    problem = !ok,
                                )
                            }
                        }
                        TRIAGE -> {
                            Title(stringResource(R.string.tour_triage_title))
                            Body(stringResource(R.string.tour_triage_text))
                        }
                        AI -> {
                            Title(stringResource(R.string.tour_ai_title))
                            Body(stringResource(R.string.tour_ai_text))
                            Spacer(Modifier.height(16.dp))
                            PhoneLine(phone, setUpAi)
                        }
                        CALM -> {
                            Title(stringResource(R.string.tour_calm_title))
                            Body(stringResource(R.string.tour_calm_text))
                        }
                        else -> {
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
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        termsVisible = true
                                    }
                                ) {
                                    Text(stringResource(R.string.terms_of_service))
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
            Dots(pager.currentPage, pages.size)
            // Two buttons, Skip (or Later) and Next; on the last page Skip shrinks away and Next
            // grows to fill the row, its words changing as it does.
            val split by
                animateFloatAsState(
                    if (last) 0f else 1f,
                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                    label = "tour buttons",
                )
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                val half = (maxWidth - 12.dp) / 2
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (split > 0.01f) {
                        Box(
                            modifier =
                                Modifier.width(half * split).clipToBounds().graphicsLayer {
                                    alpha = split
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            // On the AI and Restore pages, Later moves on to the next page;
                            // elsewhere Skip goes to the last one.
                            val later = kind == AI || (kind == RESTORE && restored != true)
                            TourButton(
                                text = stringResource(if (later) R.string.tour_later else R.string.tour_skip),
                                kind = TourButtonKind.Tonal,
                                modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(half),
                            ) {
                                if (!last) go(if (later) pager.currentPage + 1 else pages.lastIndex)
                            }
                        }
                        Spacer(Modifier.width(12.dp * split))
                    }
                    TourButton(
                        text =
                            when {
                                last -> stringResource(if (firstRun) R.string.tour_agree_start else R.string.tour_done)
                                kind == AI -> stringResource(R.string.tour_set_up_ai)
                                kind == RESTORE && restored != true -> stringResource(R.string.tour_restore)
                                else -> stringResource(R.string.tour_next)
                            },
                        kind = TourButtonKind.Filled,
                        modifier = Modifier.weight(1f),
                    ) {
                        when {
                            last -> {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onDone(setUpAi)
                            }
                            kind == AI -> {
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                setUpAi = true
                                scope.launch { pager.animateScrollToPage(aiPage + 1) }
                            }
                            kind == RESTORE && restored != true -> {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            }
                            else -> go(pager.currentPage + 1)
                        }
                    }
                }
            }
        }
    }

    RYDialog(
        visible = termsVisible,
        onDismissRequest = { termsVisible = false },
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

/**
 * The tour's art: one Expressive shape that turns slowly and, at [position] between two pages,
 * is part-way morphed from one page's shape to the next, with the colour blended and the icons
 * crossing over.
 */
@Composable
private fun MorphingArt(arts: List<TourArt>, position: () -> Float) {
    val turn by
        rememberInfiniteTransition(label = "tour art")
            .animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "turn")
    val at = position().coerceIn(0f, (arts.size - 1).toFloat())
    val from = at.toInt().coerceAtMost(arts.size - 1)
    val to = (from + 1).coerceAtMost(arts.size - 1)
    val t = at - from
    val morph = remember(from, to) { Morph(arts[from].polygon, arts[to].polygon) }
    val shape = remember(morph, t) { MorphShape(morph, t) }
    val container = androidx.compose.ui.graphics.lerp(arts[from].container, arts[to].container, t)
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(190.dp)
                .graphicsLayer {
                    // Turns slowly, and a quarter turn more over each swipe.
                    rotationZ = turn + at * 90f
                }
                .clip(shape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            // The two pages' icons cross over, upright whatever the shape's turn.
            listOf(from to 1f - t, to to t).distinctBy { it.first }.forEach { (i, amount) ->
                Icon(
                    arts[i].icon,
                    contentDescription = null,
                    tint = arts[i].content,
                    modifier =
                        Modifier.size(72.dp).graphicsLayer {
                            rotationZ = -(turn + at * 90f)
                            alpha = if (from == to) 1f else amount
                            val grow = 0.6f + 0.4f * (if (from == to) 1f else amount)
                            scaleX = grow
                            scaleY = grow
                        },
                )
            }
        }
    }
}

/** A [Morph] at [progress], scaled to the size it's drawn at (a fresh matrix each time). */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

private enum class TourButtonKind { Filled, Tonal }

/** The tour's buttons, with Triage's press: they squeeze and their corners tighten, then spring back. */
@Composable
private fun TourButton(text: String, kind: TourButtonKind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val press = rememberPressFeel(rest = 26.dp, held = 12.dp)
    val shape = RoundedCornerShape(press.corner)
    val m = with(press) { modifier.height(52.dp).squeeze() }
    // A new label fades in over the old one (Next → Agree and start).
    val label: @Composable () -> Unit = {
        androidx.compose.animation.AnimatedContent(
            targetState = text,
            transitionSpec = {
                androidx.compose.animation.fadeIn(tween(220, delayMillis = 90)) togetherWith
                    androidx.compose.animation.fadeOut(tween(90))
            },
            label = "tour button label",
        ) {
            Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
    when (kind) {
        TourButtonKind.Filled ->
            Button(onClick = onClick, shape = shape, interactionSource = press.interaction, modifier = m) { label() }
        TourButtonKind.Tonal ->
            FilledTonalButton(onClick = onClick, shape = shape, interactionSource = press.interaction, modifier = m) { label() }
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

/** The Restore page's outcome, drawn like the AI page's line. */
@Composable
private fun ResultLine(text: String, problem: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (problem) scheme.errorContainer else scheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (problem) Icons.Rounded.ErrorOutline else Icons.Rounded.Check,
            contentDescription = null,
            tint = if (problem) scheme.onErrorContainer else scheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (problem) scheme.onErrorContainer else scheme.onSurface)
    }
}

/** A settings backup read into the app's settings; throws if the file isn't one. */
private suspend fun importSettings(context: android.content.Context, json: String) {
    require(json.trimStart().startsWith("{")) { "not a settings backup" }
    json.fromJSONStringToDataStore(context)
}

/** One long press, in a connected list. */
@Composable
private fun HoldRow(text: String, first: Boolean, last: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = Group.Gap / 2)
                .clip(groupShape(if (first) 0 else if (last) 2 else 1, if (first && last) 1 else 3))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Page dots: the current one is a wider pill. */
@Composable
private fun Dots(current: Int, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val width by
                animateDpAsState(
                    if (i == current) 24.dp else 8.dp,
                    spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
                    label = "dot",
                )
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
