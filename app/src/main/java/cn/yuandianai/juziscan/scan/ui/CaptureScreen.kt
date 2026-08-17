package cn.yuandianai.juziscan.scan.ui

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import cn.yuandianai.juziscan.R
import cn.yuandianai.juziscan.common.*
import cn.yuandianai.juziscan.scan.AutoCaptureUiState
import cn.yuandianai.juziscan.scan.CaptureTask
import cn.yuandianai.juziscan.scan.CaptureModeOption
import cn.yuandianai.juziscan.scan.FlashModeOption
import cn.yuandianai.juziscan.scan.IdCardDocumentType
import cn.yuandianai.juziscan.scan.IdCardLayoutOption
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "需要相机权限才能扫描文档",
            color = HpSmartColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "授权后我们会先进入拍摄页，再到边缘调整和预览页。",
            color = HpSmartColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        ActionPillButton(
            text = "授权相机",
            filled = true,
            onClick = onGrant,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}

@Composable
fun CameraCaptureScreen(
    onPreviewViewReady: (PreviewView) -> Unit,
    captureTask: CaptureTask,
    screenTitle: String,
    screenSubtitle: String,
    captureMode: CaptureModeOption,
    flashMode: FlashModeOption,
    isCapturing: Boolean,
    autoCaptureUiState: AutoCaptureUiState,
    latestPreviewBitmap: Bitmap?,
    latestPreviewRotationDegrees: Float,
    capturedCount: Int,
    allowModeToggle: Boolean,
    showCapturedPreview: Boolean,
    showIdCardGuideOverlay: Boolean,
    selectedIdCardDocumentType: IdCardDocumentType,
    selectedIdCardLayout: IdCardLayoutOption,
    onModeSelected: (CaptureModeOption) -> Unit,
    onFlashModeClick: () -> Unit,
    onSourceClick: () -> Unit,
    onCloseClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onIdCardGuideDismiss: () -> Unit,
    onIdCardDocumentTypeSelected: (IdCardDocumentType) -> Unit,
    onIdCardLayoutSelected: (IdCardLayoutOption) -> Unit
) {
    val isIdCardTask = captureTask == CaptureTask.IdCardFront || captureTask == CaptureTask.IdCardBack

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.CameraChrome)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .zIndex(4f)
                .background(HpSmartColors.CameraChrome)
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopIconButton(onClick = onCloseClick) {
                if (isIdCardTask) {
                    BackArrowIcon(color = HpSmartColors.CameraText, modifier = Modifier.size(22.dp))
                } else {
                    CloseCrossIcon(color = HpSmartColors.CameraText, modifier = Modifier.size(22.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = screenTitle,
                    color = HpSmartColors.CameraText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TopIconButton(onClick = onFlashModeClick) {
                    FlashModeIcon(
                        mode = flashMode,
                        color = HpSmartColors.CameraText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .zIndex(0f),
            contentAlignment = if (isIdCardTask) Alignment.TopCenter else Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(top = if (isIdCardTask) 12.dp else 0.dp)
                    .fillMaxHeight()
                    .background(HpSmartColors.CameraChrome)
                    .clipToBounds()
                    .then(
                        if (isIdCardTask) Modifier.fillMaxWidth()
                        else Modifier.aspectRatio(3f / 4f)
                    )
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            onPreviewViewReady(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { onPreviewViewReady(it) }
                )

                if (!isIdCardTask || !showIdCardGuideOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.05f))
                    )
                }

                if (!isIdCardTask) {
                    CameraGuideOverlay(
                        modifier = Modifier.fillMaxSize(),
                        isIdCard = false
                    )
                }

                if (captureMode == CaptureModeOption.Auto && autoCaptureUiState.liveQuad.size == 4) {
                    LiveDocumentShadowOverlay(
                        quad = autoCaptureUiState.liveQuad,
                        isStable = autoCaptureUiState.isStable,
                        progress = autoCaptureUiState.stableProgress,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isIdCardTask && showIdCardGuideOverlay) {
                    IdCardCaptureOverlay(
                        selectedDocumentType = selectedIdCardDocumentType,
                        selectedLayout = selectedIdCardLayout,
                        onDocumentTypeSelected = onIdCardDocumentTypeSelected,
                        onLayoutSelected = onIdCardLayoutSelected,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 14.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        BubbleHint(
                            text = if (captureMode == CaptureModeOption.Auto) {
                                autoCaptureUiState.hintText
                            } else {
                                screenSubtitle
                            }
                        )
                    }
                }
            }
        }

        if (!isIdCardTask || !showIdCardGuideOverlay) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HpSmartColors.CameraChrome)
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (allowModeToggle) {
                    CaptureTabBar(
                        selected = captureMode,
                        onTabSelected = onModeSelected,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        SmallActionDock(
                            label = "相册导入",
                            iconContainerColor = Color.Transparent,
                            iconContainerSize = 52.dp,
                            labelFontSize = 12.sp,
                            labelTopPadding = 0.dp,
                            onClick = onSourceClick
                        ) {
                            GalleryPhotoIcon(color = Color.White, modifier = Modifier.size(42.dp))
                        }
                    }

                    ShutterButton(
                        isCapturing = isCapturing,
                        enabled = captureMode == CaptureModeOption.Manual,
                        progress = if (captureMode == CaptureModeOption.Auto) {
                            autoCaptureUiState.stableProgress
                        } else {
                            0f
                        },
                        onClick = onCaptureClick
                    )

                    if (showCapturedPreview && captureMode == CaptureModeOption.Auto && latestPreviewBitmap != null && capturedCount > 0) {
                        Box(
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            AutoCapturePreviewDock(
                                previewBitmap = latestPreviewBitmap,
                                rotationDegrees = latestPreviewRotationDegrees,
                                capturedCount = capturedCount,
                                onClick = onPreviewClick
                            )
                        }
                    }
                }
            }
        } else {
            IdCardGuideBottomPanel(
                selectedDocumentType = selectedIdCardDocumentType,
                selectedLayout = selectedIdCardLayout,
                onStartScan = onIdCardGuideDismiss,
                onDocumentTypeSelected = onIdCardDocumentTypeSelected,
                onMoreDocumentSelected = {
                    onIdCardDocumentTypeSelected(it)
                    onIdCardGuideDismiss()
                },
                onLayoutSelected = onIdCardLayoutSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HpSmartColors.CameraChrome)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TopIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color.Transparent, CircleShape)
            .pointerInput(Unit) { detectTapGesturesCompat(onClick) },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun CaptureTabBar(
    selected: CaptureModeOption,
    onTabSelected: (CaptureModeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CaptureModeOption.entries.forEach { tab ->
            val active = tab == selected
            Text(
                text = tab.label,
                color = if (active) Color.White else HpSmartColors.CameraText.copy(alpha = 0.5f),
                fontSize = 16.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .pointerInput(tab) {
                        detectTapGesturesCompat { onTabSelected(tab) }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun CaptureModeStatusPill(
    mode: CaptureModeOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAuto = mode == CaptureModeOption.Auto
    Row(
        modifier = modifier
            .background(
                color = if (isAuto) HpSmartColors.Accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(999.dp)
            )
            .pointerInput(mode) {
                detectTapGesturesCompat(onClick)
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(
                    color = if (isAuto) HpSmartColors.AccentSoft else HpSmartColors.CameraText.copy(alpha = 0.6f),
                    shape = CircleShape
                )
        )
        Text(
            text = if (isAuto) "自动" else "手动",
            color = if (isAuto) HpSmartColors.AccentSoft else HpSmartColors.CameraText.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AutoCapturePreviewDock(
    previewBitmap: Bitmap,
    rotationDegrees: Float,
    capturedCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(76.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "已拍页面预览",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotationDegrees)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-2).dp, y = 2.dp)
                .background(HpSmartColors.Accent, CircleShape)
                .border(2.dp, HpSmartColors.CameraChrome, CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = capturedCount.toString(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CameraGuideOverlay(
    modifier: Modifier = Modifier,
    isIdCard: Boolean = false
) {
    Canvas(modifier = modifier) {
        val guideRect = Rect(
            left = if (isIdCard) size.width * 0.12f else size.width * 0.04f,
            top = if (isIdCard) size.height * 0.18f else size.height * 0.04f,
            right = if (isIdCard) size.width * 0.88f else size.width * 0.96f,
            bottom = if (isIdCard) size.height * 0.86f else size.height * 0.96f
        )
        val cornerLength = 24.dp.toPx()
        val guideStroke = 2.0.dp.toPx()
        val cornerInset = 6.dp.toPx()

        drawCorner(
            guideRect.left + cornerInset,
            guideRect.top + cornerInset,
            cornerLength,
            guideStroke,
            true,
            true
        )
        drawCorner(
            guideRect.right - cornerInset,
            guideRect.top + cornerInset,
            cornerLength,
            guideStroke,
            false,
            true
        )
        drawCorner(
            guideRect.left + cornerInset,
            guideRect.bottom - cornerInset,
            cornerLength,
            guideStroke,
            true,
            false
        )
        drawCorner(
            guideRect.right - cornerInset,
            guideRect.bottom - cornerInset,
            cornerLength,
            guideStroke,
            false,
            false
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCorner(
    x: Float,
    y: Float,
    length: Float,
    stroke: Float,
    left: Boolean,
    top: Boolean
) {
    val horizontalEnd = if (left) x + length else x - length
    val verticalEnd = if (top) y + length else y - length
    drawLine(
        color = Color.White,
        start = Offset(x, y),
        end = Offset(horizontalEnd, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White,
        start = Offset(x, y),
        end = Offset(x, verticalEnd),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}

@Composable
private fun LiveDocumentShadowOverlay(
    quad: List<PointF>,
    isStable: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (quad.size != 4) return@Canvas

        val previewRect = Rect(0f, 0f, size.width, size.height)
        val points = quad.map { normalizedToCanvas(it, previewRect) }
        val overlayPath = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addRect(previewRect)
            moveTo(points[0].x, points[0].y)
            for (index in 1 until points.size) {
                lineTo(points[index].x, points[index].y)
            }
            close()
        }

        drawPath(
            path = overlayPath,
            color = Color.Black.copy(alpha = if (isStable) 0.26f else 0.18f)
        )

        val strokeColor = if (isStable) {
            HpSmartColors.AccentSoft
        } else {
            Color.White.copy(alpha = 0.72f)
        }
        val strokeWidth = if (isStable) 4.dp.toPx() else 2.5.dp.toPx()
        points.indices.forEach { index ->
            drawLine(
                color = strokeColor,
                start = points[index],
                end = points[(index + 1) % points.size],
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        if (progress > 0f) {
            val highlightAlpha = (0.10f + 0.16f * progress.coerceIn(0f, 1f))
            val quadPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(points[0].x, points[0].y)
                for (index in 1 until points.size) {
                    lineTo(points[index].x, points[index].y)
                }
                close()
            }
            drawPath(
                path = quadPath,
                color = HpSmartColors.Accent.copy(alpha = highlightAlpha)
            )
        }
    }
}

@Composable
private fun IdCardCaptureOverlay(
    selectedDocumentType: IdCardDocumentType,
    selectedLayout: IdCardLayoutOption,
    onDocumentTypeSelected: (IdCardDocumentType) -> Unit,
    onLayoutSelected: (IdCardLayoutOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(270.dp)
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F8FF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "下图为A4纸效果",
                        color = Color(0xFF4A557B),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${selectedDocumentType.label}  ·  ${selectedDocumentType.sizeLabel}",
                        color = Color(0xFF7A89AE),
                        fontSize = 12.sp
                    )
                    IdCardPaperPreview(
                        layout = selectedLayout,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IdCardStartScanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "id_card_scan_breath")
    val scale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350),
            repeatMode = RepeatMode.Reverse
        ),
        label = "id_card_scan_scale"
    )
    val glowAlpha = infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350),
            repeatMode = RepeatMode.Reverse
        ),
        label = "id_card_scan_glow"
    )
    Box(
        modifier = modifier
            .scale(scale.value)
            .background(HpSmartColors.Accent.copy(alpha = glowAlpha.value), RoundedCornerShape(999.dp))
            .padding(horizontal = 4.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(HpSmartColors.Accent, RoundedCornerShape(999.dp))
            .pointerInput(Unit) { detectTapGesturesCompat(onClick) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "立即扫描",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun IdCardGuideBottomPanel(
    selectedDocumentType: IdCardDocumentType,
    selectedLayout: IdCardLayoutOption,
    onStartScan: () -> Unit,
    onDocumentTypeSelected: (IdCardDocumentType) -> Unit,
    onMoreDocumentSelected: (IdCardDocumentType) -> Unit,
    onLayoutSelected: (IdCardLayoutOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreDocuments by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MoreIdCardDocumentChip(
                onClick = { showMoreDocuments = true }
            )
            IdCardDocumentType.commonEntries.forEach { type ->
                IdCardDocumentChip(
                    type = type,
                    selected = type == selectedDocumentType,
                    onClick = { onDocumentTypeSelected(type) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IdCardLayoutOption.entries.forEach { layout ->
                IdCardLayoutChip(
                    layout = layout,
                    selected = layout == selectedLayout,
                    onClick = { onLayoutSelected(layout) }
                )
            }
        }

        PrivacyProtectionText(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp)
        )

        IdCardStartScanButton(
            onClick = onStartScan,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp)
        )
    }

    if (showMoreDocuments) {
        MoreIdCardDocumentDialog(
            selectedDocumentType = selectedDocumentType,
            onDismiss = { showMoreDocuments = false },
            onDocumentSelected = { type ->
                showMoreDocuments = false
                onMoreDocumentSelected(type)
            }
        )
    }
}

@Composable
private fun PrivacyProtectionText(
    modifier: Modifier = Modifier
) {
    Text(
        text = "隐私保护已开启，证件信息不会被识别和使用。",
        color = Color.White.copy(alpha = 0.42f),
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
private fun IdCardPaperPreview(
    layout: IdCardLayoutOption,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(112.dp)
            .height(172.dp)
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        val slotCount = remember(layout) { layout.columns * layout.rows }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(layout.rows) { rowIndex ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(layout.columns) { columnIndex ->
                        val active = rowIndex * layout.columns + columnIndex < minOf(2, slotCount)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    color = if (active) Color(0xFFBFD1FF) else Color(0xFFF4F6FB),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            if (active) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth(0.72f)
                                        .fillMaxHeight(0.52f)
                                        .background(Color(0xFF8EA9FF), RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdCardDocumentChip(
    type: IdCardDocumentType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val aspectRatio = remember(type) { type.widthMm / type.heightMm }
    val isLandscape = aspectRatio >= 1f
    val previewScale = remember(type) {
        when {
            type == IdCardDocumentType.Passport -> 1.08f
            aspectRatio < 0.78f -> 1.22f
            aspectRatio < 1f -> 1.12f
            else -> 1f
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .pointerInput(type) { detectTapGesturesCompat(onClick) }
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    color = if (selected) Color(0xFFE6EEFF) else Color(0x0FFFFFFF),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = if (selected) 1.5.dp else 0.dp,
                    color = if (selected) HpSmartColors.Accent else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(type.previewRes()),
                contentDescription = type.label,
                contentScale = ContentScale.Fit,
                modifier = if (isLandscape) {
                    Modifier
                        .fillMaxWidth(0.88f)
                        .aspectRatio(aspectRatio)
                } else {
                    Modifier
                        .fillMaxHeight(0.92f)
                        .aspectRatio(aspectRatio)
                        .scale(previewScale)
                }
            )
        }
        Text(
            text = type.label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 2
        )
    }
}

@Composable
private fun MoreIdCardDocumentChip(
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .pointerInput(Unit) { detectTapGesturesCompat(onClick) }
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(HpSmartColors.Accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                PlusIcon(color = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text = "更多",
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 2
        )
    }
}

@Composable
private fun MoreIdCardDocumentDialog(
    selectedDocumentType: IdCardDocumentType,
    onDismiss: () -> Unit,
    onDocumentSelected: (IdCardDocumentType) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val sections = remember(query) {
        IdCardDocumentType.groupedEntries.mapNotNull { (title, items) ->
            val filtered = items.filter { it.matches(query) }
            if (filtered.isEmpty()) null else title to filtered
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF151924),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "更多证件",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "选择后直接进入拍摄",
                            color = Color.White.copy(alpha = 0.58f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    TopIconButton(onClick = onDismiss) {
                        CloseCrossIcon(color = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                SearchInput(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .padding(top = 14.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (sections.isEmpty()) {
                        Text(
                            text = "没有找到相关证件",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        sections.forEach { (title, items) ->
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = title,
                                    color = Color.White.copy(alpha = 0.54f),
                                    fontSize = 12.sp
                                )
                                items.forEach { type ->
                                    MoreDocumentRow(
                                        type = type,
                                        selected = type == selectedDocumentType,
                                        onClick = { onDocumentSelected(type) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color.White,
            fontSize = 14.sp
        ),
        modifier = modifier
    ) { innerTextField ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "搜",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Box(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                if (value.isBlank()) {
                    Text(
                        text = "搜索证件名称",
                        color = Color.White.copy(alpha = 0.38f),
                        fontSize = 14.sp
                    )
                }
                innerTextField()
            }
        }
    }
}

@Composable
private fun MoreDocumentRow(
    type: IdCardDocumentType,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) HpSmartColors.Accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(18.dp)
            )
            .border(
                if (selected) 1.dp else 0.dp,
                if (selected) HpSmartColors.AccentSoft else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
            .pointerInput(type.key) { detectTapGesturesCompat(onClick) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = type.label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${type.category} · ${type.sizeLabel}",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            text = "拍摄",
            color = if (selected) HpSmartColors.AccentSoft else Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun IdCardDocumentType.previewRes(): Int = when (this) {
    IdCardDocumentType.IdentityCard -> R.drawable.id_doc_identity_card
    IdCardDocumentType.BankCard -> R.drawable.id_doc_bank_card
    IdCardDocumentType.HouseholdRegister -> R.drawable.id_doc_household_register
    IdCardDocumentType.Invoice -> R.drawable.id_doc_invoice
    IdCardDocumentType.Passport -> R.drawable.id_doc_passport
    IdCardDocumentType.DriverLicense -> R.drawable.id_doc_driver_license
    IdCardDocumentType.VehicleLicense -> R.drawable.id_doc_vehicle_license
    IdCardDocumentType.PropertyCertificate -> R.drawable.id_doc_property_certificate
    IdCardDocumentType.BusinessLicense -> R.drawable.id_doc_business_license
    IdCardDocumentType.Diploma -> R.drawable.id_doc_diploma
    IdCardDocumentType.DegreeCertificate -> R.drawable.id_doc_degree_certificate
    else -> R.drawable.id_doc_identity_card
}

@Composable
private fun IdCardLayoutChip(
    layout: IdCardLayoutOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 74.dp, height = 86.dp)
            .background(
                color = if (selected) Color(0x120B63FF) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .pointerInput(layout) { detectTapGesturesCompat(onClick) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = if (selected) HpSmartColors.Accent else Color.White.copy(alpha = 0.55f),
                style = Stroke(
                    width = if (selected) 2.dp.toPx() else 1.dp.toPx(),
                    pathEffect = if (selected) null else PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 42.dp)
                    .border(
                        width = 1.dp,
                        color = if (selected) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.46f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(layout.rows) { rowIndex ->
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            repeat(layout.columns) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            if (selected || rowIndex < 2) Color.White.copy(alpha = 0.88f)
                                            else Color.White.copy(alpha = 0.36f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
