package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import cn.yuandianai.juziscan.scan.IdCardDocumentType
import cn.yuandianai.juziscan.scan.IdCardLayoutOption
import cn.yuandianai.juziscan.scan.OcrUiState
import cn.yuandianai.juziscan.scan.ScannedPage
import cn.yuandianai.juziscan.common.RotateArrowIcon
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

@Composable
fun OcrResultScreen(
    page: ScannedPage,
    ocrUiState: OcrUiState,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onCopyText: () -> Unit,
    onRetryRecognition: () -> Unit,
    onTextChange: (String) -> Unit,
    onExportWord: () -> Unit,
    onExportTxt: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SimpleTitleBar(
            title = "文字识别结果",
            onBack = onBack,
            actionText = "",
            onAction = null,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.34f)
            ) {
                OutlineActionPill(
                    text = "重新识别",
                    onClick = onRetryRecognition,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RotateArrowIcon(color = HpSmartColors.TextPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OcrSourcePreviewCard(
                page = page,
                onRetake = onRetake
            )

            when {
                ocrUiState.isRecognizing -> {
                    ResultHintCard(
                        title = "正在识别",
                        body = "已开始提取图片中的文字，通常很快就会返回结果。"
                    )
                }

                ocrUiState.errorMessage != null -> {
                    OcrStatusCard(
                        title = "识别失败",
                        body = ocrUiState.errorMessage,
                        primaryText = "重新识别",
                        secondaryText = "重新拍摄",
                        onPrimary = onRetryRecognition,
                        onSecondary = onRetake
                    )
                }

                ocrUiState.recognizedText.isBlank() -> {
                    OcrStatusCard(
                        title = "未识别到文字",
                        body = "当前图片里没有提取到可用文本，可以重新拍摄或再试一次。",
                        primaryText = "重新识别",
                        secondaryText = "重新拍摄",
                        onPrimary = onRetryRecognition,
                        onSecondary = onRetake
                    )
                }

                else -> {
                    EditableOcrTextCard(
                        text = ocrUiState.recognizedText,
                        engineName = ocrUiState.engineName,
                        lineCount = ocrUiState.lineCount,
                        blockCount = ocrUiState.blockCount,
                        paragraphCount = ocrUiState.paragraphCount,
                        onTextChange = onTextChange
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompactActionButton(
                text = "复制文字",
                filled = false,
                onClick = onCopyText,
                modifier = Modifier.weight(1f)
            )
            CompactActionButton(
                text = "导出 TXT",
                filled = false,
                onClick = onExportTxt,
                modifier = Modifier.weight(1f)
            )
            CompactActionButton(
                text = "导出 Word",
                filled = true,
                onClick = onExportWord,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TableResultScreen(
    page: ScannedPage,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onSaveImage: () -> Unit
) {
    SinglePageResultScaffold(
        title = "表格识别",
        subtitle = "这一步已经拿到了校正后的表格图片，后续可以继续接表格结构解析与 Excel 导出。",
        page = page,
        primaryText = "保存图片",
        secondaryText = "重新拍摄",
        onBack = onBack,
        onPrimary = onSaveImage,
        onSecondary = onRetake
    ) {
        ResultHintCard(
            title = "建议结果动作",
            body = "后面适合加复制表格、导出 Excel、重新裁边三种动作。现在保留单页结果结构，后续扩展最省力。"
        )
    }
}

@Composable
fun IdCardGuideScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        SimpleTitleBar(
            title = "继续拍摄",
            onBack = onBack,
            actionText = "",
            onAction = null
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(28.dp))
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "身份证正面已完成",
                        color = HpSmartColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "下一步继续拍摄背面，这样就能在结果页里统一预览和导出。",
                        color = HpSmartColors.TextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    )
                }
            }
            ActionPillButton(
                text = "拍摄背面",
                filled = true,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun IdCardResultScreen(
    documentType: IdCardDocumentType,
    layout: IdCardLayoutOption,
    pages: List<ScannedPage>,
    compositePage: ScannedPage,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onRetakeAll: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SimpleTitleBar(
            title = "证件扫描结果",
            onBack = onBack,
            actionText = "",
            onAction = null,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
            ) {
                OutlineActionPill(
                    text = "旋转",
                    onClick = onRotate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RotateArrowIcon(color = HpSmartColors.TextPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            RotatedPreviewImage(
                bitmap = requireNotNull(compositePage.previewBitmap ?: compositePage.processedBitmap),
                rotationDegrees = compositePage.rotationDegrees,
                contentDescription = "${documentType.label}预览",
                modifier = Modifier.fillMaxSize()
            )
        }

        if (pages.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "已拍证件",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pages.forEachIndexed { index, page ->
                        Box(
                            modifier = Modifier
                                .size(width = 104.dp, height = 140.dp)
                                .background(Color(0xFFF6F8FD), RoundedCornerShape(18.dp))
                                .padding(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "第 ${index + 1} 张",
                                    color = HpSmartColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RotatedPreviewImage(
                                        bitmap = requireNotNull(page.previewBitmap ?: page.processedBitmap),
                                        rotationDegrees = page.rotationDegrees,
                                        contentDescription = "证件缩略图",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionPillButton(
                text = "重新拍摄",
                filled = false,
                onClick = onRetakeAll,
                modifier = Modifier.weight(1f)
            )
            ActionPillButton(
                text = "导出",
                filled = true,
                onClick = onExport,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SinglePageResultScaffold(
    title: String,
    subtitle: String,
    page: ScannedPage,
    primaryText: String,
    secondaryText: String,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    extraContent: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        SimpleTitleBar(
            title = title,
            onBack = onBack,
            actionText = "",
            onAction = null
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ResultHintCard(
                title = "流程已打通",
                body = subtitle
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(28.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                RotatedPreviewImage(
                    bitmap = requireNotNull(page.previewBitmap ?: page.processedBitmap ?: page.sourceBitmap),
                    rotationDegrees = page.rotationDegrees,
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            extraContent()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionPillButton(
                text = secondaryText,
                filled = false,
                onClick = onSecondary,
                modifier = Modifier.weight(1f)
            )
            ActionPillButton(
                text = primaryText,
                filled = true,
                onClick = onPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ResultHintCard(
    title: String,
    body: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = HpSmartColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                color = HpSmartColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun EditableOcrTextCard(
    text: String,
    engineName: String?,
    lineCount: Int,
    blockCount: Int,
    paragraphCount: Int,
    onTextChange: (String) -> Unit
) {
    val charCount = text.trim().length
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFFE7EBF7), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "可编辑文字",
                        color = HpSmartColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!engineName.isNullOrBlank()) {
                        Text(
                            text = "引擎：$engineName",
                            color = HpSmartColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OcrEditorMetricItem(
                    accent = Color(0xFF0B63FF),
                    glyph = "A",
                    value = charCount.toString()
                )
                OcrEditorMetricItem(
                    accent = Color(0xFF2D8CFF),
                    glyph = "=",
                    value = lineCount.toString()
                )
                OcrEditorMetricItem(
                    accent = Color(0xFF6A7BFF),
                    glyph = "P",
                    value = paragraphCount.toString()
                )
                OcrEditorMetricItem(
                    accent = Color(0xFF8896B8),
                    glyph = "#",
                    value = blockCount.toString()
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 420.dp),
                textStyle = TextStyle(
                    color = HpSmartColors.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                ),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFB8C9F5),
                    unfocusedBorderColor = Color(0xFFE5EAF6),
                    focusedContainerColor = Color(0xFFF9FBFF),
                    unfocusedContainerColor = Color(0xFFF9FBFF)
                )
            )
        }
    }
}

@Composable
private fun OcrSourcePreviewCard(
    page: ScannedPage,
    onRetake: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFFE7EBF7), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(112.dp)
                    .background(Color(0xFFF4F7FD), RoundedCornerShape(16.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                RotatedPreviewImage(
                    bitmap = page.sourceBitmap,
                    rotationDegrees = 0f,
                    contentDescription = "原图预览",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "原图预览",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                CompactActionButton(
                    text = "重新拍摄",
                    filled = false,
                    onClick = onRetake,
                    modifier = Modifier.fillMaxWidth(0.66f)
                )
            }
        }
    }
}

@Composable
private fun OcrStatusCard(
    title: String,
    body: String,
    primaryText: String,
    secondaryText: String,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    color = HpSmartColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    color = HpSmartColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (secondaryText.isNotBlank() && onSecondary != null) {
                    ActionPillButton(
                        text = secondaryText,
                        filled = false,
                        onClick = onSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
                ActionPillButton(
                    text = primaryText,
                    filled = true,
                    onClick = onPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OcrEditorMetricItem(
    accent: Color,
    glyph: String,
    value: String
) {
    Row(
        modifier = Modifier
            .background(Color(0xFFF7F9FE), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE4EAF7), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = glyph,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = value,
            color = HpSmartColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CompactActionButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (filled) HpSmartColors.Accent else HpSmartColors.SurfaceSoft
    val tc = if (filled) Color.White else HpSmartColors.TextPrimary
    Box(
        modifier = modifier
            .background(
                color = bg,
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(text) { detectTapGesturesCompat(onClick) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = tc,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun IdCardPreviewCard(
    title: String,
    page: ScannedPage,
    actionText: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = HpSmartColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ActionPillButton(
                    text = actionText,
                    filled = false,
                    onClick = onAction
                )
            }
            RotatedPreviewImage(
                bitmap = requireNotNull(page.previewBitmap ?: page.processedBitmap ?: page.sourceBitmap),
                rotationDegrees = page.rotationDegrees,
                contentDescription = title,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
