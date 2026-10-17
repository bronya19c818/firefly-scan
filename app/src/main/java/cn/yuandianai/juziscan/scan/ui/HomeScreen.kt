package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.R
import cn.yuandianai.juziscan.history.HistoryRecord
import cn.yuandianai.juziscan.payment.MembershipStatus
import cn.yuandianai.juziscan.scan.HomeTab
import cn.yuandianai.juziscan.scan.LegalDocType
import cn.yuandianai.juziscan.scan.ScanEntry
import cn.yuandianai.juziscan.ui.theme.FireflyColors
import cn.yuandianai.juziscan.ui.theme.FireflyTypography
import cn.yuandianai.juziscan.common.FireflyIcon
import cn.yuandianai.juziscan.common.FireflyIconName

/**
 * 萤火Scan A1 首页（design.md 4.1 / design_image/萤火Scan 首页界面.png）。
 * 品牌栏 → 主标题 → 文件扫描主卡片 → 四快捷入口 → 最近文档 → 底部导航。
 */
@Composable
fun ScanHomeScreen(
    currentTab: HomeTab,
    isLoggedIn: Boolean,
    accountDisplayName: String,
    accountAvatarUrl: String?,
    accountSubtitle: String,
    membershipStatus: MembershipStatus?,
    recentRecords: List<HistoryRecord>,
    onTabChange: (HomeTab) -> Unit,
    onEntryClick: (ScanEntry) -> Unit,
    onImportClick: () -> Unit,
    onLegalDocClick: (LegalDocType) -> Unit,
    onViewAllDocsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAccountSecurityClick: () -> Unit,
    onMoreToolsClick: () -> Unit,
    onVipClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onNicknameClick: () -> Unit,
    onAccountClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onRecentOpen: (HistoryRecord) -> Unit,
    onRecentShare: (HistoryRecord) -> Unit,
    onFeedbackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FireflyColors.Background)
    ) {
        if (currentTab == HomeTab.Home) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 108.dp)
            ) {
                HomeBrandBar(
                    isVip = isLoggedIn && membershipStatus?.is_vip == true,
                    onMembershipClick = onVipClick
                )
                HomeTitleSection()
                MainScanCard(
                    onClick = { onEntryClick(ScanEntry.Document) },
                    modifier = Modifier.padding(top = 22.dp)
                )
                QuickEntryRow(
                    onEntryClick = onEntryClick,
                    onImportClick = onImportClick,
                    onMoreToolsClick = onMoreToolsClick,
                    modifier = Modifier.padding(top = 24.dp)
                )
                RecentHistorySection(
                    records = recentRecords,
                    onViewAllDocsClick = onViewAllDocsClick,
                    onOpen = onRecentOpen,
                    onShare = onRecentShare,
                    modifier = Modifier.padding(top = 28.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "遇到问题？",
                        color = FireflyColors.SecondaryText,
                        style = FireflyTypography.bodySmall
                    )
                    Text(
                        text = "点击反馈",
                        color = FireflyColors.Primary,
                        style = FireflyTypography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.pointerInput(Unit) { detectTapGesturesCompat(onFeedbackClick) }
                    )
                }
            }
        } else if (currentTab == HomeTab.Mine) {
            MineScreen(
                isLoggedIn = isLoggedIn,
                displayName = accountDisplayName,
                avatarUrl = accountAvatarUrl,
                subtitle = accountSubtitle,
                membershipStatus = membershipStatus,
                onViewAllDocsClick = onViewAllDocsClick,
                onSettingsClick = onSettingsClick,
                onAccountSecurityClick = onAccountSecurityClick,
                onVipClick = onVipClick,
                onAvatarClick = onAvatarClick,
                onNicknameClick = onNicknameClick,
                onAccountClick = onAccountClick,
                modifier = Modifier.padding(bottom = 108.dp)
            )
        }

        FireflyBottomNav(
            currentTab = currentTab,
            onHomeClick = { onTabChange(HomeTab.Home) },
            onCameraClick = { onEntryClick(ScanEntry.Document) },
            onMineClick = { onTabChange(HomeTab.Mine) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/* ---------------- 品牌栏 ---------------- */

@Composable
private fun HomeBrandBar(
    isVip: Boolean,
    onMembershipClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.firefly_logo_badge),
                contentDescription = "萤火Scan",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(44.dp)
            )
            Column {
                Text(
                    text = "萤火Scan",
                    color = FireflyColors.Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "萤火微光 · 为你点亮",
                    color = FireflyColors.SecondaryText,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
        VipEntryPill(isVip = isVip, onClick = onMembershipClick)
    }
}

@Composable
private fun VipEntryPill(
    isVip: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(FireflyColors.VipGoldSoft, RoundedCornerShape(999.dp))
            .border(1.dp, FireflyColors.VipGold.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .pointerInput(isVip) { detectTapGesturesCompat(onClick) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FireflyIcon(FireflyIconName.Crown, tint = FireflyColors.VipGold, modifier = Modifier.size(16.dp), contentDescription = null)
        Text(
            text = if (isVip) "会员权益" else "开通会员",
            color = FireflyColors.VipGoldText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/* ---------------- 主标题区 ---------------- */

@Composable
private fun HomeTitleSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "让扫描\n更简单高效",
                color = FireflyColors.Ink,
                style = FireflyTypography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            Text(
                text = "文件 · 证件 · 识别 · 轻松搞定",
                color = FireflyColors.SecondaryText,
                style = FireflyTypography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        // 右侧定稿插画：文档叠层 + 萤火轨道（透明底，与雾白页面自然融合；
        // 上移避让副文案，避免文字压图造成拼接感）
        Image(
            painter = painterResource(R.drawable.home_top_decor),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(128.dp)
                .offset(x = 4.dp, y = (-14).dp)
        )
    }
}

/* ---------------- 文件扫描主卡片 ---------------- */

@Composable
private fun MainScanCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(164.dp)
            // 薄荷青边缘发光：外发光阴影 + 细发光描边
            .shadow(
                16.dp,
                shape,
                clip = false,
                ambientColor = Color(0x5991DCCB),
                spotColor = Color(0x5991DCCB)
            )
            .clip(shape)
            .background(FireflyColors.Surface, shape)
            .border(1.5.dp, Color(0xFF91DCCB).copy(alpha = 0.65f), shape)
            .clickable(onClick = onClick)
    ) {
        // 底层：定稿微光背景插画（不透明内区，全幅铺满，边缘与卡片圆角完全重合）
        Image(
            painter = painterResource(R.drawable.home_scan_card_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.fillMaxSize()
        )

        // 居中垂直组合：相机图标 + 标题 + 说明（保持水平居中，不被背景视觉重心带偏）
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FireflyIcon(FireflyIconName.Scan, tint = Color.White, modifier = Modifier.size(40.dp), contentDescription = null)
            Text(
                text = "文件扫描",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = "拍照生成高清文档",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/* ---------------- 四快捷入口 ---------------- */

private data class QuickEntrySpec(
    val label: String,
    val chipColor: Color,
    val glyphColor: Color,
    val onClick: () -> Unit,
    val icon: FireflyIconName
)

@Composable
private fun QuickEntryRow(
    onEntryClick: (ScanEntry) -> Unit,
    onImportClick: () -> Unit,
    onMoreToolsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 色彩全部取自 design.md 3.1 品牌色板：深湖青系 + 薄荷青 + 次要墨青
    val entries = listOf(
        QuickEntrySpec("证件扫描", FireflyColors.Primary, Color.White, { onEntryClick(ScanEntry.IdCard) }, FireflyIconName.IdCard),
        QuickEntrySpec("文字识别", FireflyColors.PrimaryLight, Color.White, { onEntryClick(ScanEntry.Ocr) }, FireflyIconName.TextScan),
        // 薄荷青底配深湖青线条，保持小尺寸对比度。
        QuickEntrySpec("相册导入", FireflyColors.Mint, FireflyColors.PrimaryDark, onImportClick, FireflyIconName.Gallery),
        QuickEntrySpec("更多工具", FireflyColors.SecondaryText, Color.White, onMoreToolsClick, FireflyIconName.Tools)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        entries.forEach { entry ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = entry.onClick),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(FireflyColors.SurfaceSoft, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(entry.chipColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        FireflyIcon(entry.icon, tint = entry.glyphColor, size = 26.dp, contentDescription = null)
                    }
                }
                Text(
                    text = entry.label,
                    color = FireflyColors.Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/* ---------------- 最近文档 ---------------- */

@Composable
private fun RecentHistorySection(
    records: List<HistoryRecord>,
    onViewAllDocsClick: () -> Unit,
    onOpen: (HistoryRecord) -> Unit,
    onShare: (HistoryRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        FireflySectionHeader(
            title = "最近文档",
            actionText = "全部",
            onAction = onViewAllDocsClick
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (records.isEmpty()) {
            FireflyEmptyState(
                text = "暂无最近文档",
                hint = "扫描或导入文件后会展示在这里"
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                records.take(3).forEach { record ->
                    HistoryRecordCard(
                        record = record,
                        onOpen = onOpen,
                        onShare = onShare,
                        onDelete = null,
                        compact = true
                    )
                }
            }
        }
    }
}

/* ---------------- 底部导航 ---------------- */

@Composable
fun FireflyBottomNav(
    currentTab: HomeTab,
    onHomeClick: () -> Unit,
    onCameraClick: () -> Unit,
    onMineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), clip = false)
            .background(
                FireflyColors.Surface.copy(alpha = 0.97f),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .navigationBarsPadding()
            .padding(PaddingValues(start = 40.dp, top = 12.dp, end = 40.dp, bottom = 10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem("首页", currentTab == HomeTab.Home, onHomeClick) { active ->
                FireflyIcon(FireflyIconName.Home, selected = active, tint = navColor(active), contentDescription = null)
            }
            Spacer(modifier = Modifier.width(72.dp))
            BottomNavItem("我的", currentTab == HomeTab.Mine, onMineClick) { active ->
                FireflyIcon(FireflyIconName.User, selected = active, tint = navColor(active), contentDescription = null)
            }
        }

        // 居中的突出扫描按钮
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-28).dp)
                .size(64.dp)
                .shadow(10.dp, CircleShape, clip = false, ambientColor = Color(0x40287F80), spotColor = Color(0x40287F80))
                .background(
                    Brush.linearGradient(listOf(FireflyColors.Primary, Color(0xFF3E9E97))),
                    CircleShape
                )
                .border(3.dp, FireflyColors.Surface, CircleShape)
                .clickable(onClick = onCameraClick),
            contentAlignment = Alignment.Center
        ) {
            FireflyIcon(FireflyIconName.Scan, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

private fun navColor(active: Boolean): Color =
    if (active) FireflyColors.Primary else FireflyColors.SecondaryText.copy(alpha = 0.75f)

@Composable
private fun BottomNavItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon(active)
        Text(
            text = label,
            color = navColor(active),
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

/** 底部导航中央扫描按钮：四角框 + 扫描线 */
