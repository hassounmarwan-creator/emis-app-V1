package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.theme.EmisError
import com.example.ui.theme.EmisSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmisTopAppBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    onSyncClick: (() -> Unit)? = null,
    isSyncing: Boolean = false
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color.White
                    )
                }
            }
        },
        actions = {
            if (onSyncClick != null) {
                IconButton(
                    onClick = onSyncClick,
                    enabled = !isSyncing,
                    modifier = Modifier.testTag("top_bar_sync_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "مزامنة البيانات",
                            tint = Color.White
                        )
                    }
                }
            }
            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag("top_bar_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "الإعدادات",
                        tint = Color.White
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = EmisBluePrimary,
            titleContentColor = Color.White
        )
    )
}

@Composable
fun DeadlineStatusBanner(
    examName: String,
    deadlineDate: String,
    isLocked: Boolean,
    isAdmin: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isAdmin) Color(0xFFEFF6FF) else if (isLocked) Color(0xFFFEE2E2) else Color(0xFFE0F2FE)
    val contentColor = if (isAdmin) EmisBluePrimary else if (isLocked) EmisError else EmisBlueDark
    val borderColor = if (isAdmin) EmisBluePrimary.copy(alpha = 0.4f) else if (isLocked) EmisError.copy(alpha = 0.4f) else Color(0xFFBAE6FD)
    val icon = if (isAdmin) Icons.Default.LockOpen else if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isAdmin) EmisBluePrimary.copy(alpha = 0.15f) else if (isLocked) EmisError.copy(alpha = 0.15f) else EmisBluePrimary.copy(alpha = 0.15f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (isAdmin) "صلاحيات الإدارة مفتوحة دائماً" else if (isLocked) "الدرجات مقفلة" else "متاح للتعديل",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "الموعد النهائي لـ $examName: $deadlineDate",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                if (isAdmin) {
                    Text(
                        text = "وضع الإدارة: تم إلغاء كافة الأقفال والمواعيد (التعديل متاح دائماً)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = EmisBluePrimary
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(
    isPassed: Boolean,
    modifier: Modifier = Modifier
) {
    val text = if (isPassed) "ناجح" else "راسب"
    val bgColor = if (isPassed) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
    val textColor = if (isPassed) EmisSuccess else EmisError

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
