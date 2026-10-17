package cn.yuandianai.juziscan.scan.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@Composable
fun ImageStitchToolScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var stitchedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            scope.launch {
                val loadedBitmaps = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        loadBitmapFromUri(context, uri)
                    }
                }
                
                if (loadedBitmaps.isEmpty()) {
                    Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
                } else if (loadedBitmaps.size == 1) {
                    Toast.makeText(context, "请至少选择 2 张图片进行拼接", Toast.LENGTH_SHORT).show()
                } else {
                    sourceBitmaps = loadedBitmaps
                    stitchedBitmap = withContext(Dispatchers.Default) {
                        stitchBitmapsVertically(loadedBitmaps)
                    }
                }
                isProcessing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        ToolHeader(title = "长图拼接", onBack = onBack)

        if (stitchedBitmap == null && !isProcessing) {
            ToolEmptyState(
                imageRes = R.drawable.tool_stitch,
                title = "长图拼接",
                message = "一次选择多张图片，自动按顺序拼接成长图，适合聊天截图和内容归档。",
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
                // Image Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    stitchedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Stitched Preview",
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
                        text = "重新选择 (${sourceBitmaps.size}张)",
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
                        val src = stitchedBitmap
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
                                    fileName = "萤火Scan_长图拼接_${System.currentTimeMillis()}",
                                    fileType = ExportFileType.JPG,
                                    fileSize = ExportFileSize.Actual
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

/**
 * 垂直拼接多张图片，自动缩放到统一宽度（以第一张图宽度为准），中间留白
 */
private fun stitchBitmapsVertically(bitmaps: List<Bitmap>): Bitmap {
    if (bitmaps.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    if (bitmaps.size == 1) return bitmaps[0]

    val targetWidth = bitmaps[0].width
    val gap = 20 // 留白 20px
    
    // 计算总高度
    var totalHeight = (bitmaps.size - 1) * gap
    val scaledHeights = mutableListOf<Int>()
    
    for (bmp in bitmaps) {
        val scale = targetWidth.toFloat() / bmp.width.toFloat()
        val scaledHeight = (bmp.height * scale).toInt()
        scaledHeights.add(scaledHeight)
        totalHeight += scaledHeight
    }

    // 防止 OOM，如果高度太大则限制最大宽高
    var finalWidth = targetWidth
    var finalHeight = totalHeight
    val maxDimension = 8000 // 限制最大长边
    if (finalHeight > maxDimension) {
        val downScale = maxDimension.toFloat() / finalHeight.toFloat()
        finalWidth = (finalWidth * downScale).toInt()
        finalHeight = maxDimension
    }

    val resultBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultBitmap)
    canvas.drawColor(AndroidColor.WHITE) // 背景设为白色

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var currentY = 0

    val globalScale = finalWidth.toFloat() / targetWidth.toFloat()
    
    for (i in bitmaps.indices) {
        val bmp = bitmaps[i]
        val originalScale = targetWidth.toFloat() / bmp.width.toFloat()
        val finalScale = originalScale * globalScale
        
        val scaledH = (bmp.height * finalScale).toInt()
        val destRect = Rect(0, currentY, finalWidth, currentY + scaledH)
        val srcRect = Rect(0, 0, bmp.width, bmp.height)
        
        canvas.drawBitmap(bmp, srcRect, destRect, paint)
        
        currentY += scaledH + (gap * globalScale).toInt()
    }

    return resultBitmap
}
