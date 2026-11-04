package com.seazen.task.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class AppUpdateDownloader(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun hasInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    private fun getVersionCodeCompat(pInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Them tham so chong cache CDN khi file SZ_Task.apk duoc ghi de cung ten tren GitHub Release
            val separator = if (downloadUrl.contains("?")) "&" else "?"
            val uncachedUrl = "${downloadUrl.trim()}${separator}t=${System.currentTimeMillis()}"

            val request = Request.Builder()
                .url(uncachedUrl)
                .header("User-Agent", "SZ_Task_Updater")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.failure(Exception("Lỗi máy chủ tải về (HTTP ${response.code})"))
            }

            val body = response.body ?: run {
                response.close()
                return@withContext Result.failure(Exception("Không nhận được dữ liệu gói cập nhật"))
            }
            val contentLength = body.contentLength()

            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()
            val tempFile = File(updateDir, "SZ_Task_downloading.apk")
            val apkFile = File(updateDir, "SZ_Task.apk")
            if (tempFile.exists()) tempFile.delete()

            var totalRead = 0L
            body.use { b ->
                b.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var lastReportedPercent = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val percent = ((totalRead * 100) / contentLength).toInt().coerceIn(0, 99)
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    withContext(Dispatchers.Main) {
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (contentLength > 0 && totalRead != contentLength) {
                tempFile.delete()
                return@withContext Result.failure(Exception("Tải file bị gián đoạn ($totalRead / $contentLength bytes). Vui lòng thử lại."))
            }

            if (tempFile.length() < 1_000_000L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("Gói tải về không hợp lệ (dung lượng quá nhỏ)."))
            }

            if (apkFile.exists()) apkFile.delete()
            if (!tempFile.renameTo(apkFile)) {
                tempFile.copyTo(apkFile, overwrite = true)
                tempFile.delete()
            }

            // Kiem tra tinh toan ven va versionCode cua file APK truoc khi cai de tranh loi ghi de
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }

            if (archiveInfo == null) {
                apkFile.delete()
                return@withContext Result.failure(Exception("File APK tải về bị hỏng. Vui lòng thử lại."))
            }

            if (archiveInfo.packageName != context.packageName) {
                apkFile.delete()
                return@withContext Result.failure(Exception("Gói APK không khớp định danh ứng dụng (${archiveInfo.packageName})."))
            }

            val currentPkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installedCode = getVersionCodeCompat(currentPkgInfo)
            val archiveCode = getVersionCodeCompat(archiveInfo)

            if (archiveCode < installedCode) {
                return@withContext Result.failure(
                    Exception("Gói APK trên máy chủ (build $archiveCode) cũ hơn bản đang cài trên máy (build $installedCode). Vui lòng cập nhật file APK mới lên GitHub Release.")
                )
            }

            withContext(Dispatchers.Main) {
                onProgress(100)
            }
            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installApk(apkFile: File) {
        if (!apkFile.exists()) return

        // 1. Neu chua co quyen tren Android 8+, mo thang trang cai dat cua SZ Task
        if (!hasInstallPermission()) {
            withContext(Dispatchers.Main) {
                openInstallPermissionSettings()
            }
            return
        }

        // Luu dau moc thoi gian bat dau cap nhat de InstallReceiver tu dong mo lai app sau khi ghi de
        val prefs = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(PREF_PENDING_OTA_TS, System.currentTimeMillis()).apply()

        // 2. Tren Android 12+, dung PackageInstaller Session de tu dong ghi de (hoac hien bang xac nhan 1 cham)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sessionCommitted = withContext(Dispatchers.IO) {
                try {
                    val packageInstaller = context.packageManager.packageInstaller
                    // Don dep cac session cu bi treo (neu co)
                    try {
                        packageInstaller.mySessions.forEach { info ->
                            try {
                                packageInstaller.abandonSession(info.sessionId)
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}

                    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                        setAppPackageName(context.packageName)
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            setRequestUpdateOwnership(true)
                        }
                    }

                    val sessionId = packageInstaller.createSession(params)
                    val session = packageInstaller.openSession(sessionId)

                    apkFile.inputStream().use { input ->
                        session.openWrite("package", 0, apkFile.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }

                    val intent = Intent(context, InstallReceiver::class.java).apply {
                        action = InstallReceiver.ACTION_INSTALL_STATUS
                        setPackage(context.packageName)
                        putExtra(InstallReceiver.EXTRA_APK_PATH, apkFile.absolutePath)
                    }

                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

                    val commitStart = System.currentTimeMillis()
                    session.commit(pendingIntent.intentSender)
                    session.close()

                    // Doi phan hoi tu PackageInstaller toi da 3 giay; neu ROM chan broadcast thi fallback sang ACTION_VIEW
                    var waited = 0L
                    while (waited < 3000L && InstallReceiver.lastCallbackTimestamp < commitStart) {
                        delay(250L)
                        waited += 250L
                    }
                    InstallReceiver.lastCallbackTimestamp >= commitStart
                } catch (e: Exception) {
                    Log.e("SZTaskUpdate", "PackageInstaller session error", e)
                    false
                }
            }

            if (sessionCommitted) {
                return
            }
        }

        // 3. Fallback Intent ACTION_VIEW truyen thong (dam bao 100% moi thiet bi deu mo duoc trinh cai dat)
        withContext(Dispatchers.Main) {
            installWithActionView(context, apkFile)
        }
    }

    companion object {
        const val PREF_PENDING_OTA_TS = "pending_ota_install_ts"

        fun installWithActionView(context: Context, apkFile: File) {
            if (!apkFile.exists()) return
            try {
                val contentUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(installIntent)
            } catch (e: Exception) {
                Log.e("SZTaskUpdate", "Fallback ACTION_VIEW installer error", e)
                Toast.makeText(
                    context,
                    "Không thể mở trình cài đặt: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
