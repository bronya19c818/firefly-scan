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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.auth.maskPhone
import cn.yuandianai.juziscan.ui.theme.HpSmartColors

@Composable
fun AccountSecurityScreen(
    phone: String?,
    onBack: () -> Unit,
    onBindPhoneClick: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteAccount: (String) -> Unit
) {
    var showChangePassword by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }

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
            Text(
                text = "账号与安全",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = HpSmartColors.TextPrimary
            )
        }

        Text(
            text = "账号信息",
            color = HpSmartColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp)
        ) {
            AccountRow(
                title = "手机号",
                value = if (phone.isNullOrBlank()) "未绑定，去绑定" else maskPhone(phone),
                onClick = { if (phone.isNullOrBlank()) onBindPhoneClick() },
                showChevron = phone.isNullOrBlank()
            )
            Divider()
            AccountRow("登录密码", "修改密码", { showChangePassword = true })
        }

        Text(
            text = "账号操作",
            color = HpSmartColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp)
        ) {
            AccountRow("退出当前账号", "", { showLogoutConfirm = true }, showChevron = false)
            Divider()
            AccountRow(
                title = "注销账号",
                value = "",
                onClick = { showDeleteAccountConfirm = true },
                danger = true,
                showChevron = false
            )
        }
        Spacer(Modifier.height(28.dp))
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onConfirm = { oldPassword, newPassword ->
                showChangePassword = false
                onChangePassword(oldPassword, newPassword)
            }
        )
    }
    if (showLogoutConfirm) {
        LogoutConfirmDialog(
            onDismiss = { showLogoutConfirm = false },
            onConfirm = {
                showLogoutConfirm = false
                onLogoutClick()
            }
        )
    }
    if (showDeleteAccountConfirm) {
        DeleteAccountConfirmDialog(
            onDismiss = { showDeleteAccountConfirm = false },
            onConfirm = { password ->
                showDeleteAccountConfirm = false
                onDeleteAccount(password)
            }
        )
    }
}

@Composable
private fun AccountRow(
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
        Text(
            text = title,
            color = if (danger) Color(0xFFE5484D) else HpSmartColors.TextPrimary,
            fontSize = 16.sp
        )
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

@Composable
private fun LogoutConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出登录") },
        text = { Text("退出后，本机将不再使用当前账号访问会员等联网服务。本地文档不会被删除。确定要退出吗？", color = HpSmartColors.TextSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认退出") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DeleteAccountConfirmDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("注销账号", color = Color(0xFFE5484D)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "注销后，账号资料及相关联网服务数据将按法律规定处理，本机文档不会自动删除。请输入当前登录密码确认注销。",
                    color = HpSmartColors.TextSecondary
                )
                PasswordField("当前登录密码", password) { password = it }
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotBlank(), onClick = { onConfirm(password) }) {
                Text(
                    "永久注销",
                    color = if (password.isNotBlank()) Color(0xFFE5484D) else Color(0xFFB8C0CC)
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val valid = oldPassword.length >= 6 && newPassword.length >= 6 && newPassword == confirmPassword
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField("原密码", oldPassword) { oldPassword = it }
                PasswordField("新密码（至少 6 位）", newPassword) { newPassword = it }
                PasswordField("确认新密码", confirmPassword) { confirmPassword = it }
                if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                    Text("两次输入的新密码不一致", color = Color(0xFFE5484D), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(oldPassword, newPassword) }) {
                Text("确认修改")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
}
