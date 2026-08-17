package cn.yuandianai.juziscan.scan.ui

import androidx.compose.ui.graphics.Color
import cn.yuandianai.juziscan.R
import cn.yuandianai.juziscan.scan.ScanEntry

data class ToolCatalogItem(
    val entry: ScanEntry,
    val label: String,
    val subtitle: String,
    val color: Color,
    val imageRes: Int? = null,
    val emoji: String? = null
)

val allToolCatalog: List<ToolCatalogItem> = listOf(
    ToolCatalogItem(
        entry = ScanEntry.Document,
        label = "文件扫描",
        subtitle = "快速生成PDF扫描件",
        color = Color(0xFFFF6B57),
        imageRes = R.drawable.home_entry_document
    ),
    ToolCatalogItem(
        entry = ScanEntry.Ocr,
        label = "文字识别",
        subtitle = "提取图片中的文字",
        color = Color(0xFF7D4CFF),
        imageRes = R.drawable.home_entry_ocr
    ),
    ToolCatalogItem(
        entry = ScanEntry.IdCard,
        label = "证件扫描",
        subtitle = "生成清晰电子复印件",
        color = Color(0xFF24A8FF),
        imageRes = R.drawable.home_entry_id_card
    ),
    ToolCatalogItem(
        entry = ScanEntry.Table,
        label = "表格识别",
        subtitle = "识别并导出Excel",
        color = Color(0xFF12B981),
        emoji = "表"
    ),
    ToolCatalogItem(
        entry = ScanEntry.Watermark,
        label = "添加水印",
        subtitle = "保护图片隐私",
        color = Color(0xFF0057FF),
        imageRes = R.drawable.tool_watermark
    ),
    ToolCatalogItem(
        entry = ScanEntry.Stitch,
        label = "长图拼接",
        subtitle = "多图合并为一长图",
        color = Color(0xFF22A9E8),
        imageRes = R.drawable.tool_stitch
    ),
    ToolCatalogItem(
        entry = ScanEntry.ImageToPdf,
        label = "图片转PDF",
        subtitle = "图片快速打包PDF",
        color = Color(0xFF0AB67B),
        imageRes = R.drawable.tool_image_to_pdf
    ),
    ToolCatalogItem(
        entry = ScanEntry.Compress,
        label = "图片压缩",
        subtitle = "缩小图片文件体积",
        color = Color(0xFF287CF4),
        imageRes = R.drawable.tool_compress
    ),
    ToolCatalogItem(
        entry = ScanEntry.QrCode,
        label = "二维码工具",
        subtitle = "扫码与二维码生成",
        color = Color(0xFF4A64F5),
        imageRes = R.drawable.tool_qrcode
    ),
    ToolCatalogItem(
        entry = ScanEntry.WiFiTransfer,
        label = "WiFi传图",
        subtitle = "电脑免数据线下载",
        color = Color(0xFF0AB67B),
        imageRes = R.drawable.tool_wifi_transfer
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
