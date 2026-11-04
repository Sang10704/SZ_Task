package com.seazen.task.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.seazen.task.ui.MainActivity
import java.io.File

class InstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_INSTALL_STATUS -> {
                lastCallbackTimestamp = System.currentTimeMillis()
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val statusMsg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                val apkPath = intent.getStringExtra(EXTRA_APK_PATH)

                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        if (confirmIntent != null) {
                            try {
                                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(confirmIntent)
                            } catch (e: Exception) {
                                Log.e("SZTaskUpdate", "Failed to start confirmIntent, using ACTION_VIEW fallback", e)
                                if (!apkPath.isNullOrBlank()) {
                                    AppUpdateDownloader.installWithActionView(context, File(apkPath))
                                }
                            }
                        } else if (!apkPath.isNullOrBlank()) {
                            AppUpdateDownloader.installWithActionView(context, File(apkPath))
                        }
                    }

                    PackageInstaller.STATUS_SUCCESS -> {
                        handleInstallSuccess(context)
                    }

                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        // Nguoi dung chu dong bam Huy
                        Log.i("SZTaskUpdate", "Update installation cancelled by user")
                    }

                    else -> {
                        Log.w("SZTaskUpdate", "PackageInstaller session failed ($status): $statusMsg")
                        // Neu PackageInstaller Session bi chan boi ROM (MIUI/HyperOS/ColorOS) hoac loi, chuyen ngay sang trinh cai dat he thong
                        if (!apkPath.isNullOrBlank()) {
                            val file = File(apkPath)
                            if (file.exists()) {
                                AppUpdateDownloader.installWithActionView(context, file)
                                return
                            }
                        }
                        val friendlyErr = when (status) {
                            PackageInstaller.STATUS_FAILURE_CONFLICT,
                            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                                "Xung đột chữ ký ứng dụng. Vui lòng gỡ bản cũ và cài lại."
                            PackageInstaller.STATUS_FAILURE_STORAGE ->
                                "Bộ nhớ thiết bị không đủ để cài đặt bản cập nhật."
                            PackageInstaller.STATUS_FAILURE_INVALID ->
                                "Gói cập nhật không hợp lệ hoặc thấp hơn phiên bản đang cài."
                            else ->
                                "Không thể tự động ghi đè ($statusMsg)."
                        }
                        Toast.makeText(context, friendlyErr, Toast.LENGTH_LONG).show()
                    }
                }
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                lastCallbackTimestamp = System.currentTimeMillis()
                handleInstallSuccess(context)
            }
        }
    }

    private fun handleInstallSuccess(context: Context) {
        try {
            val prefs = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
            val pendingTs = prefs.getLong(AppUpdateDownloader.PREF_PENDING_OTA_TS, 0L)
            val now = System.currentTimeMillis()

            // Don dep file APK tam sau khi da ghi de thanh cong
            try {
                val apkFile = File(File(context.cacheDir, "updates"), "SZ_Task.apk")
                if (apkFile.exists()) apkFile.delete()
            } catch (_: Exception) {}

            // Chi tu dong mo lai app neu vua thuc hien cap nhat OTA trong vong 10 phut qua
            if (pendingTs > 0L && (now - pendingTs) < 10 * 60 * 1000L) {
                prefs.edit().remove(AppUpdateDownloader.PREF_PENDING_OTA_TS).apply()
                Toast.makeText(
                    context,
                    "Đã cập nhật SZ Task lên phiên bản mới thành công!",
                    Toast.LENGTH_SHORT
                ).show()

                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            Log.e("SZTaskUpdate", "Error handling post-update relaunch", e)
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.seazen.task.ACTION_INSTALL_STATUS"
        const val EXTRA_APK_PATH = "extra_apk_path"

        @Volatile
        var lastCallbackTimestamp: Long = 0L
    }
}
