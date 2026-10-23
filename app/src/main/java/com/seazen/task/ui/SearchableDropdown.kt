package com.seazen.task.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dropdown chuẩn hóa toàn bộ ứng dụng SeaZen theo quy chuẩn Reference (Image 4).
 * Đặc điểm:
 * 1. Khung Trigger Full-width, viền tím PrimaryPurple khi mở, mũi tên chevron xoay mượt mà.
 * 2. Khung danh sách bên dưới ôm trọn full-width, có shadow tinh tế.
 * 3. BẮT BUỘC có ô tìm kiếm (Search input) với icon kính lúp lọc tức thời.
 * 4. Item đang chọn có nền tím nhạt (AccentPurpleLight) và vạch chỉ báo màu tím bên phải.
 */
@Composable
fun SearchableDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Chọn...",
    searchPlaceholder: String = "Tìm kiếm...",
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    var internalExpanded by remember { mutableStateOf(false) }
    val isExpanded = expanded ?: internalExpanded
    val setExpanded: (Boolean) -> Unit = { newExp ->
        if (onExpandedChange != null) {
            onExpandedChange(newExp)
        } else {
            internalExpanded = newExp
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val chevronRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "chevron")

    val filteredOptions = remember(options, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) options
        else options.filter { it.contains(q, ignoreCase = true) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium
            )
        }

        // TRIGGER BOX (Khung bấm chọn)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (isExpanded) 1.5.dp else 1.dp,
                    color = if (isExpanded) PrimaryPurple else Color(0xFFCBD5E1),
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable {
                    setExpanded(!isExpanded)
                },
            shape = RoundedCornerShape(10.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedValue.ifBlank { placeholder },
                    fontSize = 13.5.sp,
                    fontWeight = if (selectedValue.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                    color = if (selectedValue.isNotBlank()) TextDark else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (isExpanded) PrimaryPurple else TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        // DROPDOWN PANEL (Danh sách mở rộng bên dưới)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    // Ô TÌM KIẾM (Bắt buộc theo yêu cầu)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(searchPlaceholder, fontSize = 12.5.sp, color = TextMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = PillBg,
                            unfocusedContainerColor = PillBg,
                            focusedBorderColor = PrimaryPurple,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // DANH SÁCH CÁC MỤC LỰA CHỌN
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(scrollState)
                    ) {
                        if (filteredOptions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Không tìm thấy kết quả",
                                    fontSize = 12.5.sp,
                                    color = TextMuted
                                )
                            }
                        } else {
                            filteredOptions.forEach { opt ->
                                val isSelected = (opt == selectedValue)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) AccentPurpleLight else Color.Transparent)
                                        .clickable {
                                            onSelect(opt)
                                            searchQuery = ""
                                            setExpanded(false)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = opt,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) PrimaryPurple else TextDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Vạch chỉ báo tím bên phải (chuẩn như Image 4)
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.5.dp)
                                                .height(20.dp)
                                                .background(PrimaryPurple, RoundedCornerShape(2.dp))
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

/**
 * Dropdown nâng cao hỗ trợ kiểu Object có Id và Title
 */
data class DropdownItemData<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null
)

@Composable
fun <T> SearchableDropdownAdvanced(
    label: String,
    selectedTitle: String,
    options: List<DropdownItemData<T>>,
    onSelect: (DropdownItemData<T>) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Chọn...",
    searchPlaceholder: String = "Tìm kiếm...",
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    var internalExpanded by remember { mutableStateOf(false) }
    val isExpanded = expanded ?: internalExpanded
    val setExpanded: (Boolean) -> Unit = { newExp ->
        if (onExpandedChange != null) {
            onExpandedChange(newExp)
        } else {
            internalExpanded = newExp
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val chevronRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "chevron_adv")

    val filteredOptions = remember(options, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) options
        else options.filter {
            it.title.contains(q, ignoreCase = true) ||
            (it.subtitle != null && it.subtitle.contains(q, ignoreCase = true))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium
            )
        }

        // TRIGGER BOX
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (isExpanded) 1.5.dp else 1.dp,
                    color = if (isExpanded) PrimaryPurple else Color(0xFFCBD5E1),
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable {
                    setExpanded(!isExpanded)
                },
            shape = RoundedCornerShape(10.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedTitle.ifBlank { placeholder },
                    fontSize = 13.5.sp,
                    fontWeight = if (selectedTitle.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                    color = if (selectedTitle.isNotBlank()) TextDark else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (isExpanded) PrimaryPurple else TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        // DROPDOWN PANEL
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    // Ô TÌM KIẾM
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(searchPlaceholder, fontSize = 12.5.sp, color = TextMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = PillBg,
                            unfocusedContainerColor = PillBg,
                            focusedBorderColor = PrimaryPurple,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(scrollState)
                    ) {
                        if (filteredOptions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Không tìm thấy kết quả",
                                    fontSize = 12.5.sp,
                                    color = TextMuted
                                )
                            }
                        } else {
                            filteredOptions.forEach { item ->
                                val isSelected = (item.title == selectedTitle)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) AccentPurpleLight else Color.Transparent)
                                        .clickable {
                                            onSelect(item)
                                            searchQuery = ""
                                            setExpanded(false)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) PrimaryPurple else TextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!item.subtitle.isNullOrBlank()) {
                                            Text(
                                                text = item.subtitle,
                                                fontSize = 11.sp,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.5.dp)
                                                .height(20.dp)
                                                .background(PrimaryPurple, RoundedCornerShape(2.dp))
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
