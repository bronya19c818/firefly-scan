package cn.yuandianai.juziscan.scan.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.common.CloseCrossIcon
import cn.yuandianai.juziscan.payment.MembershipPlan
import cn.yuandianai.juziscan.payment.MembershipStatus
import cn.yuandianai.juziscan.scan.LegalDocType
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

private val VipBackgroundBlue = Color(0xFFE8F2FF)
private val VipBackgroundLight = Color(0xFFF7FAFF)
private val VipGolden = Color(0xFFD4AF37)
private val VipGoldenLight = Color(0xFFFFF9E6)
private val SelectedBorderColor = Color(0xFFFF4D4F)
private val SelectedBgColor = Color(0xFFFFF2F2)
private val UnselectedBorderColor = Color(0xFFE2E8F0)
private val UnselectedBgColor = Color.White
private val TextDark = Color(0xFF1E293B)
private val TextGray = Color(0xFF64748B)

private enum class PaymentMethod {
    WeChat, Alipay
}

private data class TickerNotification(
    val avatarEmoji: String,
    val name: String,
    val message: String
)

private fun fallbackMembershipPlans(): List<MembershipPlan> = listOf(
    MembershipPlan(
        plan_code = "lifetime",
        title = "永久会员",
        price = "199.00",
        original_price = "599.00",
        bottom_label = "一次付费 终身使用",
        tag_text = "老用户福利",
        is_default = false
    ),
    MembershipPlan(
        plan_code = "annual",
        title = "年度会员",
        price = "99.00",
        original_price = "288.00",
        bottom_label = "0.27元/天",
        tag_text = "超值推荐",
        is_default = true
    ),
    MembershipPlan(
        plan_code = "quarterly",
        title = "季度会员",
        price = "39.00",
        original_price = "78.00",
        bottom_label = "0.43元/天",
        tag_text = null,
        is_default = false
    ),
    MembershipPlan(
        plan_code = "monthly",
        title = "月度会员",
        price = "19.00",
        original_price = "30.00",
        bottom_label = "0.63元/天",
        tag_text = null,
        is_default = false
    )
)

private fun formatPlanAmount(amount: String): String =
    amount.removeSuffix(".00").removeSuffix(".0")

