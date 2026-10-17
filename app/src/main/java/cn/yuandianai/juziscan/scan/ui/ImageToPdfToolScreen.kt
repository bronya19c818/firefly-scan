package cn.yuandianai.juziscan.scan.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import cn.yuandianai.juziscan.common.FireflyIconName

@Composable
fun ImageToPdfToolScreen(onBack: () -> Unit, openPickerOnEntry: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var pickerRequest by remember { mutableIntStateOf(0) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            scope.launch {
                val loadedBitmaps = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching { loadBitmapFromUri(context, uri) }.getOrNull()
                    }
                }
                
                if (loadedBitmaps.size != uris.size) {
                    Toast.makeText(context, "部分图片无法读取，请重新选择", Toast.LENGTH_SHORT).show()
                } else {
                    sourceBitmaps = loadedBitmaps
                }
                isProcessing = false
                if (openPickerOnEntry && sourceBitmaps.isEmpty()) pickerRequest++
            }
        } else if (openPickerOnEntry && sourceBitmaps.isEmpty()) onBack()
    }

    LaunchedEffect(pickerRequest) {
        if (openPickerOnEntry) photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        ToolHeader(title = "图片转 PDF", onBack = onBack)

        if (sourceBitmaps.isEmpty() && !isProcessing) {
            if (!openPickerOnEntry) ToolEmptyState(
                icon = FireflyIconName.ImageToPdf,
                title = "图片转 PDF",
                message = "把多张图片整理成 PDF 文档，方便打印、发送和归档保存。",
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
                Text("正在加载图片，请稍候...", color = HpSmartColors.TextSecondary, fontSize = 16.sp)
            }
        } else {
            // Preview state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "将导出为 ${sourceBitmaps.size} 页的 PDF 文档",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                // Horizontal list of selected images
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sourceBitmaps) { bmp ->
                        Box(
                            modifier = Modifier
                                .size(160.dp, 220.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }

                // Reselect button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "重新选择图片",
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
                    text = "导出 PDF",
                    filled = true,
                    onClick = {
                        val pages = sourceBitmaps.map { bmp ->
                            ScannedPage(
                                sourceBitmap = bmp,
                                processedBitmap = bmp,
                                previewBitmap = bmp
                            )
                        }
                        runCatching {
                            executeShare(
                                context = context,
                                pages = pages,
                                fileName = "萤火Scan_图片转PDF_${System.currentTimeMillis()}",
                                fileType = ExportFileType.PDF,
                                fileSize = ExportFileSize.Actual
                            )
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                "生成 PDF 失败：${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
