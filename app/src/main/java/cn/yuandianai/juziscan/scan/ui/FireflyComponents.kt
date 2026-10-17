package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.common.BackArrowIcon
import cn.yuandianai.juziscan.ui.theme.FireflyColors
import cn.yuandianai.juziscan.ui.theme.FireflyTypography

/**
 * 萤火Scan 通用组件（design.md 第 6 节）。
 * 组件外观统一，交互回调由调用方注入，不改变既有业务逻辑。
 */

/** 主按钮：深湖青底白字，胶囊/圆角，最小高度 48dp */
@Composable
fun FireflyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    capsule: Boolean = true
) {
    val shape = if (capsule) CircleShape else RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                if (enabled) FireflyColors.Primary else FireflyColors.Primary.copy(alpha = 0.45f),
                shape
            )
            .pointerInput(text, enabled) { if (enabled) detectTapGesturesCompat(onClick) }
            .padding(horizontal = 24.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            style = FireflyTypography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 次按钮：浅薄荷底深湖青字 */
@Composable
fun FireflySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) FireflyColors.SurfaceSoft else FireflyColors.SurfaceSoft.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
            .pointerInput(text, enabled) { if (enabled) detectTapGesturesCompat(onClick) }
            .padding(horizontal = 24.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = FireflyColors.Primary,
            style = FireflyTypography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 统一页面顶栏：返回 + 居中标题 + 可选右侧操作 */
@Composable
fun FireflyTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .pointerInput(Unit) { detectTapGesturesCompat(onBack) },
                contentAlignment = Alignment.Center
            ) {
                BackArrowIcon(color = FireflyColors.Ink, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            text = title,
            color = FireflyColors.Ink,
            style = FireflyTypography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center)
        )
        if (actions != null) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}

/** 分区标题：模块标题 + 可选右侧「全部 >」入口 */
@Composable
fun FireflySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = FireflyColors.Ink,
            style = FireflyTypography.titleLarge
        )
        if (actionText != null && onAction != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(actionText) { detectTapGesturesCompat(onAction) }
            ) {
                Text(
                    text = actionText,
                    color = FireflyColors.SecondaryText,
                    style = FireflyTypography.labelMedium
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "›",
                    color = FireflyColors.SecondaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** 卡片容器：白底、圆角 18dp、弱边框、低强度阴影 */
@Composable
fun FireflyCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    var cardModifier = modifier
        .shadow(4.dp, shape, clip = false, ambientColor = Color(0x14263A40), spotColor = Color(0x14263A40))
        .clip(shape)
        .background(FireflyColors.Surface, shape)
        .border(1.dp, FireflyColors.Stroke, shape)
    if (onClick != null) {
        cardModifier = cardModifier.pointerInput(Unit) { detectTapGesturesCompat(onClick) }
    }
    Box(modifier = cardModifier) {
        content()
    }
}

/** 品牌化空状态/提示 */
@Composable
fun FireflyEmptyState(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FireflyColors.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, FireflyColors.Stroke, RoundedCornerShape(18.dp))
            .padding(vertical = 30.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FireflySparkGlyph(color = FireflyColors.Mint, modifier = Modifier.size(30.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            color = FireflyColors.SecondaryText,
            style = FireflyTypography.bodyMedium
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hint,
                color = FireflyColors.SecondaryText.copy(alpha = 0.75f),
                style = FireflyTypography.bodySmall
            )
        }
    }
}

/** 萤火光点装饰：发光圆点 + 十字星芒，用于品牌区与空状态 */
@Composable
fun FireflySparkGlyph(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
        // 光晕
        drawCircle(
            color = color.copy(alpha = 0.30f),
            radius = size.minDimension * 0.42f,
            center = center
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.18f,
            center = center
        )
        // 星芒
        val stroke = 1.6.dp.toPx()
        val inner = size.minDimension * 0.28f
        val outer = size.minDimension * 0.48f
        drawLine(
            color = color.copy(alpha = 0.85f),
            start = androidx.compose.ui.geometry.Offset(center.x, center.y - outer),
            end = androidx.compose.ui.geometry.Offset(center.x, center.y - inner),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = color.copy(alpha = 0.85f),
            start = androidx.compose.ui.geometry.Offset(center.x, center.y + inner),
            end = androidx.compose.ui.geometry.Offset(center.x, center.y + outer),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = color.copy(alpha = 0.85f),
            start = androidx.compose.ui.geometry.Offset(center.x - outer, center.y),
            end = androidx.compose.ui.geometry.Offset(center.x - inner, center.y),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = color.copy(alpha = 0.85f),
            start = androidx.compose.ui.geometry.Offset(center.x + inner, center.y),
            end = androidx.compose.ui.geometry.Offset(center.x + outer, center.y),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}
