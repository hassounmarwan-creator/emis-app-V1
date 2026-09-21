package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FullContractHoursRow
import com.example.model.Teacher
import com.example.model.isTeacherNameMatch
import com.example.model.normalizeTeacherName
import com.example.ui.components.EmisTopAppBar
import com.example.ui.theme.EmisBlueContainer
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.theme.EmisError
import com.example.ui.theme.EmisSuccess
import com.example.ui.theme.EmisWarning
import com.example.viewmodel.AppScreen
import com.example.viewmodel.EmisViewModel
import java.util.Calendar

data class MonthItem(
    val index: Int,
    val year: Int,
    val nameAr: String,
    val shortName: String,
    val daysCount: Int
)

val academicMonths = listOf(
    MonthItem(10, 2026, "تشرين الأول (أكتوبر)", "أكتوبر", 31),
    MonthItem(11, 2026, "تشرين الثاني (نوفمبر)", "نوفمبر", 30),
    MonthItem(12, 2026, "كانون الأول (ديسمبر)", "ديسمبر", 31),
    MonthItem(1, 2027, "كانون الثاني (يناير)", "يناير", 31),
    MonthItem(2, 2027, "شباط (فبراير)", "فبراير", 28),
    MonthItem(3, 2027, "آذار (مارس)", "مارس", 31),
    MonthItem(4, 2027, "نيسان (أبريل)", "أبريل", 30),
    MonthItem(5, 2027, "أيار (مايو)", "مايو", 31),
    MonthItem(6, 2027, "حزيران (يونيو)", "يونيو", 30)
)

data class WeekDayHeader(val ar: String, val en: String)

val weekDayHeaders = listOf(
    WeekDayHeader("الإثنين", "Mon"),
    WeekDayHeader("الثلاثاء", "Tue"),
    WeekDayHeader("الأربعاء", "Wed"),
    WeekDayHeader("الخميس", "Thu"),
    WeekDayHeader("الجمعة", "Fri"),
    WeekDayHeader("السبت", "Sat"),
    WeekDayHeader("الأحد", "Sun")
)

/**
 * Returns a list of day numbers (1..maxDays) prefixed with nulls so that the 1st day of
 * the month aligns under its Monday-based column (Monday = 0 ... Sunday = 6).
 */
fun getCalendarDaysForMonth(year: Int, monthIndex: Int): List<Int?> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, monthIndex - 1)
    cal.set(Calendar.DAY_OF_MONTH, 1)

    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    // Map Calendar.DAY_OF_WEEK to Monday-based offset (0..6)
    val mondayOffset = when (firstDayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }

    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = mutableListOf<Int?>()
    for (i in 0 until mondayOffset) {
        cells.add(null)
    }
    for (d in 1..maxDays) {
        cells.add(d)
    }
    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return cells
}

fun getDayOfWeekArabicName(year: Int, monthIndex: Int, day: Int): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, monthIndex - 1)
    cal.set(Calendar.DAY_OF_MONTH, day)
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "الإثنين"
        Calendar.TUESDAY -> "الثلاثاء"
        Calendar.WEDNESDAY -> "الأربعاء"
        Calendar.THURSDAY -> "الخميس"
        Calendar.FRIDAY -> "الجمعة"
        Calendar.SATURDAY -> "السبت"
        Calendar.SUNDAY -> "الأحد"
        else -> ""
    }
}

data class DayHours(
    val regular: Double = 0.0,
    val training: Double = 0.0
) {
    val total: Double get() = regular + training
    val hasHours: Boolean get() = total > 0.0
}

private fun formatHours(hours: Double): String {
    return if (hours % 1.0 == 0.0) "${hours.toInt()}" else "$hours"
}

