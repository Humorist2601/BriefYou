package com.ay.briefyou.ui.component.base

import android.graphics.drawable.PictureDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import coil3.compose.rememberAsyncImagePainter // Brief You: Coil 3
import com.caverock.androidsvg.SVG
import com.ay.briefyou.infrastructure.preference.LocalDarkTheme
import com.ay.briefyou.ui.svg.parseDynamicColor
import com.ay.briefyou.ui.theme.palette.LocalTonalPalettes

@Composable
fun DynamicSVGImage(
    modifier: Modifier = Modifier,
    svgImageString: String,
    contentDescription: String,
) {
    val useDarkTheme = LocalDarkTheme.current.isDarkTheme()
    val tonalPalettes = LocalTonalPalettes.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val pic by
        remember(useDarkTheme, tonalPalettes, size) {
            mutableStateOf(
                PictureDrawable(
                    SVG.getFromString(svgImageString.parseDynamicColor(tonalPalettes, useDarkTheme))
                        .renderToPicture(size.width, size.height)
                )
            )
        }

    Row(
        modifier =
            modifier.aspectRatio(1.38f).onGloballyPositioned {
                if (it.size != IntSize.Zero) {
                    size = it.size
                }
            }
    ) {
        Crossfade(targetState = pic) {
            Image(contentDescription = contentDescription, painter = rememberAsyncImagePainter(it))
        }
    }
}
