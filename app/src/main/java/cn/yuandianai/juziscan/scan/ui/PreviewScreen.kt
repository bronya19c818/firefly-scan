package cn.yuandianai.juziscan.scan.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.common.*
import cn.yuandianai.juziscan.scan.ScannedPage
import cn.yuandianai.juziscan.scan.PageProcessingPhase
import cn.yuandianai.juziscan.scan.loadBitmapFromUri
import cn.yuandianai.juziscan.scan.preparePreviewBitmap
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

@Composable
fun PreviewResultScreen(
    pages: List<ScannedPage>,
    activeIndex: Int,
    onActiveIndexChange: (Int) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onRotate: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onReorder: () -> Unit,
    // 新增参数统一放在末尾，保持既有调用点的位置参数兼容。
    pagePhases: Map<String, PageProcessingPhase> = emptyMap(),
    onRetryPage: (String) -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        SimpleTitleBar(
            title = "预览",
            onBack = onBack,
            actionText = "",
            onAction = null,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlineActionPill(
                    text = "添加",
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PlusIcon(color = HpSmartColors.TextPrimary, modifier = Modifier.size(16.dp))
                }
            }

            if (pages.size > 1) {
                Box(modifier = Modifier.weight(1.3f)) {
                    OutlineActionPill(
                        text = "重新排序",
                        onClick = onReorder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReorderIcon(color = HpSmartColors.TextPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlineActionPill(
                    text = "旋转",
                    onClick = onRotate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RotateArrowIcon(color = HpSmartColors.TextPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val pagerState = rememberPagerState(initialPage = activeIndex) { pages.size }

            LaunchedEffect(activeIndex) {
                if (pagerState.currentPage != activeIndex) {
                    pagerState.scrollToPage(activeIndex)
                }
            }
            LaunchedEffect(pagerState.currentPage) {
                if (activeIndex != pagerState.currentPage) {
                    onActiveIndexChange(pagerState.currentPage)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) { pageIdx ->
                val page = pages[pageIdx]
                val context = LocalContext.current
                val isCurrent = pageIdx == activeIndex
                // 两级显示：当前页加载高清处理图；未处理页面先用快速预览占位，
                // 其余页面只保留小图，避免同时持有大量 Bitmap。
                // 只有真正换页时才清空画面：同一页由后台处理补齐时保留当前图像，
                // 直到新图解码完成再替换，避免瞬间空白。
                var shownPageId by remember { mutableStateOf<String?>(null) }
                val displayBitmap by produceState<Bitmap?>(
                    null, page.id, page.contentRevision, page.imageStyle, page.editableQuad, page.correctionMode, page.processedImagePath,
                    page.processedBitmap, page.previewBitmap, isCurrent
                ) {
                    val switchingPage = shownPageId != null && shownPageId != page.id
                    shownPageId = page.id
                    if (switchingPage) value = null
                    value = withContext(Dispatchers.IO) {
                        try {
                            if (isCurrent) {
                                val detailed = page.processedImagePath?.let {
                                    loadBitmapFromUri(context, Uri.fromFile(File(it)), 2048)
                                } ?: page.processedBitmap?.let { preparePreviewBitmap(it, 2048) }
                                detailed ?: quickPagePreview(context, page)
                            } else {
                                quickPagePreview(context, page)
                            }
                        } catch (error: Exception) {
                            Log.w("JuZiScan", "Could not load page preview", error)
                            page.previewBitmap
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = displayBitmap
                    if (bitmap != null) {
                        RotatedPreviewImage(
                            bitmap = bitmap,
                            rotationDegrees = pages[pageIdx].rotationDegrees,
                            contentDescription = "扫描预览 ${pageIdx + 1}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // 单页处理状态：未完成处理的页面明确提示，不误导为最终成品。
                    when (pagePhases[page.id]) {
                        PageProcessingPhase.PENDING -> PageStatusBadge(text = "等待处理…")
                        PageProcessingPhase.PROCESSING -> PageStatusBadge(text = "正在处理这一页…")
                        PageProcessingPhase.FAILED -> PageStatusBadge(
                            text = "处理失败，点击重试",
                            failed = true,
                            onClick = { onRetryPage(page.id) }
                        )
                        else -> Unit
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (menuOpen) {
                        PopupMenuCard(
                            onEdit = {
                                menuOpen = false
                                onEdit()
                            },
                            onReplace = {
                                menuOpen = false
                                onReplace()
                            },
                            onDelete = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                    FloatingMoreButton(
                        isOpen = menuOpen,
                        onClick = { menuOpen = !menuOpen }
                    )
                }
            }

            if (pages.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFE5E6F2), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${activeIndex + 1} / ${pages.size}",
                        color = HpSmartColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewBottomAction(label = "打印", onClick = onPrint) { color ->
                PrintIcon(color = color, modifier = Modifier.size(22.dp))
            }
            PreviewBottomAction(label = "导出", onClick = onShare) { color ->
                ShareUploadIcon(color = color, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun PopupMenuCard(
    onEdit: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(130.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE5E6F2), RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp)
    ) {
        PopupMenuItem(text = "编辑", icon = { PencilIcon(color = HpSmartColors.Accent, modifier = Modifier.size(16.dp)) }, onClick = onEdit)
        DividerHorizontal()
        PopupMenuItem(text = "更换", icon = { ReplaceArrowsIcon(color = HpSmartColors.Accent, modifier = Modifier.size(16.dp)) }, onClick = onReplace)
        DividerHorizontal()
        PopupMenuItem(text = "删除", icon = { TrashBinIcon(color = HpSmartColors.Accent, modifier = Modifier.size(16.dp)) }, onClick = onDelete)
    }
}

@Composable
private fun PopupMenuItem(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(text) { detectTapGesturesCompat(onClick) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = HpSmartColors.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 未处理页面的快速预览：优先复用已有缩略图，其次按需降采样原图。
 * 结果只是占位画面，页面仍处于处理中时会叠加处理状态提示。
 */
private suspend fun quickPagePreview(context: Context, page: ScannedPage): Bitmap? =
    page.previewBitmap ?: runCatching {
        page.sourceImagePath?.let { loadBitmapFromUri(context, Uri.fromFile(File(it)), 720) }
            ?: preparePreviewBitmap(page.sourceBitmap, 720)
    }.getOrNull()

@Composable
private fun DividerHorizontal() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE5E6F2))
    )
}

@Composable
private fun BoxScope.PageStatusBadge(
    text: String,
    failed: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 52.dp)
            .background(if (failed) Color(0xFFFFF1F0) else Color.White, shape)
            .border(1.dp, if (failed) Color(0xFFFFC7C2) else Color(0xFFE5E6F2), shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    if (failed) Color(0xFFD4380D) else Color(0xFF0B63FF),
                    androidx.compose.foundation.shape.CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = if (failed) Color(0xFFD4380D) else HpSmartColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
