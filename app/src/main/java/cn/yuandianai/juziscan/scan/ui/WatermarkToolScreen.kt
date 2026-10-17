package cn.yuandianai.juziscan.scan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.R
import cn.yuandianai.juziscan.scan.*
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WatermarkToolScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    var watermarkText by remember { mutableStateOf("仅供办理业务使用") }
    var watermarkStyle by remember { mutableStateOf(WatermarkStyleOption.DiagonalRepeat) }
    var watermarkStrength by remember { mutableStateOf(WatermarkStrengthOption.Medium) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(context, uri)
                }
                if (bitmap != null) {
                    sourceBitmap = bitmap
                } else {
                    Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Refresh preview when settings change
    LaunchedEffect(sourceBitmap, watermarkText, watermarkStyle, watermarkStrength) {
        val src = sourceBitmap ?: return@LaunchedEffect
        val settings = ExportWatermarkSettings(
            enabled = true,
            text = watermarkText,
            style = watermarkStyle,
            strength = watermarkStrength
        )
        val result = withContext(Dispatchers.Default) {
            applyWatermarkToBitmap(src, settings)
        }
        previewBitmap = result
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        ToolHeader(title = "添加水印", onBack = onBack)

        if (sourceBitmap == null) {
            ToolEmptyState(
                imageRes = R.drawable.tool_watermark,
                title = "添加水印",
                message = "选择一张图片后，可以快速加上业务办理、仅供使用等水印内容。",
                actionText = "从相册选择",
                onAction = {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            // Preview and Edit state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Image Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    previewBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Reselect button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "重选图片",
                        color = HpSmartColors.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Settings
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Text(
                        text = "水印内容",
                        color = HpSmartColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFDCDDF2), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        textStyle = TextStyle(
                            color = HpSmartColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(HpSmartColors.Accent),
                        decorationBox = { innerTextField ->
                            if (watermarkText.isBlank()) {
                                Text(
                                    text = "例如：仅供办理业务使用",
                                    color = Color(0xFF9AA2BF),
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    Text(
                        text = "样式",
                        color = HpSmartColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WatermarkStyleOption.entries.forEach { option ->
                            val isSelected = option == watermarkStyle
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSelected) Color(0xFFEAF2FF) else Color(0xFFF6F7FB),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) HpSmartColors.Accent.copy(alpha = 0.32f) else Color.Transparent,
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .clickable { watermarkStyle = option }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (isSelected) HpSmartColors.Accent else HpSmartColors.TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Text(
                        text = "透明度",
                        color = HpSmartColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WatermarkStrengthOption.entries.forEach { option ->
                            val isSelected = option == watermarkStrength
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSelected) Color(0xFFEAF2FF) else Color(0xFFF6F7FB),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) HpSmartColors.Accent.copy(alpha = 0.32f) else Color.Transparent,
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .clickable { watermarkStrength = option }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (isSelected) HpSmartColors.Accent else HpSmartColors.TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                ActionPillButton(
                    text = "导出/分享",
                    filled = true,
                    onClick = {
                        val src = sourceBitmap
                        if (src != null) {
                            val settings = ExportWatermarkSettings(
                                enabled = true,
                                text = watermarkText,
                                style = watermarkStyle,
                                strength = watermarkStrength
                            )
                            val dummyPage = ScannedPage(
                                sourceBitmap = src,
                                processedBitmap = src,
                                previewBitmap = src
                            )
                            runCatching {
                                executeShare(
                                    context = context,
                                    pages = listOf(dummyPage),
                                    fileName = "萤火Scan_水印_${System.currentTimeMillis()}",
                                    fileType = ExportFileType.JPG,
                                    fileSize = ExportFileSize.Actual,
                                    watermarkSettings = settings
                                )
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "分享失败：${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
