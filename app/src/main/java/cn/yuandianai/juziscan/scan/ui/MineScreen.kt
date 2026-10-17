package cn.yuandianai.juziscan.scan.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.common.SettingsGearIcon
import cn.yuandianai.juziscan.ui.theme.FireflyColors
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import cn.yuandianai.juziscan.payment.MembershipStatus
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MineScreen(
    isLoggedIn: Boolean,
    displayName: String,
    avatarUrl: String?,
    subtitle: String,
    membershipStatus: MembershipStatus?,
    onViewAllDocsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAccountSecurityClick: () -> Unit,
    onVipClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onNicknameClick: () -> Unit,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVip = isLoggedIn && membershipStatus?.is_vip == true
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FireflyColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // User Profile Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .then(
                            if (isVip) {
                                // 会员仅以细金环点缀，不大面积使用黄色
                                Modifier.border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(listOf(FireflyColors.VipGold, FireflyColors.VipGoldSoft)),
                                    shape = CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(if (isVip) 3.dp else 0.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isLoggedIn) onAvatarClick() else onAccountClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarBitmap = rememberAvatarBitmap(avatarUrl)
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap,
                            contentDescription = "用户头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = if (isVip) "♛" else if (isLoggedIn) displayName.take(1) else "登录",
                            color = if (isVip) Color(0xFF5A3B00) else Color(0xFF64748B),
                            fontSize = if (isVip) 28.sp else 16.sp,
                            fontWeight = if (isVip) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                Spacer(modifier = Modifier.width(18.dp))
                Column(
                    modifier = Modifier.clickable {
                        if (isLoggedIn) onNicknameClick() else onAccountClick()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLoggedIn) displayName else "点击登录/注册",
                            color = HpSmartColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isVip) {
                            Spacer(modifier = Modifier.width(6.dp))
                            CrownIcon(modifier = Modifier.size(18.dp, 15.dp))
                        }
                    }
                    Text(
                        text = subtitle,
                        color = HpSmartColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // VIP Banner：深湖青品牌基底，暖金仅作皇冠/按钮小面积点缀（design.md 3.1 色彩规则）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(FireflyColors.Primary, FireflyColors.PrimaryDark)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onVipClick() }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CrownIcon(modifier = Modifier.size(20.dp, 17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isVip) "高级会员 · 尊享权益" else "开通高级会员",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isVip) {
                            membershipExpiryText(membershipStatus?.expire_at)
                        } else {
                            "解锁PDF与图片无固定品牌水印导出"
                        },
                        color = FireflyColors.Mint.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isVip) Color.White else FireflyColors.VipGoldSoft,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isVip) "会员中心" else "立即开通",
                        color = if (isVip) FireflyColors.Primary else FireflyColors.VipGoldText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Menu List 1
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FireflyColors.Surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            MenuItem(title = "全部文档", onClick = onViewAllDocsClick) {
                DocGlyph(color = FireflyColors.Primary, modifier = Modifier.size(21.dp))
            }
            MenuDivider()
            MenuItem(title = "应用设置", onClick = onSettingsClick) {
                SettingsGearIcon(color = FireflyColors.Primary, modifier = Modifier.size(21.dp))
            }
            MenuDivider()
            MenuItem(title = "账号与安全", onClick = onAccountSecurityClick) {
                ShieldGlyph(color = FireflyColors.Primary, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun rememberAvatarBitmap(avatarUrl: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, avatarUrl) {
        value = avatarUrl?.let { url ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection().apply {
                        connectTimeout = 10_000
                        readTimeout = 10_000
                    }
                    connection.getInputStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}

private fun membershipExpiryText(expireAt: String?): String {
    if (expireAt.isNullOrBlank()) return "会员权益已生效"
    val normalized = expireAt.trim()
    val parsers = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss"
    )
    val date = parsers.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.CHINA).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }.parse(normalized)
        }.getOrNull()
    }
    val formatted = date?.let { SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA).format(it) }
        ?: normalized.take(10).split('-').let { parts ->
            if (parts.size == 3) "${parts[0]}年${parts[1]}月${parts[2]}日" else normalized
        }
    return "有效期至 $formatted"
}

