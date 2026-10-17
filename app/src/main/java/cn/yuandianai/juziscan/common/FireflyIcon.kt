package cn.yuandianai.juziscan.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.yuandianai.juziscan.R

/** Tabler Icons v3.49.0; sources, hashes and MIT license: third_party/tabler-icons. */
enum class FireflyIconName(@param:DrawableRes val drawable: Int, val label: String) {
    Back(R.drawable.ic_tabler_arrow_left, "返回"),
    Close(R.drawable.ic_tabler_x, "关闭"),
    FlashOn(R.drawable.ic_tabler_bolt, "闪光灯：开启"),
    FlashOff(R.drawable.ic_tabler_bolt_off, "闪光灯：关闭"),
    FlashAuto(R.drawable.ic_tabler_camera_bolt, "闪光灯：自动"),
    Settings(R.drawable.ic_tabler_settings, "设置"),
    Gallery(R.drawable.ic_tabler_photo, "相册导入"),
    Add(R.drawable.ic_tabler_plus, "添加"),
    Rotate(R.drawable.ic_tabler_rotate_clockwise, "旋转"),
    Crop(R.drawable.ic_tabler_crop, "裁剪"),
    Expand(R.drawable.ic_tabler_arrows_maximize, "展开"),
    Share(R.drawable.ic_tabler_share, "分享"),
    Refresh(R.drawable.ic_tabler_refresh, "重拍"),
    CheckCircle(R.drawable.ic_tabler_circle_check, "已选中"),
    ChevronRight(R.drawable.ic_tabler_chevron_right, "进入"),
    Print(R.drawable.ic_tabler_printer, "打印"),
    Reorder(R.drawable.ic_tabler_arrows_sort, "重新排序"),
    Edit(R.drawable.ic_tabler_pencil, "编辑"),
    Replace(R.drawable.ic_tabler_replace, "更换"),
    Delete(R.drawable.ic_tabler_trash, "删除"),
    Crown(R.drawable.ic_tabler_crown, "会员"),
    Camera(R.drawable.ic_tabler_camera, "相机"),
    Scan(R.drawable.ic_tabler_scan, "扫描"),
    IdCard(R.drawable.ic_tabler_id, "证件扫描"),
    TextScan(R.drawable.ic_tabler_text_scan_2, "文字识别"),
    Tools(R.drawable.ic_tabler_layout_grid, "更多工具"),
    Home(R.drawable.ic_tabler_home, "首页"),
    User(R.drawable.ic_tabler_user, "我的"),
    Document(R.drawable.ic_tabler_file_text, "文档"),
    Shield(R.drawable.ic_tabler_shield_check, "账号与安全"),
    Watermark(R.drawable.ic_tabler_rubber_stamp, "添加水印"),
    Stitch(R.drawable.ic_tabler_layout_rows, "长图拼接"),
    ImageToPdf(R.drawable.ic_tabler_file_type_pdf, "图片转PDF"),
    Compress(R.drawable.ic_tabler_arrows_minimize, "图片压缩"),
    QrCode(R.drawable.ic_tabler_qrcode, "二维码工具"),
    Wifi(R.drawable.ic_tabler_wifi, "WiFi传图"),
    More(R.drawable.ic_tabler_dots, "更多操作"),
    Check(R.drawable.ic_tabler_check, "已开启"),
    Warning(R.drawable.ic_tabler_alert_triangle, "提示"),
    Table(R.drawable.ic_tabler_table, "表格识别"),
    Wallet(R.drawable.ic_tabler_wallet, "支付"),
}

object FireflyIconDefaults {
    val Toolbar = 24.dp
    val QuickEntry = 28.dp
    val Inline = 16.dp
    const val DisabledAlpha = 0.38f
}

/** Callers own click targets and callbacks. Pass null for icons next to an equivalent label. */
@Composable
fun FireflyIcon(
    icon: FireflyIconName,
    modifier: Modifier = Modifier,
    contentDescription: String? = icon.label,
    size: Dp = FireflyIconDefaults.Toolbar,
    selected: Boolean = false,
    enabled: Boolean = true,
    tint: Color = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current
) {
    Icon(
        painter = androidx.compose.ui.res.painterResource(icon.drawable),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (enabled) tint else tint.copy(alpha = tint.alpha * FireflyIconDefaults.DisabledAlpha)
    )
}
