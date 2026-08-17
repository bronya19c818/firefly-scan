package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.common.BackArrowIcon
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LegalDocumentScreen(
    title: String,
    filename: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("加载中...") }

    LaunchedEffect(filename) {
        content = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("legal/$filename").bufferedReader().use { it.readText() }
            }.getOrElse { "无法加载文档内容" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FF))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.CenterStart
            ) {
                BackArrowIcon(
                    color = HpSmartColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HpSmartColors.TextPrimary
            )
            Spacer(modifier = Modifier.size(36.dp))
        }
        
        // Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                content.lines().forEach { line ->
                    when {
                        line.isBlank() -> Spacer(modifier = Modifier.height(10.dp))
                        line.startsWith("【重要提示】") -> Text(
                            text = line,
                            color = Color(0xFFB42318),
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        line.matches(Regex("^\\d+\\.\\s+.*")) -> Text(
                            text = line,
                            color = HpSmartColors.TextPrimary,
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        else -> Text(
                            text = line,
                            color = HpSmartColors.TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }
    }
}
