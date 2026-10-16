package cn.yuandianai.juziscan.scan.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cn.yuandianai.juziscan.common.CloseCrossIcon
import cn.yuandianai.juziscan.scan.ScannedPage
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

@Composable
fun ReorderScreen(
    pages: List<ScannedPage>,
    onCancel: () -> Unit,
    onDone: (List<ScannedPage>) -> Unit
) {
    var localPages by remember(pages) { mutableStateOf(pages) }
    var draggedPageId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HpSmartColors.Background)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            ActionPillButton(
                text = "取消",
                filled = false,
                onClick = onCancel,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            Text(
                text = "重新排序",
                color = HpSmartColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            ActionPillButton(
                text = "完成",
                filled = true,
                onClick = { onDone(localPages) },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val density = LocalDensity.current

            val itemWidth = 84.dp
            val itemHeight = 146.dp
            val labelHeight = 24.dp
            val totalCellHeight = itemHeight + labelHeight

            val itemWidthPx = with(density) { itemWidth.toPx() }
            val itemHeightPx = with(density) { itemHeight.toPx() }
            val totalCellHeightPx = with(density) { totalCellHeight.toPx() }

            val numColumns = 3
            val horizontalSpacingPx = (widthPx - (itemWidthPx * numColumns)) / (numColumns + 1)
            val verticalSpacingPx = with(density) { 24.dp.toPx() }

            fun targetX(index: Int): Float {
                val col = index % numColumns
                return horizontalSpacingPx + col * (itemWidthPx + horizontalSpacingPx)
            }

            fun targetY(index: Int): Float {
                val row = index / numColumns
                return verticalSpacingPx + row * (totalCellHeightPx + verticalSpacingPx)
            }

            val totalRows = (localPages.size + numColumns - 1) / numColumns
            val contentHeightDp = with(density) {
                ((totalRows * (totalCellHeightPx + verticalSpacingPx) + verticalSpacingPx).toDp())
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeightDp)
            ) {
                localPages.forEachIndexed { index, page ->
                    key(page.id) {
                        val isDragging = page.id == draggedPageId
                        val tX = targetX(index)
                        val tY = targetY(index)

                        val targetOffset = if (isDragging) {
                            Offset(tX + dragOffset.x, tY + dragOffset.y)
                        } else {
                            Offset(tX, tY)
                        }

                        val animatedOffset by animateOffsetAsState(
                            targetValue = targetOffset,
                            animationSpec = if (isDragging) snap() else spring(stiffness = Spring.StiffnessMedium),
                            label = "card_offset_${page.id}"
                        )

                        val xDp = with(density) { (if (isDragging) (tX + dragOffset.x) else animatedOffset.x).toDp() }
                        val yDp = with(density) { (if (isDragging) (tY + dragOffset.y) else animatedOffset.y).toDp() }

                        Box(
                            modifier = Modifier
                                .offset(x = xDp, y = yDp)
                                .zIndex(if (isDragging) 1f else 0f)
                                .width(itemWidth)
                                .height(totalCellHeight)
                                .shadow(
                                    elevation = if (isDragging) 8.dp else 0.dp,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .pointerInput(page.id, widthPx, density.density) {
                                    detectDragGestures(
                                        onDragStart = { _ ->
                                            draggedPageId = page.id
                                            dragOffset = Offset.Zero
                                        },
                                        onDragEnd = {
                                            draggedPageId = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            draggedPageId = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount

                                            val currentIdx = localPages.indexOfFirst { it.id == draggedPageId }
                                            if (currentIdx >= 0) {
                                                val currX = targetX(currentIdx) + dragOffset.x + itemWidthPx / 2f
                                                val currY = targetY(currentIdx) + dragOffset.y + itemHeightPx / 2f

                                                val col = ((currX - horizontalSpacingPx / 2f) / (itemWidthPx + horizontalSpacingPx))
                                                    .toInt()
                                                    .coerceIn(0, numColumns - 1)
                                                val row = ((currY - verticalSpacingPx / 2f) / (totalCellHeightPx + verticalSpacingPx))
                                                    .toInt()
                                                    .coerceAtLeast(0)
                                                val hoverIndex = (row * numColumns + col).coerceIn(0, localPages.lastIndex)

                                                if (hoverIndex != currentIdx) {
                                                    val newPages = localPages.toMutableList()
                                                    val itemToMove = newPages.removeAt(currentIdx)
                                                    newPages.add(hoverIndex, itemToMove)

                                                    val dx = targetX(currentIdx) - targetX(hoverIndex)
                                                    val dy = targetY(currentIdx) - targetY(hoverIndex)

                                                    localPages = newPages
                                                    dragOffset = Offset(dragOffset.x + dx, dragOffset.y + dy)
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(itemWidth)
                                    .height(itemHeight)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFE5E6F2), RoundedCornerShape(8.dp))
                            ) {
                                RotatedPreviewImage(
                                    bitmap = requireNotNull(page.previewBitmap),
                                    rotationDegrees = page.rotationDegrees,
                                    contentDescription = "重新排序 ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .size(24.dp)
                                    .background(Color(0xFFD32F2F), CircleShape)
                                    .pointerInput(page.id) {
                                        detectTapGesturesCompat {
                                            val newPages = localPages.toMutableList()
                                            newPages.removeAll { it.id == page.id }
                                            localPages = newPages
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CloseCrossIcon(color = Color.White, modifier = Modifier.size(10.dp))
                            }

                            Text(
                                text = "${index + 1}",
                                color = HpSmartColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(labelHeight),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
