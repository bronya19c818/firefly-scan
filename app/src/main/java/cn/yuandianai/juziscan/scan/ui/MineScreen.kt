package cn.yuandianai.juziscan.scan.ui

import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import cn.yuandianai.juziscan.common.FireflyIcon
import cn.yuandianai.juziscan.common.FireflyIconName

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
                        if (isVip) {
                            FireflyIcon(FireflyIconName.Crown, size = 32.dp, tint = FireflyColors.VipGoldText)
                        } else if (isLoggedIn) {
                            Text(text = displayName.take(1), color = FireflyColors.SecondaryText, fontSize = 16.sp)
                        } else {
                            FireflyIcon(FireflyIconName.User, size = 32.dp, tint = FireflyColors.SecondaryText, contentDescription = "登录")
                        }
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
                            FireflyIcon(FireflyIconName.Crown, tint = FireflyColors.VipGold, modifier = Modifier.size(20.dp))
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
                        FireflyIcon(FireflyIconName.Crown, tint = FireflyColors.VipGold, modifier = Modifier.size(20.dp))
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
                FireflyIcon(FireflyIconName.Document, contentDescription = null, tint = FireflyColors.Primary, modifier = Modifier.size(21.dp))
            }
            MenuDivider()
            MenuItem(title = "应用设置", onClick = onSettingsClick) {
                SettingsGearIcon(color = FireflyColors.Primary, modifier = Modifier.size(21.dp))
            }
            MenuDivider()
            MenuItem(title = "账号与安全", onClick = onAccountSecurityClick) {
                FireflyIcon(FireflyIconName.Shield, contentDescription = null, tint = FireflyColors.Primary, modifier = Modifier.size(21.dp))
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
        FireflyIcon(FireflyIconName.ChevronRight, contentDescription = null, tint = FireflyColors.SecondaryText, modifier = Modifier.size(20.dp))
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
