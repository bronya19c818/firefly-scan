package cn.yuandianai.juziscan.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import cn.yuandianai.juziscan.scan.FlashModeOption
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

@Composable
fun BackArrowIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx()
        drawLine(color = color, start = Offset(w * 0.75f, h * 0.5f), end = Offset(w * 0.25f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.25f, h * 0.5f), end = Offset(w * 0.48f, h * 0.28f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.25f, h * 0.5f), end = Offset(w * 0.48f, h * 0.72f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun CloseCrossIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx()
        drawLine(color = color, start = Offset(w * 0.28f, h * 0.28f), end = Offset(w * 0.72f, h * 0.72f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.72f, h * 0.28f), end = Offset(w * 0.28f, h * 0.72f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun FlashModeIcon(
    mode: FlashModeOption,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.58f, h * 0.12f)
            lineTo(w * 0.28f, h * 0.56f)
            lineTo(w * 0.50f, h * 0.56f)
            lineTo(w * 0.42f, h * 0.88f)
            lineTo(w * 0.72f, h * 0.44f)
            lineTo(w * 0.50f, h * 0.44f)
            close()
        }
        drawPath(path = path, color = color)

        when (mode) {
            FlashModeOption.Auto -> {
                val stroke = 2.0.dp.toPx()
                val aPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.15f, h * 0.86f)
                    lineTo(w * 0.24f, h * 0.62f)
                    lineTo(w * 0.33f, h * 0.86f)
                    moveTo(w * 0.18f, h * 0.78f)
                    lineTo(w * 0.30f, h * 0.78f)
                }
                drawPath(
                    path = aPath,
                    color = color,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
            }
            FlashModeOption.Off -> {
                val stroke = 2.3.dp.toPx()
                drawLine(
                    color = color,
                    start = Offset(w * 0.18f, h * 0.82f),
                    end = Offset(w * 0.82f, h * 0.18f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            FlashModeOption.On -> Unit
        }
    }
}

@Composable
fun SettingsGearIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 2.2.dp.toPx()
        drawCircle(color = color, radius = size.minDimension * 0.16f, style = Stroke(width = stroke))
        drawCircle(color = color, radius = size.minDimension * 0.32f, style = Stroke(width = stroke))
        val center = Offset(size.width / 2, size.height / 2)
        val numTeeth = 8
        val innerR = size.minDimension * 0.32f
        val outerR = size.minDimension * 0.44f
        for (i in 0 until numTeeth) {
            val angle = (i * 2 * Math.PI / numTeeth).toFloat()
            val cos = kotlin.math.cos(angle)
            val sin = kotlin.math.sin(angle)
            drawLine(
                color = color,
                start = Offset(center.x + innerR * cos, center.y + innerR * sin),
                end = Offset(center.x + outerR * cos, center.y + outerR * sin),
                strokeWidth = stroke * 1.4f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun GalleryPhotoIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.18f, h * 0.22f),
            size = Size(w * 0.64f, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = color,
            radius = w * 0.06f,
            center = Offset(w * 0.36f, h * 0.40f)
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.23f, h * 0.72f)
            lineTo(w * 0.44f, h * 0.48f)
            lineTo(w * 0.60f, h * 0.66f)
            lineTo(w * 0.70f, h * 0.54f)
            lineTo(w * 0.77f, h * 0.72f)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun PlusIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx()
        drawLine(color = color, start = Offset(w * 0.25f, h * 0.5f), end = Offset(w * 0.75f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.5f, h * 0.25f), end = Offset(w * 0.5f, h * 0.75f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun RotateArrowIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.56f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val arrowPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.48f, h * 0.04f)
            lineTo(w * 0.76f, h * 0.18f)
            lineTo(w * 0.50f, h * 0.38f)
        }
        drawPath(path = arrowPath, color = color, style = Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun CropIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx()
        drawLine(color = color, start = Offset(w * 0.35f, h * 0.15f), end = Offset(w * 0.35f, h * 0.75f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.25f, h * 0.25f), end = Offset(w * 0.85f, h * 0.25f), strokeWidth = stroke, cap = StrokeCap.Round)
        
        drawLine(color = color, start = Offset(w * 0.65f, h * 0.85f), end = Offset(w * 0.65f, h * 0.25f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.75f, h * 0.75f), end = Offset(w * 0.15f, h * 0.75f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun ExpandIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.15f, h * 0.15f),
            size = Size(w * 0.7f, h * 0.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(color = color, start = Offset(w * 0.35f, h * 0.35f), end = Offset(w * 0.22f, h * 0.22f), strokeWidth = stroke)
        drawLine(color = color, start = Offset(w * 0.22f, h * 0.22f), end = Offset(w * 0.34f, h * 0.22f), strokeWidth = stroke)
        drawLine(color = color, start = Offset(w * 0.22f, h * 0.22f), end = Offset(w * 0.22f, h * 0.34f), strokeWidth = stroke)
        
        drawLine(color = color, start = Offset(w * 0.65f, h * 0.65f), end = Offset(w * 0.78f, h * 0.78f), strokeWidth = stroke)
        drawLine(color = color, start = Offset(w * 0.78f, h * 0.78f), end = Offset(w * 0.66f, h * 0.78f), strokeWidth = stroke)
        drawLine(color = color, start = Offset(w * 0.78f, h * 0.78f), end = Offset(w * 0.78f, h * 0.66f), strokeWidth = stroke)
    }
}

@Composable
fun ShareUploadIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        val boxPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, h * 0.45f)
            lineTo(w * 0.25f, h * 0.82f)
            lineTo(w * 0.75f, h * 0.82f)
            lineTo(w * 0.75f, h * 0.45f)
        }
        drawPath(path = boxPath, color = color, style = Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawLine(color = color, start = Offset(w * 0.5f, h * 0.62f), end = Offset(w * 0.5f, h * 0.18f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.32f, h * 0.34f), end = Offset(w * 0.5f, h * 0.18f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.68f, h * 0.34f), end = Offset(w * 0.5f, h * 0.18f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun RetakeRefreshIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.56f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val arrowPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.35f, h * 0.08f)
            lineTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.35f, h * 0.36f)
        }
        drawPath(path = arrowPath, color = color, style = Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun BlueCheckIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color = HpSmartColors.Accent, radius = w / 2f)
        val stroke = 2.2.dp.toPx()
        drawLine(
            color = Color.White,
            start = Offset(w * 0.32f, h * 0.52f),
            end = Offset(w * 0.46f, h * 0.66f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(w * 0.46f, h * 0.66f),
            end = Offset(w * 0.72f, h * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun ChevronRightIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0.dp.toPx()
        drawLine(color = color, start = Offset(w * 0.35f, h * 0.25f), end = Offset(w * 0.65f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.65f, h * 0.5f), end = Offset(w * 0.35f, h * 0.75f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun PrintIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.9.dp.toPx()
        val width = size.width
        val height = size.height
        val bodyTop = height * 0.36f
        val bodyBottom = height * 0.74f

        drawRoundRect(
            color = color,
            topLeft = Offset(width * 0.2f, bodyTop),
            size = Size(width * 0.6f, bodyBottom - bodyTop),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(width * 0.28f, height * 0.12f),
            size = Size(width * 0.44f, height * 0.22f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(width * 0.28f, height * 0.58f),
            size = Size(width * 0.44f, height * 0.22f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(width * 0.34f, height * 0.18f),
            end = Offset(width * 0.66f, height * 0.18f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(width * 0.34f, height * 0.64f),
            end = Offset(width * 0.66f, height * 0.64f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = color,
            radius = stroke * 0.7f,
            center = Offset(width * 0.68f, height * 0.48f)
        )
    }
}

@Composable
fun ReorderIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.1f, h * 0.25f),
            size = Size(w * 0.25f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.65f, h * 0.25f),
            size = Size(w * 0.25f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )

        drawLine(
            color = color,
            start = Offset(w * 0.4f, h * 0.42f),
            end = Offset(w * 0.6f, h * 0.42f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.54f, h * 0.34f),
            end = Offset(w * 0.6f, h * 0.42f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = Offset(w * 0.4f, h * 0.58f),
            end = Offset(w * 0.6f, h * 0.58f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.46f, h * 0.66f),
            end = Offset(w * 0.4f, h * 0.58f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun PencilIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        drawLine(color = color, start = Offset(w * 0.25f, h * 0.75f), end = Offset(w * 0.75f, h * 0.25f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.35f, h * 0.85f), end = Offset(w * 0.85f, h * 0.35f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.75f, h * 0.25f), end = Offset(w * 0.85f, h * 0.35f), strokeWidth = stroke, cap = StrokeCap.Round)

        val tipPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, h * 0.75f)
            lineTo(w * 0.15f, h * 0.85f)
            lineTo(w * 0.35f, h * 0.85f)
            close()
        }
        drawPath(path = tipPath, color = color)
    }
}

@Composable
fun ReplaceArrowsIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.2f),
            size = Size(w * 0.6f, h * 0.6f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.2f),
            size = Size(w * 0.6f, h * 0.6f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val path1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.8f, h * 0.4f)
            lineTo(w * 0.8f, h * 0.2f)
            lineTo(w * 0.6f, h * 0.2f)
        }
        drawPath(path = path1, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))

        val path2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.2f, h * 0.6f)
            lineTo(w * 0.2f, h * 0.8f)
            lineTo(w * 0.4f, h * 0.8f)
        }
        drawPath(path = path2, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun TrashBinIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        drawLine(color = color, start = Offset(w * 0.2f, h * 0.25f), end = Offset(w * 0.8f, h * 0.25f), strokeWidth = stroke, cap = StrokeCap.Round)

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.4f, h * 0.15f),
            size = Size(w * 0.2f, h * 0.1f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            style = Stroke(width = stroke)
        )

        val bodyPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.28f, h * 0.25f)
            lineTo(w * 0.32f, h * 0.85f)
            lineTo(w * 0.68f, h * 0.85f)
            lineTo(w * 0.72f, h * 0.25f)
        }
        drawPath(path = bodyPath, color = color, style = Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))

        drawLine(color = color, start = Offset(w * 0.43f, h * 0.35f), end = Offset(w * 0.43f, h * 0.75f), strokeWidth = stroke)
        drawLine(color = color, start = Offset(w * 0.57f, h * 0.35f), end = Offset(w * 0.57f, h * 0.75f), strokeWidth = stroke)
    }
}
