package cn.yuandianai.juziscan.auth

data class AuthUserProfile(
    val id: Int,
    val username: String?,
    val nickname: String?,
    val avatarUrl: String?,
    val phone: String?,
    val email: String?,
    val isActive: Boolean,
    val isAdmin: Boolean,
    val createdAt: String?
) {
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: phone?.takeIf { it.isNotBlank() }?.let(::maskPhone)
            ?: email?.takeIf { it.isNotBlank() }
            ?: "已登录用户"

    val secondaryLabel: String
        get() = phone?.takeIf { it.isNotBlank() }?.let(::maskPhone)
            ?: username?.takeIf { it.isNotBlank() }?.let { "用户名：$it" }
            ?: email?.takeIf { it.isNotBlank() }
            ?: "已登录，可管理账号及会员权益"
}

data class AuthSession(
    val accessToken: String,
    val user: AuthUserProfile
)

fun maskPhone(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    if (digits.length < 7) return phone
    return buildString {
        append(digits.take(3))
        append("****")
        append(digits.takeLast(4))
    }
}
