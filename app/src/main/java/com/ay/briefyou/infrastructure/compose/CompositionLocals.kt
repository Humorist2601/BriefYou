package com.ay.briefyou.infrastructure.compose

import androidx.compose.runtime.Composable

@Composable
fun ProvideCompositionLocals(content: @Composable () -> Unit) {
    ProvideUriHandler(content)
}