private fun formatPlanPriceText(amount: String): String = "${formatPlanAmount(amount)}元"

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VipScreen(
    onBack: () -> Unit,
    onLegalDocClick: (LegalDocType) -> Unit,
    plans: List<MembershipPlan>,
    paymentChannels: List<String>,
    membershipStatus: MembershipStatus?,
    isPurchaseInFlight: Boolean,
    purchaseMessage: String?,
    onPurchaseClick: (planCode: String) -> Unit
) {
    val isVip = membershipStatus?.is_vip == true
    val context = LocalContext.current
    val resolvedPlans = remember(plans) {
        if (plans.isNotEmpty()) plans else fallbackMembershipPlans()
    }
    var selectedPlanCode by remember { mutableStateOf<String?>(null) }
    var selectedPayment by remember { mutableStateOf(PaymentMethod.Alipay) }
    var isAgreed by remember { mutableStateOf(false) }

    LaunchedEffect(resolvedPlans) {
        val availableCodes = resolvedPlans.map { it.plan_code }.toSet()
        if (selectedPlanCode !in availableCodes) {
            selectedPlanCode = resolvedPlans.firstOrNull { it.is_default }?.plan_code
                ?: resolvedPlans.firstOrNull()?.plan_code
        }
    }

    val selectedPlan = resolvedPlans.firstOrNull { it.plan_code == selectedPlanCode } ?: resolvedPlans.firstOrNull()

    // CTA Button scale animation (Breathing effect)
    val infiniteTransition = rememberInfiniteTransition(label = "cta_pulse")
    val ctaScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cta_scale"
    )

    // Flowing light sweep animation (流光效果)
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer_flow")
    val shimmerTranslate by shimmerTransition.animateFloat(
        initialValue = -600f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE53935), // Primary red
            Color(0xFFFF8A80), // Shimmer sweep light red
            Color(0xFFE53935)  // Primary red
        ),
        start = Offset(shimmerTranslate, 0f),
        end = Offset(shimmerTranslate + 300f, 300f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Main Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Top Feature Slides Header
            if (isVip) {
                MemberCenterHeader(
                    expireAt = membershipStatus?.expire_at,
                    onBack = onBack
                )
            } else {
                TopPagerSection(onBack = onBack)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isVip) "续费会员" else "选择会员套餐",
                color = TextDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            if (isVip) {
                Text(
                    text = "续费时长将在当前会员有效期基础上顺延",
                    color = TextGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Membership Option Cards (左右可滑动)
            HorizontalPriceOptionsSection(
                plans = resolvedPlans,
                selectedPlanCode = selectedPlan?.plan_code,
                onSelectOption = { selectedPlanCode = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Compact Payment Selection (放在会员卡片的下方)
            CompactPaymentSelectionSection(
                paymentChannels = paymentChannels,
                selectedPayment = selectedPayment,
                onSelectPayment = { selectedPayment = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Refund Guarantee Promise statement (精简为一行)
            RefundGuaranteeSection()

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Typical usage scenarios
            UserReviewsGridSection()

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Benefits Comparison Table (权益对比表格)
            BenefitsTableSection()

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Pinned Bottom Checkout Button (Only red breathing + flowing button and compact agreements)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // CTA Action Button with flow and pulse
                val priceText = selectedPlan?.price?.let(::formatPlanPriceText) ?: "--"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .scaleCompat(ctaScale)
                        .background(shimmerBrush, RoundedCornerShape(25.dp))
                        .clickable {
                            if (isPurchaseInFlight) {
                                Toast.makeText(context, "正在拉起支付宝，请稍候", Toast.LENGTH_SHORT).show()
                            } else if (!isAgreed) {
                                Toast.makeText(context, "请先勾选并同意服务协议", Toast.LENGTH_SHORT).show()
                            } else if (selectedPlan == null) {
                                Toast.makeText(context, "当前暂无可购买套餐", Toast.LENGTH_SHORT).show()
                            } else {
                                onPurchaseClick(selectedPlan.plan_code)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPurchaseInFlight) {
                            "正在跳转支付宝…"
                        } else if (isVip) {
                            "立即续费 $priceText"
                        } else {
                            "立即开通 $priceText"
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                purchaseMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Agreements Link Checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                color = if (isAgreed) Color(0xFFE53935) else Color.Transparent,
                                shape = CircleShape
                            )
                            .border(1.5.dp, if (isAgreed) Color(0xFFE53935) else Color(0xFF94A3B8), CircleShape)
                            .clickable { isAgreed = !isAgreed },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAgreed) {
                            Text(
                                text = "✓",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    val annotatedText = buildAnnotatedString {
                        append("同意")
                        pushStringAnnotation(tag = "USER_AGREE", annotation = "agreement")
                        withStyle(style = SpanStyle(color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)) {
                            append("《会员服务协议》")
                        }
                        pop()
                        append("和")
                        pushStringAnnotation(tag = "PRIVACY", annotation = "privacy")
                        withStyle(style = SpanStyle(color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)) {
                            append("《隐私政策》")
                        }
                        pop()
                    }

                    ClickableTextCompat(
                        text = annotatedText,
                        onClick = { offset ->
                            annotatedText.getStringAnnotations(tag = "USER_AGREE", start = offset, end = offset)
                                .firstOrNull()?.let {
                                    onLegalDocClick(LegalDocType.MemberServiceAgreement)
                                }
                            annotatedText.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset)
                                .firstOrNull()?.let {
                                    onLegalDocClick(LegalDocType.PrivacyPolicy)
                                }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberCenterHeader(expireAt: String?, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF20232D), Color(0xFF34302A)))
            )
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            CloseCrossIcon(color = Color.White, modifier = Modifier.size(16.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 58.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFFFEDAF), Color(0xFFD59A25))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "♛", color = Color(0xFF5B3B00), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "高级会员", color = Color(0xFFFFDF83), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                text = vipExpiryLabel(expireAt),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MemberBenefit("无水印", "PDF 导出")
                MemberBenefit("无水印", "图片导出")
                MemberBenefit("按套餐", "权益有效期")
            }
        }
    }
}

@Composable
private fun MemberBenefit(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color(0xFFFFDF83), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

private fun vipExpiryLabel(expireAt: String?): String {
    if (expireAt.isNullOrBlank()) return "会员权益已生效"
    val parts = expireAt.trim().take(10).split('-')
    return if (parts.size == 3) "有效期至 ${parts[0]}年${parts[1]}月${parts[2]}日" else "有效期至 $expireAt"
}

@Composable
private fun TopPagerSection(onBack: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
            .background(
                Brush.verticalGradient(
                    listOf(VipBackgroundBlue, VipBackgroundLight)
                )
            )
            .statusBarsPadding()
    ) {
        // Horizontal Pager for Feature Previews
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Info block
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = when (page) {
                            0 -> "证件扫描"
                            1 -> "文字识别"
                            else -> "文档导出"
                        },
                        color = TextDark,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (page) {
                            0 -> "清晰生成证件复印件\n打印提交更省心"
                            1 -> "设备端快速提取文字\n重要内容请使用前核验"
                            else -> "导出 PDF / 图片更方便\n会员可去除导出水印"
                        },
                        color = TextGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }

                // Custom Graphic illustration of each feature
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (page) {
                        0 -> IdCardIllustration(modifier = Modifier.fillMaxSize())
                        1 -> OcrIllustration(modifier = Modifier.fillMaxSize())
                        else -> TableIllustration(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        // Close Button (Declared after HorizontalPager so it is on top and clickable)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 12.dp)
                .size(36.dp)
                .background(Color.White.copy(alpha = 0.6f), CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            CloseCrossIcon(
                color = TextDark,
                modifier = Modifier.size(16.dp)
            )
        }

        // Pager Indicator Dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(3) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (active) 12.dp else 6.dp, 6.dp)
                        .background(
                            color = if (active) Color(0xFF3B82F6) else Color(0xFFCBD5E1),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun IdCardIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val clipWidth = size.width * 0.75f
        val clipHeight = size.height * 0.90f
        val centerOffset = Offset(size.width * 0.5f, size.height * 0.5f)

        // Draw Lanyard Strap
        val lanyardPath = Path().apply {
            moveTo(size.width * 0.5f, 0f)
            quadraticTo(
                size.width * 0.5f,
                size.height * 0.25f,
                size.width * 0.5f,
                size.height * 0.25f
            )
        }
        drawPath(
            path = lanyardPath,
            color = Color(0xFFEAB308), // Yellow lanyard strap
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Lanyard Metal Clip
        drawCircle(
            color = Color(0xFF94A3B8),
            radius = 6.dp.toPx(),
            center = Offset(size.width * 0.5f, size.height * 0.23f)
        )

        // The ID Card badge itself
        drawRoundRect(
            color = Color(0xFFFFD700), // Card yellow
            topLeft = Offset(size.width * 0.25f, size.height * 0.27f),
            size = Size(size.width * 0.58f, size.height * 0.65f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
        )

        // Card header text representation
        drawRoundRect(
            color = Color(0xFFEAB308),
            topLeft = Offset(size.width * 0.32f, size.height * 0.33f),
            size = Size(size.width * 0.44f, size.height * 0.08f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        )

        // Avatar Profile placeholder inside ID Card
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * 0.38f, size.height * 0.45f),
            size = Size(size.width * 0.32f, size.height * 0.32f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )

        // Stylized user avatar figure
        drawCircle(
            color = Color(0xFF3B82F6), // Blue head
            radius = 7.dp.toPx(),
            center = Offset(size.width * 0.54f, size.height * 0.52f)
        )
        val bodyPath = Path().apply {
            moveTo(size.width * 0.45f, size.height * 0.72f)
            lineTo(size.width * 0.63f, size.height * 0.72f)
            lineTo(size.width * 0.59f, size.height * 0.60f)
            lineTo(size.width * 0.49f, size.height * 0.60f)
            close()
        }
        drawPath(bodyPath, Color(0xFF1E293B))

        // Barcode / detail placeholder
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(size.width * 0.35f, size.height * 0.82f),
            end = Offset(size.width * 0.73f, size.height * 0.82f),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun OcrIllustration(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "ocr_scan")
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_y"
    )

    Canvas(modifier = modifier) {
        // Background paper sheet
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * 0.22f, size.height * 0.15f),
            size = Size(size.width * 0.56f, size.height * 0.70f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
        )

        // Lines of text placeholders
        val lines = listOf(
            Offset(0.3f, 0.25f) to 0.4f,
            Offset(0.3f, 0.35f) to 0.3f,
            Offset(0.3f, 0.45f) to 0.45f,
            Offset(0.3f, 0.55f) to 0.35f,
            Offset(0.3f, 0.65f) to 0.4f,
            Offset(0.3f, 0.75f) to 0.25f
        )

        lines.forEach { (startRatio, lengthRatio) ->
            val startX = size.width * startRatio.x
            val startY = size.height * startRatio.y
            val width = size.width * lengthRatio
            
            // Check if scanning line covers this line to highlight it
            val isHighlighted = kotlin.math.abs((startY / size.height) - scanY) < 0.08f
            
            drawRoundRect(
                color = if (isHighlighted) Color(0xFF60A5FA) else Color(0xFFE2E8F0),
                topLeft = Offset(startX, startY),
                size = Size(width, 6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }

        // Scanning beam laser line
        val scanLineY = size.height * scanY
        drawLine(
            color = Color(0xFF3B82F6),
            start = Offset(size.width * 0.18f, scanLineY),
            end = Offset(size.width * 0.82f, scanLineY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Glowing gradient under laser
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.22f), Color.Transparent),
                startY = scanLineY,
                endY = scanLineY + 24.dp.toPx()
            ),
            topLeft = Offset(size.width * 0.22f, scanLineY),
            size = Size(size.width * 0.56f, 24.dp.toPx())
        )
    }
}

@Composable
private fun TableIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // Draw a spreadsheet background
        drawRoundRect(
            color = Color(0xFFF1F5F9),
            topLeft = Offset(size.width * 0.15f, size.height * 0.12f),
            size = Size(size.width * 0.7f, size.height * 0.76f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
        )
        
        // Draw grid headers
        drawRoundRect(
            color = Color(0xFF3B82F6).copy(alpha = 0.2f),
            topLeft = Offset(size.width * 0.15f, size.height * 0.12f),
            size = Size(size.width * 0.7f, size.height * 0.15f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
        )

        // Draw vertical grid lines
        val cols = 4
        val colWidth = (size.width * 0.7f) / cols
        for (i in 1 until cols) {
            val x = size.width * 0.15f + i * colWidth
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(x, size.height * 0.12f),
                end = Offset(x, size.height * 0.88f),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // Draw horizontal grid lines
        val rows = 5
        val rowHeight = (size.height * 0.76f) / rows
        for (i in 1 until rows) {
            val y = size.height * 0.12f + i * rowHeight
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(size.width * 0.15f, y),
                end = Offset(size.width * 0.85f, y),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

@Composable
private fun CompactPaymentSelectionSection(
    paymentChannels: List<String>,
    selectedPayment: PaymentMethod,
    onSelectPayment: (PaymentMethod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if ("wechat" in paymentChannels) Row(
            modifier = Modifier
                .clickable { onSelectPayment(PaymentMethod.WeChat) }
                .padding(vertical = 4.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Custom Circle Selector
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(
                        1.5.dp,
                        if (selectedPayment == PaymentMethod.WeChat) Color(0xFFC62828) else Color(0xFFCBD5E1),
                        CircleShape
                    )
                    .background(Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selectedPayment == PaymentMethod.WeChat) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFC62828), CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // WeChat Icon bubble
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(0xFF09BB07), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✔", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Text(text = "微信支付", color = TextDark, fontSize = 14.sp)
        }

        if ("wechat" in paymentChannels && "alipay" in paymentChannels) {
            Spacer(modifier = Modifier.width(20.dp))
        }

        // Alipay
        if ("alipay" in paymentChannels) Row(
            modifier = Modifier
                .clickable { onSelectPayment(PaymentMethod.Alipay) }
                .padding(vertical = 4.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Custom Circle Selector
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(
                        1.5.dp,
                        if (selectedPayment == PaymentMethod.Alipay) Color(0xFFC62828) else Color(0xFFCBD5E1),
                        CircleShape
                    )
                    .background(Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selectedPayment == PaymentMethod.Alipay) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFC62828), CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Alipay Icon
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(0xFF108EE9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "支", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Text(text = "支付宝", color = TextDark, fontSize = 14.sp)
        }
    }
}

@Composable
private fun HorizontalPriceOptionsSection(
    plans: List<MembershipPlan>,
    selectedPlanCode: String?,
    onSelectOption: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            plans.forEach { plan ->
                PriceOptionCard(
                    selected = selectedPlanCode == plan.plan_code,
                    title = plan.title,
                    price = formatPlanAmount(plan.price),
                    originalPrice = plan.original_price?.let { "原价${formatPlanAmount(it)}元" },
                    bottomLabel = plan.bottom_label,
                    tagText = plan.tag_text,
                    onClick = { onSelectOption(plan.plan_code) },
                    modifier = Modifier.width(115.dp)
                )
            }
        }
    }
}

@Composable
private fun PriceOptionCard(
    selected: Boolean,
    title: String,
    price: String,
    originalPrice: String?,
    bottomLabel: String,
    tagText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "card_scale"
    )

    val borderColor = if (selected) SelectedBorderColor else UnselectedBorderColor
    val borderWidth = if (selected) 2.dp else 1.dp
    val bgColor = if (selected) SelectedBgColor else UnselectedBgColor
    
    Box(
        modifier = modifier
            .padding(top = 8.dp) // Space for badge overlap
            .scaleCompat(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title
            Text(
                text = title,
                color = if (selected) SelectedBorderColor else TextDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Price (e.g. 98元)
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = price,
                    color = if (selected) SelectedBorderColor else TextDark,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "元",
                    color = if (selected) SelectedBorderColor else TextDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Original Price
            if (!originalPrice.isNullOrBlank()) {
                Text(
                    text = originalPrice,
                    color = TextGray,
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.LineThrough
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Bottom Bar
            val bottomBg = if (selected) SelectedBorderColor else Color(0xFFE2E8F0)
            val bottomTextColor = if (selected) Color.White else TextGray
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(bottomBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bottomLabel,
                    color = bottomTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Overlapping tag badge
        if (tagText != null) {
            Box(
                modifier = Modifier
                    .offset(x = 10.dp, y = (-8).dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFEF5350), Color(0xFFFF7043))
                        ),
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tagText,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RefundGuaranteeSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🛡️", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "会员权益开通后生效，可在有效期内持续使用",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = ">",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun UserReviewsGridSection() {
    val scrollState = rememberScrollState()
    val reviews = listOf(
        Triple("办公整理", "合同与资料", "扫描纸质文件，完成裁边、增强后导出并自行归档。"),
        Triple("学习笔记", "文字提取", "使用设备端OCR提取图片文字，再核验、编辑并导出。"),
        Triple("证件材料", "规范排版", "按页面指引生成证件材料；提交前请核对清晰度和内容。"),
        Triple("文件分享", "PDF与图片", "将本地扫描结果导出为常用格式后，通过系统分享。"),
        Triple("会员权益", "去固定水印", "按实际需要选择套餐，在有效期内导出无固定品牌水印文件。")
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .background(Color(0xFF3B82F6), RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "常用功能场景",
                color = TextDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            reviews.forEach { (name, stars, content) ->
                Box(
                    modifier = Modifier
                        .width(240.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = name, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = stars, fontSize = 11.sp)
                        }
                        Text(
                            text = content,
                            color = TextGray,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 3
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitsTableSection() {
    val tableData = listOf(
        Triple("单页 / 多页扫描", "✓", "✓"),
        Triple("OCR 文字识别", "✓", "✓"),
        Triple("证件扫描", "✓", "✓"),
        Triple("PDF 导出", "带水印", "无水印"),
        Triple("图片导出", "带水印", "无水印"),
        Triple("本地文档整理", "✓", "✓")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .background(Color(0xFF3B82F6), RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "权益对比",
                color = TextDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "功能及对比",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.4f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "普通用户",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.3f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "会员",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.3f),
                textAlign = TextAlign.Center
            )
        }

        // Table Rows
        tableData.forEachIndexed { index, (feature, free, vip) ->
            val isEven = index % 2 == 0
            val bgColor = if (isEven) Color(0xFFF8FAFC) else Color.White
            val isLast = index == tableData.lastIndex
            val shape = if (isLast) RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp) else RoundedCornerShape(0.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor, shape)
                    .border(0.5.dp, Color(0xFFE2E8F0))
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feature,
                    color = TextDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .weight(0.4f)
                        .padding(start = 12.dp),
                    textAlign = TextAlign.Left
                )

                // Free user column
                val freeColor = if (free == "✖") Color(0xFFEF4444) else TextGray
                Text(
                    text = free,
                    color = freeColor,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(0.3f),
                    textAlign = TextAlign.Center
                )

                // VIP column
                val vipColor = if (vip == "✓") Color(0xFF10B981) else Color(0xFFD4AF37)
                Text(
                    text = vip,
                    color = vipColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(0.3f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Utility compatibility scale modifier
private fun Modifier.scaleCompat(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
)

@Composable
private fun ClickableTextCompat(
    text: androidx.compose.ui.text.AnnotatedString,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.text.ClickableText(
        text = text,
        onClick = onClick,
        style = androidx.compose.ui.text.TextStyle(
            color = TextGray,
            fontSize = 11.sp,
            lineHeight = 16.sp
        ),
        modifier = modifier
    )
}

// Simulated delay helper
private suspend fun delay(timeMillis: Long) {
    kotlinx.coroutines.delay(timeMillis)
}
