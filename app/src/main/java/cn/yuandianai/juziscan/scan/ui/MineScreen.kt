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
            .background(Color(0xFFF5F8FF))
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
                                Modifier.border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(listOf(Color(0xFFFFE59A), Color(0xFFD89B27))),
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

        // VIP Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .background(
                    brush = if (isVip) {
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFFE59A), Color(0xFFD89B27))
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(Color(0xFF323642), Color(0xFF1E212A))
                        )
                    },
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
                        if (isVip) {
                            CrownIcon(modifier = Modifier.size(20.dp, 17.dp))
                        } else {
                            Text(text = "👑", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isVip) "高级会员 · 尊享权益" else "开通高级会员",
                            color = if (isVip) Color(0xFF5A3B00) else Color(0xFFFFD76E),
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
                        color = if (isVip) Color(0xFF7A591A) else Color(0xFFA0A6B1),
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
                            brush = if (isVip) {
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF323642), Color(0xFF1E212A))
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFEAAB), Color(0xFFFFD76E))
                                )
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isVip) "会员中心" else "立即开通",
                        color = if (isVip) Color(0xFFFFD76E) else Color(0xFF5A3B00),
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
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            MenuItem(icon = "📄", title = "全部文档", onClick = onViewAllDocsClick)
            MenuDivider()
            MenuItem(icon = "⚙️", title = "应用设置", onClick = onSettingsClick)
            MenuDivider()
            MenuItem(icon = "🛡️", title = "账号与安全", onClick = onAccountSecurityClick)
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
private fun MenuItem(icon: String, title: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                color = HpSmartColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        RightChevronIcon(color = Color(0xFFC7D0DF), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 34.dp)
            .height(1.dp)
            .background(Color(0xFFF1F5F9))
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
