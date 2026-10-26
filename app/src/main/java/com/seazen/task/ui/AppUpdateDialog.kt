package com.seazen.task.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    val downloader = remember { AppUpdateDownloader(context) }

    Dialog(onDismissRequest = {
        if (!isDownloading) onDismiss()
    }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF6366F1).copy(alpha = 0.12f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Có bản cập nhật mới",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Phiên bản v${updateInfo.latestVersion} (Hiện tại: v$currentVersion)",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                // Release notes
                if (updateInfo.notes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Nội dung cập nhật:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = updateInfo.notes,
                                fontSize = 12.sp,
                                color = Color(0xFF334155),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // Progress or Error
                if (isDownloading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = Color(0xFF6366F1),
                            trackColor = Color(0xFFE2E8F0)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Đang tải bản cập nhật: $downloadProgress%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF6366F1)
                        )
                    }
                } else if (downloadError != null) {
                    Text(
                        text = "Lỗi tải: $downloadError",
                        fontSize = 12.sp,
                        color = Color(0xFFDC2626)
                    )
                }

                // Buttons
                if (!isDownloading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Để sau", fontSize = 13.sp, color = Color(0xFF64748B))
                        }

                        Button(
                            onClick = {
                                isDownloading = true
                                downloadError = null
                                coroutineScope.launch {
                                    val result = downloader.downloadApk(updateInfo.downloadUrl) { progress ->
                                        downloadProgress = progress
                                    }
                                    isDownloading = false
                                    if (result.isSuccess) {
                                        val file = result.getOrNull()
                                        if (file != null && file.exists()) {
                                            downloader.installApk(file)
                                            onDismiss()
                                        }
                                    } else {
                                        downloadError = result.exceptionOrNull()?.message ?: "Lỗi tải file"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.3f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                        ) {
                            Text("Cập nhật ngay", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
