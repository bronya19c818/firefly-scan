package cn.yuandianai.juziscan.scan.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.R
import cn.yuandianai.juziscan.scan.*
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ImageCompressToolScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalSizeKb by remember { mutableStateOf(0L) }
    
    var selectedSize by remember { mutableStateOf(ExportFileSize.Medium) }
    var compressedSizeKb by remember { mutableStateOf(0L) }
    var compressedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    var isProcessing by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(context, uri)
                }
                if (bitmap != null) {
                    sourceBitmap = bitmap
                    originalSizeKb = withContext(Dispatchers.Default) {
                        calculateBitmapSizeKb(bitmap, ExportFileSize.Actual)
                    }
                } else {
                    Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
                }
                isProcessing = false
            }
        }
    }

    // Refresh compression preview when settings change
    LaunchedEffect(sourceBitmap, selectedSize) {
        val src = sourceBitmap ?: return@LaunchedEffect
        val (sizeKb, previewBmp) = withContext(Dispatchers.Default) {
            val scale = selectedSize.scale
            val scaledBmp = if (scale == 1.0f) src else {
                val w = (src.width * scale).toInt().coerceAtLeast(1)
                val h = (src.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(src, w, h, true)
            }
            
            val out = ByteArrayOutputStream()
            scaledBmp.compress(Bitmap.CompressFormat.JPEG, selectedSize.quality, out)
            val kb = out.size() / 1024L
            
            Pair(kb, scaledBmp)
        }
        compressedSizeKb = sizeKb
        compressedBitmap = previewBmp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        ToolHeader(title = "图片压缩", onBack = onBack)

        if (sourceBitmap == null && !isProcessing) {
            ToolEmptyState(
                imageRes = R.drawable.tool_compress,
                title = "图片压缩",
                message = "选择图片后可调整压缩等级，平衡清晰度和文件体积，发图更轻松。",
                actionText = "从相册选择",
                onAction = {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f)
            )
        } else if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("正在处理图片，请稍候...", color = HpSmartColors.TextSecondary, fontSize = 16.sp)
            }
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
                // Compression Comparison Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Original
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("原图大小", color = HpSmartColors.TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (originalSizeKb > 1024) String.format("%.2f MB", originalSizeKb / 1024f) else "${originalSizeKb} KB",
                            color = HpSmartColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Compressed
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(HpSmartColors.Accent.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("压缩后大小", color = HpSmartColors.Accent, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (compressedSizeKb > 1024) String.format("%.2f MB", compressedSizeKb / 1024f) else "${compressedSizeKb} KB",
                            color = HpSmartColors.Accent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Image Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    compressedBitmap?.let { bmp ->
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
                        text = "压缩等级",
                        color = HpSmartColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ExportFileSize.entries.forEach { option ->
                            val isSelected = option == selectedSize
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                                    .background(
                                        color = if (isSelected) Color(0xFFEAF2FF) else Color(0xFFF6F7FB),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) HpSmartColors.Accent.copy(alpha = 0.32f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedSize = option }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
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
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "说明：等级越小，图片体积越小，但清晰度会有所下降。",
                        color = Color(0xFF9AA2BF),
                        fontSize = 12.sp
                    )
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
                            val dummyPage = ScannedPage(
                                sourceBitmap = src,
                                processedBitmap = src,
                                previewBitmap = src
                            )
                            runCatching {
                                executeShare(
                                    context = context,
                                    pages = listOf(dummyPage),
                                    fileName = "桔子扫描_图片压缩_${System.currentTimeMillis()}",
                                    fileType = ExportFileType.JPG,
                                    fileSize = selectedSize
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

private fun calculateBitmapSizeKb(bitmap: Bitmap, size: ExportFileSize): Long {
    val scale = size.scale
    val scaledBmp = if (scale == 1.0f) bitmap else {
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
    
    val out = ByteArrayOutputStream()
    scaledBmp.compress(Bitmap.CompressFormat.JPEG, size.quality, out)
    
    if (scaledBmp !== bitmap) {
        scaledBmp.recycle()
    }
    
    return out.size() / 1024L
}
