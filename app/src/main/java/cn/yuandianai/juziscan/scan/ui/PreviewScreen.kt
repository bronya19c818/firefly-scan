package cn.yuandianai.juziscan.scan.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    onReorder: () -> Unit
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
                // Keep the document's thumbnails small; decode detail only for the visible page.
                val detailedPreview by produceState<Bitmap?>(null, page.id, page.contentRevision,
                    page.processedImagePath, page.processedBitmap, isCurrent) {
                    value = null
                    if (isCurrent) {
                        value = withContext(Dispatchers.IO) {
                            try {
                                page.processedImagePath?.let {
                                    loadBitmapFromUri(context, Uri.fromFile(File(it)), 2048)
                                } ?: page.processedBitmap?.let { preparePreviewBitmap(it, 2048) }
                            } catch (error: Exception) {
                                Log.w("JuZiScan", "Could not load detailed page preview", error)
                                null
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    RotatedPreviewImage(
                        bitmap = detailedPreview ?: requireNotNull(page.previewBitmap),
                        rotationDegrees = pages[pageIdx].rotationDegrees,
                        contentDescription = "扫描预览 ${pageIdx + 1}",
                        modifier = Modifier.fillMaxSize()
                    )
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

@Composable
private fun DividerHorizontal() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE5E6F2))
    )
}
