package cn.yuandianai.juziscan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FireflyLightColorScheme = lightColorScheme(
    primary = FireflyColors.Primary,
    onPrimary = Color.White,
    primaryContainer = FireflyColors.SurfaceSoft,
    onPrimaryContainer = FireflyColors.Ink,
    secondary = FireflyColors.Mint,
    onSecondary = FireflyColors.Ink,
    secondaryContainer = FireflyColors.SurfaceSoft,
    onSecondaryContainer = FireflyColors.Ink,
    tertiary = FireflyColors.VipGold,
    onTertiary = Color.White,
    background = FireflyColors.Background,
    onBackground = FireflyColors.Ink,
    surface = FireflyColors.Surface,
    onSurface = FireflyColors.Ink,
    surfaceVariant = FireflyColors.SurfaceSoft,
    onSurfaceVariant = FireflyColors.SecondaryText,
    outline = FireflyColors.Stroke,
    outlineVariant = FireflyColors.Stroke,
    error = FireflyColors.Error,
    onError = Color.White
)

private val FireflyDarkColorScheme = darkColorScheme(
    primary = FireflyColors.DarkPrimary,
    onPrimary = FireflyColors.DarkBackground,
    primaryContainer = FireflyColors.DarkSurfaceSoft,
    onPrimaryContainer = FireflyColors.DarkText,
    secondary = FireflyColors.Mint,
    onSecondary = FireflyColors.DarkBackground,
    secondaryContainer = FireflyColors.DarkSurfaceSoft,
    onSecondaryContainer = FireflyColors.DarkText,
    tertiary = FireflyColors.VipGold,
    onTertiary = FireflyColors.DarkBackground,
    background = FireflyColors.DarkBackground,
    onBackground = FireflyColors.DarkText,
    surface = FireflyColors.DarkSurface,
    onSurface = FireflyColors.DarkText,
    surfaceVariant = FireflyColors.DarkSurfaceSoft,
    onSurfaceVariant = FireflyColors.DarkSecondaryText,
    outline = FireflyColors.DarkStroke,
    outlineVariant = FireflyColors.DarkStroke,
    error = FireflyColors.Error,
    onError = Color.White
)

/** 圆角规范（design.md 3.2）：主卡片 22–24，普通卡片 14–18，输入框/工具按钮 10–14 */
val FireflyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 萤火Scan 全局主题。
 * 品牌配色优先，不启用 Material You 动态取色（design.md 3.1 系统主题规则）。
 */
@Composable
fun FireflyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) FireflyDarkColorScheme else FireflyLightColorScheme,
        typography = FireflyTypography,
        shapes = FireflyShapes,
        content = content
    )
}

@Deprecated(
    message = "请改用 FireflyTheme",
    replaceWith = ReplaceWith("FireflyTheme(darkTheme = darkTheme, content = content)")
)
@Composable
fun JuZiScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // dynamicColor 参数保留仅为源码兼容，品牌规范要求不启用动态取色。
    FireflyTheme(darkTheme = darkTheme, content = content)
}
