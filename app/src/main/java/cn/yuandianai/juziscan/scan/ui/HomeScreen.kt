package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.R
import cn.yuandianai.juziscan.common.BackArrowIcon
import cn.yuandianai.juziscan.common.SettingsGearIcon
import cn.yuandianai.juziscan.history.HistoryRecord
import cn.yuandianai.juziscan.payment.MembershipStatus
import cn.yuandianai.juziscan.scan.HomeTab
import cn.yuandianai.juziscan.scan.LegalDocType
import cn.yuandianai.juziscan.scan.ScanEntry
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay

private val HomeBlue = Color(0xFF085BFF)
private val HomeDeepBlue = Color(0xFF0046E5)
private val HomePageBackground = Color(0xFFF5F8FF)
private val CardBlue = Color(0xFF0057FF)
private val CardOrange = Color(0xFFD78616)
private val CardCyan = Color(0xFF1297F5)
private val CardGreen = Color(0xFF0AB67B)

// Premium gradients for the cards
private val DocumentGradientColors = listOf(Color(0xFFFF7E40), Color(0xFFFF4E50))
private val IdCardGradientColors = listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
private val OcrGradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))

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
            .background(HomePageBackground)
    ) {
        if (currentTab == HomeTab.Home) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 108.dp)
            ) {
                HomeHero(
                    isVip = isLoggedIn && membershipStatus?.is_vip == true,
                    onMembershipClick = onVipClick,
                    onVipBannerClick = onVipClick,
                    onDocumentBannerClick = { onEntryClick(ScanEntry.Document) },
                    onIdCardBannerClick = { onEntryClick(ScanEntry.IdCard) }
                )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-58).dp)
                    .padding(horizontal = 18.dp)
            ) {
                PrimaryEntryGrid(onEntryClick = onEntryClick)
                ToolboxSection(
                    onEntryClick = onEntryClick,
                    onImportClick = onImportClick,
                    onMoreToolsClick = onMoreToolsClick,
                    modifier = Modifier.padding(top = 28.dp)
                )
                RecentHistorySection(
                    records = recentRecords,
                    onViewAllDocsClick = onViewAllDocsClick,
                    onOpen = onRecentOpen,
                    onShare = onRecentShare,
                    modifier = Modifier.padding(top = 28.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "遇到问题？",
                        color = HpSmartColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "点击反馈",
                        color = HomeBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onFeedbackClick() }
                    )
                }
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

        BottomHomeBar(
            currentTab = currentTab,
            onHomeClick = { onTabChange(HomeTab.Home) },
            onCameraClick = { onEntryClick(ScanEntry.Document) },
            onMineClick = { onTabChange(HomeTab.Mine) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun HomeHero(
    isVip: Boolean,
    onMembershipClick: () -> Unit,
    onVipBannerClick: () -> Unit,
    onDocumentBannerClick: () -> Unit,
    onIdCardBannerClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(298.dp)
            .background(
                Brush.verticalGradient(
                    listOf(HomeBlue, HomeDeepBlue)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.width * 0.38f,
                center = Offset(size.width * 0.95f, size.height * 0.12f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.width * 0.24f,
                center = Offset(size.width * 0.08f, size.height * 0.54f)
            )
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrustBadge()
                    Text(
                        text = "已持续为用户服务8年",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                MembershipPill(
                    isVip = isVip,
                    onClick = onMembershipClick
                )
            }

            PromoBanner(
                onVipClick = onVipBannerClick,
                onDocumentClick = onDocumentBannerClick,
                onIdCardClick = onIdCardBannerClick,
                modifier = Modifier.padding(top = 22.dp)
            )
        }
    }
}

@Composable
private fun TrustBadge() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.50f, size.height * 0.08f)
                lineTo(size.width * 0.82f, size.height * 0.22f)
                lineTo(size.width * 0.76f, size.height * 0.70f)
                lineTo(size.width * 0.50f, size.height * 0.92f)
                lineTo(size.width * 0.24f, size.height * 0.70f)
                lineTo(size.width * 0.18f, size.height * 0.22f)
                close()
            }
            drawPath(path, Color.White)
            drawLine(
                color = HomeBlue,
                start = Offset(size.width * 0.34f, size.height * 0.52f),
                end = Offset(size.width * 0.46f, size.height * 0.64f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = HomeBlue,
                start = Offset(size.width * 0.46f, size.height * 0.64f),
                end = Offset(size.width * 0.68f, size.height * 0.38f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun MembershipPill(
    isVip: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(Color(0xFFFFF2C9), RoundedCornerShape(18.dp))
            .pointerInput(Unit) { detectTapGesturesCompat(onClick) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (isVip) {
            Text(text = "♛", color = Color(0xFF8A6A22), fontSize = 13.sp)
        }
        Text(
            text = if (isVip) "会员权益" else "开通会员",
            color = Color(0xFF6E5520),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PromoBanner(
    onVipClick: () -> Unit,
    onDocumentClick: () -> Unit,
    onIdCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val banners = remember {
        listOf(
            HomeBannerSpec(
                imageRes = R.drawable.home_banner_vip,
                onClick = onVipClick
            ),
            HomeBannerSpec(
                imageRes = R.drawable.home_banner_document,
                onClick = onDocumentClick
            ),
            HomeBannerSpec(
                imageRes = R.drawable.home_banner_idcard,
                onClick = onIdCardClick
            )
        )
    }
    val pagerState = rememberPagerState(pageCount = { banners.size })

    LaunchedEffect(pagerState) {
        while (true) {
            delay(5200)
            val nextPage = (pagerState.currentPage + 1) % banners.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1400f / 420f)
            .shadow(14.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val banner = banners[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { banner.onClick() }
            ) {
                Image(
                    painter = painterResource(banner.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .background(Color.Black.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                .padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(banners.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (pagerState.currentPage == index) 18.dp else 6.dp, height = 6.dp)
                        .background(
                            color = if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(999.dp)
                        )
                )
            }
        }
    }
}

private data class HomeBannerSpec(
    val imageRes: Int,
    val onClick: () -> Unit
)

@Composable
private fun PrimaryEntryGrid(onEntryClick: (ScanEntry) -> Unit) {
    val ocrSpec = PrimaryEntrySpec(
        entry = ScanEntry.Ocr,
        gradientColors = OcrGradientColors,
        title = "文字识别",
        subtitle = "提取文字 生成Word",
        imageRes = R.drawable.home_entry_ocr
    )
    val documentSpec = PrimaryEntrySpec(
        entry = ScanEntry.Document,
        gradientColors = DocumentGradientColors,
        title = "文件扫描",
        subtitle = "快速生成PDF扫描件",
        imageRes = R.drawable.home_entry_document
    )
    val idCardSpec = PrimaryEntrySpec(
        entry = ScanEntry.IdCard,
        gradientColors = IdCardGradientColors,
        title = "证件扫描",
        subtitle = "生成1:1电子复印件",
        imageRes = R.drawable.home_entry_id_card
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Left Column: Document (Full Height)
        PrimaryEntryCard(
            spec = documentSpec,
            height = 226.dp,
            compact = false,
            onClick = { onEntryClick(documentSpec.entry) },
            modifier = Modifier.weight(1f)
        )

        // Right Column: IdCard (Top), OCR (Bottom)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PrimaryEntryCard(
                spec = idCardSpec,
                height = 106.dp,
                compact = true,
                onClick = { onEntryClick(idCardSpec.entry) },
                modifier = Modifier.fillMaxWidth()
            )
            PrimaryEntryCard(
                spec = ocrSpec,
                height = 106.dp,
                compact = true,
                onClick = { onEntryClick(ocrSpec.entry) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class PrimaryEntrySpec(
    val entry: ScanEntry,
    val gradientColors: List<Color>,
    val title: String,
    val subtitle: String,
    val imageRes: Int
)

@Composable
private fun PrimaryEntryCard(
    spec: PrimaryEntrySpec,
    height: Dp,
    compact: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(height)
            .shadow(8.dp, RoundedCornerShape(12.dp), clip = false)
            .background(
                Brush.linearGradient(spec.gradientColors),
                RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(spec.entry) { detectTapGesturesCompat(onClick) }
            .padding(if (compact) 12.dp else 16.dp)
    ) {
        if (compact) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1.3f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = spec.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = spec.subtitle,
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 11.sp,
                        maxLines = 2,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Image(
                    painter = painterResource(spec.imageRes),
                    contentDescription = spec.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(0.9f)
                        .size(50.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = spec.title,
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = spec.subtitle,
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Image(
                        painter = painterResource(spec.imageRes),
                        contentDescription = spec.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(100.dp)
                            .offset(x = 6.dp, y = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolboxSection(
    onEntryClick: (ScanEntry) -> Unit,
    onImportClick: () -> Unit,
    onMoreToolsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "工具箱",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black
                )
                Box(
                    modifier = Modifier
                        .padding(start = 18.dp, top = 3.dp)
                        .width(24.dp)
                        .height(4.dp)
                        .background(HomeBlue, RoundedCornerShape(99.dp))
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(Unit) { detectTapGesturesCompat { onMoreToolsClick() } }
            ) {
                Text(
                    text = "更多",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                BackArrowIcon(
                    color = HpSmartColors.TextPrimary,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val tools = toolboxToolCatalog.map { tool ->
            ToolSpec(
                label = tool.label,
                color = tool.color,
                imageRes = requireNotNull(tool.imageRes),
                onClick = { onEntryClick(tool.entry) }
            )
        }

        tools.chunked(3).forEach { rowTools ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                rowTools.forEach { tool ->
                    ToolItem(tool = tool)
                }
                repeat(3 - rowTools.size) {
                    Spacer(modifier = Modifier.width(86.dp))
                }
            }
        }
    }
}

private data class ToolSpec(
    val label: String,
    val color: Color,
    val imageRes: Int,
    val onClick: () -> Unit
)

@Composable
private fun ToolItem(tool: ToolSpec) {
    Column(
        modifier = Modifier
            .width(92.dp)
            .pointerInput(tool.label) { detectTapGesturesCompat(tool.onClick) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .background(tool.color.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(tool.imageRes),
                contentDescription = tool.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(46.dp)
            )
        }
        Text(
            text = tool.label,
            color = HpSmartColors.TextPrimary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 9.dp)
        )
    }
}

@Composable
private fun RecentHistorySection(
    records: List<HistoryRecord>,
    onViewAllDocsClick: () -> Unit,
    onOpen: (HistoryRecord) -> Unit,
    onShare: (HistoryRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "最近文档",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black
                )
                Box(
                    modifier = Modifier
                        .padding(start = 18.dp, top = 3.dp)
                        .width(24.dp)
                        .height(4.dp)
                        .background(HomeBlue, RoundedCornerShape(99.dp))
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(Unit) { detectTapGesturesCompat { onViewAllDocsClick() } }
            ) {
                Text(
                    text = "查看全部",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                BackArrowIcon(
                    color = HpSmartColors.TextPrimary,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(start = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (records.isEmpty()) {
            EmptyHistoryState(text = "暂无最近扫描记录")
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

@Composable
private fun BottomHomeBar(
    currentTab: HomeTab,
    onHomeClick: () -> Unit,
    onCameraClick: () -> Unit,
    onMineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false)
            .background(
                Color.White.copy(alpha = 0.96f),
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            )
            .navigationBarsPadding()
            .padding(PaddingValues(start = 36.dp, top = 12.dp, end = 36.dp, bottom = 10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem("首页", currentTab == HomeTab.Home, onHomeClick) {
                HomeGlyph(color = if (currentTab == HomeTab.Home) HomeBlue else Color(0xFF91A1BD), modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(60.dp))
            BottomNavItem("我的", currentTab == HomeTab.Mine, onMineClick) {
                ProfileGlyph(color = if (currentTab == HomeTab.Mine) HomeBlue else Color(0xFF91A1BD), modifier = Modifier.size(24.dp))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-30).dp)
                .size(78.dp)
                .shadow(12.dp, CircleShape)
                .background(HomeBlue, CircleShape)
                .pointerInput(Unit) { detectTapGesturesCompat(onCameraClick) },
            contentAlignment = Alignment.Center
        ) {
            CameraGlyph(color = Color.White, modifier = Modifier.size(35.dp))
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .pointerInput(label) { detectTapGesturesCompat(onClick) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Text(
            text = label,
            color = if (active) HomeBlue else Color(0xFF8795AD),
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun HomeGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.50f, size.height * 0.13f)
            lineTo(size.width * 0.14f, size.height * 0.42f)
            lineTo(size.width * 0.20f, size.height * 0.86f)
            lineTo(size.width * 0.80f, size.height * 0.86f)
            lineTo(size.width * 0.86f, size.height * 0.42f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun FolderGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.10f, size.height * 0.28f),
            size = Size(size.width * 0.80f, size.height * 0.58f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.16f, size.height * 0.18f),
            size = Size(size.width * 0.34f, size.height * 0.20f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
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
            size = Size(size.width * 0.60f, size.height * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
        )
    }
}

@Composable
private fun CameraGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 2.8.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.12f, size.height * 0.28f),
            size = Size(size.width * 0.76f, size.height * 0.58f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.31f, size.height * 0.15f),
            size = Size(size.width * 0.26f, size.height * 0.16f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.16f,
            center = Offset(size.width * 0.50f, size.height * 0.57f),
            style = Stroke(width = stroke)
        )
    }
}