@Composable
private fun MenuItem(
    title: String,
    onClick: () -> Unit = {},
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                color = HpSmartColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        RightChevronIcon(color = FireflyColors.Stroke, modifier = Modifier.size(16.dp))
    }
}

/** 文档图标：圆角折角文档 + 行线 */
@Composable
private fun DocGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.9.dp.toPx()
        val r = 3.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.14f)
            lineTo(w * 0.62f, h * 0.14f)
            lineTo(w * 0.78f, h * 0.32f)
            lineTo(w * 0.78f, h * 0.86f)
            lineTo(w * 0.22f, h * 0.86f)
            close()
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        val fold = Path().apply {
            moveTo(w * 0.62f, h * 0.14f)
            lineTo(w * 0.62f, h * 0.32f)
            lineTo(w * 0.78f, h * 0.32f)
        }
        drawPath(fold, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        for (i in 0..1) {
            val y = h * (0.48f + i * 0.16f)
            drawLine(color, Offset(w * 0.32f, y), Offset(w * 0.62f, y), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** 盾牌图标：账号与安全 */
@Composable
private fun ShieldGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.9.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.10f)
            lineTo(w * 0.84f, h * 0.24f)
            lineTo(w * 0.84f, h * 0.54f)
            quadraticTo(w * 0.84f, h * 0.78f, w * 0.5f, h * 0.90f)
            quadraticTo(w * 0.16f, h * 0.78f, w * 0.16f, h * 0.54f)
            lineTo(w * 0.16f, h * 0.24f)
            close()
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        // 对勾
        drawLine(color, Offset(w * 0.36f, h * 0.50f), Offset(w * 0.47f, h * 0.62f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, Offset(w * 0.47f, h * 0.62f), Offset(w * 0.66f, h * 0.38f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 35.dp)
            .height(1.dp)
            .background(FireflyColors.Stroke)
    )
}

@Composable
private fun RightChevronIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.15f)
            lineTo(size.width * 0.7f, size.height * 0.5f)
            lineTo(size.width * 0.35f, size.height * 0.85f)
        }
        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun CrownIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // A classic crown shape Path
        val path = Path().apply {
            moveTo(width * 0.15f, height * 0.8f) // bottom left
            lineTo(width * 0.05f, height * 0.35f) // left tip
            lineTo(width * 0.35f, height * 0.55f) // inner left valley
            lineTo(width * 0.5f, height * 0.2f)   // middle tip (higher)
            lineTo(width * 0.65f, height * 0.55f) // inner right valley
            lineTo(width * 0.95f, height * 0.35f) // right tip
            lineTo(width * 0.85f, height * 0.8f) // bottom right
            close()
        }

        val goldGradient = Brush.linearGradient(
            colors = listOf(Color(0xFFFFF099), Color(0xFFD89B27))
        )

        drawPath(
            path = path,
            brush = goldGradient
        )

        // Bottom band
        val baseBar = Path().apply {
            moveTo(width * 0.15f, height * 0.83f)
            lineTo(width * 0.85f, height * 0.83f)
            lineTo(width * 0.83f, height * 0.93f)
            lineTo(width * 0.17f, height * 0.93f)
            close()
        }
        drawPath(
            path = baseBar,
            brush = goldGradient
        )

        // Jewels at the tips
        val jewelColor = Color(0xFFFFF7C2)
        drawCircle(color = jewelColor, radius = width * 0.05f, center = Offset(width * 0.05f, height * 0.35f))
        drawCircle(color = jewelColor, radius = width * 0.07f, center = Offset(width * 0.5f, height * 0.2f))
        drawCircle(color = jewelColor, radius = width * 0.05f, center = Offset(width * 0.95f, height * 0.35f))
    }
}
