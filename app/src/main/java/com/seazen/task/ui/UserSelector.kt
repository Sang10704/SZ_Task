package com.seazen.task.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.seazen.task.data.SeaZenUser


/**
 * Thành phần chọn 1 nhân sự từ DanhSachPhanQuyen
 * Cho phép tìm kiếm theo Họ tên hoặc Email, hiển thị Chức vụ và Phòng ban
 */
@Composable
fun SingleUserSelector(
    label: String,
    selectedUser: SeaZenUser?,
    users: List<SeaZenUser>,
    onSelect: (SeaZenUser) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 12.sp, color = TextMuted)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = if (selectedUser != null) Color.White else PillBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedUser != null) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryPurple,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = selectedUser.initials,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedUser.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = selectedUser.email,
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (selectedUser.phongBan.isNotBlank()) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = AccentPurpleLight) {
                                        Text(
                                            text = selectedUser.phongBan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryPurple,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (selectedUser.chucVu.isNotBlank()) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = PillBg) {
                                        Text(
                                            text = selectedUser.chucVu,
                                            fontSize = 9.sp,
                                            color = TextDark,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TextButton(onClick = { showDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(text = "Đổi", fontSize = 12.sp, color = PrimaryPurple, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(text = "Chạm để chọn người từ Danh bạ...", fontSize = 12.sp, color = TextMuted)
                    }
                }
            }
        }
    }

    if (showDialog) {
        UserPickerDialog(
            title = "Chọn $label",
            users = users,
            onDismiss = { showDialog = false },
            onSelect = {
                onSelect(it)
                showDialog = false
            }
        )
    }
}

/**
 * Thành phần chọn nhiều người nhận thông báo (CC)
 */
@Composable
fun MultiUserSelector(
    label: String,
    selectedUsers: List<SeaZenUser>,
    users: List<SeaZenUser>,
    onAddUser: (SeaZenUser) -> Unit,
    onRemoveUser: (SeaZenUser) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 12.sp, color = TextMuted)
            TextButton(
                onClick = { showDialog = true },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "Thêm người", fontSize = 11.sp, color = PrimaryPurple, fontWeight = FontWeight.Bold)
            }
        }

        if (selectedUsers.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDialog = true },
                shape = RoundedCornerShape(10.dp),
                color = PillBg,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
            ) {
                Text(
                    text = "Chưa có người nhận thông báo (CC). Chạm để thêm.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selectedUsers.forEach { u ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryPurple,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = u.initials, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = u.name.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                                    Text(text = "${u.email} • ${u.phongBan}", fontSize = 10.sp, color = TextMuted)
                                }
                            }

                            IconButton(
                                onClick = { onRemoveUser(u) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Xóa", tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        UserPickerDialog(
            title = "Chọn $label",
            users = users.filter { it.email !in selectedUsers.map { s -> s.email } },
            onDismiss = { showDialog = false },
            onSelect = {
                onAddUser(it)
                showDialog = false
            }
        )
    }
}

/**
 * Hộp thoại tìm kiếm và chọn nhân sự từ DanhSachPhanQuyen
 */
@Composable
fun UserPickerDialog(
    title: String,
    users: List<SeaZenUser>,
    onDismiss: () -> Unit,
    onSelect: (SeaZenUser) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedDeptFilter by remember { mutableStateOf("Tất cả") }

    val departments = remember(users) {
        listOf("Tất cả") + users.map { it.phongBan }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredUsers = remember(users, searchQuery, selectedDeptFilter) {
        users.filter { u ->
            val matchDept = (selectedDeptFilter == "Tất cả" || u.phongBan.equals(selectedDeptFilter, ignoreCase = true))
            val matchQuery = searchQuery.isBlank() ||
                    u.name.contains(searchQuery, ignoreCase = true) ||
                    u.email.contains(searchQuery, ignoreCase = true) ||
                    u.phongBan.contains(searchQuery, ignoreCase = true) ||
                    u.chucVu.contains(searchQuery, ignoreCase = true)
            matchDept && matchQuery
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Text(text = "${filteredUsers.size} nhân sự tìm thấy", fontSize = 11.sp, color = TextMuted)
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextDark)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tìm theo tên, email, chức vụ...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Xóa", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = CardBorderColor
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(departments) { dept ->
                        val isSelected = (dept == selectedDeptFilter)
                        Surface(
                            onClick = { selectedDeptFilter = dept },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) PrimaryPurple else PillBg,
                            border = if (isSelected) null else CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
                        ) {
                            Text(
                                text = dept,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else TextDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = PillBg, thickness = 1.dp)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filteredUsers.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (users.isEmpty()) "Chưa có dữ liệu nhân sự từ SharePoint.\nVui lòng kết nối mạng để tải lại." else "Không tìm thấy nhân sự phù hợp",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(filteredUsers, key = { it.email.ifBlank { it.id.toString() } }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelect(user) }
                                    .background(PillBg)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryPurple,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = user.initials, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.name.uppercase(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = user.email,
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (user.phongBan.isNotBlank()) {
                                            Surface(shape = RoundedCornerShape(4.dp), color = AccentPurpleLight) {
                                                Text(
                                                    text = user.phongBan,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PrimaryPurple,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (user.chucVu.isNotBlank()) {
                                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE2E8F0)) {
                                                Text(
                                                    text = user.chucVu,
                                                    fontSize = 9.sp,
                                                    color = TextDark,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
