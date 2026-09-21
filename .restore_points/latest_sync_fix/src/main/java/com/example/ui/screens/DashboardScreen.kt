package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Teacher
import com.example.model.isTeacherNameMatch
import com.example.ui.components.EmisTopAppBar
import com.example.ui.theme.EmisBlueContainer
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.theme.EmisError
import com.example.ui.theme.EmisSecondary
import com.example.viewmodel.AppScreen
import com.example.viewmodel.EmisViewModel

@Composable
fun DashboardScreen(viewModel: EmisViewModel) {
    val teacher by viewModel.currentTeacher.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val monthHours by viewModel.currentTeacherMonthHours.collectAsState()
    val cumulativeHours by viewModel.currentTeacherCumulativeHours.collectAsState()
    val courses by viewModel.courses.collectAsState()

    val teacherCoursesCount = if (teacher?.isAdmin == true) {
        courses.size
    } else {
        courses.count { isTeacherNameMatch(it.teacherName, teacher?.name) }
    }

    Scaffold(
        topBar = {
            EmisTopAppBar(
                title = "نظام إدارة المعلومات التربوية EMIS",
                subtitle = "بوابة المعلمين والإدارة التربوية",
                onSettingsClick = null,
                onSyncClick = { viewModel.syncWithGoogleSheets() },
                isSyncing = isSyncing
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val gridColumns = if (maxWidth > 650.dp) 2 else 1

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Teacher Profile Banner
                TeacherProfileHeader(
                    teacher = teacher,
                    onLogout = { viewModel.logout() }
                )

                // Stats Overview Row (Shown only for teachers; hidden for admin)
                if (teacher?.isAdmin != true) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMetricCard(
                            title = "المواد المكلف بها",
                            value = "$teacherCoursesCount مادة",
                            icon = Icons.Default.School,
                            color = EmisBluePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricCard(
                            title = "ساعات الشهر الحالية",
                            value = "${monthHours.toInt()} ساعة",
                            icon = Icons.Default.DateRange,
                            color = EmisSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricCard(
                            title = "الساعات التراكمية",
                            value = "${cumulativeHours.toInt()} ساعة",
                            icon = Icons.AutoMirrored.Filled.Assignment,
                            color = EmisBlueDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "الخدمات والوظائف المتاحة:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmisBlueDark,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Main Navigation Buttons Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Grades button
                    item {
                        DashboardActionButton(
                            title = "العلامات",
                            description = "إدخال واستعراض درجات الطلاب والسعي والامتحانات",
                            icon = Icons.AutoMirrored.Filled.Assignment,
                            tag = "grades_menu_button",
                            onClick = { viewModel.navigateTo(AppScreen.GRADES) }
                        )
                    }

                    // 2. Contract Hours button
                    item {
                        DashboardActionButton(
                            title = "ساعات التعاقد",
                            description = "تسجيل الساعات الأسبوعية والشهرية وإجمالي التراكمي",
                            icon = Icons.Default.DateRange,
                            tag = "hours_menu_button",
                            onClick = { viewModel.navigateTo(AppScreen.CONTRACT_HOURS) }
                        )
                    }

                    // 3. Exam Upload button
                    item {
                        DashboardActionButton(
                            title = "رفع الامتحانات",
                            description = "رفع نماذج الأسئلة وسلم التصحيح إلى Google Drive",
                            icon = Icons.Default.CloudUpload,
                            tag = "upload_exams_menu_button",
                            onClick = { viewModel.navigateTo(AppScreen.EXAM_UPLOAD) }
                        )
                    }

                    // Admin only: 4. Student Reports, 5. QR Code Portal, 6. Missing Grades
                    if (teacher?.isAdmin == true) {
                        item {
                            DashboardActionButton(
                                title = "بطاقات وتقارير الطلاب",
                                description = "عرض بطاقات العلامات الرسمية وحساب النتائج والمعدلات",
                                icon = Icons.Default.Assessment,
                                tag = "student_reports_menu_button",
                                onClick = { viewModel.navigateTo(AppScreen.STUDENT_REPORTS) }
                            )
                        }

                        item {
                            DashboardActionButton(
                                title = "بوابة نتائج الطلاب (QR Code)",
                                description = "رمز واستعلام مباشر للطلاب لمشاهدة نتائجهم عند إغلاق الامتحانات",
                                icon = Icons.Default.QrCode,
                                tag = "student_portal_qr_menu_button",
                                onClick = { viewModel.navigateTo(AppScreen.STUDENT_PORTAL_QR) }
                            )
                        }

                        item {
                            DashboardActionButton(
                                title = "العلامات الناقصة",
                                description = "متابعة الأساتذة والصفوف التي لم تكتمل درجاتها",
                                icon = Icons.Default.Warning,
                                tag = "missing_grades_menu_button",
                                onClick = { viewModel.navigateTo(AppScreen.MISSING_GRADES) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherProfileHeader(
    teacher: Teacher?,
    onLogout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EmisBlueContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(EmisBluePrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "مرحباً، ${teacher?.name ?: "الاستاذ(ة)"}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = EmisBlueDark
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val roleLabel = if (teacher?.isAdmin == true) "مدير النظام (Admin)" else "استاذ(ة) معتمد(ة)"
                    Text(
                        text = roleLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (teacher?.isAdmin == true) Color(0xFFB45309) else EmisBlueDark,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " • الرمز: ${teacher?.code ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onLogout,
                modifier = Modifier.testTag("logout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "تسجيل الخروج",
                    tint = EmisError
                )
            }
        }
    }
}

@Composable
fun StatMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = EmisBlueDark
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DashboardActionButton(
    title: String,
    description: String,
    icon: ImageVector,
    tag: String,
    badge: String? = null,
    badgeColor: Color = EmisBluePrimary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(EmisBlueContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EmisBluePrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmisBlueDark,
                        fontSize = 18.sp
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                color = badgeColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
