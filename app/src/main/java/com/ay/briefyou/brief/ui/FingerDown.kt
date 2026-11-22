package com.ay.briefyou.brief.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Runs [action] as soon as a finger goes down on this (a press, before it is known to be a tap),
 * without taking the touch away from the tap, a long press or a scroll. Used to start loading what
 * a tap would open, so it is ready sooner. [key]: what the action is for (a new key restarts it).
 */
fun Modifier.onFingerDown(key: Any, action: () -> Unit): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            action()
        }
    }
