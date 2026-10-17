package cn.yuandianai.juziscan.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 萤火Scan 品牌设计 Token（design.md 第 3.1 节）。
 * 全部页面颜色必须取自这里，不再使用散落的硬编码颜色。
 */
object FireflyColors {
    /** 品牌薄荷青：Logo、品牌装饰、柔和选中光 */
    val Mint = Color(0xFF91DCCB)

    /** 深湖青：主要按钮、选中态、重点图标 */
    val Primary = Color(0xFF287F80)

    /** 深湖青按压/加深态 */
    val PrimaryDark = Color(0xFF1F6667)

    /** 深湖青浅阶（渐变与次级辨识用） */
    val PrimaryLight = Color(0xFF3E9E97)

    /** 机甲墨青：主文案、标题、强调文字 */
    val Ink = Color(0xFF263A40)

    /** 雾白：普通页面背景 */
    val Background = Color(0xFFF6FAF9)

    /** 纯白：卡片、设置块、列表 */
    val Surface = Color(0xFFFFFFFF)

    /** 浅薄荷：次级卡片、选中底、功能容器 */
    val SurfaceSoft = Color(0xFFE8F6F2)

    /** 次要文案、时间、元数据 */
    val SecondaryText = Color(0xFF607A80)

    /** 弱边框、分隔线 */
    val Stroke = Color(0xFFDCEBE8)

    /** 少量装饰星点；不作为主按钮 */
    val Pink = Color(0xFFF3ADBF)

    /** 会员入口和权益装饰 */
    val VipGold = Color(0xFFC8A667)
    val VipGoldSoft = Color(0xFFF7ECD4)
    val VipGoldText = Color(0xFF8A6A2E)

    /** 相机底栏、沉浸背景 */
    val CameraDark = Color(0xFF111C1F)
    val CameraPill = Color(0xFF1E2A2E)

    /** 相机底栏文字/图标 */
    val CameraLight = Color(0xFFF7FCFB)

    /** 深色主题 */
    val DarkBackground = Color(0xFF0E2024)
    val DarkSurface = Color(0xFF163037)
    val DarkSurfaceSoft = Color(0xFF1D3D43)
    val DarkText = Color(0xFFF5FBFA)
    val DarkSecondaryText = Color(0xFF9FBCB8)
    val DarkStroke = Color(0xFF2A4A4E)
    val DarkPrimary = Color(0xFF73D6C3)

    val Error = Color(0xFFE05B5B)
}

/**
 * 旧全局色板的兼容别名。
 * 历史上所有页面引用 HpSmartColors；统一映射到萤火品牌 Token 后，
 * 未逐页改造的页面也会立即落入品牌配色，后续阶段再逐页迁移到 FireflyColors。
 */
@Deprecated(
    message = "请改用 FireflyColors；保留仅为未改造页面兼容",
    replaceWith = ReplaceWith("FireflyColors")
)
object HpSmartColors {
    val Background = FireflyColors.Background
    val CameraChrome = FireflyColors.CameraDark
    val CameraPill = FireflyColors.CameraPill
    val CameraText = FireflyColors.CameraLight
    val Accent = FireflyColors.Primary
    val AccentSoft = FireflyColors.Mint
    val Surface = FireflyColors.Surface
    val SurfaceSoft = FireflyColors.SurfaceSoft
    val TextPrimary = FireflyColors.Ink
    val TextSecondary = FireflyColors.SecondaryText
}
