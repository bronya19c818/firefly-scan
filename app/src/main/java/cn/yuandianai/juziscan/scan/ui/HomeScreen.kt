package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
        CrownGlyph(color = FireflyColors.VipGold, modifier = Modifier.size(13.dp))
        Text(
            text = if (isVip) "会员权益" else "开通会员",
            color = FireflyColors.VipGoldText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CrownGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.78f)
            lineTo(w * 0.10f, h * 0.30f)
            lineTo(w * 0.32f, h * 0.52f)
            lineTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.68f, h * 0.52f)
            lineTo(w * 0.90f, h * 0.30f)
            lineTo(w * 0.88f, h * 0.78f)
            close()
        }
        drawPath(path, color)
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
            .pointerInput(Unit) { detectTapGesturesCompat(onClick) }
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
            ScanCameraGlyph(color = Color.White, modifier = Modifier.size(46.dp))
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

/** 相机图标 + 四角取景框 */
@Composable
private fun ScanCameraGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.6.dp.toPx()
        val bracketStroke = 2.8.dp.toPx()
        // 四角取景框
        val len = w * 0.16f
        val m = w * 0.02f
        // 左上
        drawLine(color, Offset(m, m + len), Offset(m, m), bracketStroke, cap = StrokeCap.Round)
        drawLine(color, Offset(m, m), Offset(m + len, m), bracketStroke, cap = StrokeCap.Round)
        // 右上
        drawLine(color, Offset(w - m - len, m), Offset(w - m, m), bracketStroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w - m, m), Offset(w - m, m + len), bracketStroke, cap = StrokeCap.Round)
        // 左下
        drawLine(color, Offset(m, h - m - len), Offset(m, h - m), bracketStroke, cap = StrokeCap.Round)
        drawLine(color, Offset(m, h - m), Offset(m + len, h - m), bracketStroke, cap = StrokeCap.Round)
        // 右下
        drawLine(color, Offset(w - m - len, h - m), Offset(w - m, h - m), bracketStroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w - m, h - m), Offset(w - m, h - m - len), bracketStroke, cap = StrokeCap.Round)
        // 相机机身
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.28f, h * 0.36f),
            size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.32f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.40f, h * 0.28f),
            size = androidx.compose.ui.geometry.Size(w * 0.20f, h * 0.10f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // 镜头
        drawCircle(
            color = color,
            radius = w * 0.09f,
            center = Offset(w * 0.50f, h * 0.52f),
            style = Stroke(width = stroke)
        )
    }
}

/* ---------------- 四快捷入口 ---------------- */

private data class QuickEntrySpec(
    val label: String,
    val chipColor: Color,
    val onClick: () -> Unit,
    val glyph: @Composable (Color, Modifier) -> Unit
)

@Composable
private fun QuickEntryRow(
    onEntryClick: (ScanEntry) -> Unit,
    onImportClick: () -> Unit,
    onMoreToolsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        QuickEntrySpec("证件扫描", FireflyColors.EntryBlue, { onEntryClick(ScanEntry.IdCard) }) { c, m ->
            IdCardGlyph(c, m)
        },
        QuickEntrySpec("文字识别", FireflyColors.EntryTeal, { onEntryClick(ScanEntry.Ocr) }) { c, m ->
            TextScanGlyph(c, m)
        },
        QuickEntrySpec("相册导入", FireflyColors.EntryPurple, onImportClick) { c, m ->
            GalleryGlyph(c, m)
        },
        QuickEntrySpec("更多工具", FireflyColors.EntrySlate, onMoreToolsClick) { c, m ->
            GridGlyph(c, m)
        }
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
                    .pointerInput(entry.label) { detectTapGesturesCompat(entry.onClick) },
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
                        entry.glyph(Color.White, Modifier.size(22.dp))
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

@Composable
private fun IdCardGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.20f),
            size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.60f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = color,
            radius = w * 0.09f,
            center = Offset(w * 0.32f, h * 0.44f)
        )
        drawLine(color, Offset(w * 0.20f, h * 0.66f), Offset(w * 0.44f, h * 0.66f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.56f, h * 0.38f), Offset(w * 0.78f, h * 0.38f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.56f, h * 0.52f), Offset(w * 0.78f, h * 0.52f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.56f, h * 0.66f), Offset(w * 0.70f, h * 0.66f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun TextScanGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.4.dp.toPx()
        // “T” 字形
        drawLine(color, Offset(w * 0.28f, h * 0.24f), Offset(w * 0.72f, h * 0.24f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.50f, h * 0.24f), Offset(w * 0.50f, h * 0.72f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.38f, h * 0.72f), Offset(w * 0.62f, h * 0.72f), stroke, cap = StrokeCap.Round)
    }
}

