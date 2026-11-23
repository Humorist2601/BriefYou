package com.ay.briefyou.brief.ui

import android.view.SoundEffectConstants
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ay.briefyou.domain.model.general.Filter

/** The height of everything in the bottom bar: the filter toolbar and the AI button. */
val BottomBarHeight = 64.dp

/**
 * The gap under everything that floats at the bottom of the screen: the reading page's pill (above
 * the system gesture bar), and the pull-up hint and the "now playing" pill (above the bottom bar).
 */
val FloatingBarGap = 12.dp

/**
 * How far the floating bottom bars (the filter bar and the reading page's pill) float above the
 * system's navigation bar area: Material's distance of a floating toolbar from the screen's edge
 * (FloatingToolbarDefaults.ScreenOffset, 16 dp). The filter bar sat right on that area (0 dp), so it
 * looked too low next to the gesture handle.
 */
val BarScreenOffset = 16.dp

/**
 * The Starred / Unread / All toolbar: a solid tonal pill like Material's floating toolbar. The chosen
 * filter is a filled accent pill with its name; the others are just icons. The pill slides (with a
 * little wobble) when the filter changes, follows the page while you swipe between the pages
 * ([pagerState]), and can be dragged along the bar with a finger, ticking at each filter. How it
 * moves is Trainable's floating bar (see NavPill.kt).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BriefFilterBar(
    modifier: Modifier = Modifier,
    filter: Filter,
    pagerState: PagerState? = null,
    filterOnClick: (Filter) -> Unit = {},
) {
    val view = LocalView.current
    val items = Filter.values
    val count = items.size
    val selected = items.indexOf(filter).coerceAtLeast(0)
    val banner = bannerContainer()

    // Where each filter's button is in the row: left edge and width, in pixels.
    val lefts = remember { mutableStateListOf(*FloatArray(count).toTypedArray()) }
    val widths = remember { mutableStateListOf(*FloatArray(count).toTypedArray()) }

    val scope = rememberCoroutineScope()
    val currentSelected by rememberUpdatedState(selected)
    val currentOnClick by rememberUpdatedState(filterOnClick)
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selected.toFloat()) }
    val settle = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) {
        settle.animateTo(
            selected.toFloat(),
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }
    // Where the pill is, in filters: under the finger, with the pages while they are swiped, else settling.
    fun position(): Float =
        when {
            dragging -> dragPosition
            pagerState != null && pagerState.isScrollInProgress ->
                (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (count - 1).toFloat())
            else -> settle.value
        }
    val jiggle = rememberNavPillJiggle { position() }
    val shownIndex = if (dragging) NavPillMath.nearestIndex(dragPosition, count) else selected

    fun centreOf(i: Int) = lefts[i] + widths[i] / 2f
    fun positionForFinger(x: Float): Float {
        if (x <= centreOf(0)) return 0f
        for (i in 0 until count - 1) {
            val a = centreOf(i)
            val b = centreOf(i + 1)
            if (x < b) return i + (x - a) / (b - a).coerceAtLeast(1f)
        }
        return (count - 1).toFloat()
    }

    Row(
        modifier =
            modifier
                .glass(CircleShape)
                .height(BottomBarHeight)
                .padding(horizontal = 8.dp)
                .selectableGroup()
                .drawBehind {
                    // The pill: between the two buttons the position is between, with the wobble.
                    val p = position().coerceIn(0f, (count - 1).toFloat())
                    val i0 = p.toInt().coerceIn(0, count - 1)
                    val i1 = (i0 + 1).coerceAtMost(count - 1)
                    val f = p - i0
                    val w = widths[i0] + (widths[i1] - widths[i0]) * f
                    if (w > 0f) {
                        val x = lefts[i0] + (lefts[i1] - lefts[i0]) * f
                        val h = 48.dp.toPx()
                        val top = (size.height - h) / 2f
                        scale(jiggle.scaleX, jiggle.scaleY, pivot = Offset(x + w / 2f, size.height / 2f)) {
                            drawRoundRect(banner, topLeft = Offset(x, top), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
                        }
                    }
                }
                .pointerInput(count) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // A tap is handled by the button itself; only a sideways drag moves the pill.
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (start == null) return@awaitEachGesture
                        dragging = true
                        dragPosition = positionForFinger(start.position.x)
                        var tick = NavPillMath.nearestIndex(dragPosition, count)
                        val finished =
                            horizontalDrag(start.id) { change ->
                                dragPosition = positionForFinger(change.position.x)
                                change.consume()
                                val index = NavPillMath.closestIndexWithHysteresis(tick, dragPosition, count)
                                if (index != tick) {
                                    tick = index
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            }
                        val target = NavPillMath.nearestIndex(dragPosition, count)
                        val current = currentSelected
                        val dropped = dragPosition
                        // The pill is handed over to the settling spring from where it was dropped.
                        scope.launch {
                            settle.snapTo(dropped)
                            dragging = false
                            if (!finished || target == current) {
                                settle.animateTo(
                                    current.toFloat(),
                                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                                )
                            }
                        }
                        if (finished && target != current) currentOnClick(items[target])
                    }
                },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            FilterButton(
                item = item,
                selected = index == shownIndex,
                banner = banner,
                nearness = { NavPillMath.proximity(position(), index) },
                modifier =
                    Modifier.onGloballyPositioned {
                        lefts[index] = it.positionInParent().x
                        widths[index] = it.size.width.toFloat()
                    },
            ) {
                view.playSoundEffect(SoundEffectConstants.CLICK)
                filterOnClick(item)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterButton(
    item: Filter,
    selected: Boolean,
    banner: Color,
    nearness: () -> Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.92f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "filter press")
    // The icon and name take the pill's colour as it comes near, and go back as it leaves.
    val content = lerp(colors.onSurfaceVariant, colors.contentColorFor(banner), nearness())
    val name = item.toName()
    Row(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .height(48.dp)
                .clip(CircleShape)
                .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) item.iconFilled else item.iconOutline,
            contentDescription = if (selected) null else name,
            tint = content,
            modifier = Modifier.size(24.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Text(
                text = name,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            )
        }
    }
}

/**
 * The bottom bar's surface, like Material's floating toolbar: a solid tonal container
 * (`surfaceContainer`) with a soft shadow, and no outline.
 */
@Composable
internal fun Modifier.glass(shape: Shape): Modifier {
    val container = MaterialTheme.colorScheme.surfaceContainer
    return this
        .barShadow(shape)
        .clip(shape)
        .background(container)
}

/**
 * The shadow under the bottom bar and the AI button: Material's standard one at level 3 (6 dp),
 * the elevation of a floating action button, so its look follows the platform.
 */
internal fun Modifier.barShadow(shape: Shape): Modifier = this.shadow(elevation = 6.dp, shape = shape)
