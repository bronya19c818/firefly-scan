package cn.yuandianai.juziscan.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AuthSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var session by mutableStateOf(loadSession())
        private set

    fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putInt(KEY_USER_ID, session.user.id)
            .putString(KEY_USERNAME, session.user.username)
            .putString(KEY_NICKNAME, session.user.nickname)
            .putString(KEY_AVATAR_URL, session.user.avatarUrl)
            .putString(KEY_PHONE, session.user.phone)
            .putString(KEY_EMAIL, session.user.email)
            .putBoolean(KEY_IS_ACTIVE, session.user.isActive)
            .putBoolean(KEY_IS_ADMIN, session.user.isAdmin)
            .putString(KEY_CREATED_AT, session.user.createdAt)
            .apply()
        this.session = session
    }

    fun clear() {
        prefs.edit().clear().apply()
        session = createDefaultDevSession()
    }

    fun accessTokenOrNull(): String? = session?.accessToken

    private fun loadSession(): AuthSession {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
        val userId = prefs.getInt(KEY_USER_ID, -1).takeIf { it > 0 }

        if (token == null || userId == null) {
            return createDefaultDevSession()
        }

        return AuthSession(
            accessToken = token,
            user = AuthUserProfile(
                id = userId,
                username = prefs.getString(KEY_USERNAME, null),
                nickname = prefs.getString(KEY_NICKNAME, null),
                avatarUrl = prefs.getString(KEY_AVATAR_URL, null),
                phone = prefs.getString(KEY_PHONE, null),
                email = prefs.getString(KEY_EMAIL, null),
                isActive = prefs.getBoolean(KEY_IS_ACTIVE, true),
                isAdmin = prefs.getBoolean(KEY_IS_ADMIN, true),
                createdAt = prefs.getString(KEY_CREATED_AT, null)
            )
        )
    }

    private fun createDefaultDevSession(): AuthSession {
        return AuthSession(
            accessToken = "dev_offline_mock_token_2099",
            user = AuthUserProfile(
                id = 9999,
                username = "Developer",
                nickname = "开发者测试账号",
                avatarUrl = null,
                phone = "13800000000",
                email = "dev@juziscan.local",
                isActive = true,
                isAdmin = true,
                createdAt = "2026-01-01T00:00:00Z"
            )
        )
    }

    private companion object {
        const val PREFS_NAME = "auth_session"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_NICKNAME = "nickname"
        const val KEY_AVATAR_URL = "avatar_url"
        const val KEY_PHONE = "phone"
        const val KEY_EMAIL = "email"
        const val KEY_IS_ACTIVE = "is_active"
        const val KEY_IS_ADMIN = "is_admin"
        const val KEY_CREATED_AT = "created_at"
    }
}
