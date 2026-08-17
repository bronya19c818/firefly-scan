package cn.yuandianai.juziscan.settings

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.scan.LegalDocType
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

@Composable
fun SettingsScreen(
    scanQuality: String,
    defaultExportFormat: String,
    cacheSize: String,
    appVersion: String,
    onBack: () -> Unit,
    onScanQualityClick: () -> Unit,
    onDefaultExportFormatClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onPermissionClick: () -> Unit,
    onLegalDocClick: (LegalDocType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FF))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹",
                fontSize = 38.sp,
                color = HpSmartColors.TextPrimary,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 18.dp)
            )
            Text("应用设置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HpSmartColors.TextPrimary)
        }

        SectionTitle("扫描与导出")
        SettingsCard {
            SettingRow("扫描保存质量", scanQuality, onScanQualityClick)
            Divider()
            SettingRow("默认导出格式", defaultExportFormat, onDefaultExportFormatClick)
        }

        SectionTitle("存储与隐私")
        SettingsCard {
            SettingRow("清理缓存", cacheSize, onClearCacheClick)
            Divider()
            SettingRow("系统权限管理", "相机、相册", onPermissionClick)
        }

        SectionTitle("关于")
        SettingsCard {
            SettingRow("隐私政策", "", { onLegalDocClick(LegalDocType.PrivacyPolicy) })
            Divider()
            SettingRow("用户服务协议", "", { onLegalDocClick(LegalDocType.UserAgreement) })
            Divider()
            SettingRow("会员服务协议", "", { onLegalDocClick(LegalDocType.MemberServiceAgreement) })
            Divider()
            SettingRow("关于桔子扫描", "v$appVersion", { onLegalDocClick(LegalDocType.AboutUs) })
        }
        Spacer(Modifier.height(28.dp))
    }

}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = HpSmartColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp)) { content() }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 17.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = if (danger) Color(0xFFE5484D) else HpSmartColors.TextPrimary, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotEmpty()) Text(value, color = HpSmartColors.TextSecondary, fontSize = 14.sp)
            if (showChevron) Text("  ›", color = Color(0xFFC7D0DF), fontSize = 23.sp)
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F5F9)))
}