/**
 * 相册导入图标：与 IdCardGlyph / TextScanGlyph 同一套线宽与圆角语言，
 * 避免与其他入口图案产生“拼接感”。
 */
@Composable
private fun GalleryGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.1.dp.toPx()
        // 外框
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.14f, h * 0.20f),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.60f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // 太阳
        drawCircle(
            color = color,
            radius = w * 0.075f,
            center = Offset(w * 0.34f, h * 0.38f)
        )
        // 山形（与山脊同一描边语言，圆角衔接）
        val mountain = Path().apply {
            moveTo(w * 0.20f, h * 0.74f)
            lineTo(w * 0.42f, h * 0.48f)
            lineTo(w * 0.56f, h * 0.62f)
        }
        drawPath(mountain, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        val hill = Path().apply {
            moveTo(w * 0.48f, h * 0.74f)
            lineTo(w * 0.63f, h * 0.56f)
            lineTo(w * 0.80f, h * 0.74f)
        }
        drawPath(hill, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun GridGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cell = w * 0.28f
        val gap = w * 0.10f
        val startX = (w - cell * 2 - gap) / 2f
        val startY = (h - cell * 2 - gap) / 2f
        val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
        for (row in 0..1) {
            for (col in 0..1) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(startX + col * (cell + gap), startY + row * (cell + gap)),
                    size = androidx.compose.ui.geometry.Size(cell, cell),
                    cornerRadius = r
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
                HomeGlyph(color = navColor(active), modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(72.dp))
            BottomNavItem("我的", currentTab == HomeTab.Mine, onMineClick) { active ->
                ProfileGlyph(color = navColor(active), modifier = Modifier.size(24.dp))
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
                .pointerInput(Unit) { detectTapGesturesCompat(onCameraClick) },
            contentAlignment = Alignment.Center
        ) {
            ScanLineGlyph(color = Color.White, modifier = Modifier.size(28.dp))
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
            .pointerInput(label) { detectTapGesturesCompat(onClick) },
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

@Composable
private fun HomeGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 2.2.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.50f, size.height * 0.10f)
            lineTo(size.width * 0.12f, size.height * 0.42f)
            lineTo(size.width * 0.88f, size.height * 0.42f)
            close()
        }
        drawPath(path, color)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.22f, size.height * 0.42f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.46f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
private fun ProfileGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = color,
            radius = size.minDimension * 0.20f,
            center = Offset(size.width * 0.50f, size.height * 0.32f)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.20f, size.height * 0.58f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
        )
    }
}

/** 底部导航中央扫描按钮：四角框 + 扫描线 */
@Composable
private fun ScanLineGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.4.dp.toPx()
        val len = w * 0.22f
        val m = w * 0.10f
        drawLine(color, Offset(m, m + len), Offset(m, m), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(m, m), Offset(m + len, m), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w - m - len, m), Offset(w - m, m), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w - m, m), Offset(w - m, m + len), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(m, h - m - len), Offset(m, h - m), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(m, h - m), Offset(m + len, h - m), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w - m - len, h - m), Offset(w - m, h - m), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w - m, h - m), Offset(w - m, h - m - len), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.20f, h * 0.5f), Offset(w * 0.80f, h * 0.5f), stroke, cap = StrokeCap.Round)
    }
}
