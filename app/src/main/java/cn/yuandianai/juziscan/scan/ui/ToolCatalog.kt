package cn.yuandianai.juziscan.scan.ui

import androidx.compose.ui.graphics.Color
import cn.yuandianai.juziscan.R
import cn.yuandianai.juziscan.scan.ScanEntry
import cn.yuandianai.juziscan.common.FireflyIconName
import cn.yuandianai.juziscan.ui.theme.FireflyColors

data class ToolCatalogItem(
    val entry: ScanEntry,
    val label: String,
    val subtitle: String,
    val color: Color,
    val icon: FireflyIconName
)

val allToolCatalog: List<ToolCatalogItem> = listOf(
    ToolCatalogItem(
        entry = ScanEntry.Document,
        label = "文件扫描",
        subtitle = "快速生成PDF扫描件",
        color = FireflyColors.Primary,
        icon = FireflyIconName.Scan
    ),
    ToolCatalogItem(
        entry = ScanEntry.Ocr,
        label = "文字识别",
        subtitle = "提取图片中的文字",
        color = FireflyColors.Primary,
        icon = FireflyIconName.TextScan
    ),
    ToolCatalogItem(
        entry = ScanEntry.IdCard,
        label = "证件扫描",
        subtitle = "生成清晰电子复印件",
        color = FireflyColors.Primary,
        icon = FireflyIconName.IdCard
    ),
    ToolCatalogItem(
        entry = ScanEntry.Table,
        label = "表格识别",
        subtitle = "识别并导出Excel",
        color = FireflyColors.Primary,
        icon = FireflyIconName.Table
    ),
    ToolCatalogItem(
        entry = ScanEntry.Watermark,
        label = "添加水印",
        subtitle = "保护图片隐私",
        color = FireflyColors.Primary,
        icon = FireflyIconName.Watermark
    ),
    ToolCatalogItem(
        entry = ScanEntry.Stitch,
        label = "长图拼接",
        subtitle = "多图合并为一长图",
        color = FireflyColors.Primary,
        icon = FireflyIconName.Stitch
    ),
    ToolCatalogItem(
        entry = ScanEntry.ImageToPdf,
        label = "图片转PDF",
        subtitle = "图片快速打包PDF",
        color = FireflyColors.Primary,
        icon = FireflyIconName.ImageToPdf
    ),
    ToolCatalogItem(
        entry = ScanEntry.Compress,
        label = "图片压缩",
        subtitle = "缩小图片文件体积",
        color = FireflyColors.Primary,
        icon = FireflyIconName.Compress
    ),
    ToolCatalogItem(
        entry = ScanEntry.QrCode,
        label = "二维码工具",
        subtitle = "扫码与二维码生成",
        color = FireflyColors.Primary,
        icon = FireflyIconName.QrCode
    ),
    ToolCatalogItem(
        entry = ScanEntry.WiFiTransfer,
        label = "WiFi传图",
        subtitle = "电脑免数据线下载",
        color = FireflyColors.Primary,
        icon = FireflyIconName.Wifi
    )
)

val toolboxToolCatalog: List<ToolCatalogItem> = listOf(
    ScanEntry.Watermark,
    ScanEntry.Stitch,
    ScanEntry.ImageToPdf,
    ScanEntry.Compress,
    ScanEntry.QrCode,
    ScanEntry.WiFiTransfer
).mapNotNull { entry ->
    allToolCatalog.firstOrNull { it.entry == entry }
}
