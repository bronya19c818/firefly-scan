package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun FeedbackDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: suspend (String, String) -> Result<Unit>
) {
    var content by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val accentColor = Color(0xFFFF6B00) // Match web primary color or app accent

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSuccess) {
                Text(
                    text = "提交成功",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "非常感谢！我们已收到您的意见反馈，将尽快处理您的问题。",
                    fontSize = 15.sp,
                    color = Color(0xFF64748B)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("好 的", color = Color.White)
                    }
                }
            } else {
                Text(
                    text = "遇到问题？意见反馈",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "请详细描述您遇到的问题（如：扫描不清晰、支付未到账等）以及您的宝贵建议：",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it; errorMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("反馈内容 *") },
                    placeholder = { Text("请输入至少5个字的描述内容...") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        focusedLabelColor = accentColor
                    ),
                    enabled = !isBusy
                )

                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("联系方式 (选填)") },
                    placeholder = { Text("手机号或邮箱，方便与您取得联系") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        focusedLabelColor = accentColor
                    ),
                    enabled = !isBusy
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        color = Color.Red,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isBusy
                    ) {
                        Text("取消", color = Color(0xFF64748B))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        enabled = !isBusy && content.trim().length >= 5,
                        onClick = {
                            scope.launch {
                                onSubmit(content.trim(), contact.trim()).onSuccess {
                                    isSuccess = true
                                }.onFailure {
                                    errorMessage = it.message ?: "提交失败，请检查网络"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            disabledContainerColor = accentColor.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = if (isBusy) "提交中..." else "提交反馈",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