@Composable
fun ContractHoursScreen(viewModel: EmisViewModel) {
    val teacher by viewModel.currentTeacher.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val contractHours by viewModel.contractHours.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // Discover all teachers appearing in contract hours (including those with only "تدريب")
    val teachersFromContract = remember(contractHours) {
        contractHours.mapNotNull { entry ->
            val baseName = entry.teacherName
                .replace("تدريب", "")
                .replace("(", "")
                .replace(")", "")
                .trim()
            if (baseName.isNotBlank()) {
                Teacher(
                    id = "t_ch_${kotlin.math.abs(baseName.hashCode())}",
                    name = baseName,
                    code = if (entry.teacherCode.isNotBlank()) entry.teacherCode else baseName,
                    isAdmin = false
                )
            } else null
        }.distinctBy { normalizeTeacherName(it.name) }
    }

    val allAvailableTeachers = remember(teachers, teachersFromContract) {
        val list = teachers.toMutableList()
        teachersFromContract.forEach { cTeacher ->
            if (list.none { isTeacherNameMatch(it.name, cTeacher.name) }) {
                list.add(cTeacher)
            }
        }
        list.filter { !it.isAdmin }
    }

    var adminSelectedTeacher by remember(allAvailableTeachers, teacher) {
        mutableStateOf(
            if (teacher?.isAdmin == true) allAvailableTeachers.firstOrNull() ?: teacher
            else teacher
        )
    }

    val fullContractHours by viewModel.fullContractHoursTable.collectAsState()
    var adminSelectedTab by remember { mutableStateOf(0) } // 0: كامل ساعات التعاقد (Sheet), 1: تقويم المعلم الفردي

    val effectiveTeacher = if (teacher?.isAdmin == true) (adminSelectedTeacher ?: teacher) else teacher
    val teacherCode = effectiveTeacher?.code ?: ""
    val teacherName = effectiveTeacher?.name ?: ""

    // Filter hours for effective teacher in the selected month
    val monthEntries = remember(contractHours, effectiveTeacher, selectedMonth) {
        contractHours.filter { entry ->
            val cleanName = entry.teacherName.replace("تدريب", "").replace("(", "").replace(")", "").trim()
            val matchesTeacher = (teacherCode.isNotBlank() && entry.teacherCode.equals(teacherCode, ignoreCase = true)) ||
                    (teacherName.isNotBlank() && isTeacherNameMatch(entry.teacherName, teacherName)) ||
                    (teacherName.isNotBlank() && cleanName.isNotBlank() && isTeacherNameMatch(cleanName, teacherName)) ||
                    (entry.teacherName.isNotBlank() && teacherCode.isNotBlank() && isTeacherNameMatch(entry.teacherName, teacherCode)) ||
                    (cleanName.isNotBlank() && teacherCode.isNotBlank() && isTeacherNameMatch(cleanName, teacherCode))
            matchesTeacher && entry.monthIndex == selectedMonth
        }.sortedBy { it.day }
    }

    val allTeacherEntries = remember(contractHours, effectiveTeacher) {
        contractHours.filter { entry ->
            val cleanName = entry.teacherName.replace("تدريب", "").replace("(", "").replace(")", "").trim()
            val matchesTeacher = (teacherCode.isNotBlank() && entry.teacherCode.equals(teacherCode, ignoreCase = true)) ||
                    (teacherName.isNotBlank() && isTeacherNameMatch(entry.teacherName, teacherName)) ||
                    (teacherName.isNotBlank() && cleanName.isNotBlank() && isTeacherNameMatch(cleanName, teacherName)) ||
                    (entry.teacherName.isNotBlank() && teacherCode.isNotBlank() && isTeacherNameMatch(entry.teacherName, teacherCode)) ||
                    (cleanName.isNotBlank() && teacherCode.isNotBlank() && isTeacherNameMatch(cleanName, teacherCode))
            matchesTeacher
        }
    }

    // Split hours calculations (Regular courses vs Training "تدريب")
    val monthRegularHours = remember(monthEntries) {
        monthEntries.filter { !it.isTraining && !it.notes.contains("تدريب") }.sumOf { it.hours }
    }
    val monthTrainingHours = remember(monthEntries) {
        monthEntries.filter { it.isTraining || it.notes.contains("تدريب") }.sumOf { it.hours }
    }
    val cumulativeRegularHours = remember(allTeacherEntries) {
        allTeacherEntries.filter { !it.isTraining && !it.notes.contains("تدريب") }.sumOf { it.hours }
    }
    val cumulativeTrainingHours = remember(allTeacherEntries) {
        allTeacherEntries.filter { it.isTraining || it.notes.contains("تدريب") }.sumOf { it.hours }
    }

    // Quotas from Column AG in Google Sheets
    val teacherQuota = viewModel.getQuotaForTeacher(effectiveTeacher)
    val regularQuota = teacherQuota?.regularHours
    val trainingQuota = teacherQuota?.trainingHours

    // Check if teacher has training
    val hasTraining = remember(cumulativeTrainingHours, trainingQuota, allTeacherEntries) {
        cumulativeTrainingHours > 0.0 ||
                (trainingQuota != null && trainingQuota > 0.0) ||
                allTeacherEntries.any { (it.isTraining || it.notes.contains("تدريب")) && it.hours > 0.0 }
    }

    // Check if teacher has regular courses
    val hasRegular = remember(cumulativeRegularHours, regularQuota, allTeacherEntries, hasTraining) {
        cumulativeRegularHours > 0.0 ||
                (regularQuota != null && regularQuota > 0.0) ||
                allTeacherEntries.any { !it.isTraining && !it.notes.contains("تدريب") && it.hours > 0.0 } ||
                !hasTraining // fallback: if teacher has no training, always show regular courses card
    }

    // Map of day number -> DayHours (regular and training)
    val hoursByDay: Map<Int, DayHours> = remember(monthEntries) {
        (1..31).associateWith { d ->
            val forDay = monthEntries.filter { it.day == d }
            val reg = forDay.filter { !it.isTraining && !it.notes.contains("تدريب") }.sumOf { it.hours }
            val tr = forDay.filter { it.isTraining || it.notes.contains("تدريب") }.sumOf { it.hours }
            DayHours(regular = reg, training = tr)
        }
    }

    val currentMonthObj = academicMonths.find { it.index == selectedMonth } ?: academicMonths.first()
    val calendarDays = remember(currentMonthObj.year, currentMonthObj.index) {
        getCalendarDaysForMonth(currentMonthObj.year, currentMonthObj.index)
    }

    Scaffold(
        topBar = {
            EmisTopAppBar(
                title = "ساعات التعاقد",
                subtitle = if (teacher?.isAdmin == true) "ورقة «كامل ساعات التعاقد»" else "الاستاذ(ة): ${effectiveTeacher?.name ?: ""}",
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
            if (teacher?.isAdmin == true) {
                // Admin directly and exclusively views "كامل ساعات التعاقد" (Image 3)
                FullContractHoursSheetView(rows = fullContractHours)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
            // Admin Teacher Selector (if admin is viewing, allows browsing any teacher including training-only)
            if (teacher?.isAdmin == true && allAvailableTeachers.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = EmisBluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "عرض جدول الأستاذ(ة): ${effectiveTeacher?.name ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            allAvailableTeachers.forEach { t ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = t.name,
                                            fontWeight = if (t.code == effectiveTeacher?.code) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        adminSelectedTeacher = t
                                        selectedDay = null
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Split Summary Cards (Row 1: Courses hours & Cumulative, Row 2: Training hours & Cumulative Training)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Regular courses hours (only if teacher has regular courses)
                if (hasRegular) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Right Card: Month regular hours
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "ساعات ${currentMonthObj.shortName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E40AF)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatHours(monthRegularHours),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E40AF)
                                )
                            }
                        }

                        // Left Card: Cumulative regular hours / quota
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = EmisBluePrimary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "الساعات التراكمية",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (regularQuota != null && regularQuota > 0) {
                                        "${formatHours(cumulativeRegularHours)} / ${formatHours(regularQuota)}"
                                    } else {
                                        formatHours(cumulativeRegularHours)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Row 2: Training ("تدريب") hours (only if teacher has training)
                if (hasTraining) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Right Card: Month training hours
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "ساعات تدريب ${currentMonthObj.shortName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E40AF)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatHours(monthTrainingHours),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E40AF)
                                )
                            }
                        }

                        // Left Card: Cumulative training hours / quota
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = EmisBluePrimary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "الساعات التراكمية تدريب",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (trainingQuota != null && trainingQuota > 0) {
                                        "${formatHours(cumulativeTrainingHours)} / ${formatHours(trainingQuota)}"
                                    } else {
                                        formatHours(cumulativeTrainingHours)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Quick Month Selector Chips (October till June) - cleanly placed above calendar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    academicMonths.forEach { m ->
                        val isSelected = m.index == selectedMonth
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) EmisBluePrimary else Color(0xFFF1F5F9),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) EmisBluePrimary else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .clickable {
                                    viewModel.setSelectedMonth(m.index)
                                    selectedDay = null
                                }
                                .testTag("month_chip_${m.index}")
                        ) {
                            Text(
                                text = m.shortName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // CALENDAR CARD (Week starts on Monday)
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // LTR Layout for Calendar Grid so Monday is Column 1 (Mon..Sun)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Weekday Headers: Monday (left) to Sunday (right)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                weekDayHeaders.forEach { header ->
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = header.ar,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = EmisBlueDark,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = header.en,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Weeks Grid
                            val weeks = calendarDays.chunked(7)
                            weeks.forEach { week ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    week.forEach { dayNumber ->
                                        if (dayNumber == null) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(72.dp)
                                            )
                                        } else {
                                            val dayHours = hoursByDay[dayNumber] ?: DayHours()
                                            val hasHours = dayHours.hasHours
                                            val isSelected = selectedDay == dayNumber

                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(72.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        selectedDay = if (selectedDay == dayNumber) null else dayNumber
                                                    }
                                                    .testTag("cal_day_$dayNumber"),
                                                shape = RoundedCornerShape(8.dp),
                                                color = when {
                                                    isSelected -> EmisBluePrimary.copy(alpha = 0.15f)
                                                    hasHours -> Color(0xFFEFF6FF)
                                                    else -> MaterialTheme.colorScheme.surface
                                                },
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else if (hasHours) 1.5.dp else 1.dp,
                                                    color = when {
                                                        isSelected -> EmisBluePrimary
                                                        hasHours -> Color(0xFF3B82F6)
                                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                                    }
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 1.dp, vertical = 3.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Top
                                                ) {
                                                    Text(
                                                        text = "$dayNumber",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (hasHours || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (hasHours) EmisBlueDark else MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 11.sp
                                                    )

                                                    if (hasHours) {
                                                        Spacer(modifier = Modifier.height(3.dp))

                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                                        ) {
                                                            // Slot 1: ALWAYS UP (Regular courses hours)
                                                            if (dayHours.regular > 0.0) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .defaultMinSize(minWidth = 18.dp)
                                                                        .height(15.dp)
                                                                        .background(EmisBluePrimary, RoundedCornerShape(3.dp))
                                                                        .padding(horizontal = 3.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = formatHours(dayHours.regular),
                                                                        color = Color.White,
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        textAlign = TextAlign.Center,
                                                                        lineHeight = 11.sp
                                                                    )
                                                                }
                                                            } else if (dayHours.training > 0.0) {
                                                                // Empty upper slot so training is guaranteed to stay DOWN
                                                                Spacer(modifier = Modifier.height(15.dp))
                                                            }

                                                            // Slot 2: ALWAYS DOWN (Training "تدريب" hours)
                                                            if (dayHours.training > 0.0) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .defaultMinSize(minWidth = 18.dp)
                                                                        .height(15.dp)
                                                                        .background(Color(0xFFD97706), RoundedCornerShape(3.dp))
                                                                        .padding(horizontal = 3.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = formatHours(dayHours.training),
                                                                        color = Color.White,
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        textAlign = TextAlign.Center,
                                                                        lineHeight = 11.sp
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selected Day Details Card (Read-only)
            if (selectedDay != null) {
                val day = selectedDay!!
                val dayHours = hoursByDay[day] ?: DayHours()
                val dayName = getDayOfWeekArabicName(currentMonthObj.year, currentMonthObj.index, day)
                val dayEntries = monthEntries.filter { it.day == day }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (dayHours.hasHours) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)),
                    border = BorderStroke(1.5.dp, if (dayHours.hasHours) EmisSuccess else Color(0xFFCBD5E1))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (dayHours.hasHours) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (dayHours.hasHours) EmisSuccess else Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "يوم $dayName، $day ${currentMonthObj.nameAr}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmisBlueDark
                                    )
                                    if (dayHours.hasHours) {
                                        Text(
                                            text = if (dayHours.regular > 0.0 && dayHours.training > 0.0) {
                                                "ساعات التعاقد: ${formatHours(dayHours.regular)} | ساعات التدريب: ${formatHours(dayHours.training)}"
                                            } else if (dayHours.training > 0.0) {
                                                "ساعات التدريب: ${formatHours(dayHours.training)}"
                                            } else {
                                                "ساعات التدريس: ${formatHours(dayHours.regular)}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EmisSuccess
                                        )
                                    } else {
                                        Text(
                                            text = "لا توجد ساعات مسجلة في هذا اليوم في جدول التعاقد",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (dayHours.hasHours) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = EmisBluePrimary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = formatHours(dayHours.total),
                                        color = EmisBluePrimary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (dayEntries.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = Color(0xFFDCFCE7), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            dayEntries.forEach { entry ->
                                val isTr = entry.isTraining || entry.notes.contains("تدريب")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = if (isTr) Color(0xFFD97706) else EmisBluePrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = entry.notes.ifBlank { if (isTr) "ساعات تدريب" else "ساعات تدريس تعاقد" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${formatHours(entry.hours)} ساعة",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTr) Color(0xFFD97706) else EmisBluePrimary
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

/**
 * Display for the Google Sheets sheet called "كامل ساعات التعاقد":
 * - Row 2: Columns B to J contains months (10, 11, 12, 1, 2, 3, 4, 5, 6)
 * - Rows 3 to 49: The teachers
 * - Column K: Original contract hours (original quota)
 * - Column L: Remaining hours (Column K minus sum of months B to J)
 */
@Composable
fun FullContractHoursSheetView(
    rows: List<FullContractHoursRow>
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredRows = remember(rows, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) rows
        else {
            rows.filter {
                it.teacherName.contains(q, ignoreCase = true) ||
                it.rowIndex.toString().contains(q)
            }
        }
    }

    val defaultMonths = listOf(10, 11, 12, 1, 2, 3, 4, 5, 6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Image 3 header: "كامل ساعات التعاقد" with icon and underline
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = EmisBluePrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "كامل ساعات التعاقد",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmisBluePrimary
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(EmisBluePrimary)
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("بحث باسم الأستاذ(ة) أو رقم السطر...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        // Spreadsheet Table Card with Freeze Panes (pinned header vertically, pinned teacher column horizontally)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            if (filteredRows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة لبحثك" else "لا توجد بيانات مسجلة في شيت كامل ساعات التعاقد",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val horizontalScroll = rememberScrollState()
                val verticalScroll = rememberScrollState()

                Row(modifier = Modifier.fillMaxSize()) {
                    // ==================== 1. FROZEN COLUMN: # & Teacher Name (Pinned horizontally) ====================
                    Column(
                        modifier = Modifier.width(190.dp)
                    ) {
                        // Top-left frozen header (Row # & Teacher Name) - Fixed vertically & horizontally
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(EmisBluePrimary)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableHeaderCell(text = "#", width = 36.dp)
                            TableHeaderCell(text = "اسم الأستاذ(ة)", width = 142.dp, textAlign = TextAlign.Start)
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        // Scrollable rows for Teacher names (scrolls vertically with verticalScroll)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScroll)
                        ) {
                            filteredRows.forEachIndexed { idx, rowItem ->
                                val isEven = idx % 2 == 0
                                val rowBg = if (isEven) Color.White else Color(0xFFF8FAFC)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .background(rowBg)
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TableCell(
                                        text = "${rowItem.rowIndex}",
                                        width = 36.dp,
                                        fontWeight = FontWeight.Bold,
                                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TableCell(
                                        text = rowItem.teacherName,
                                        width = 142.dp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Start,
                                        textColor = EmisBlueDark
                                    )
                                }
                                Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                            }
                        }
                    }

                    // Vertical Divider separating frozen column from scrollable months
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )

                    // ==================== 2. HORIZONTALLY SCROLLABLE MONTHS & TOTALS ====================
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScroll)
                    ) {
                        // Top-right frozen header for Months (Scrolls horizontally, FROZEN vertically at top!)
                        Row(
                            modifier = Modifier
                                .height(48.dp)
                                .background(EmisBluePrimary)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            defaultMonths.forEach { m ->
                                TableHeaderCell(text = "$m", width = 50.dp)
                            }
                            TableHeaderCell(text = "المنجز", width = 64.dp)
                            TableHeaderCell(text = "الأصل (K)", width = 74.dp)
                            TableHeaderCell(text = "المتبقي (L)", width = 76.dp)
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        // Scrollable months data rows (Scrolls vertically with the SAME verticalScroll!)
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(verticalScroll)
                        ) {
                            filteredRows.forEachIndexed { idx, rowItem ->
                                val isEven = idx % 2 == 0
                                val rowBg = if (isEven) Color.White else Color(0xFFF8FAFC)

                                Row(
                                    modifier = Modifier
                                        .height(46.dp)
                                        .background(rowBg)
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Months 10, 11, 12, 1, 2, 3, 4, 5, 6
                                    defaultMonths.forEach { m ->
                                        val h = rowItem.monthlyHours[m] ?: 0.0
                                        TableCell(
                                            text = if (h > 0.0) formatHours(h) else "-",
                                            width = 50.dp,
                                            textColor = if (h > 0.0) Color(0xFF1E293B) else Color(0xFF94A3B8)
                                        )
                                    }
                                    // Total Done (Sum B..J)
                                    TableCell(
                                        text = formatHours(rowItem.totalDone),
                                        width = 64.dp,
                                        fontWeight = FontWeight.Bold,
                                        textColor = EmisBluePrimary
                                    )
                                    // Original Quota (Col K)
                                    TableCell(
                                        text = formatHours(rowItem.originalQuota),
                                        width = 74.dp,
                                        fontWeight = FontWeight.Bold,
                                        textColor = Color(0xFF334155)
                                    )
                                    // Remaining (Col L = K - sum(B..J))
                                    val rem = rowItem.remainingHours
                                    val remColor = when {
                                        rem < 0.0 -> EmisError
                                        rem == 0.0 -> EmisSuccess
                                        else -> Color(0xFF0369A1)
                                    }
                                    TableCell(
                                        text = formatHours(rem),
                                        width = 76.dp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textColor = remColor
                                    )
                                }
                                Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(
    title: String,
    value: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    textAlign: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = textAlign
    )
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Center,
    textColor: Color = Color.Unspecified
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = fontWeight,
        color = textColor,
        textAlign = textAlign,
        maxLines = 1
    )
}
