package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Download
import me.ash.reader.ui.component.menu.AnimatedDropdownMenu
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import me.ash.reader.R
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.showToast
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

data class ImageData(val imageUrl: String = "", val altText: String = "")

/** How far (part of the screen height) the picture is dragged before letting go closes it. */
private const val DismissFraction = 0.18f

/** A flick at least this fast (px/s) closes it from anywhere. */
private const val FlingVelocity = 2500f

@Composable
fun ReaderImageViewer(
    imageData: ImageData,
    onDownloadImage: (String) -> Unit,
    onDismissRequest: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        // The black behind the picture is drawn here (not the window's dim), so it can fade
        // while the picture is dragged away.
        val dialogWindowProvider = view.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setDimAmount(0f)

        val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f))

        // Drag to close, like a Triage card: the picture follows your finger (tilting a little
        // as it goes sideways) and the background fades; let go past the point (you feel a tick
        // there) or with a quick flick and it flies off and closes, otherwise it springs back.
        // Only when not zoomed in, so panning a zoomed picture still works.
        val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        var size by remember { mutableStateOf(IntSize(1, 1)) }
        var pastPoint by remember { mutableStateOf(false) }
        val threshold = size.height * DismissFraction
        val progress = (drag.value.getDistance() / threshold).coerceIn(0f, 1f)

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .onSizeChanged { size = it }
                    .background(Color.Black.copy(alpha = 1f - 0.65f * progress))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down =
                                awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                            if ((zoomableState.zoomFraction ?: 0f) > 0.01f) return@awaitEachGesture
                            val velocity = VelocityTracker()
                            velocity.addPosition(down.uptimeMillis, down.position)
                            var dragging = false
                            var moved = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                // A second finger means pinch to zoom: leave it to the zoom.
                                if (event.changes.count { it.pressed } > 1) break
                                val change =
                                    event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.position - change.previousPosition
                                moved += delta
                                velocity.addPosition(change.uptimeMillis, change.position)
                                if (
                                    !dragging &&
                                        abs(moved.y) > viewConfiguration.touchSlop &&
                                        abs(moved.y) > abs(moved.x)
                                ) {
                                    dragging = true
                                }
                                if (dragging) {
                                    change.consume()
                                    val next = drag.value + delta
                                    scope.launch { drag.snapTo(next) }
                                    val past = next.getDistance() > size.height * DismissFraction
                                    if (past != pastPoint) {
                                        pastPoint = past
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate
                                        )
                                    }
                                }
                            }
                            if (dragging) {
                                val v = velocity.calculateVelocity()
                                val flung = abs(v.y) > FlingVelocity
                                if (pastPoint || flung) {
                                    val direction =
                                        if (flung) Offset(v.x, v.y) / v.y.let { abs(it) }
                                        else drag.value / drag.value.getDistance().coerceAtLeast(1f)
                                    val target = drag.value + direction * (size.height * 1.2f)
                                    scope.launch {
                                        drag.animateTo(target, tween(220))
                                        onDismissRequest()
                                    }
                                } else {
                                    scope.launch {
                                        drag.animateTo(
                                            Offset.Zero,
                                            spring(dampingRatio = 0.7f, stiffness = 500f),
                                        )
                                    }
                                }
                                pastPoint = false
                            } else if (drag.value != Offset.Zero) {
                                scope.launch { drag.animateTo(Offset.Zero) }
                            }
                        }
                    }
        ) {

            val painter =
                rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .addHeader("Referer", imageData.imageUrl.extractDomain() ?: "")
                        .data(data = imageData.imageUrl)
                        .build()
                )

            var expanded by remember { mutableStateOf(false) }

            LaunchedEffect(painter.intrinsicSize) {
                zoomableState.setContentLocation(
                    ZoomableContentLocation.scaledInsideAndCenterAligned(painter.intrinsicSize)
                )
            }

            Image(
                painter = painter,
                contentDescription = imageData.altText,
                modifier =
                    // Moved outside the zoom (which clips to its own bounds).
                    Modifier.graphicsLayer {
                            translationX = drag.value.x
                            translationY = drag.value.y
                            rotationZ = drag.value.x / size.width.coerceAtLeast(1) * 10f
                            val s = 1f - 0.08f * progress
                            scaleX = s
                            scaleY = s
                        }
                        .zoomable(
                            state = zoomableState,
                            clipToBounds = true,
                            onClick = { onDismissRequest() },
                            onLongClick = { expanded = true },
                        )
                        .fillMaxSize(),
                alignment = Alignment.Center,
                contentScale = ContentScale.Inside,
            )

            val launcher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { result ->
                        if (result) {
                            onDownloadImage(imageData.imageUrl)
                        } else {
                            context.showToast(context.getString(R.string.permission_denied))
                        }
                    },
                )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .safeDrawingPadding()
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = 1f - progress },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstantsCompat.KEYBOARD_TAP)
                        onDismissRequest()
                    },
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                        ),
                    modifier = Modifier.padding(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(id = R.string.close),
                    )
                }

                Spacer(Modifier.weight(1f))

                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    // The same menu as the article and Triage menus (animated, icons on the left).
                    AnimatedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        alignToAnchorEnd = true,
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.save)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                val isStoragePermissionGranted =
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    ) == PackageManager.PERMISSION_GRANTED

                                if (Build.VERSION.SDK_INT > 28 || isStoragePermissionGranted) {
                                    onDownloadImage(imageData.imageUrl)
                                } else {
                                    launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                                expanded = false
                            },
                        )
                        // Brief You: share the picture's link.
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.share)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                expanded = false
                                val send =
                                    android.content.Intent(android.content.Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_TEXT, imageData.imageUrl)
                                context.startActivity(
                                    android.content.Intent.createChooser(send, null)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                        )
                    }
                    IconButton(
                        onClick = { expanded = true },
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White,
                            ),
                        modifier = Modifier.padding(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = stringResource(id = R.string.more),
                        )
                    }
                }
            }
        }
    }
}
