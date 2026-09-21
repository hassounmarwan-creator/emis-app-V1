package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExamType
import com.example.ui.components.EmisTopAppBar
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.theme.EmisSuccess
import com.example.viewmodel.AppScreen
import com.example.viewmodel.EmisViewModel
import java.text.Collator
import java.util.Locale

// App original blue theme colors
private val MissingHeaderBlue = EmisBluePrimary
private val TeacherBlueText = EmisBlueDark
private val BorderGrey = Color(0xFFCBD5E1)
private val RowDividerGrey = Color(0xFFE2E8F0)

@Composable
fun MissingGradesScreen(viewModel: EmisViewModel) {
    val isMissingByTeacher by viewModel.isMissingByTeacher.collectAsState()
    val selectedExamType by viewModel.missingGradeExamType.collectAsState()
    val allMissing by viewModel.missingGrades.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val classBlocks by viewModel.classBlocks.collectAsState()

    // 1st: The 4 exams strictly without "الكل"
    val examTabOptions = listOf(
        ExamType.C1,
        ExamType.E1,
        ExamType.C2,
        ExamType.E2
    )
    val selectedExamTabIndex = examTabOptions.indexOf(selectedExamType).coerceAtLeast(0)

    // Arabic Collator for sorting teachers alphabetically
    val arabicCollator = remember {
        Collator.getInstance(Locale.forLanguageTag("ar")).apply {
            strength = Collator.PRIMARY
        }
    }

    Scaffold(
        topBar = {
            EmisTopAppBar(
                title = "العلامات الناقصة",
                subtitle = "متابعة إدخال درجات الأساتذة والصفوف",
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
            // 1. Exam Type Tabs (4 exams: C1, E1, C2, E2 - no "الكل")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 2.dp,
                color = Color.White
            ) {
                TabRow(
                    selectedTabIndex = selectedExamTabIndex,
                    containerColor = Color.White,
                    contentColor = EmisBluePrimary,
                    indicator = { tabPositions ->
                        if (selectedExamTabIndex in tabPositions.indices) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedExamTabIndex]),
                                color = EmisBluePrimary,
                                height = 3.dp
                            )
                        }
                    }
                ) {
                    examTabOptions.forEach { examOption ->
                        val isSelected = selectedExamType == examOption
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.setMissingGradeExamType(examOption) },
                            text = {
                                Text(
                                    text = examOption.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.testTag("missing_exam_tab_${examOption.shortCode}")
                        )
                    }
                }
            }

            // 2. View Mode Tabs: "حسب الاستاذ(ة)" vs "حسب الصف"
            TabRow(
                selectedTabIndex = if (isMissingByTeacher) 0 else 1,
                containerColor = Color(0xFFF1F5F9),
                contentColor = EmisBluePrimary
            ) {
                Tab(
                    selected = isMissingByTeacher,
                    onClick = { viewModel.toggleMissingFilter(true) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حسب الاستاذ(ة)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    modifier = Modifier.testTag("missing_tab_teacher")
                )
                Tab(
                    selected = !isMissingByTeacher,
                    onClick = { viewModel.toggleMissingFilter(false) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Class, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حسب الصف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    modifier = Modifier.testTag("missing_tab_class")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (allMissing.isEmpty()) {
                // Empty state: All grades are entered
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmisSuccess,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "لا توجد علامات ناقصة!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "تم رصد علامات جميع المواد لـ ${selectedExamType.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF15803D),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (isMissingByTeacher) {
                // IMAGE 6: Group by Teacher, Teachers sorted alphabetically
                val groupedByTeacher = remember(allMissing) {
                    allMissing.groupBy { it.teacherName }
                }
                val sortedTeacherNames = remember(groupedByTeacher) {
                    groupedByTeacher.keys.sortedWith { a, b ->
                        arabicCollator.compare(a.trim(), b.trim())
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    items(sortedTeacherNames, key = { it }) { teacherName ->
                        val teacherItems = groupedByTeacher[teacherName] ?: emptyList()

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Top rounded header pill - Teacher Name
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MissingHeaderBlue
                            ) {
                                Text(
                                    text = teacherName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Table Card: المادة | الصف
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("missing_teacher_table_${teacherName.hashCode()}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, BorderGrey)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Header Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MissingHeaderBlue)
                                            .padding(vertical = 8.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "المادة",
                                            modifier = Modifier.weight(1.4f),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Start
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(18.dp)
                                                .background(Color.White.copy(alpha = 0.4f))
                                        )
                                        Text(
                                            text = "الصف",
                                            modifier = Modifier.weight(1f),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    // Content Rows
                                    teacherItems.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (index % 2 == 0) Color.White else Color(0xFFFAFAFA))
                                                .padding(vertical = 9.dp, horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Course Name
                                            Text(
                                                text = item.courseName,
                                                modifier = Modifier.weight(1.4f),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Normal,
                                                color = Color(0xFF1E293B),
                                                textAlign = TextAlign.Start
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(18.dp)
                                                    .background(RowDividerGrey)
                                            )
                                            // Class Name
                                            Text(
                                                text = item.className,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        if (index < teacherItems.size - 1) {
                                            HorizontalDivider(color = RowDividerGrey, thickness = 0.8.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            } else {
                // Group by Class, keeping the exact class order as it is in the sheet
                val groupedByClassIndex = remember(allMissing) {
                    allMissing.groupBy { it.classIndex }
                }
                val sortedClassIndices = remember(groupedByClassIndex, classBlocks) {
                    val blockOrderMap = classBlocks.mapIndexed { idx, block -> block.index to idx }.toMap()
                    groupedByClassIndex.keys.sortedBy { blockOrderMap[it] ?: it }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    items(sortedClassIndices, key = { it }) { classIndex ->
                        val classItems = groupedByClassIndex[classIndex] ?: emptyList()
                        val className = classItems.firstOrNull()?.className ?: "الصف $classIndex"
                        // "and the let teacher appear alphabetcly"
                        val sortedClassItems = remember(classItems) {
                            classItems.sortedWith { a, b ->
                                arabicCollator.compare(a.teacherName.trim(), b.teacherName.trim())
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Top rounded header pill - Class Name
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MissingHeaderBlue
                            ) {
                                Text(
                                    text = className,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Table Card: المادة | اسم الأستاذ
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("missing_class_table_${classIndex}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, BorderGrey)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Header Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MissingHeaderBlue)
                                            .padding(vertical = 8.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "المادة",
                                            modifier = Modifier.weight(1.3f),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Start
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(18.dp)
                                                .background(Color.White.copy(alpha = 0.4f))
                                        )
                                        Text(
                                            text = "اسم الأستاذ",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .padding(start = 8.dp)
                                        )
                                    }

                                    // Content Rows
                                    sortedClassItems.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (index % 2 == 0) Color.White else Color(0xFFFAFAFA))
                                                .padding(vertical = 9.dp, horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Course Name
                                            Text(
                                                text = item.courseName,
                                                modifier = Modifier.weight(1.3f),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Normal,
                                                color = Color(0xFF1E293B),
                                                textAlign = TextAlign.Start
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(18.dp)
                                                    .background(RowDividerGrey)
                                            )
                                            // Teacher Name in Blue
                                            Text(
                                                text = item.teacherName,
                                                modifier = Modifier
                                                    .weight(1.3f)
                                                    .padding(start = 8.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TeacherBlueText,
                                                textAlign = TextAlign.Start
                                            )
                                        }

                                        if (index < sortedClassItems.size - 1) {
                                            HorizontalDivider(color = RowDividerGrey, thickness = 0.8.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}
