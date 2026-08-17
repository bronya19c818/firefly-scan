package cn.yuandianai.juziscan.payment

import cn.yuandianai.juziscan.BuildConfig
import cn.yuandianai.juziscan.auth.AuthSession
import cn.yuandianai.juziscan.auth.AuthUserProfile
import org.json.JSONObject
import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class JuZiScanBackendClient(
    private val baseUrl: String = BuildConfig.JUZISCAN_API_BASE_URL,
    private val accessTokenProvider: () -> String? = { null },
    private val onUnauthorized: () -> Unit = {}
) {
    fun loginWithPassword(identifier: String, password: String): AuthSession {
        val json = JSONObject().apply {
            put("identifier", identifier)
            put("password", password)
        }
        return parseAuthSession(
            request(
                method = "POST",
                path = "/api/v1/auth/password/login",
                requestBody = json.toString(),
                requireAuth = false
            )
        )
    }

    fun register(username: String, password: String): AuthSession {
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        return parseAuthSession(request("POST", "/api/v1/auth/register", json.toString(), false))
    }

    fun sendSmsCode(phone: String, purpose: String) {
        val json = JSONObject().apply {
            put("phone", phone)
            put("purpose", purpose)
        }
        request("POST", "/api/v1/auth/sms/send-code", json.toString(), false)
    }

    fun loginWithSms(phone: String, code: String): AuthSession {
        val json = JSONObject().apply {
            put("phone", phone)
            put("code", code)
        }
        return parseAuthSession(request("POST", "/api/v1/auth/sms/login", json.toString(), false))
    }

    fun bindPhone(phone: String, code: String): AuthUserProfile {
        val json = JSONObject().apply {
            put("phone", phone)
            put("code", code)
        }
        return parseUser(request("POST", "/api/v1/auth/phone/bind", json.toString()))
    }

    fun getCurrentUser(): AuthUserProfile {
        return parseUser(
            request(
                method = "GET",
                path = "/api/v1/auth/me"
            )
        )
    }

    fun updateNickname(nickname: String): AuthUserProfile {
        val json = JSONObject().apply { put("nickname", nickname) }
        return parseUser(request("PATCH", "/api/v1/auth/me", json.toString()))
    }

    fun uploadAvatar(imageBytes: ByteArray, contentType: String = "image/jpeg"): AuthUserProfile {
        val json = JSONObject().apply {
            put("image_base64", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
            put("content_type", contentType)
        }
        return parseUser(request("POST", "/api/v1/auth/avatar", json.toString()))
    }

    fun resolveMediaUrl(path: String?): String? {
        val value = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        return baseUrl.trimEnd('/') + "/" + value.trimStart('/')
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        val json = JSONObject().apply {
            put("old_password", oldPassword)
            put("new_password", newPassword)
        }
        request("POST", "/api/v1/auth/password/change", json.toString())
    }

    fun deleteAccount(password: String) {
        val json = JSONObject().apply { put("password", password) }
        request("DELETE", "/api/v1/auth/account", json.toString())
    }

    fun createAlipayOrder(planCode: String): OrderResponse {
        val json = JSONObject().apply {
            put("plan_code", planCode)
        }
        val response = request(
            method = "POST",
            path = "/api/v1/payments/orders",
            requestBody = json.toString()
        )
        return OrderResponse(
            order_no = response.getString("order_no"),
            status = response.getString("status"),
            amount = response.get("amount").toString(),
            plan_code = response.getString("plan_code"),
            payment_channel = response.getString("payment_channel"),
            order_string = response.getString("order_string")
        )
    }

    fun getOrder(orderNo: String): OrderDetail {
        val response = request(
            method = "GET",
            path = "/api/v1/payments/orders/$orderNo"
        )
        return OrderDetail(
            order_no = response.getString("order_no"),
            status = response.getString("status"),
            amount = response.get("amount").toString(),
            plan_code = response.getString("plan_code"),
            payment_channel = response.getString("payment_channel"),
            created_at = response.getString("created_at"),
            paid_at = response.optString("paid_at").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun getMembershipStatus(): MembershipStatus {
        val response = request(
            method = "GET",
            path = "/api/v1/membership/current"
        )
        return MembershipStatus(
            is_vip = response.getBoolean("is_vip"),
            plan_code = response.optString("plan_code").takeIf { it.isNotBlank() && it != "null" },
            status = response.getString("status"),
            source = response.optString("source").takeIf { it.isNotBlank() && it != "null" },
            expire_at = response.optString("expire_at").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun getMembershipPlans(): MembershipPlanListResponse {
        val response = request(
            method = "GET",
            path = "/api/v1/membership/plans",
            requireAuth = false
        )
        val plans = response.getJSONArray("plans")
        return MembershipPlanListResponse(
            plans = List(plans.length()) { index ->
                val item = plans.getJSONObject(index)
                MembershipPlan(
                    plan_code = item.getString("plan_code"),
                    title = item.getString("title"),
                    price = item.get("price").toString(),
                    original_price = item.opt("original_price")?.toString()?.takeIf { it != "null" && it.isNotBlank() },
                    bottom_label = item.getString("bottom_label"),
                    tag_text = item.optString("tag_text").takeIf { it.isNotBlank() && it != "null" },
                    is_default = item.optBoolean("is_default", false)
                )
            },
            payment_channels = response.optJSONArray("payment_channels")?.let { channels ->
                List(channels.length()) { index -> channels.getString(index) }
            } ?: listOf("alipay")
        )
    }

    private fun request(
        method: String,
        path: String,
        requestBody: String? = null,
        requireAuth: Boolean = true
    ): JSONObject {
        val accessToken = accessTokenProvider()?.takeIf { it.isNotBlank() }
        if (requireAuth && accessToken == null) {
            throw IllegalStateException("当前尚未登录，请先使用手机号和密码登录。")
        }
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            accessToken?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
            if (requestBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        requestBody?.let {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(it)
            }
        }

        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        if (statusCode !in 200..299) {
            if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && requireAuth) {
                onUnauthorized()
            }
            val detail = runCatching {
                JSONObject(body).optString("detail").takeIf { it.isNotBlank() }
            }.getOrNull()
            throw BackendApiException(
                statusCode = statusCode,
                message = detail ?: "请求失败，请稍后重试"
            )
        }
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    private fun parseAuthSession(response: JSONObject): AuthSession {
        return AuthSession(
            accessToken = response.getString("access_token"),
            user = parseUser(response.getJSONObject("user"))
        )
    }

    private fun parseUser(response: JSONObject): AuthUserProfile {
        return AuthUserProfile(
            id = response.getInt("id"),
            username = response.optString("username").takeIf { it.isNotBlank() && it != "null" },
            nickname = response.optString("nickname").takeIf { it.isNotBlank() && it != "null" },
            avatarUrl = response.optString("avatar_url").takeIf { it.isNotBlank() && it != "null" },
            phone = response.optString("phone").takeIf { it.isNotBlank() && it != "null" },
            email = response.optString("email").takeIf { it.isNotBlank() && it != "null" },
            isActive = response.optBoolean("is_active", true),
            isAdmin = response.optBoolean("is_admin", false),
            createdAt = response.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun submitFeedback(content: String, contact: String?): JSONObject {
        val json = JSONObject().apply {
            put("content", content)
            put("contact", contact)
        }
        return request(
            method = "POST",
            path = "/api/v1/feedback",
            requestBody = json.toString(),
            requireAuth = false
        )
    }

    fun checkLatestVersion(currentVersionCode: Int): AppVersionCheckResponse? {
        val response = request(
            method = "GET",
            path = "/api/v1/app/version/latest?version_code=$currentVersionCode",
            requireAuth = false
        )
        val latest = response.optJSONObject("latest_version") ?: return null
        return AppVersionCheckResponse(
            versionCode = latest.getInt("version_code"),
            versionName = latest.getString("version_name"),
            downloadUrl = latest.getString("download_url"),
            updateLog = latest.getString("update_log"),
            isForceUpdate = latest.getBoolean("is_force_update")
        )
    }
}

data class AppVersionCheckResponse(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val updateLog: String,
    val isForceUpdate: Boolean
)

class BackendApiException(
    val statusCode: Int,
    message: String
) : IllegalStateException(message)
