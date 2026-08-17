package cn.yuandianai.juziscan.scan.ui

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.common.CropIcon
import cn.yuandianai.juziscan.common.ExpandIcon
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun EdgeReviewScreen(
    bitmap: Bitmap,
    quad: List<PointF>,
    onQuadChange: (List<PointF>) -> Unit,
    onBack: () -> Unit,
    onAuto: () -> Unit,
    onFill: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        SimpleTitleBar(
            title = "检测边缘",
            onBack = onBack,
            actionText = "下一步",
            onAction = onNext,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "边缘检测",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            EditableQuadOverlay(
                bitmap = bitmap,
                quad = quad,
                onQuadChange = onQuadChange,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomModeButton(
                    label = "重识别",
                    active = false,
                    onClick = onAuto
                ) { color ->
                    CropIcon(color = color, modifier = Modifier.size(22.dp))
                }
                BottomModeButton(
                    label = "铺满",
                    active = false,
                    onClick = onFill
                ) { color ->
                    ExpandIcon(color = color, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun EditableQuadOverlay(
    bitmap: Bitmap,
    quad: List<PointF>,
    onQuadChange: (List<PointF>) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentQuad by rememberUpdatedState(quad)
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 7.dp.toPx() }
    val handleInnerRadiusPx = with(density) { 3.5.dp.toPx() }
    val touchTargetRadiusPx = with(density) { 42.dp.toPx() }

    Canvas(
        modifier = modifier.pointerInput(bitmap) {
            var draggingIndex = -1
            detectDragGestures(
                onDragStart = { touch ->
                    val imageRect = calculateImageRect(
                        size = Size(size.width.toFloat(), size.height.toFloat()),
                        bitmap = bitmap
                    )
                    val points = currentQuad.map { normalizedToCanvas(it, imageRect) }
                    draggingIndex = points.indexOfNearest(touch, touchTargetRadiusPx)
                },
                onDragEnd = { draggingIndex = -1 },
                onDragCancel = { draggingIndex = -1 },
                onDrag = { change, dragAmount ->
                    if (draggingIndex == -1) return@detectDragGestures
                    change.consume()
                    val quadSnapshot = currentQuad
                    if (draggingIndex >= quadSnapshot.size) {
                        draggingIndex = -1
                        return@detectDragGestures
                    }
                    val imageRect = calculateImageRect(
                        size = Size(size.width.toFloat(), size.height.toFloat()),
                        bitmap = bitmap
                    )
                    val current = normalizedToCanvas(quadSnapshot[draggingIndex], imageRect)
                    val next = Offset(
                        x = (current.x + dragAmount.x).coerceIn(imageRect.left, imageRect.right),
                        y = (current.y + dragAmount.y).coerceIn(imageRect.top, imageRect.bottom)
                    )
                    onQuadChange(
                        quadSnapshot.mapIndexed { index, point ->
                            if (index == draggingIndex) canvasToNormalized(next, imageRect) else point
                        }
                    )
                }
            )
        }
    ) {
        if (quad.size != 4) return@Canvas

        val imageRect = calculateImageRect(size, bitmap)
        val points = quad.map { normalizedToCanvas(it, imageRect) }

        points.indices.forEach { index ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            drawLine(
                color = HpSmartColors.AccentSoft,
                start = start,
                end = end,
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }

        points.forEach { point ->
            drawCircle(
                color = HpSmartColors.Accent,
                radius = handleRadiusPx,
                center = point
            )
            drawCircle(
                color = HpSmartColors.Surface,
                radius = handleInnerRadiusPx,
                center = point
            )
        }
    }
}

private fun calculateImageRect(size: Size, bitmap: Bitmap): Rect {
    val scale = min(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
    val displayedWidth = bitmap.width * scale
    val displayedHeight = bitmap.height * scale
    val left = (size.width - displayedWidth) / 2f
    val top = (size.height - displayedHeight) / 2f
    return Rect(left, top, left + displayedWidth, top + displayedHeight)
}

private fun List<Offset>.indexOfNearest(target: Offset, maxDistance: Float): Int {
    var bestIndex = -1
    var bestDistance = maxDistance
    forEachIndexed { index, point ->
        val distance = hypot(point.x - target.x, point.y - target.y)
        if (distance <= bestDistance) {
            bestIndex = index
            bestDistance = distance
        }
    }
    return bestIndex
}
