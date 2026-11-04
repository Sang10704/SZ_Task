package com.seazen.task.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.seazen.task.data.AppUpdateDownloader
import com.seazen.task.data.AppUpdateInfo
import kotlinx.coroutines.launch

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    currentVersion: String,
    resumeTrigger: Long = 0L,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var waitingForInstallPermission by remember { mutableStateOf(false) }
    val downloader = remember { AppUpdateDownloader(context) }

    fun startDownloadAndInstall() {
        if (isDownloading || isInstalling) return
        if (!downloader.hasInstallPermission()) {
            waitingForInstallPermission = true
            Toast.makeText(
                context,
                "Vui lòng cấp quyền cài đặt cho SZ Task (1 lần duy nhất)",
                Toast.LENGTH_LONG
            ).show()
            downloader.openInstallPermissionSettings()
            return
        }
        waitingForInstallPermission = false
        isDownloading = true
        isInstalling = false
        downloadProgress = 0
        downloadError = null
        coroutineScope.launch {
            val result = downloader.downloadApk(updateInfo.downloadUrl) { progress ->
                downloadProgress = progress
            }
            if (result.isSuccess) {
                val file = result.getOrNull()
                if (file != null && file.exists()) {
                    isInstalling = true
                    downloader.installApk(file)
                    isInstalling = false
                    isDownloading = false
                    onDismiss()
                } else {
                    isDownloading = false
                    downloadError = "Không tìm thấy gói APK sau khi tải"
                }
            } else {
                isDownloading = false
                downloadError = result.exceptionOrNull()?.message ?: "Lỗi tải bản cập nhật"
            }
        }
    }

    LaunchedEffect(resumeTrigger) {
        if (waitingForInstallPermission && downloader.hasInstallPermission()) {
            startDownloadAndInstall()
        }
    }

    Dialog(onDismissRequest = {
        if (!isDownloading && !isInstalling) onDismiss()
    }) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon
                Surface(
                    shape = CircleShape,
                    color = PrimaryPurple.copy(alpha = 0.12f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Đã có bản cập nhật mới",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Phiên bản v${updateInfo.latestVersion} (Hiện tại: v$currentVersion)",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }

                // Release notes
                if (updateInfo.notes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8F9FD),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Nội dung cập nhật:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = updateInfo.notes,
                                fontSize = 12.sp,
                                color = TextMuted,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // Progress or Error
                if (isDownloading || isInstalling) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = PrimaryPurple,
                            trackColor = Color(0xFFE2E8F0)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isInstalling) "Đang cài đặt ghi đè phiên bản mới..."
                                   else "Đang tải bản cập nhật: $downloadProgress%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryPurple
                        )
                    }
                } else if (downloadError != null) {
                    Text(
                        text = "Lỗi: $downloadError",
                        fontSize = 12.sp,
                        color = Color(0xFFDC2626),
                        textAlign = TextAlign.Center
                    )
                }

                // Buttons
                if (!isDownloading && !isInstalling) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(32.dp)
                        ) {
                            Text("Để sau", fontSize = 13.sp, color = TextMuted)
                        }

                        Button(
                            onClick = { startDownloadAndInstall() },
                            modifier = Modifier.weight(1.3f).height(46.dp),
                            shape = RoundedCornerShape(32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Text("Cập nhật ngay", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
