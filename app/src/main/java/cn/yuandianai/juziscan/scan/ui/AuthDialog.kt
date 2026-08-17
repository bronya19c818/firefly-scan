package cn.yuandianai.juziscan.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yuandianai.juziscan.ui.theme.HpSmartColors
import kotlinx.coroutines.launch

private enum class AuthMode { Password, Sms, Register }

@Composable
fun AuthDialog(
    isBusy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onPasswordLogin: suspend (String, String) -> Result<Unit>,
    onSmsLogin: suspend (String, String) -> Result<Unit>,
    onRegister: suspend (String, String) -> Result<Unit>,
    onSendSms: suspend (String) -> Result<Unit>
) {
    var mode by remember { mutableStateOf(AuthMode.Password) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runAction(action: suspend () -> Result<Unit>) {
        scope.launch {
            localMessage = null
            action().onFailure { localMessage = it.message ?: "操作失败" }
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = when (mode) {
                        AuthMode.Password -> "密码登录"
                        AuthMode.Sms -> "短信验证"
                        AuthMode.Register -> "注册账号"
                    },
                    color = HpSmartColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (mode) {
                        AuthMode.Password -> "使用用户名和密码登录"
                        AuthMode.Sms -> "输入手机号及验证码登录/重置密码"
                        AuthMode.Register -> "注册专属账号，管理会员权益"
                    },
                    color = HpSmartColors.TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == AuthMode.Password || mode == AuthMode.Register) {
                    CustomTextField(
                        value = identifier,
                        onValueChange = { identifier = it.trim().lowercase().take(32) },
                        placeholder = if (mode == AuthMode.Register) "用户名" else "用户名或手机号"
                    )
                    CustomTextField(
                        value = password,
                        onValueChange = { password = it.take(128) },
                        placeholder = "密码",
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (mode == AuthMode.Register) {
                        CustomTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it.take(128) },
                            placeholder = "确认密码",
                            isPassword = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { mode = AuthMode.Sms; localMessage = null },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("忘记密码？", fontSize = 12.sp, color = HpSmartColors.Accent)
                            }
                        }
                    }
                } else {
                    CustomTextField(
                        value = phone,
                        onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                        placeholder = "已绑定手机号",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomTextField(
                            value = code,
                            onValueChange = { code = it.filter(Char::isDigit).take(6) },
                            placeholder = "验证码",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            enabled = !isBusy && phone.length == 11,
                            onClick = { runAction { onSendSms(phone) } },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = HpSmartColors.Accent,
                                disabledContentColor = HpSmartColors.TextSecondary.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(46.dp)
                        ) { Text("获取验证码", fontSize = 14.sp) }
                    }
                }
            }

            val displayError = errorMessage ?: localMessage
            if (displayError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = displayError,
                    color = Color(0xFFD14343),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                    colors = ButtonDefaults.textButtonColors(contentColor = HpSmartColors.TextSecondary)
                ) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    enabled = !isBusy,
                    onClick = {
                        when (mode) {
                            AuthMode.Password -> when {
                                identifier.length < 4 -> localMessage = "请输入用户名或手机号"
                                password.length < 6 -> localMessage = "密码至少需要 6 位"
                                else -> runAction { onPasswordLogin(identifier, password) }
                            }
                            AuthMode.Register -> when {
                                !identifier.matches(Regex("[a-z0-9_]{4,32}")) -> localMessage = "用户名格式不正确"
                                password.length < 6 -> localMessage = "密码至少需要 6 位"
                                password != confirmPassword -> localMessage = "两次输入的密码不一致"
                                else -> runAction { onRegister(identifier, password) }
                            }
                            AuthMode.Sms -> when {
                                phone.length != 11 -> localMessage = "请输入正确的手机号"
                                code.length < 4 -> localMessage = "请输入验证码"
                                else -> runAction { onSmsLogin(phone, code) }
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HpSmartColors.Accent,
                        contentColor = Color.White
                    )
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(18.dp)
                        )
                    } else {
                        Text(if (mode == AuthMode.Register) "注册并登录" else "登录")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (mode) {
                    AuthMode.Password -> {
                        Text("还没有账号？", fontSize = 13.sp, color = HpSmartColors.TextSecondary)
                        Text(
                            text = "立即注册",
                            fontSize = 13.sp,
                            color = HpSmartColors.Accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = !isBusy) {
                                mode = AuthMode.Register
                                localMessage = null
                                confirmPassword = ""
                            }
                        )
                    }
                    AuthMode.Register -> {
                        Text("已有账号？", fontSize = 13.sp, color = HpSmartColors.TextSecondary)
                        Text(
                            text = "立即登录",
                            fontSize = 13.sp,
                            color = HpSmartColors.Accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = !isBusy) {
                                mode = AuthMode.Password
                                localMessage = null
                                confirmPassword = ""
                            }
                        )
                    }
                    AuthMode.Sms -> {
                        Text(
                            text = "返回密码登录",
                            fontSize = 13.sp,
                            color = HpSmartColors.Accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = !isBusy) {
                                mode = AuthMode.Password
                                localMessage = null
                                confirmPassword = ""
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Color(0xFFF8F9FE), RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (isFocused) HpSmartColors.Accent else Color(0xFFE2E4FA),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 16.dp),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(fontSize = 14.sp, color = HpSmartColors.TextPrimary),
        cursorBrush = SolidColor(HpSmartColors.Accent),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 14.sp,
                        color = HpSmartColors.TextSecondary.copy(alpha = 0.6f)
                    )
                }
                innerTextField()
            }
        }
    )
}
