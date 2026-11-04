package com.seazen.task.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.seazen.task.data.SpAttachmentItem
import com.seazen.task.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun resolveDisplayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(nameIndex)
                if (!name.isNullOrBlank()) return name
            }
        }
    } catch (_: Exception) {}
    val seg = uri.lastPathSegment ?: "attachment_${System.currentTimeMillis()}"
    return if (seg.contains(".")) seg else "$seg.jpg"
}

private fun openLocalFileWithProvider(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Không tìm thấy ứng dụng mở tệp này", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun TaskAttachmentSection(
    repository: TaskRepository,
    isMainProject: Boolean,
    itemId: Long,
    canEdit: Boolean
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val isVi = lang != "en"
    val coroutineScope = rememberCoroutineScope()

    var attachments by remember(itemId, isMainProject) { mutableStateOf<List<SpAttachmentItem>>(emptyList()) }
    var isLoading by remember(itemId, isMainProject) { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var deletingAttachment by remember { mutableStateOf<SpAttachmentItem?>(null) }
    var previewImageBitmap by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }

    LaunchedEffect(itemId, isMainProject) {
        isLoading = true
        attachments = repository.fetchItemAttachments(isMainProject, itemId)
        isLoading = false
    }

    // 1. Launcher chon anh / tai lieu tu may
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploading = true
            val readRes = withContext(Dispatchers.IO) {
                try {
                    val fileName = resolveDisplayName(context, uri)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) Pair(fileName, bytes) else null
                } catch (_: Exception) {
                    null
                }
            }
            if (readRes != null) {
                val (fileName, bytes) = readRes
                val upRes = repository.uploadItemAttachment(isMainProject, itemId, fileName, bytes)
                if (upRes.isSuccess) {
                    attachments = repository.fetchItemAttachments(isMainProject, itemId)
                    Toast.makeText(
                        context,
                        if (isVi) "Đã tải tệp đính kèm lên SharePoint" else "Attachment uploaded",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        upRes.exceptionOrNull()?.message ?: "Lỗi tải tệp lên",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    context,
                    if (isVi) "Không thể đọc tệp đã chọn" else "Cannot read selected file",
                    Toast.LENGTH_SHORT
                ).show()
            }
            isUploading = false
        }
    }

    // 2. Launcher chup anh truc tiep tu Camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploading = true
            val photoData = withContext(Dispatchers.IO) {
                try {
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    Pair("nghiem_thu_$stamp.jpg", out.toByteArray())
                } catch (_: Exception) {
                    null
                }
            }
            if (photoData != null) {
                val (fileName, bytes) = photoData
                val upRes = repository.uploadItemAttachment(isMainProject, itemId, fileName, bytes)
                if (upRes.isSuccess) {
                    attachments = repository.fetchItemAttachments(isMainProject, itemId)
                    Toast.makeText(
                        context,
                        if (isVi) "Đã đính kèm ảnh chụp nghiệm thu" else "Photo attached",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        upRes.exceptionOrNull()?.message ?: "Lỗi tải ảnh lên",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            isUploading = false
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8F9FD),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isVi) "Hình ảnh & Tệp đính kèm (${attachments.size})"
                        else "Attachments (${attachments.size})",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                if (isLoading || isUploading) {
                    CircularProgressIndicator(
                        color = PrimaryPurple,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Nut thao tac Chup anh & Dinh kem tep (khi co quyen)
            if (canEdit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (!isUploading) cameraLauncher.launch(null) },
                        enabled = !isUploading,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryPurple)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isVi) "Chụp ảnh" else "Take Photo",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = { if (!isUploading) filePickerLauncher.launch("*/*") },
                        enabled = !isUploading,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryPurple)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isVi) "Chọn ảnh / Tệp" else "Pick File",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isUploading) {
                Text(
                    text = if (isVi) "Đang tải tệp lên SharePoint..." else "Uploading to SharePoint...",
                    fontSize = 11.sp,
                    color = PrimaryPurple,
                    fontWeight = FontWeight.Medium
                )
            }

            if (!isLoading && attachments.isEmpty()) {
                Text(
                    text = if (isVi) "Chưa có hình ảnh hoặc tài liệu đính kèm." else "No attachments yet.",
                    fontSize = 11.5.sp,
                    color = TextMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachments.forEach { att ->
                        AttachmentItemRow(
                            repository = repository,
                            attachment = att,
                            canEdit = canEdit,
                            onPreviewImage = { title, bmp ->
                                previewImageBitmap = Pair(title, bmp)
                            },
                            onDeleteClick = {
                                deletingAttachment = att
                            }
                        )
                    }
                }
            }
        }
    }

    // Hop thoai xem anh phong to
    previewImageBitmap?.let { (fileName, bmp) ->
        Dialog(onDismissRequest = { previewImageBitmap = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fileName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { previewImageBitmap = null }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                        }
                    }

                    Image(
                        bitmap = bmp,
                        contentDescription = fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 420.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                    )
                }
            }
        }
    }

    // Hop thoai xac nhan xoa tep dinh kem
    deletingAttachment?.let { targetAtt ->
        AlertDialog(
            onDismissRequest = { deletingAttachment = null },
            title = {
                Text(
                    text = if (isVi) "Xóa tệp đính kèm" else "Delete Attachment",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            },
            text = {
                Text(
                    text = if (isVi) "Bạn có chắc chắn muốn xóa tệp '${targetAtt.fileName}' khỏi SharePoint?"
                    else "Are you sure you want to delete '${targetAtt.fileName}'?",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDel = targetAtt
                        deletingAttachment = null
                        coroutineScope.launch {
                            val res = repository.deleteItemAttachment(isMainProject, itemId, toDel.fileName)
                            if (res.isSuccess) {
                                attachments = attachments.filter { it.fileName != toDel.fileName }
                                Toast.makeText(
                                    context,
                                    if (isVi) "Đã xóa tệp đính kèm" else "Attachment deleted",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    res.exceptionOrNull()?.message ?: "Không thể xóa tệp",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isVi) "Xóa" else "Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAttachment = null }) {
                    Text(if (isVi) "Hủy" else "Cancel", color = TextMuted)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun AttachmentItemRow(
    repository: TaskRepository,
    attachment: SpAttachmentItem,
    canEdit: Boolean,
    onPreviewImage: (String, ImageBitmap) -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var thumbnailBitmap by remember(attachment.serverRelativeUrl) { mutableStateOf<ImageBitmap?>(null) }
    var isOpening by remember { mutableStateOf(false) }

    LaunchedEffect(attachment.serverRelativeUrl) {
        if (attachment.isImage) {
            val fileRes = repository.downloadAttachmentFile(attachment)
            val file = fileRes.getOrNull()
            if (file != null && file.exists()) {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
                thumbnailBitmap = bmp
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (thumbnailBitmap != null) {
                    onPreviewImage(attachment.fileName, thumbnailBitmap!!)
                } else if (!isOpening) {
                    isOpening = true
                    coroutineScope.launch {
                        val res = repository.downloadAttachmentFile(attachment)
                        isOpening = false
                        val f = res.getOrNull()
                        if (f != null && f.exists()) {
                            if (attachment.isImage) {
                                val bmp = withContext(Dispatchers.IO) {
                                    try {
                                        BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                                    } catch (_: Exception) { null }
                                }
                                if (bmp != null) {
                                    thumbnailBitmap = bmp
                                    onPreviewImage(attachment.fileName, bmp)
                                    return@launch
                                }
                            }
                            openLocalFileWithProvider(context, f)
                        } else {
                            Toast.makeText(
                                context,
                                res.exceptionOrNull()?.message ?: "Không thể tải tệp",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap!!,
                    contentDescription = attachment.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentPurpleLight,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (attachment.isImage) Icons.Default.Image else Icons.Default.Description,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (attachment.isImage) "Hình ảnh đính kèm • Chạm để xem" else "Tài liệu đính kèm • Chạm để mở",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            if (isOpening) {
                CircularProgressIndicator(
                    color = PrimaryPurple,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (canEdit) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionPromptDialog(
    lang: String,
    onEnableClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val isVi = lang != "en"
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = PrimaryPurple.copy(alpha = 0.12f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = if (isVi) "Bật thông báo nhắc việc tự động" else "Enable Automatic Task Reminders",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isVi)
                        "Cho phép SZ Task gửi thông báo để tự động nhắc lịch công việc cần hoàn thành trong ngày (08:00 & 16:00) và cảnh báo sớm các công việc sắp đến hạn hoặc trễ hạn."
                    else
                        "Allow SZ Task to send daily task reminders (08:00 & 16:00) and alert you when personal tasks are due or overdue.",
                    fontSize = 12.5.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        Text(
                            text = if (isVi) "Để sau" else "Later",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }

                    Button(
                        onClick = onEnableClick,
                        modifier = Modifier.weight(1.3f).height(46.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Text(
                            text = if (isVi) "Bật thông báo" else "Enable Now",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
