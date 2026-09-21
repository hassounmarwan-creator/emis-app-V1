package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import com.example.ui.util.PdfReportGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Course
import com.example.model.ReportStage
import com.example.model.StudentReportRow
import com.example.model.isTeacherNameMatch
import com.example.model.resolveClassSpecialty
import com.example.model.resolveDegreeAndYear
import com.example.ui.components.EmisTopAppBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.EmisBlueContainer
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.theme.EmisError
import com.example.ui.theme.EmisSuccess
import com.example.viewmodel.AppScreen
import com.example.viewmodel.EmisViewModel

@Composable
fun StudentReportsScreen(viewModel: EmisViewModel) {
    val teacher by viewModel.currentTeacher.collectAsState()
    val reportsClassIndex by viewModel.reportsClassIndex.collectAsState()
    val searchQuery by viewModel.studentSearchQuery.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val classBlocks by viewModel.classBlocks.collectAsState()
    val students by viewModel.students.collectAsState()
    val activeStage by viewModel.reportStage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val schoolName by viewModel.schoolName.collectAsState()
    val schoolYear by viewModel.schoolYear.collectAsState()

    val validClassBlocks = remember(classBlocks, students, courses, teacher) {
        classBlocks.filter { block ->
            val hasName = block.name.isNotBlank()
            val hasStudents = students.any { s -> s.classIndex == block.index }
            val hasCourse = if (teacher?.isAdmin == true) {
                courses.any { it.classIndex == block.index }
            } else {
                courses.any { it.classIndex == block.index && isTeacherNameMatch(it.teacherName, teacher?.name) }
            }
            hasName && hasStudents && hasCourse
        }
    }

    LaunchedEffect(validClassBlocks) {
        if (validClassBlocks.isNotEmpty() && validClassBlocks.none { it.index == reportsClassIndex }) {
            viewModel.setReportsClassIndex(validClassBlocks.first().index)
        }
    }

    val isClassValidForTeacher = validClassBlocks.any { it.index == reportsClassIndex }
    val reportRows = if (isClassValidForTeacher) {
        viewModel.calculateReportsForClass(reportsClassIndex, activeStage)
    } else {
        emptyList()
    }
    val currentClassBlock = classBlocks.find { it.index == reportsClassIndex }
    val classCourses = remember(courses, reportsClassIndex) {
        courses.filter { it.classIndex == reportsClassIndex }.ifEmpty { courses }
    }

    val totalStudents = reportRows.size
    val passedCount = reportRows.count { it.isPassed }
    val failedCount = totalStudents - passedCount
    val passPercentage = if (totalStudents > 0) (passedCount * 100) / totalStudents else 0

    var expandedStudentId by remember { mutableStateOf<String?>(null) }
    var selectedOfficialCardRow by remember { mutableStateOf<StudentReportRow?>(null) }

    Scaffold(
        topBar = {
            EmisTopAppBar(
                title = "بطاقات وتقارير علامات الطلاب",
                subtitle = if (schoolName.isNotBlank()) "حساب المحصلات والنتائج الرسمية ($schoolName)" else "حساب المحصلات والنتائج الرسمية",
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                onSyncClick = { viewModel.refreshFromGoogleSheet() },
                isSyncing = isSyncing
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Class Selector Chips
                Text(
                    text = "اختر الشعبة / الصف للتقرير:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmisBlueDark
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    validClassBlocks.forEach { block ->
                        FilterChip(
                            selected = block.index == reportsClassIndex,
                            onClick = { viewModel.setReportsClassIndex(block.index) },
                            label = { Text(block.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmisBluePrimary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("report_class_chip_${block.index}")
                        )
                    }
                }

                // Stage Selection Chips (Dynamic Calculation Rule)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "نوع بطاقة التقرير / المرحلة:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmisBlueDark
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReportStage.values().forEach { stage ->
                        FilterChip(
                            selected = stage == activeStage,
                            onClick = { viewModel.setReportStage(stage) },
                            label = {
                                Text(
                                    text = stage.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (stage == activeStage) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0F172A),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF1F5F9),
                                labelColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.testTag("report_stage_chip_${stage.name}")
                        )
                    }
                }

                // Summary Stats Row
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmisBlueContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "إجمالي الطلاب",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmisBlueDark,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "$totalStudents",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmisBlueDark
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFD1FAE5),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ناجح (≥ 10/20)",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmisSuccess,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "$passedCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmisSuccess
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "راسب (< 10/20)",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmisError,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "$failedCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmisError
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "نسبة النجاح",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "$passPercentage%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmisBlueDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Search Box and Refresh Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setStudentSearchQuery(it) },
                        placeholder = { Text("بحث عن اسم الطالب...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = EmisBluePrimary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setStudentSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmisBluePrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmisBlueContainer,
                        modifier = Modifier
                            .size(50.dp)
                            .clickable(enabled = !isSyncing) { viewModel.refreshFromGoogleSheet() }
                            .testTag("refresh_student_grades_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSyncing) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = EmisBluePrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "تحديث العلامات من Google Sheets",
                                    tint = EmisBluePrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Student Reports List
            if (reportRows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (validClassBlocks.isEmpty()) {
                            "لا توجد شعب مسندة لك"
                        } else if (!isClassValidForTeacher) {
                            "ليس لديك مواد مسندة في هذه الشعبة"
                        } else {
                            "لا يوجد طلاب مسجلين في هذا الصف"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(reportRows) { idx, row ->
                        val isExpanded = expandedStudentId == row.student.id

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("report_row_${row.student.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (row.isPassed) Color(0xFFCBD5E1) else Color(0xFFFECACA)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Header: Rank, Name, Score, Status
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Circular Rank Badge
                                        Surface(
                                            shape = CircleShape,
                                            color = if (row.rank <= 3) Color(0xFFFEF3C7) else Color(0xFFF1F5F9),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (row.rank <= 3) Color(0xFFD97706) else Color(0xFFCBD5E1)
                                            ),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "#${row.rank}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (row.rank <= 3) Color(0xFFB45309) else Color(0xFF475569)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = row.student.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = EmisBlueDark
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "المجموع: ${String.format("%.2f", row.finalScore)} / ${row.maxPointsTotal.toInt()}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFFDC2626)
                                                )
                                                Text(
                                                    text = "•",
                                                    color = Color.Gray,
                                                    fontSize = 10.sp
                                                )
                                                Text(
                                                    text = "المعدل: ${String.format("%.2f", row.generalAverage20)} / 20",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (row.isPassed) EmisSuccess else EmisError
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusBadge(isPassed = row.isPassed)
                                        IconButton(
                                            onClick = {
                                                expandedStudentId = if (isExpanded) null else row.student.id
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "طي" else "توسيع",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Action Row: View Official Card Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المرتبة ${row.rank} من أصل ${row.classStudentCount} طالباً",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B)
                                    )

                                    Button(
                                        onClick = { selectedOfficialCardRow = row },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EmisBluePrimary
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 12.dp,
                                            vertical = 6.dp
                                        ),
                                        modifier = Modifier.testTag("view_official_card_button_${row.student.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Assessment,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "عرض بطاقة العلامات الرسمية",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // In-Card Expandable Official Table Preview
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "${row.stage.cardTitle} - ملخص المواد:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = EmisBlueDark
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        OfficialReportTableView(
                                            row = row,
                                            courses = classCourses,
                                            compactMode = true
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

    // Official Report Card Fullscreen Dialog
    selectedOfficialCardRow?.let { row ->
        val currentBlockName = currentClassBlock?.name ?: row.student.className
        val liveRow = reportRows.find { it.student.id == row.student.id } ?: row
        OfficialReportCardDialog(
            row = liveRow,
            courses = classCourses,
            className = currentBlockName,
            schoolName = schoolName,
            schoolYear = schoolYear,
            onDismiss = { selectedOfficialCardRow = null },
            onRefresh = { viewModel.refreshFromGoogleSheet() },
            isSyncing = isSyncing,
            selectedStage = activeStage,
            onStageSelected = { newStage -> viewModel.setReportStage(newStage) },
            isStageEnabled = { true }
        )
    }
}

/**
 * Fullscreen / Sheet Dialog showing the complete Lebanese Technical School Report Card
 * exactly matching the design in Image 1 and Image 2.
 */
@Composable
fun OfficialReportCardDialog(
    row: StudentReportRow,
    courses: List<Course>,
    className: String,
    schoolName: String = "",
    schoolYear: String = "2025-2026",
    onDismiss: () -> Unit,
    onRefresh: () -> Unit = {},
    isSyncing: Boolean = false,
    selectedStage: ReportStage = row.stage,
    onStageSelected: ((ReportStage) -> Unit)? = null,
    isStageEnabled: ((ReportStage) -> Boolean)? = null,
    stageDisabledMessage: ((ReportStage) -> String?)? = null,
    showStageSelector: Boolean = true
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_official_card_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onRefresh,
                            enabled = !isSyncing,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("refresh_student_card_grades")
                        ) {
                            if (isSyncing) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = EmisBluePrimary
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "تحديث", modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تحديث العلامات", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                PdfReportGenerator.generateAndSharePdf(
                                    context = context,
                                    row = row,
                                    courses = courses,
                                    className = className,
                                    schoolName = schoolName,
                                    schoolYear = schoolYear
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("share_pdf_report_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة / طباعة", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة / طباعة (PDF)", fontSize = 12.sp)
                        }
                    }
                }

                // Stage Selector Bar (C1, C1+E1, C2, C1+E1+C2+E2)
                if (showStageSelector && onStageSelected != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_stage_selector_container")
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = row.stage.cardTitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmisBluePrimary
                                )
                            }

                            val stageOptions = listOf(
                                Triple(ReportStage.C1_ONLY, "السعي الأول", "c1_chip"),
                                Triple(ReportStage.C1_E1, "الفصل الأول", "c1_e1_chip"),
                                Triple(ReportStage.C2_ONLY, "السعي الثاني", "c2_chip"),
                                Triple(ReportStage.ALL, "التقرير السنوي", "all_chip")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                stageOptions.forEach { (st, label, tag) ->
                                    val isSelected = (selectedStage == st) || (selectedStage == ReportStage.AUTO && row.stage == st)
                                    val isEnabled = isStageEnabled?.invoke(st) ?: true

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag(tag)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (isEnabled) {
                                                    onStageSelected(st)
                                                } else {
                                                    val msg = stageDisabledMessage?.invoke(st)
                                                        ?: "هذا الامتحان غير متاح حتى انقضاء الموعد المحدد"
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        color = when {
                                            isSelected -> EmisBluePrimary
                                            !isEnabled -> Color(0xFFF1F5F9)
                                            else -> Color.White
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = when {
                                                isSelected -> EmisBlueDark
                                                !isEnabled -> Color(0xFFE2E8F0)
                                                else -> Color(0xFFCBD5E1)
                                            }
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 2.dp, vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            if (!isEnabled) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "مقفل",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.height(1.dp))
                                            }
                                            Text(
                                                text = label,
                                                fontSize = 9.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = when {
                                                    isSelected -> Color.White
                                                    !isEnabled -> Color(0xFF94A3B8)
                                                    else -> Color(0xFF1E293B)
                                                },
                                                textAlign = TextAlign.Center,
                                                lineHeight = 12.sp,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // The Official Certificate / Card View (Paper Container)
                OfficialCertificatePaper(
                    row = row,
                    courses = courses,
                    className = className,
                    schoolName = schoolName,
                    schoolYear = schoolYear
                )
            }
        }
    }
}

/**
 * Renders the Lebanese Technical School Official Certificate Container
 */
@Composable
fun OfficialCertificatePaper(
    row: StudentReportRow,
    courses: List<Course>,
    className: String,
    schoolName: String = "",
    schoolYear: String = "2025-2026"
) {
    val borderColor = Color(0xFF1E293B)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, borderColor)
                .background(Color.White)
                .padding(12.dp)
        ) {
            // 1. Header: Lebanese Ministry on Right, Badge Box on Left (Image 3 design)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Right: Ministry & School
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "الجمهورية اللبنانية",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "وزارة التربية و التعليم العالي",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "المديرية العامة للتعليم المهني والتقني",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 11.sp
                    )
                    if (schoolName.isNotBlank()) {
                        Text(
                            text = schoolName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                // Left: Badge Box (Image 3: [ 2027-2028 | بطاقة / علامات / السعي / الاول ])
                Surface(
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Right sub-cell in RTL (inner side): Academic Year
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = schoolYear.ifBlank { "2027-2028" },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color.Black
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .fillMaxHeight()
                                .background(borderColor)
                        )

                        // Left sub-cell in RTL (outer edge): Stacked Stage Card Words
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val stageWords = when (row.stage) {
                                ReportStage.C1_ONLY -> listOf("بطاقة", "علامات", "السعي", "الاول")
                                ReportStage.C1_E1 -> listOf("بطاقة", "علامات", "الفصل", "الاول")
                                ReportStage.C2_ONLY -> listOf("بطاقة", "علامات", "السعي", "الثاني")
                                ReportStage.ALL -> listOf("بطاقة", "علامات", "التقرير", "السنوي")
                                else -> listOf("بطاقة", "علامات", "السعي", "الاول")
                            }
                            stageWords.forEach { word ->
                                Text(
                                    text = word,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    color = Color.Black,
                                    lineHeight = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Student Metadata Box (Table-like bordered container)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor)
            ) {
                // Row 1: الشهادة و السنة و الاختصاص (replaces previous image 1 two-column layout)
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetaCell(
                        label = "الشهادة و السنة و الاختصاص:",
                        value = className,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = borderColor)
                // Row 2: الصف | الشعبة | اسم الطالب
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetaCell(
                        label = "الصف:",
                        value = className.takeWhile { it != ' ' }.ifBlank { className },
                        modifier = Modifier.weight(0.8f)
                    )
                    MetaCell(
                        label = "الشعبة:",
                        value = "F1",
                        modifier = Modifier.weight(0.6f),
                        hasStartBorder = true
                    )
                    MetaCell(
                        label = "اسم الطالب:",
                        value = row.student.name,
                        modifier = Modifier.weight(1.6f),
                        hasStartBorder = true,
                        isHighlight = true
                    )
                }
            }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. The Main Grades Table
        OfficialReportTableView(
            row = row,
            courses = courses,
            compactMode = false
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Signatures Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "رئيس الدروس النظرية",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.Black
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "رئيس الدروس التطبيقية",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.Black
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "اسم المدير:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.Black
                )
                Text(
                    text = "التوقيع والختم الرسمي",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
    }
}

@Composable
private fun MetaCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hasStartBorder: Boolean = false,
    isHighlight: Boolean = false
) {
    val borderColor = Color(0xFF1E293B)
    Row(
        modifier = modifier
            .then(if (hasStartBorder) Modifier.drawBehind {
                // Vertical divider line on the start/right edge (in RTL)
                drawLine(
                    color = borderColor,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            } else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color.Black
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            fontWeight = if (isHighlight) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = 11.sp,
            color = if (isHighlight) Color(0xFF1E3A8A) else Color.Black
        )
    }
}

/**
 * The Table displaying Courses, Max Grades, Exams, and Calculations
 */
@Composable
fun OfficialReportTableView(
    row: StudentReportRow,
    courses: List<Course>,
    compactMode: Boolean = false
) {
    val borderColor = Color(0xFF334155)
    val headerBg = Color(0xFFE2E8F0)

    val stage = row.stage
    val isAll = stage == ReportStage.ALL
    val isC2Only = stage == ReportStage.C2_ONLY
    val isC1Only = stage == ReportStage.C1_ONLY

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor)
    ) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(text = "اسم المادة", weight = 2.5f, isHeader = true, compact = compactMode)
            TableCell(text = "العلامة القصوى", weight = 1.1f, isHeader = true, compact = compactMode, hasStartBorder = true)

            if (isC1Only) {
                TableCell(text = "علامة السعي", weight = 1.2f, isHeader = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = "علامة الفصل الاول", weight = 1.2f, isHeader = true, compact = compactMode, hasStartBorder = true)
            } else if (isC2Only) {
                TableCell(text = "علامة السعي", weight = 1.2f, isHeader = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = "علامة الفصل الثاني", weight = 1.2f, isHeader = true, compact = compactMode, hasStartBorder = true)
            } else if (isAll) {
                TableCell(text = "علامة السعي الاول", weight = 1.1f, isHeader = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = "علامة الفصل الاول", weight = 1.1f, isHeader = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = "علامة السعي الثاني", weight = 1.1f, isHeader = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = "علامة الفصل الثاني", weight = 1.1f, isHeader = true, compact = compactMode, hasStartBorder = true)
            } else {
                TableCell(text = "علامة السعي", weight = 1.2f, isHeader = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = "علامة الفصل الاول", weight = 1.2f, isHeader = true, compact = compactMode, hasStartBorder = true)
            }

            TableCell(text = "المعدل النهائي", weight = 1.2f, isHeader = true, compact = compactMode, hasStartBorder = true)
        }

        HorizontalDivider(thickness = 1.dp, color = borderColor)

        // Course Rows
        courses.forEachIndexed { index, course ->
            val courseMax = (course.coefficient * 20.0).coerceAtLeast(20.0)
            val c1 = row.c1RawGrades[course.id]
            val e1 = row.e1RawGrades[course.id]
            val c2 = row.c2RawGrades[course.id]
            val e2 = row.e2RawGrades[course.id]
            val finalGrade = if (isC1Only) c1 else if (isC2Only) c2 else row.courseGrades[course.id]

            fun formatVal(v: Double?): String {
                if (v == null) return ""
                return if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (index % 2 == 0) Color.White else Color(0xFFF8FAFC)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(
                    text = course.name,
                    weight = 2.5f,
                    textAlign = TextAlign.Start,
                    compact = compactMode
                )
                TableCell(
                    text = courseMax.toInt().toString(),
                    weight = 1.1f,
                    compact = compactMode,
                    hasStartBorder = true
                )

                if (isC1Only) {
                    TableCell(text = formatVal(c1), weight = 1.2f, compact = compactMode, hasStartBorder = true)
                    TableCell(text = "", weight = 1.2f, compact = compactMode, hasStartBorder = true)
                } else if (isC2Only) {
                    TableCell(text = formatVal(c2), weight = 1.2f, compact = compactMode, hasStartBorder = true)
                    TableCell(text = "", weight = 1.2f, compact = compactMode, hasStartBorder = true)
                } else if (isAll) {
                    TableCell(text = formatVal(c1), weight = 1.1f, compact = compactMode, hasStartBorder = true)
                    TableCell(text = formatVal(e1), weight = 1.1f, compact = compactMode, hasStartBorder = true)
                    TableCell(text = formatVal(c2), weight = 1.1f, compact = compactMode, hasStartBorder = true)
                    TableCell(text = formatVal(e2), weight = 1.1f, compact = compactMode, hasStartBorder = true)
                } else {
                    TableCell(text = formatVal(c1), weight = 1.2f, compact = compactMode, hasStartBorder = true)
                    TableCell(text = formatVal(e1), weight = 1.2f, compact = compactMode, hasStartBorder = true)
                }

                TableCell(
                    text = formatVal(finalGrade),
                    weight = 1.2f,
                    isBold = true,
                    textColor = Color(0xFF1E3A8A),
                    compact = compactMode,
                    hasStartBorder = true
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFCBD5E1))
        }

        // Summary 1: المجموع
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F5F9)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(text = "المجموع", weight = 2.5f, isBold = true, compact = compactMode)
            TableCell(text = row.maxPointsTotal.toInt().toString(), weight = 1.1f, isBold = true, compact = compactMode, hasStartBorder = true)

            fun formatSum(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v)

            if (isC1Only) {
                TableCell(text = formatSum(row.c1WeightedTotal), weight = 1.2f, isBold = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = "", weight = 1.2f, compact = compactMode, hasStartBorder = true)
            } else if (isC2Only) {
                TableCell(text = formatSum(row.c2WeightedTotal), weight = 1.2f, isBold = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = "", weight = 1.2f, compact = compactMode, hasStartBorder = true)
            } else if (isAll) {
                TableCell(text = formatSum(row.c1WeightedTotal), weight = 1.1f, isBold = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = formatSum(row.e1WeightedTotal), weight = 1.1f, isBold = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = formatSum(row.c2WeightedTotal), weight = 1.1f, isBold = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = formatSum(row.e2WeightedTotal), weight = 1.1f, isBold = true, compact = compactMode, hasStartBorder = true)
            } else {
                TableCell(text = formatSum(row.c1WeightedTotal), weight = 1.2f, isBold = true, compact = compactMode, hasStartBorder = true)
                TableCell(text = if (row.e1WeightedTotal > 0) formatSum(row.e1WeightedTotal) else "", weight = 1.2f, compact = compactMode, hasStartBorder = true)
            }

            // Final total highlighted in RED like the official image
            val displayedFinalScore = if (isC1Only) row.c1WeightedTotal else if (isC2Only) row.c2WeightedTotal else row.finalScore
            TableCell(
                text = String.format("%.2f", displayedFinalScore),
                weight = 1.2f,
                isBold = true,
                textColor = Color(0xFFDC2626),
                compact = compactMode,
                hasStartBorder = true
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = borderColor)

        // Summary 2: المعدل العام 20/
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(text = "المعدل العام 20/", weight = 2.5f, isBold = true, compact = compactMode)
            TableCell(text = "معدل النجاح: 10 \\ 20", weight = 1.1f, fontSize = 9.sp, compact = compactMode, hasStartBorder = true)

            fun formatAvg(v: Double): String = String.format("%.2f", (v / row.maxPointsTotal) * 20.0)

            if (isC1Only) {
                TableCell(text = formatAvg(row.c1WeightedTotal), weight = 1.2f, compact = compactMode, hasStartBorder = true)
                TableCell(text = "", weight = 1.2f, compact = compactMode, hasStartBorder = true)
            } else if (isC2Only) {
                TableCell(text = formatAvg(row.c2WeightedTotal), weight = 1.2f, compact = compactMode, hasStartBorder = true)
                TableCell(text = "", weight = 1.2f, compact = compactMode, hasStartBorder = true)
            } else if (isAll) {
                TableCell(text = formatAvg(row.c1WeightedTotal), weight = 1.1f, compact = compactMode, hasStartBorder = true)
                TableCell(text = formatAvg(row.e1WeightedTotal), weight = 1.1f, compact = compactMode, hasStartBorder = true)
                TableCell(text = formatAvg(row.c2WeightedTotal), weight = 1.1f, compact = compactMode, hasStartBorder = true)
                TableCell(text = formatAvg(row.e2WeightedTotal), weight = 1.1f, compact = compactMode, hasStartBorder = true)
            } else {
                TableCell(text = formatAvg(row.c1WeightedTotal), weight = 1.2f, compact = compactMode, hasStartBorder = true)
                TableCell(text = if (row.e1WeightedTotal > 0) formatAvg(row.e1WeightedTotal) else "", weight = 1.2f, compact = compactMode, hasStartBorder = true)
            }

            val displayedFinalAvg = if (isC1Only) {
                if (row.maxPointsTotal > 0) (row.c1WeightedTotal / row.maxPointsTotal) * 20.0 else 0.0
            } else if (isC2Only) {
                if (row.maxPointsTotal > 0) (row.c2WeightedTotal / row.maxPointsTotal) * 20.0 else 0.0
            } else {
                row.generalAverage20
            }
            val displayedPassed = displayedFinalAvg >= 10.0
            TableCell(
                text = String.format("%.2f", displayedFinalAvg),
                weight = 1.2f,
                isBold = true,
                textColor = if (displayedPassed) EmisSuccess else EmisError,
                compact = compactMode,
                hasStartBorder = true
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = borderColor)

        // Summary 3: النتيجة (ناجح / راسب)
        val finalAvgForPass = if (isC1Only) {
            if (row.maxPointsTotal > 0) (row.c1WeightedTotal / row.maxPointsTotal) * 20.0 else 0.0
        } else if (isC2Only) {
            if (row.maxPointsTotal > 0) (row.c2WeightedTotal / row.maxPointsTotal) * 20.0 else 0.0
        } else {
            row.generalAverage20
        }
        val isCardPassed = finalAvgForPass >= 10.0

        val c1Avg = if (row.maxPointsTotal > 0) (row.c1WeightedTotal / row.maxPointsTotal) * 20.0 else 0.0
        val e1Avg = if (row.maxPointsTotal > 0) (row.e1WeightedTotal / row.maxPointsTotal) * 20.0 else 0.0
        val c2Avg = if (row.maxPointsTotal > 0) (row.c2WeightedTotal / row.maxPointsTotal) * 20.0 else 0.0
        val e2Avg = if (row.maxPointsTotal > 0) (row.e2WeightedTotal / row.maxPointsTotal) * 20.0 else 0.0

        val c1Passed = c1Avg >= 10.0
        val e1Passed = e1Avg >= 10.0
        val c2Passed = c2Avg >= 10.0
        val e2Passed = e2Avg >= 10.0

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isCardPassed) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(text = "النتيجة", weight = 2.5f, isBold = true, compact = compactMode)
            TableCell(text = "", weight = 1.1f, compact = compactMode, hasStartBorder = true)

            if (isC1Only) {
                TableCell(text = if (c1Passed) "ناجح" else "راسب", weight = 1.2f, textColor = if (c1Passed) EmisSuccess else EmisError, compact = compactMode, hasStartBorder = true)
                TableCell(text = "", weight = 1.2f, compact = compactMode, hasStartBorder = true)
            } else if (isC2Only) {
                TableCell(text = if (c2Passed) "ناجح" else "راسب", weight = 1.2f, textColor = if (c2Passed) EmisSuccess else EmisError, compact = compactMode, hasStartBorder = true)
                TableCell(text = "", weight = 1.2f, compact = compactMode, hasStartBorder = true)
            } else if (isAll) {
                TableCell(text = if (row.c1WeightedTotal > 0) (if (c1Passed) "ناجح" else "راسب") else "", weight = 1.1f, textColor = if (c1Passed) EmisSuccess else EmisError, compact = compactMode, hasStartBorder = true)
                TableCell(text = if (row.e1WeightedTotal > 0) (if (e1Passed) "ناجح" else "راسب") else "", weight = 1.1f, textColor = if (e1Passed) EmisSuccess else EmisError, compact = compactMode, hasStartBorder = true)
                TableCell(text = if (row.c2WeightedTotal > 0) (if (c2Passed) "ناجح" else "راسب") else "", weight = 1.1f, textColor = if (c2Passed) EmisSuccess else EmisError, compact = compactMode, hasStartBorder = true)
                TableCell(text = if (row.e2WeightedTotal > 0) (if (e2Passed) "ناجح" else "راسب") else "", weight = 1.1f, textColor = if (e2Passed) EmisSuccess else EmisError, compact = compactMode, hasStartBorder = true)
            } else {
                TableCell(text = if (c1Passed) "ناجح" else "راسب", weight = 1.2f, textColor = if (c1Passed) EmisSuccess else EmisError, compact = compactMode, hasStartBorder = true)
                TableCell(text = if (row.e1WeightedTotal > 0) (if (e1Passed) "ناجح" else "راسب") else "", weight = 1.2f, textColor = if (e1Passed) EmisSuccess else EmisError, compact = compactMode, hasStartBorder = true)
            }

            TableCell(
                text = if (isCardPassed) "ناجح" else "راسب",
                weight = 1.2f,
                isBold = true,
                textColor = if (isCardPassed) EmisSuccess else EmisError,
                compact = compactMode,
                hasStartBorder = true
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = borderColor)

        // Summary 4: عدد الطلاب والمرتبة
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val leftColWeight = 3.6f
            val remainingWeight = if (isAll) 5.6f else 3.6f
            TableCell(
                text = "عدد الطلاب: ${row.classStudentCount}",
                weight = leftColWeight,
                isBold = true,
                compact = compactMode
            )
            TableCell(
                text = "المرتبة: ${row.rank}",
                weight = remainingWeight,
                isBold = true,
                textColor = Color(0xFF1E3A8A),
                compact = compactMode,
                hasStartBorder = true
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(
    text: String,
    weight: Float,
    isHeader: Boolean = false,
    isBold: Boolean = false,
    textColor: Color = Color.Black,
    textAlign: TextAlign = TextAlign.Center,
    fontSize: androidx.compose.ui.unit.TextUnit? = null,
    compact: Boolean = false,
    hasStartBorder: Boolean = false
) {
    val borderColor = Color(0xFF334155)
    Box(
        modifier = Modifier
            .weight(weight)
            .then(if (hasStartBorder) Modifier.drawBehind {
                drawLine(
                    color = borderColor,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            } else Modifier)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = if (compact) 4.dp else 6.dp),
        contentAlignment = when (textAlign) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        val size = fontSize ?: if (compact) 10.sp else if (isHeader) 11.sp else 11.sp
        Text(
            text = text,
            fontWeight = if (isHeader || isBold) FontWeight.Bold else FontWeight.Normal,
            fontSize = size,
            color = textColor,
            textAlign = textAlign,
            maxLines = 2
        )
    }
}

/**
 * QR Code Graphic drawn with the app logo in the middle
 */
@Composable
fun QrCodeCanvas(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val qrBitmap = remember {
        com.example.ui.util.QrCodeGenerator.generateQrImageBitmapWithAppLogo(
            context = context,
            content = "https://emis.school/portal",
            size = 200
        )
    }

    if (qrBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = qrBitmap,
            contentDescription = "رمز QR لبوابة النتائج مع الشعار",
            modifier = modifier
                .background(Color.White)
                .border(1.dp, Color(0xFF1E293B))
        )
    } else {
        Canvas(modifier = modifier.background(Color.White)) {
            val stroke = 2.dp.toPx()
            // Outer border
            drawRect(color = Color.Black, size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))

            val s = size.width
            val cornerSize = s * 0.28f

            // Top Right finder
            drawRect(Color.Black, Offset(0f, 0f), Size(cornerSize, cornerSize))
            drawRect(Color.White, Offset(s * 0.06f, s * 0.06f), Size(cornerSize - s * 0.12f, cornerSize - s * 0.12f))
            drawRect(Color.Black, Offset(s * 0.10f, s * 0.10f), Size(cornerSize - s * 0.20f, cornerSize - s * 0.20f))

            // Top Left finder
            drawRect(Color.Black, Offset(s - cornerSize, 0f), Size(cornerSize, cornerSize))
            drawRect(Color.White, Offset(s - cornerSize + s * 0.06f, s * 0.06f), Size(cornerSize - s * 0.12f, cornerSize - s * 0.12f))
            drawRect(Color.Black, Offset(s - cornerSize + s * 0.10f, s * 0.10f), Size(cornerSize - s * 0.20f, cornerSize - s * 0.20f))

            // Bottom Right finder
            drawRect(Color.Black, Offset(0f, s - cornerSize), Size(cornerSize, cornerSize))
            drawRect(Color.White, Offset(s * 0.06f, s - cornerSize + s * 0.06f), Size(cornerSize - s * 0.12f, cornerSize - s * 0.12f))
            drawRect(Color.Black, Offset(s * 0.10f, s - cornerSize + s * 0.10f), Size(cornerSize - s * 0.20f, cornerSize - s * 0.20f))

            // Center simulated dots
            val dot = s * 0.08f
            drawRect(Color.Black, Offset(s * 0.40f, s * 0.35f), Size(dot, dot))
            drawRect(Color.Black, Offset(s * 0.55f, s * 0.45f), Size(dot, dot))
            drawRect(Color.Black, Offset(s * 0.35f, s * 0.55f), Size(dot, dot))
            drawRect(Color.Black, Offset(s * 0.50f, s * 0.65f), Size(dot, dot))
            drawRect(Color.Black, Offset(s * 0.65f, s * 0.70f), Size(dot, dot))
            drawRect(Color.Black, Offset(s * 0.45f, s * 0.20f), Size(dot, dot))
        }
    }
}
