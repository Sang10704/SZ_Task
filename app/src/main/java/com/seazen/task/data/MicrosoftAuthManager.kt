package com.seazen.task.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MicrosoftAuthManager {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    companion object {
        const val TENANT_ID = "29101901-3778-4339-827c-7d2b50914db0"
        const val CLIENT_ID = "0390280b-12b8-49c4-adbf-a9c2b8bccfb5"
        const val SP_SCOPE = "https://seazenvn.sharepoint.com/.default offline_access"
        val TOKEN_URL = "https://login.microsoftonline.com/$TENANT_ID/oauth2/v2.0/token"
        val DEVICE_CODE_URL = "https://login.microsoftonline.com/$TENANT_ID/oauth2/v2.0/devicecode"
    }

    data class AuthResult(
        val success: Boolean,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val isMfaRequired: Boolean = false,
        val errorMessage: String? = null
    )

    data class DeviceCodeResult(
        val userCode: String,
        val deviceCode: String,
        val verificationUri: String,
        val interval: Long
    )

    suspend fun loginWithUsernamePassword(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "password")
            .add("username", username.trim())
            .add("password", password.trim())
            .add("scope", SP_SCOPE)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(form)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"
                val json = gson.fromJson(bodyStr, JsonObject::class.java)

                if (response.isSuccessful && json.has("access_token")) {
                    val accessToken = json.get("access_token").asString
                    val refreshToken = json.get("refresh_token")?.asString
                    AuthResult(success = true, accessToken = accessToken, refreshToken = refreshToken)
                } else {
                    val errorDesc = json.get("error_description")?.asString ?: json.get("error")?.asString ?: "Đăng nhập thất bại"
                    val isMfa = errorDesc.contains("AADSTS50076") || errorDesc.contains("AADSTS50079") ||
                                errorDesc.contains("AADSTS50074") || errorDesc.contains("MFA") || errorDesc.contains("multi-factor")
                    val userFriendlyMsg = when {
                        isMfa -> "Tài khoản của bạn bật xác thực 2 lớp (MFA). Vui lòng sử dụng Đăng nhập bằng mã xác thực (Device Code)."
                        errorDesc.contains("AADSTS50126") -> "Tài khoản hoặc mật khẩu không chính xác."
                        errorDesc.contains("AADSTS50034") -> "Tài khoản không tồn tại trong hệ thống SeaZen."
                        else -> errorDesc
                    }
                    AuthResult(success = false, isMfaRequired = isMfa, errorMessage = userFriendlyMsg)
                }
            }
        } catch (e: Exception) {
            AuthResult(success = false, errorMessage = "Lỗi kết nối mạng: ${e.message}")
        }
    }

    suspend fun requestDeviceCode(): DeviceCodeResult? = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", SP_SCOPE)
            .build()

        val request = Request.Builder()
            .url(DEVICE_CODE_URL)
            .post(form)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    val json = gson.fromJson(body, JsonObject::class.java)
                    DeviceCodeResult(
                        userCode = json.get("user_code").asString,
                        deviceCode = json.get("device_code").asString,
                        verificationUri = json.get("verification_uri")?.asString ?: "https://microsoft.com/devicelogin",
                        interval = json.get("interval")?.asLong ?: 5L
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pollDeviceCodeToken(deviceCode: String): AuthResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("device_code", deviceCode)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(form)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"
                val json = gson.fromJson(bodyStr, JsonObject::class.java)
                if (response.isSuccessful && json.has("access_token")) {
                    val accessToken = json.get("access_token").asString
                    val refreshToken = json.get("refresh_token")?.asString
                    AuthResult(success = true, accessToken = accessToken, refreshToken = refreshToken)
                } else {
                    val err = json.get("error")?.asString ?: "pending"
                    AuthResult(success = false, errorMessage = err)
                }
            }
        } catch (e: Exception) {
            AuthResult(success = false, errorMessage = e.message)
        }
    }

    suspend fun refreshAccessToken(refreshToken: String): AuthResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken.trim())
            .add("scope", SP_SCOPE)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(form)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"
                val json = gson.fromJson(bodyStr, JsonObject::class.java)
                if (response.isSuccessful && json.has("access_token")) {
                    val accessToken = json.get("access_token").asString
                    val newRefreshToken = json.get("refresh_token")?.asString ?: refreshToken
                    AuthResult(success = true, accessToken = accessToken, refreshToken = newRefreshToken)
                } else {
                    val errorDesc = json.get("error_description")?.asString ?: json.get("error")?.asString ?: "Làm mới token thất bại"
                    AuthResult(success = false, errorMessage = errorDesc)
                }
            }
        } catch (e: Exception) {
            AuthResult(success = false, errorMessage = "Lỗi kết nối khi làm mới token: ${e.message}")
        }
    }
}
