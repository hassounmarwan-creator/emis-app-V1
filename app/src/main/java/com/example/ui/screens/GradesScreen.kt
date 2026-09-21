package com.example.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExamType
import com.example.model.Student
import com.example.model.isTeacherNameMatch
import com.example.ui.components.DeadlineStatusBanner
import com.example.ui.components.EmisTopAppBar
import com.example.ui.theme.EmisBlueContainer
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.theme.EmisError
import com.example.ui.theme.EmisSuccess
import com.example.viewmodel.AppScreen
import com.example.viewmodel.EmisViewModel
import kotlinx.coroutines.launch

@Composable
fun GradesScreen(viewModel: EmisViewModel) {
    val teacher by viewModel.currentTeacher.collectAsState()
    val courses by viewModel.currentExamCourses.collectAsState()
    val students by viewModel.students.collectAsState()
    val deadlines by viewModel.deadlines.collectAsState()
    val selectedExamType by viewModel.selectedExamType.collectAsState()
    val selectedClassIndex by viewModel.selectedClassIndex.collectAsState()
    val selectedCourseId by viewModel.selectedCourseId.collectAsState()
    val editedGrades by viewModel.editedGrades.collectAsState()
    val saveMessage by viewModel.saveStatusMessage.collectAsState()
    val classBlocks by viewModel.classBlocks.collectAsState()
    val autoSyncStatus by viewModel.autoSyncStatus.collectAsState()
    val autoSyncMessage by viewModel.autoSyncMessage.collectAsState()
    val isSavingToSheet by viewModel.isSavingToSheet.collectAsState()
    val showWebAppDialog by viewModel.showWebAppDialog.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val webAppUrl by viewModel.webAppUrl.collectAsState()
    var tempWebAppUrl by remember(webAppUrl) { mutableStateOf(webAppUrl) }
    val focusManager = LocalFocusManager.current

    // Filter courses available for this teacher (or all for admin)
    val availableCourses = remember(courses, teacher) {
        if (teacher?.isAdmin == true) {
            courses
        } else {
            courses.filter { isTeacherNameMatch(it.teacherName, teacher?.name) }
        }
    }

    // A class is only shown if:
    // 1. It has a non-blank name
    // 2. It has students enrolled
    // 3. The teacher has at least one course registered to his/her name in that class (or if admin, class has courses)
    val validClassBlocks = remember(classBlocks, students, availableCourses, teacher) {
        classBlocks.filter { block ->
            val hasName = block.name.isNotBlank()
            val hasStudents = students.any { s -> s.classIndex == block.index }
            val hasCourseForTeacher = if (teacher?.isAdmin == true) {
                courses.any { it.classIndex == block.index }
            } else {
                availableCourses.any { it.classIndex == block.index }
            }
            hasName && hasStudents && hasCourseForTeacher
        }
    }

    // Auto-select first class where teacher has courses and students
    androidx.compose.runtime.LaunchedEffect(validClassBlocks) {
        if (validClassBlocks.isNotEmpty() && validClassBlocks.none { it.index == selectedClassIndex }) {
            viewModel.setSelectedClassIndex(validClassBlocks.first().index)
        }
    }

    // Strictly courses for this selected class that belong to this teacher (no fallback to other classes)
    val displayCourses = remember(availableCourses, selectedClassIndex) {
        availableCourses.filter { it.classIndex == selectedClassIndex }
    }

    val selectedCourse = displayCourses.find { it.id == selectedCourseId }
        ?: displayCourses.firstOrNull()

    // Course and Exam Lock Evaluation:
    // 1. Exam date in cell A1 passed
    // 2. Course header cell in Google Sheet (row 3, 27, 51... 267) is highlighted in red
    // 3. Admin has NO lock whatever (even if date passed, even if course is in red)
    val isAdmin = teacher?.isAdmin == true
    val isExamDateLocked = !isAdmin && viewModel.isCurrentExamLocked()
    val isCourseRedLocked = !isAdmin && selectedCourse != null && viewModel.isCourseRedHighlighted(selectedCourse.id, selectedExamType)
    val isLocked = !isAdmin && (isExamDateLocked || isCourseRedLocked)

    // Keep ViewModel selectedCourseId in sync if current selectedCourseId is not in displayCourses
    androidx.compose.runtime.LaunchedEffect(selectedCourse?.id) {
        if (selectedCourse != null && selectedCourseId != selectedCourse.id) {
            viewModel.setSelectedCourseId(selectedCourse.id)
        }
    }

    val currentClassBlock = validClassBlocks.find { it.index == selectedClassIndex }

    // If teacher has no course in this class, do NOT show students
    val hasCourseInClass = selectedCourse != null && displayCourses.isNotEmpty()
    val classStudents = if (hasCourseInClass) {
        students.filter { it.classIndex == selectedClassIndex }
    } else {
        emptyList()
    }

    val deadlineDate = deadlines.getDate(selectedExamType)

    androidx.compose.runtime.LaunchedEffect(selectedExamType, selectedClassIndex, selectedCourse?.id, students.size) {
        viewModel.loadGradesForCurrentSelection()
    }

    Scaffold(
        topBar = {
            EmisTopAppBar(
                title = "إدخال وعرض العلامات",
                subtitle = "الاستاذ(ة): ${teacher?.name ?: ""} • ${currentClassBlock?.name ?: ""}",
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
            // 1. Exam Type Selector Tabs (السعي الأول, الامتحان الأول, السعي الثاني, الامتحان الثاني)
            val examTypes = ExamType.values()
            val selectedTabIndex = examTypes.indexOf(selectedExamType)

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                contentColor = EmisBluePrimary,
                indicator = { tabPositions ->
                    if (selectedTabIndex in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = EmisBluePrimary,
                            height = 3.dp
                        )
                    }
                }
            ) {
                examTypes.forEach { examType ->
                    Tab(
                        selected = examType == selectedExamType,
                        onClick = { viewModel.setSelectedExamType(examType) },
                        text = {
                            Text(
                                text = examType.displayName,
                                fontWeight = if (examType == selectedExamType) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        },
                        modifier = Modifier.testTag("exam_tab_${examType.shortCode}")
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                // Deadline and Lock Status Banner (only shown for teachers, hidden for admin)
                if (!isAdmin) {
                    DeadlineStatusBanner(
                        examName = selectedExamType.displayName,
                        deadlineDate = deadlineDate,
                        isLocked = isExamDateLocked,
                        isAdmin = false
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (validClassBlocks.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = EmisBlueContainer.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = EmisBluePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "لا توجد شعب أو مواد مسندة للأستاذ (${teacher?.name ?: ""}) في هذا الملف.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EmisBlueDark
                            )
                        }
                    }
                } else {
                    // 2. Class Block Selector (Only classes where teacher has registered courses)
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
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        validClassBlocks.forEach { block ->
                            FilterChip(
                                selected = block.index == selectedClassIndex,
                                onClick = { viewModel.setSelectedClassIndex(block.index) },
                                label = { Text(block.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmisBluePrimary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("class_chip_${block.index}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. Course Selector (Only courses for this class that belong to this teacher)
                    if (displayCourses.isNotEmpty()) {
                        Text(
                            text = "اختر المادة:",
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
                            displayCourses.forEach { course ->
                                val courseIsRedLocked = viewModel.isCourseRedHighlighted(course.id, selectedExamType)
                                FilterChip(
                                    selected = course.id == selectedCourse?.id,
                                    onClick = { viewModel.setSelectedCourseId(course.id) },
                                    leadingIcon = if (courseIsRedLocked) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "المادة مقفلة باللون الأحمر",
                                                tint = if (course.id == selectedCourse?.id) Color(0xFFFCA5A5) else EmisError,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    } else null,
                                    label = { Text("${course.name} (${course.coefficient})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (courseIsRedLocked) Color(0xFF991B1B) else EmisBlueDark,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("course_chip_${course.id}")
                                )
                            }
                        }
                    }
                }

                // Status and feedback banners (Errors, Warnings, WebApp status)
                if (saveMessage != null &&
                    !saveMessage!!.contains("تم تحديث البيانات والعلامات بنجاح") &&
                    !saveMessage!!.contains("يوجد تعديلات غير محفوظة")
                ) {
                    val msg = saveMessage ?: ""
                    val isErr = msg.contains("عذراً") || msg.contains("تعذر") || msg.contains("خطأ") || msg.contains("❌")
                    val isSucc = msg.contains("✓") || msg.contains("بنجاح") || msg.contains("تم حفظ") || msg.contains("تم تعديل")

                    val bgColor = when {
                        isSucc -> EmisSuccess.copy(alpha = 0.15f)
                        isErr -> EmisError.copy(alpha = 0.12f)
                        else -> EmisBluePrimary.copy(alpha = 0.10f)
                    }
                    val iconColor = when {
                        isSucc -> EmisSuccess
                        isErr -> EmisError
                        else -> EmisBluePrimary
                    }
                    val icon = when {
                        isSucc -> Icons.Default.CheckCircle
                        isErr -> Icons.Default.Warning
                        else -> Icons.Default.Info
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = bgColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = iconColor,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearSaveStatusMessage() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إغلاق",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (msg.contains("Apps Script") || msg.contains("تطبيق الويب")) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.openWebAppDialog() },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = "تفعيل رابط تطبيق الويب (Apps Script)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Students Grade Input List
            if (classStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PersonOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (validClassBlocks.isEmpty()) {
                                "لا توجد شعب مسندة لك"
                            } else if (!hasCourseInClass) {
                                "ليس لديك مادة مسندة في هذا الصف، لذلك لا تظهر أسماء الطلاب"
                            } else {
                                "لا يوجد طلاب مسجلين في هذا الصف"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "قائمة الطلاب (${classStudents.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmisBlueDark
                    )
                    IconButton(
                        onClick = { viewModel.refreshFromGoogleSheet() },
                        enabled = !isSyncing,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("refresh_grades_list_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = EmisBluePrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تحديث العلامات",
                                tint = EmisBluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(classStudents, key = { _, s -> s.id }) { index, student ->
                        val gradeText = editedGrades[student.id] ?: ""
                        StudentGradeCard(
                            student = student,
                            index = index,
                            gradeText = gradeText,
                            isLocked = isLocked,
                            isLastStudent = index == classStudents.lastIndex,
                            viewModel = viewModel,
                            focusManager = focusManager
                        )
                    }
                }
            }

            // 5. Direct Save Button (Hidden when locked)
            if (!isLocked) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        val hasInvalid = viewModel.hasInvalidGrades()
                        Button(
                            onClick = {
                                viewModel.saveCurrentGrades()
                            },
                            enabled = !isSavingToSheet && classStudents.isNotEmpty() && hasCourseInClass && !hasInvalid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmisBluePrimary,
                                disabledContainerColor = Color(0xFFE2E8F0),
                                disabledContentColor = Color(0xFF64748B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_grades_button")
                        ) {
                            if (isSavingToSheet) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "جاري الحفظ والمزامنة...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "حفظ",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "حفظ على Google Sheet",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWebAppDialog) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        var copiedCode by remember { mutableStateOf(false) }
        var isTestingConnection by remember { mutableStateOf(false) }
        var dialogTestResult by remember { mutableStateOf<String?>(null) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        AlertDialog(
            onDismissRequest = {
                dialogTestResult = null
                viewModel.dismissWebAppDialog()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = EmisBluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ربط التعديل المباشر في Google Sheet",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "لحفظ وتعديل درجات الطلاب مباشرة في ملف Google Sheet في أسطرهم المحددة، يلزم تفعيل سكريبت التعديل (Google Apps Script):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = EmisBlueContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "خطوات التفعيل السريعة (دقيقة واحدة):",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = EmisBlueDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. افتح جدول Google Sheet الخاص بالمدرسة على الكمبيوتر أو المتصفح.\n" +
                                        "2. من القائمة العلوية اضغط: إضافات (Extensions) ثم Apps Script.\n" +
                                        "3. الصق الكود الجاهز، ثم اضغط حفظ ثم نشر (Deploy) -> New deployment.\n" +
                                        "4. اختر Web app، واضبط (Who has access) على (Anyone / أي شخص).\n" +
                                        "5. انسخ الرابط الناتج المنتهي بـ /exec والصقه بالأسفل.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(viewModel.getGoogleAppsScriptCode()))
                            copiedCode = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (copiedCode) "✓ تم نسخ كود Apps Script إلى الحافظة" else "📋 نسخ كود Google Apps Script الجاهز")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "رابط تطبيق الويب (Web App URL):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = tempWebAppUrl,
                        onValueChange = {
                            tempWebAppUrl = it
                            dialogTestResult = null
                        },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_web_app_url_input"),
                        singleLine = true,
                        isError = tempWebAppUrl.contains("docs.google.com/spreadsheets")
                    )

                    if (tempWebAppUrl.contains("docs.google.com/spreadsheets")) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ تنبيه: هذا رابط ملف الشيت العادي وليس رابط تطبيق الويب. يتطلب التعديل المباشر رابطاً ينتهي بـ /exec",
                            color = EmisError,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (tempWebAppUrl.isBlank()) {
                                    dialogTestResult = "يرجى كتابة أو لصق الرابط أولاً"
                                    return@OutlinedButton
                                }
                                isTestingConnection = true
                                dialogTestResult = null
                                scope.launch {
                                    val res = viewModel.testWebAppConnection(tempWebAppUrl)
                                    isTestingConnection = false
                                    dialogTestResult = if (res.isSuccess) {
                                        "✓ ${res.getOrNull()}"
                                    } else {
                                        "❌ ${res.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            enabled = !isTestingConnection && tempWebAppUrl.isNotBlank()
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جاري الفحص...")
                            } else {
                                Text("فحص الاتصال")
                            }
                        }
                    }

                    if (dialogTestResult != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = dialogTestResult ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dialogTestResult?.startsWith("✓") == true) EmisSuccess else EmisError,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateWebAppUrl(tempWebAppUrl)
                        viewModel.dismissWebAppDialog()
                        viewModel.saveCurrentGrades()
                    },
                    modifier = Modifier.testTag("dialog_save_url_button")
                ) {
                    Text("حفظ الرابط والتعديل في Google Sheet الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialogTestResult = null
                    viewModel.dismissWebAppDialog()
                }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun StudentGradeCard(
    student: Student,
    index: Int,
    gradeText: String,
    isLocked: Boolean,
    isLastStudent: Boolean,
    viewModel: EmisViewModel,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    val isInvalidGrade = gradeText.isNotBlank() && !viewModel.isValidGrade(gradeText)

    // Store TextFieldValue with selection initialized at the end of the text
    var textFieldValue by remember(student.id) {
        mutableStateOf(
            TextFieldValue(
                text = gradeText,
                selection = TextRange(gradeText.length)
            )
        )
    }

    LaunchedEffect(gradeText) {
        if (textFieldValue.text != gradeText) {
            textFieldValue = TextFieldValue(
                text = gradeText,
                selection = TextRange(gradeText.length)
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_grade_row_${student.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isInvalidGrade) EmisError else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Student Index & Name
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmisBluePrimary,
                    modifier = Modifier.width(28.dp)
                )
                Column {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isInvalidGrade) {
                        Text(
                            text = "العلامة يجب أن تكون بين 0 و 20 ومضاعفات 0.25 فقط",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmisError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // If locked: Teacher CAN ONLY SEE the grade, cannot modify!
            if (isLocked) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .width(96.dp)
                        .height(48.dp)
                        .testTag("locked_grade_${student.id}")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (gradeText.isNotBlank()) gradeText else "-",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (gradeText.isNotBlank()) Color(0xFF0F172A) else Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("grade_text_${student.id}")
                        )
                    }
                }
            } else {
                // Grade Input with numeric keyboard & Next/Enter action
                // LTR layout ensures numerals and cursor positioning follow standard Left-to-Right orientation
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { newTfv ->
                            val normalized = viewModel.normalizeGradeDigits(newTfv.text)
                            if (viewModel.isAllowedTypingGrade(normalized)) {
                                val cursorOffset = newTfv.selection.start.coerceIn(0, normalized.length)
                                textFieldValue = newTfv.copy(
                                    text = normalized,
                                    selection = TextRange(cursorOffset)
                                )
                                viewModel.updateGradeInput(student.id, normalized)
                            }
                        },
                        isError = isInvalidGrade,
                        singleLine = true,
                        placeholder = {
                            Text(
                                "-",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = if (isLastStudent) ImeAction.Done else ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                focusManager.moveFocus(FocusDirection.Down)
                            },
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmisBluePrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            errorBorderColor = EmisError,
                            errorCursorColor = EmisError
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            textDirection = TextDirection.Ltr
                        ),
                        modifier = Modifier
                            .width(96.dp)
                            .height(52.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    // When focused (whether by clicking or pressing Next on keyboard),
                                    // position the cursor strictly at the END of the number
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(textFieldValue.text.length)
                                    )
                                }
                            }
                            .testTag("grade_input_${student.id}")
                    )
                }
            }
        }
    }
}
