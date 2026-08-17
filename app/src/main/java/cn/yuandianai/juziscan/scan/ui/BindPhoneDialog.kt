package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BindPhoneDialog(
    isBusy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSendCode: suspend (String) -> Result<Unit>,
    onBind: suspend (String, String) -> Result<Unit>
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Surface(shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("绑定手机号")
            Text("绑定手机号，保障您的会员权益。")
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("手机号") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.weight(1f),
                    label = { Text("验证码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                TextButton(enabled = !isBusy && phone.length == 11, onClick = {
                    scope.launch { onSendCode(phone).onSuccess { message = "验证码已发送" }.onFailure { message = it.message } }
                }) { Text("获取验证码") }
            }
            (errorMessage ?: message)?.let { Text(it) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !isBusy) { Text("取消") }
                Button(enabled = !isBusy && phone.length == 11 && code.length >= 4, onClick = {
                    scope.launch { onBind(phone, code).onFailure { message = it.message } }
                }) { Text("确认绑定") }
            }
        }
    }
}
