package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun NicknameEditDialog(
    currentNickname: String,
    isBusy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nickname by remember(currentNickname) { mutableStateOf(currentNickname) }
    val normalized = nickname.trim()

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("修改昵称") },
        text = {
            OutlinedTextField(
                value = nickname,
                onValueChange = { if (it.length <= 64) nickname = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("昵称") },
                singleLine = true,
                enabled = !isBusy,
                isError = errorMessage != null,
                supportingText = errorMessage?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (!isBusy && normalized.isNotEmpty()) onConfirm(normalized) }
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isBusy && normalized.isNotEmpty() && normalized != currentNickname,
                onClick = { onConfirm(normalized) }
            ) {
                Text(if (isBusy) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(enabled = !isBusy, onClick = onDismiss) { Text("取消") }
        }
    )
}
