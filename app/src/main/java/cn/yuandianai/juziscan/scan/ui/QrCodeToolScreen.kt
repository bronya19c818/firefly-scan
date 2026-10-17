package cn.yuandianai.juziscan.scan.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.R
import cn.yuandianai.juziscan.scan.*
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QrCodeToolScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        ToolHeader(title = "二维码工具", onBack = onBack)

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = HpSmartColors.Background,
            contentColor = HpSmartColors.Accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = HpSmartColors.Accent
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("生成二维码", fontWeight = FontWeight.Medium) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("识别二维码", fontWeight = FontWeight.Medium) }
            )
        }

        if (selectedTab == 0) {
            GenerateQrCodeView()
        } else {
            DecodeQrCodeView()
        }
    }
}

@Composable
private fun GenerateQrCodeView() {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var generatedQrCode by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (generatedQrCode == null) {
            ToolIntroCard(
                imageRes = R.drawable.tool_qrcode,
                title = "生成二维码",
                message = "输入文本、链接或联系方式，马上生成可保存可分享的二维码。"
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Text(
                text = "输入文本或链接",
                color = HpSmartColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            BasicTextField(
                value = inputText,
                onValueChange = { 
                    inputText = it 
                    // Auto generate when text changes
                    if (it.isNotEmpty()) {
                        scope.launch {
                            generatedQrCode = withContext(Dispatchers.Default) {
                                generateQrCode(it)
                            }
                        }
                    } else {
                        generatedQrCode = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .background(Color(0xFFF6F7FB), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                textStyle = TextStyle(
                    color = HpSmartColors.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(HpSmartColors.Accent),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "例如：https://www.baidu.com，或者任何你想转换的文字...",
                            color = Color(0xFF9AA2BF),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            )
        }

        if (generatedQrCode != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = generatedQrCode!!.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(240.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    ActionPillButton(
                        text = "导出/分享",
                        filled = true,
                        onClick = {
                            val dummyPage = ScannedPage(
                                sourceBitmap = generatedQrCode!!,
                                processedBitmap = generatedQrCode!!,
                                previewBitmap = generatedQrCode!!
                            )
                            runCatching {
                                executeShare(
                                    context = context,
                                    pages = listOf(dummyPage),
                                    fileName = "萤火Scan_二维码_${System.currentTimeMillis()}",
                                    fileType = ExportFileType.PNG,
                                    fileSize = ExportFileSize.Actual
                                )
                            }.onFailure { error ->
                                Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun DecodeQrCodeView() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var decodedText by remember { mutableStateOf<String?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            decodedText = null
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(context, uri)
                }
                if (bitmap != null) {
                    selectedBitmap = bitmap
                    decodedText = withContext(Dispatchers.Default) {
                        decodeQrCode(bitmap)
                    }
                    if (decodedText == null) {
                        Toast.makeText(context, "未发现二维码或识别失败", Toast.LENGTH_SHORT).show()
                    }
                }
                isProcessing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (selectedBitmap == null) {
            ToolIntroCard(
                imageRes = R.drawable.tool_qrcode,
                title = "识别二维码",
                message = "从相册选择带二维码的图片，自动识别内容并支持一键复制。"
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .clickable {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center
        ) {
            if (selectedBitmap != null) {
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = "Selected Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("点击重选图片", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("从相册选择包含二维码的图片", color = HpSmartColors.TextSecondary, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "选择图片",
                        color = Color.White,
                        modifier = Modifier
                            .background(HpSmartColors.Accent, RoundedCornerShape(999.dp))
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    )
                }
            }
        }

        if (isProcessing) {
            Text("正在识别中...", color = HpSmartColors.TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        if (decodedText != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = "识别结果",
                    color = HpSmartColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                SelectionContainer {
                    Text(
                        text = decodedText!!,
                        color = HpSmartColors.TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                ActionPillButton(
                    text = "复制内容",
                    filled = true,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(decodedText!!))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun generateQrCode(text: String): Bitmap? {
    return try {
        val size = 800
        val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun decodeQrCode(bitmap: Bitmap): String? {
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        
        val result = MultiFormatReader().decode(binaryBitmap)
        result.text
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
