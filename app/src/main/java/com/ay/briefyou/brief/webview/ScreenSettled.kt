package com.ay.briefyou.brief.webview

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the screen the article is on has finished sliding in. The article page is not handed to
 * the web view until then: drawing a long page while the screen slides in (coming back from the
 * reading style pages, which close the article screen) made the slide stutter. Anywhere the
 * screen isn't provided (it isn't sliding), it is always settled.
 */
val LocalScreenSettled = staticCompositionLocalOf<() -> Boolean> { { true } }
