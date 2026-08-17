package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.scan.ScanEntry
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

@Composable
fun AllToolsScreen(
    onBack: () -> Unit,
    onToolClick: (ScanEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FF))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        TopSoftButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 22.dp, top = 12.dp)
        )

        Text(
            text = "全部工具",
            color = HpSmartColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 22.dp, top = 20.dp)
        )

        Text(
            text = "首页现有功能入口都在这里",
            color = HpSmartColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 22.dp, top = 6.dp, end = 22.dp)
        )

        ToolSectionCard(
            title = "扫描识别",
            tools = allToolCatalog.filter {
                it.entry == ScanEntry.Document ||
                    it.entry == ScanEntry.Ocr ||
                    it.entry == ScanEntry.IdCard ||
                    it.entry == ScanEntry.Table
            },
            onToolClick = onToolClick,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp)
        )

        ToolSectionCard(
            title = "图片处理",
            tools = allToolCatalog.filter {
                it.entry == ScanEntry.Watermark ||
                    it.entry == ScanEntry.Stitch ||
                    it.entry == ScanEntry.ImageToPdf ||
                    it.entry == ScanEntry.Compress
            },
            onToolClick = onToolClick,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp)
        )

        ToolSectionCard(
            title = "效率工具",
            tools = allToolCatalog.filter {
                it.entry == ScanEntry.QrCode || it.entry == ScanEntry.WiFiTransfer
            },
            onToolClick = onToolClick,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun ToolSectionCard(
    title: String,
    tools: List<ToolCatalogItem>,
    onToolClick: (ScanEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = Color.White, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Text(
            text = title,
            color = HpSmartColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(18.dp))

        tools.chunked(3).forEachIndexed { index, rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowTools.forEach { tool ->
                    AllToolsItem(
                        tool = tool,
                        onClick = { onToolClick(tool.entry) }
                    )
                }
                repeat(3 - rowTools.size) {
                    Spacer(modifier = Modifier.width(94.dp))
                }
            }
            if (index != tools.chunked(3).lastIndex) {
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun AllToolsItem(
    tool: ToolCatalogItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(94.dp)
            .pointerInput(tool.label) { detectTapGesturesCompat(onClick) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(tool.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            val imageRes = tool.imageRes
            if (imageRes != null) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = tool.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(46.dp)
                )
            } else {
                Text(
                    text = tool.emoji ?: tool.label.take(1),
                    color = tool.color,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = tool.label,
            color = HpSmartColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
