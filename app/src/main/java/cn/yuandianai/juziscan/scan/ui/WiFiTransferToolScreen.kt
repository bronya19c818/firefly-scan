package cn.yuandianai.juziscan.scan.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import cn.yuandianai.juziscan.scan.WiFiServer
import cn.yuandianai.juziscan.scan.loadBitmapFromUri
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import cn.yuandianai.juziscan.common.FireflyIconName

@Composable
fun WiFiTransferToolScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var serverUrl by remember { mutableStateOf<String?>(null) }
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var serverRunning by remember { mutableStateOf(false) }

    // Start server
    DisposableEffect(Unit) {
        val port = 8080
        val ip = getLocalIpAddress(context)
        
        var server: WiFiServer? = null
        if (ip != null) {
            try {
                server = WiFiServer(context, port)
                server.start()
                serverRunning = true
                val url = "http://$ip:$port"
                serverUrl = url
                
                scope.launch(Dispatchers.Default) {
                    qrCodeBitmap = generateQrCode(url)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "启动服务失败，端口可能被占用", Toast.LENGTH_SHORT).show()
            }
        }

        onDispose {
            server?.stop()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val transferDir = File(context.filesDir, "wifi_transfer").apply {
                        if (!exists()) mkdirs()
                    }
                    var successCount = 0
                    uris.forEachIndexed { index, uri ->
                        try {
                            val isStream = context.contentResolver.openInputStream(uri)
                            if (isStream != null) {
                                val destFile = File(transferDir, "upload_${System.currentTimeMillis()}_$index.jpg")
                                val os = FileOutputStream(destFile)
                                isStream.copyTo(os)
                                isStream.close()
                                os.close()
                                successCount++
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已成功添加 $successCount 个文件到服务", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        ToolHeader(title = "WiFi 传图", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ToolIntroCard(
                icon = FireflyIconName.Wifi,
                title = "WiFi 传图",
                message = "让手机和电脑处于同一 WiFi 下，扫码或访问地址，就能无线传图。"
            )
            if (serverUrl == null) {
                Text(
                    text = "未能获取到局域网 IP，请确保已连接 WiFi",
                    color = HpSmartColors.TextSecondary,
                    fontSize = 16.sp
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "1. 请确保电脑和手机连接在同一个 WiFi",
                        color = HpSmartColors.TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "2. 在电脑浏览器中输入以下地址",
                        color = HpSmartColors.TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    SelectionContainer {
                        Text(
                            text = serverUrl!!,
                            color = HpSmartColors.Accent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(HpSmartColors.Accent.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                    
                    if (qrCodeBitmap != null) {
                        Image(
                            bitmap = qrCodeBitmap!!.asImageBitmap(),
                            contentDescription = "URL QR Code",
                            modifier = Modifier.size(200.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "或者用其他手机扫码直接访问",
                            color = Color(0xFF9AA2BF),
                            fontSize = 12.sp
                        )
                    }
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "想要将手机相册的图片传给电脑？",
                        color = HpSmartColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ActionPillButton(
                        text = "添加图片到服务",
                        filled = true,
                        onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "添加后，电脑端刷新网页即可下载这些图片",
                        color = HpSmartColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun getLocalIpAddress(context: Context): String? {
    try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ip = wifiInfo.ipAddress
            return String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        }
        
        // Fallback to iterate interfaces
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            val addrs = intf.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

private fun generateQrCode(text: String): Bitmap? {
    return try {
        val size = 600
        val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
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
