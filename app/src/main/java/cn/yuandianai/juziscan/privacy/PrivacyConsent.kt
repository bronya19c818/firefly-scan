package cn.yuandianai.juziscan.privacy

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.scan.LegalDocType
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

const val CURRENT_PRIVACY_POLICY_VERSION = "2026-07-23-v3"

class PrivacyConsentStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasAcceptedCurrentPolicy(): Boolean =
        prefs.getString(KEY_ACCEPTED_VERSION, null) == CURRENT_PRIVACY_POLICY_VERSION

    fun acceptCurrentPolicy() {
        prefs.edit().putString(KEY_ACCEPTED_VERSION, CURRENT_PRIVACY_POLICY_VERSION).apply()
    }

    private companion object {
        const val PREFS_NAME = "privacy_consent"
        const val KEY_ACCEPTED_VERSION = "accepted_policy_version"
    }
}

@Composable
fun PrivacyConsentDialog(
    onAccept: () -> Unit,
    onExit: () -> Unit,
    onOpenLegalDocument: (LegalDocType) -> Unit
) {
    AlertDialog(
        modifier = Modifier.widthIn(max = 320.dp),
        onDismissRequest = {},
        title = {
            Text(
                text = "欢迎使用萤火Scan",
                color = HpSmartColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "为提供扫描、文字识别、账号及会员服务，我们会按相关协议处理必要信息。相机等权限仅在您使用对应功能时申请；进入含文本输入框的页面或主动粘贴、复制时，系统界面组件可能在本机访问剪贴板，仅用于判断可粘贴内容或完成您触发的操作，不存储、不上传。",
                    color = HpSmartColors.TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Text(
                    text = "请阅读以下协议并选择是否同意。点击“不同意”将退出 App。",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "《隐私政策》",
                        color = Color(0xFF2563EB),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { onOpenLegalDocument(LegalDocType.PrivacyPolicy) }
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        text = "《用户服务协议》",
                        color = Color(0xFF2563EB),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { onOpenLegalDocument(LegalDocType.UserAgreement) }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("我同意", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text("不同意", color = Color(0xFF64748B))
            }
        }
    )
}
