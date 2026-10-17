package cn.yuandianai.juziscan.scan.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.common.ShareUploadIcon
import cn.yuandianai.juziscan.common.TrashBinIcon
import cn.yuandianai.juziscan.history.HistoryRecord
import cn.yuandianai.juziscan.history.HistoryRecordKind
import cn.yuandianai.juziscan.ui.theme.FireflyColors
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryRecordCard(
    record: HistoryRecord,
    onOpen: (HistoryRecord) -> Unit,
    onShare: (HistoryRecord) -> Unit,
    onDelete: ((HistoryRecord) -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val thumbnail = remember(record.thumbnailPath) {
        record.thumbnailPath
            ?.takeIf { it.isNotBlank() }
            ?.let(BitmapFactory::decodeFile)
    }

    Row(
        modifier = modifier
            .testTag("history-${record.id}")
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .pointerInput(record.id) { detectTapGesturesCompat { onOpen(record) } }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(if (compact) 0.92f else 0.75f)
                .aspectRatio(if (compact) 1.0f else 0.88f)
                .background(
                    Brush.linearGradient(
                        listOf(FireflyColors.SurfaceSoft, Color(0xFFD8EFE7))
                    ),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = record.title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = record.kind.displayLabel(),
                    color = FireflyColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier.weight(1.45f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = record.title,
                color = HpSmartColors.TextPrimary,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = record.subtitle,
                color = HpSmartColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            record.textPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                Text(
                    text = preview.replace('\n', ' '),
                    color = FireflyColors.SecondaryText,
                    fontSize = 12.sp,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatHistoryTime(record.updatedAtMillis),
                    color = FireflyColors.SecondaryText.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallHistoryAction(
                        text = "分享",
                        onClick = { onShare(record) }
                    ) {
                        ShareUploadIcon(color = FireflyColors.Primary, modifier = Modifier.size(15.dp))
                    }
                    if (onDelete != null) {
                        SmallHistoryAction(
                            text = "删除",
                            onClick = { onDelete(record) }
                        ) {
                            TrashBinIcon(color = FireflyColors.Error, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color.White, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = FireflyColors.SecondaryText,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SmallHistoryAction(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .background(FireflyColors.SurfaceSoft, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .pointerInput(text) { detectTapGesturesCompat(onClick) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = text,
            color = HpSmartColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun HistoryRecordKind.displayLabel(): String {
    return when (this) {
        HistoryRecordKind.Document -> "PDF"
        HistoryRecordKind.IdCard -> "证件"
        HistoryRecordKind.Ocr -> "OCR"
    }
}

private fun formatHistoryTime(timestamp: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
