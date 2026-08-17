package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.history.HistoryRecord
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

@Composable
fun AllDocumentsScreen(
    records: List<HistoryRecord>,
    onBack: () -> Unit,
    onOpenRecord: (HistoryRecord) -> Unit,
    onShareRecord: (HistoryRecord) -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FF))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopSoftButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 22.dp, top = 12.dp)
        )

        Text(
            text = "全部文档",
            color = HpSmartColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 16.dp)
        )

        if (records.isEmpty()) {
            EmptyHistoryState(
                text = "暂无文档记录",
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryRecordCard(
                        record = record,
                        onOpen = onOpenRecord,
                        onShare = onShareRecord,
                        onDelete = onDeleteRecord
                    )
                }
            }
        }
    }
}
