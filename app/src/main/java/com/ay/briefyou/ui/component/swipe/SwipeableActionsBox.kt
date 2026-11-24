package com.ay.briefyou.ui.component.swipe

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.material3.toShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A composable that can be swiped left or right for revealing actions.
 *
 * @param swipeThreshold Minimum drag distance before any [SwipeAction] is
 * activated and can be swiped.
 *
 * @param backgroundUntilSwipeThreshold Color drawn behind the content until
 * [swipeThreshold] is reached. When the threshold is passed, this color is
 * replaced by the currently visible [SwipeAction]'s background.
 */
@Composable
fun SwipeableActionsBox(
  modifier: Modifier = Modifier,
  state: SwipeableActionsState = rememberSwipeableActionsState(),
  startActions: List<SwipeAction> = emptyList(),
  endActions: List<SwipeAction> = emptyList(),
  swipeThreshold: Dp = 40.dp,
  backgroundUntilSwipeThreshold: Color = Color.DarkGray,
  content: @Composable BoxScope.() -> Unit
) = Box(modifier) {
  state.also {
    it.swipeThresholdPx = LocalDensity.current.run { swipeThreshold.toPx() }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    it.actions = remember(endActions, startActions, isRtl) {
      ActionFinder(
        left = if (isRtl) endActions else startActions,
        right = if (isRtl) startActions else endActions,
      )
    }
  }

  val backgroundColor = when {
    state.swipedAction != null -> state.swipedAction!!.value.background
    !state.hasCrossedSwipeThreshold() -> backgroundUntilSwipeThreshold
    state.visibleAction != null -> state.visibleAction!!.value.background
    else -> Color.Transparent
  }
  val animatedBackgroundColor: Color = if (state.layoutWidth == 0) {
    // Use the current color immediately because paparazzi can only capture the 1st frame.
    // https://github.com/cashapp/paparazzi/issues/1261
    backgroundColor
  } else {
    animateColorAsState(backgroundColor).value
  }

  val scope = rememberCoroutineScope()
  // Brief You: the row lifts into a rounded card while it is dragged (the tone of the app's other
  // cards, inset 8 dp from the edges), and settles back into an open row when it is let go.
  val lift by androidx.compose.animation.core.animateFloatAsState(
    if (kotlin.math.abs(state.offset.value) > 1f) 1f else 0f,
    androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f),
    label = "swipeLift",
  )
  val cardColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest // Brief You: a step lighter than the rows, so the lifted card reads
  // Brief You: the action surface sits behind the card; the card lifts above it (see below).
  (state.swipedAction ?: state.visibleAction)?.let { action ->
    ActionIconBox(
      modifier = Modifier.matchParentSize(),
      action = action,
      lift = lift,
      backgroundColor = animatedBackgroundColor,
      crossed = state.swipedAction != null || state.hasCrossedSwipeThreshold(),
      content = { action.value.icon() }
    )
  }

  Box(
    modifier = Modifier
      .onSizeChanged { state.layoutWidth = it.width }
      .absoluteOffset { IntOffset(x = state.offset.value.roundToInt(), y = 0) }
      .drawBehind {
        if (lift > 0f) {
          val inset = 8.dp.toPx()
          // A soft shadow under the lifted card: a few faint, growing rounded rectangles.
          for (i in 4 downTo 1) {
            val grow = i * 2.5.dp.toPx()
            drawRoundRect(
              color = Color.Black.copy(alpha = 0.07f * lift),
              topLeft = androidx.compose.ui.geometry.Offset(inset - grow, 2.dp.toPx() - grow),
              size = androidx.compose.ui.geometry.Size(size.width - inset * 2 + grow * 2, size.height + grow * 2),
              cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx() + grow),
            )
          }
          drawRoundRect(
            color = cardColor.copy(alpha = lift),
            topLeft = androidx.compose.ui.geometry.Offset(inset, 0f),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
          )
        }
      }
      .drawOverContent { state.ripple.draw(scope = this) }
      .horizontalDraggable(
        enabled = !state.isResettingOnRelease,
        onDragStopped = {
          scope.launch {
            state.handleOnDragStopped()
          }
        },
        state = state.draggableState,
      ),
    content = content
  )

  val hapticFeedback = LocalHapticFeedback.current
  if (state.hasCrossedSwipeThreshold() && state.swipedAction == null) {
    // Keyed on the side, not the action object: the actions are rebuilt whenever the caller // Brief You
    // recomposes, which would otherwise buzz again on every frame.
    LaunchedEffect(state.visibleAction?.isOnRightSide) {
      hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
  }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionIconBox(
    action: SwipeActionMeta,
    lift: Float,
    backgroundColor: Color,
    crossed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
  // Brief You: the action sits in a cookie that pops with a springy overshoot as the threshold is
  // crossed, on a rounded tray that fills with the accent container.
  val scheme = androidx.compose.material3.MaterialTheme.colorScheme
  val cookieScale by androidx.compose.animation.core.animateFloatAsState(
    if (crossed) 1f else 0.72f,
    androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 420f),
    label = "swipeCookie",
  )
  val cookieColor by animateColorAsState(if (crossed) scheme.primary else scheme.surfaceContainerHighest, label = "swipeCookieColor")
  val cookieShape = androidx.compose.material3.MaterialShapes.Cookie12Sided.toShape()
  // Brief You: not moved with the card: the tray is the whole row (inset 8 dp), seen only where the card
  // has lifted away from it, and the cookie waits at the screen edge.
  Box(
    modifier = modifier.drawBehind {
      val inset = 8.dp.toPx()
      drawRoundRect(
        color = backgroundColor.copy(alpha = backgroundColor.alpha * lift),
        topLeft = androidx.compose.ui.geometry.Offset(inset, 0f),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
      )
    },
  ) {
    Box(
      modifier = Modifier
        .layout { measurable, constraints ->
          val placeable = measurable.measure(androidx.compose.ui.unit.Constraints())
          val edge = 30.dp.roundToPx()
          val x = if (action.isOnRightSide) constraints.maxWidth - edge - placeable.width else edge
          layout(placeable.width, placeable.height) { placeable.place(x = x, y = (constraints.maxHeight - placeable.height) / 2) }
        }
        .size(44.dp)
        .graphicsLayer { scaleX = cookieScale; scaleY = cookieScale; alpha = lift }
        .clip(cookieShape)
        .background(cookieColor),
      contentAlignment = Alignment.Center,
    ) {
      content()
    }
  }
}

private fun Modifier.drawOverContent(onDraw: DrawScope.() -> Unit): Modifier {
  return drawWithContent {
    drawContent()
    onDraw(this)
  }
}
