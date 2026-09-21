package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Course
import com.example.model.isTeacherNameMatch
import com.example.ui.components.EmisTopAppBar
import com.example.ui.theme.EmisBlueContainer
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.theme.EmisError
import com.example.ui.theme.EmisSecondary
import com.example.ui.theme.EmisSuccess
import com.example.viewmodel.AppScreen
import com.example.viewmodel.EmisViewModel

@Composable
fun ExamUploadScreen(viewModel: EmisViewModel) {
    val context = LocalContext.current
    val teacher by viewModel.currentTeacher.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val deadlines by viewModel.deadlines.collectAsState()
    val uploadedExams by viewModel.uploadedExams.collectAsState()
    val classBlocks by viewModel.classBlocks.collectAsState()
    val students by viewModel.students.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var selectedExamPeriodIndex by remember { mutableStateOf(0) } // 0: Mid Year, 1: Final Year
    var selectedClassIndex by remember { mutableStateOf(1) }
    var targetCourseForUpload by remember { mutableStateOf<Course?>(null) }

    val examPeriods = listOf(
        Pair("امتحان منتصف العام (الامتحان الأول)", deadlines.exam1UploadEnabled),
        Pair("امتحان نهاية العام (الامتحان الثاني)", deadlines.exam2UploadEnabled)
    )

    val currentExamPeriod = examPeriods[selectedExamPeriodIndex]
    val isPeriodEnabled = teacher?.isAdmin == true || currentExamPeriod.second

    // File picker launcher supporting .pdf, docx, doc, xlsx, xls, pptx, ppt, images, text
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && targetCourseForUpload != null) {
            var displayName = "exam_file_${System.currentTimeMillis()}"
            var sizeBytes = 0L

            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) displayName = cursor.getString(nameIdx)
                        if (sizeIdx != -1) sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            } catch (e: Exception) {
                // fallback name
            }

            val ext = displayName.substringAfterLast(".", "pdf")
            val sizeFormatted = if (sizeBytes > 1024 * 1024) {
                String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            } else if (sizeBytes > 0) {
                "${sizeBytes / 1024} KB"
            } else {
                "1.2 MB"
            }

            viewModel.addUploadedExam(
                classIndex = selectedClassIndex,
                courseName = targetCourseForUpload!!.name,
                examType = currentExamPeriod.first,
                fileName = displayName,
                extension = ext,
                sizeStr = sizeFormatted
            )
        }
    }

    val availableCourses = if (teacher?.isAdmin == true) {
        courses
    } else {
        courses.filter { isTeacherNameMatch(it.teacherName, teacher?.name) }
    }

    val validClassBlocks = remember(classBlocks, students, availableCourses, teacher) {
        classBlocks.filter { block ->
            val hasName = block.name.isNotBlank()
            val hasStudents = students.any { s -> s.classIndex == block.index }
            val hasCourses = if (teacher?.isAdmin == true) {
                courses.any { it.classIndex == block.index }
            } else {
                availableCourses.any { it.classIndex == block.index }
            }
            hasName && hasStudents && hasCourses
        }
    }

    LaunchedEffect(validClassBlocks) {
        if (validClassBlocks.isNotEmpty() && validClassBlocks.none { it.index == selectedClassIndex }) {
            selectedClassIndex = validClassBlocks.first().index
        }
    }

    val classCourses = availableCourses.filter { it.classIndex == selectedClassIndex }
    val currentClassBlock = classBlocks.find { it.index == selectedClassIndex }

    Scaffold(
        topBar = {
            EmisTopAppBar(
                title = "رفع نماذج الامتحانات",
                subtitle = "الاستاذ(ة): ${teacher?.name ?: ""}",
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                onSyncClick = { viewModel.syncWithGoogleSheets() },
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
            // Exam Period Tabs (Mid Year / Final Year based on A2)
            TabRow(
                selectedTabIndex = selectedExamPeriodIndex,
                containerColor = Color.White,
                contentColor = EmisBluePrimary
            ) {
                examPeriods.forEachIndexed { idx, pair ->
                    Tab(
                        selected = selectedExamPeriodIndex == idx,
                        onClick = { selectedExamPeriodIndex = idx },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (idx == 0) "منتصف العام" else "نهاية العام",
                                    fontWeight = if (selectedExamPeriodIndex == idx) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val isUploadLockedForUser = teacher?.isAdmin != true && !pair.second
                                if (isUploadLockedForUser) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "مغلق",
                                        tint = EmisError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = if (teacher?.isAdmin == true && !pair.second) "متاح للإدارة" else "متاح",
                                        tint = EmisSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Google Drive Folder Destination Notice
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmisBlueContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = EmisBluePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مجلد الحفظ في Google Drive: \"Exam Uploads\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmisBlueDark
                            )
                            Text(
                                text = "الصيغ المدعومة: PDF, Word, Excel, PowerPoint, صور، نصوص",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Class block horizontal chips
                Text(
                    text = "اختر الشعبة / الصف:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmisBlueDark
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    validClassBlocks.forEach { block ->
                        FilterChip(
                            selected = block.index == selectedClassIndex,
                            onClick = { selectedClassIndex = block.index },
                            label = { Text(block.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmisBluePrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "المواد المكلف بها في ${currentClassBlock?.name ?: ""}:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = EmisBlueDark
                )
            }

            // Courses List with Upload/Delete actions
            if (classCourses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد مواد مكلف بها في هذه الشعبة",
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(classCourses) { course ->
                    val courseFiles = uploadedExams.filter {
                        it.classIndex == selectedClassIndex &&
                                it.courseName.equals(course.name, ignoreCase = true) &&
                                it.examType == currentExamPeriod.first
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = course.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = EmisBlueDark
                                    )
                                    Text(
                                        text = "الاستاذ(ة): ${course.teacherName} • (${course.coefficient})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = {
                                        targetCourseForUpload = course
                                        // Filter for standard documents, spreadsheets, slides, images, text
                                        filePickerLauncher.launch("*/*")
                                    },
                                    enabled = isPeriodEnabled,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmisBluePrimary,
                                        disabledContainerColor = Color(0xFFE2E8F0),
                                        disabledContentColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.testTag("upload_exam_btn_${course.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isPeriodEnabled) Icons.Default.CloudUpload else Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isPeriodEnabled) "رفع نموذج" else "مغلق")
                                }
                            }

                            // Uploaded Files for this course
                            if (courseFiles.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "الملفات المرفوعة إلى Google Drive (Exam Uploads):",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmisSecondary
                                    )
                                    courseFiles.forEach { file ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = EmisBluePrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.fileName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${file.fileSizeFormatted} • ${file.uploadDate}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteUploadedExam(file.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "حذف",
                                                    tint = EmisError,
                                                    modifier = Modifier.size(18.dp)
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
}
