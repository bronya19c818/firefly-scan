package cn.yuandianai.juziscan.scan.ui

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.common.*
import cn.yuandianai.juziscan.scan.CaptureModeOption
import cn.yuandianai.juziscan.scan.ExportFileSize
import cn.yuandianai.juziscan.scan.ExportFileType
import cn.yuandianai.juziscan.scan.normalizePageRotation
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import kotlin.math.min
import kotlin.math.roundToInt

suspend fun PointerInputScope.detectTapGesturesCompat(
    onTap: () -> Unit
) {
    detectTapGestures(onTap = { onTap() })
}

@Composable
fun SimpleTitleBar(
    title: String,
    onBack: () -> Unit,
    actionText: String,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        TopSoftButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = title,
            color = HpSmartColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (actionText.isNotBlank() && onAction != null) {
            ActionPillButton(
                text = actionText,
                filled = false,
                onClick = onAction,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
fun TopSoftButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .background(HpSmartColors.SurfaceSoft, CircleShape)
            .pointerInput(Unit) { detectTapGesturesCompat(onClick) },
        contentAlignment = Alignment.Center
    ) {
        BackArrowIcon(
            color = HpSmartColors.TextPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ActionPillButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val bg = containerColor ?: if (filled) HpSmartColors.Accent else HpSmartColors.SurfaceSoft
    val tc = contentColor ?: if (filled) Color.White else HpSmartColors.TextPrimary
    Box(
        modifier = modifier
            .background(
                color = bg,
                shape = RoundedCornerShape(24.dp)
            )
            .pointerInput(text) { detectTapGesturesCompat(onClick) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = tc,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun OutlineActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, HpSmartColors.TextSecondary.copy(alpha = 0.35f)),
        modifier = modifier.pointerInput(text) { detectTapGesturesCompat(onClick) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = HpSmartColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun BubbleHint(text: String) {
    Box(
        modifier = Modifier
            .background(HpSmartColors.Surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = HpSmartColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SmallActionDock(
    label: String,
    onClick: () -> Unit,
    iconContainerColor: Color = HpSmartColors.CameraPill,
    iconContainerSize: Dp = 68.dp,
    labelFontSize: TextUnit = 14.sp,
    labelTopPadding: Dp = 10.dp,
    icon: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pointerInput(label) { detectTapGesturesCompat(onClick) }
    ) {
        Box(
            modifier = Modifier
                .size(iconContainerSize)
                .background(iconContainerColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text = label,
            color = HpSmartColors.CameraText,
            fontSize = labelFontSize,
            modifier = Modifier.padding(top = labelTopPadding)
        )
    }
}

@Composable
fun ShutterButton(
    isCapturing: Boolean,
    enabled: Boolean,
    progress: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .pointerInput(isCapturing, enabled) {
                if (!isCapturing && enabled) detectTapGesturesCompat(onClick)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 5.dp.toPx()
            drawCircle(
                color = HpSmartColors.Surface.copy(alpha = if (enabled) 1f else 0.42f),
                radius = (size.minDimension / 2f) - (strokeWidth / 2f),
                style = Stroke(width = strokeWidth)
            )
            drawCircle(
                color = HpSmartColors.Surface.copy(alpha = if (enabled) 1f else 0.32f),
                radius = size.minDimension / 2.8f
            )
            if (!enabled && progress > 0f) {
                drawArc(
                    color = HpSmartColors.AccentSoft,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        if (isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = HpSmartColors.Accent,
                strokeWidth = 3.dp
            )
        } else if (!enabled) {
            Text(
                text = "AUTO",
                color = HpSmartColors.CameraText.copy(alpha = 0.82f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BottomModeButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pointerInput(label) { detectTapGesturesCompat(onClick) }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    if (active) HpSmartColors.Accent else Color.Transparent,
                    CircleShape
                )
                .then(
                    if (!active) {
                        Modifier.border(1.5.dp, HpSmartColors.TextSecondary.copy(alpha = 0.35f), CircleShape)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            icon(if (active) Color.White else HpSmartColors.Accent)
        }
        Text(
            text = label,
            color = if (active) HpSmartColors.Accent else HpSmartColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun FloatingMoreButton(
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(10.dp)
            .size(36.dp)
            .background(HpSmartColors.Accent, CircleShape)
            .pointerInput(Unit) { detectTapGesturesCompat(onClick) },
        contentAlignment = Alignment.Center
    ) {
        if (isOpen) {
            CloseCrossIcon(color = Color.White, modifier = Modifier.size(16.dp))
        } else {
            Text(
                text = "···",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

@Composable
fun PreviewBottomAction(
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .pointerInput(label) { detectTapGesturesCompat(onClick) }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(1.5.dp, HpSmartColors.TextSecondary.copy(alpha = 0.35f), CircleShape)
                .background(Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon(HpSmartColors.Accent)
        }
        Text(
            text = label,
            color = HpSmartColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BackPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(HpSmartColors.SurfaceSoft, RoundedCornerShape(22.dp))
            .pointerInput(Unit) { detectTapGesturesCompat(onClick) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BackArrowIcon(
            color = HpSmartColors.TextPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "导出分享",
            color = HpSmartColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun CorrectionModeChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (active) HpSmartColors.Accent else HpSmartColors.TextSecondary,
        fontSize = 15.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .pointerInput(label) { detectTapGesturesCompat(onClick) }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
fun ProcessingSpinner(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing_spinner")
    
    val rotationOuter by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing)
        ),
        label = "outer_rotation"
    )

    val rotationInner by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing)
        ),
        label = "inner_rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val accentColor = HpSmartColors.Accent
    val accentSoft = HpSmartColors.AccentSoft

    Box(
        modifier = modifier.size(68.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotationOuter)
        ) {
            val strokeWidth = 3.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            
            drawCircle(
                color = accentColor.copy(alpha = 0.06f),
                radius = radius,
                style = Stroke(width = strokeWidth)
            )

            val sweepGradient = Brush.sweepGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0f),
                    accentColor.copy(alpha = 0.2f),
                    accentColor.copy(alpha = 0.6f),
                    accentColor
                ),
                center = center
            )

            drawArc(
                brush = sweepGradient,
                startAngle = -270f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawCircle(
                color = accentColor.copy(alpha = 0.25f),
                radius = 5.dp.toPx() * pulseScale,
                center = Offset(center.x + radius, center.y)
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(center.x + radius, center.y)
            )
        }

        Canvas(
            modifier = Modifier
                .size(44.dp)
                .rotate(rotationInner)
        ) {
            val strokeWidth = 1.5.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f

            drawCircle(
                color = accentSoft.copy(alpha = 0.04f),
                radius = radius,
                style = Stroke(width = strokeWidth)
            )

            val sweepGradientInner = Brush.sweepGradient(
                colors = listOf(
                    accentSoft.copy(alpha = 0f),
                    accentSoft.copy(alpha = 0.25f),
                    accentSoft
                ),
                center = center
            )

            drawArc(
                brush = sweepGradientInner,
                startAngle = -180f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun PreviewLoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        SimpleTitleBar(
            title = "预览",
            onBack = {},
            actionText = "",
            onAction = null,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
fun RotatedPreviewImage(
    bitmap: Bitmap,
    rotationDegrees: Float,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val normalizedRotation = normalizePageRotation(rotationDegrees)
        val quarterTurns = ((normalizedRotation / 90f).roundToInt() % 4 + 4) % 4
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val rotatedWidthPx = if (quarterTurns % 2 == 0) bitmap.width.toFloat() else bitmap.height.toFloat()
        val rotatedHeightPx = if (quarterTurns % 2 == 0) bitmap.height.toFloat() else bitmap.width.toFloat()
        val scale = min(
            containerWidthPx / rotatedWidthPx.coerceAtLeast(1f),
            containerHeightPx / rotatedHeightPx.coerceAtLeast(1f)
        )
        val displayRotatedWidthDp = with(density) { (rotatedWidthPx * scale).toDp() }
        val displayRotatedHeightDp = with(density) { (rotatedHeightPx * scale).toDp() }
        val imageModifier = if (quarterTurns % 2 == 0) {
            Modifier
                .width(displayRotatedWidthDp)
                .height(displayRotatedHeightDp)
        } else {
            Modifier
                .width(displayRotatedHeightDp)
                .height(displayRotatedWidthDp)
        }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = imageModifier.rotate(normalizedRotation),
            contentScale = ContentScale.Fit
        )
    }
}

fun normalizedToCanvas(point: PointF, imageRect: Rect): Offset {
    return Offset(
        x = imageRect.left + point.x * imageRect.width,
        y = imageRect.top + point.y * imageRect.height
    )
}

fun canvasToNormalized(offset: Offset, imageRect: Rect): PointF {
    return PointF(
        ((offset.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
        ((offset.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
    )
}
