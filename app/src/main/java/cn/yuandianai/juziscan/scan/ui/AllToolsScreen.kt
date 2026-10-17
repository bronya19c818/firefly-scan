package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.scan.ScanEntry
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import cn.yuandianai.juziscan.common.FireflyIcon

@Composable
fun AllToolsScreen(
    onBack: () -> Unit,
    onToolClick: (ScanEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(cn.yuandianai.juziscan.ui.theme.FireflyColors.Background)
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
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            color = HpSmartColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        tools.chunked(4).forEachIndexed { index, rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTools.forEach { tool ->
                    AllToolsItem(
                        tool = tool,
                        onClick = { onToolClick(tool.entry) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - rowTools.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (index != tools.chunked(4).lastIndex) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AllToolsItem(
    tool: ToolCatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tool.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            FireflyIcon(tool.icon, size = 24.dp, tint = tool.color, contentDescription = null)
        }

        Text(
            text = tool.label,
            color = HpSmartColors.TextPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
