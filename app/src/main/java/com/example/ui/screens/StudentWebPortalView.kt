package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import com.example.ui.util.PdfReportGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Course
import com.example.model.ExamType
import com.example.model.ReportStage
import com.example.model.StudentExamPortalResult
import com.example.model.StudentPortalReport
import com.example.model.StudentReportRow
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBluePrimary
import com.example.viewmodel.AppScreen
import com.example.viewmodel.EmisViewModel

/**
 * Web Page Design for Student Portal:
 * Opened directly or after QR code scanning without any Google Account prompt.
 * Linked directly to Google Sheets (sheet "الإعدادات (التلاميذ)").
 * Multiple students can enter their credentials independently and see their own results.
 *
 * Displays the exact official report card ("بطاقة العلامات الرسمية") just as the school admin sees it.
 */
@Composable
fun StudentWebPortalView(
    viewModel: EmisViewModel,
    onBack: () -> Unit = { viewModel.navigateTo(AppScreen.STUDENT_PORTAL_QR) },
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val sheetId by viewModel.sheetId.collectAsState()
    val studentReport by viewModel.studentPortalReport.collectAsState()
    val queryError by viewModel.studentPortalError.collectAsState()
    val isQuerying by viewModel.isQueryingStudentPortal.collectAsState()
    val schoolName by viewModel.schoolName.collectAsState()
    val schoolYear by viewModel.schoolYear.collectAsState()
    val deadlines by viewModel.deadlines.collectAsState()

    LaunchedEffect(sheetId) {
        if (sheetId.isNotBlank()) {
            viewModel.refreshSchoolSettings()
        }
    }

    var phoneInput by remember { mutableStateOf("") }
    var dobInput by remember { mutableStateOf("") }
    var selectedStage by remember { mutableStateOf(ReportStage.AUTO) }

    val c1HasDueDate = deadlines.c1Date.isNotBlank()
    val c1Passed = !c1HasDueDate || viewModel.isDatePassed(deadlines.c1Date)
    val e1Passed = viewModel.isDatePassed(deadlines.e1Date)
    val c2Passed = viewModel.isDatePassed(deadlines.c2Date)
    val e2Passed = viewModel.isDatePassed(deadlines.e2Date)

    fun isStudentStageEnabled(st: ReportStage): Boolean {
        return when (st) {
            ReportStage.C1_ONLY -> c1Passed
            ReportStage.C1_E1 -> e1Passed
            ReportStage.C2_ONLY -> c2Passed
            ReportStage.ALL -> e2Passed
            ReportStage.AUTO -> true
        }
    }

    fun getStudentStageDisabledReason(st: ReportStage): String {
        return when (st) {
            ReportStage.C1_ONLY -> "غير متاح حتى انقضاء موعد السعي الأول (${deadlines.c1Date})"
            ReportStage.C1_E1 -> "غير متاح حتى انقضاء موعد الامتحان الأول (${deadlines.e1Date})"
            ReportStage.C2_ONLY -> "غير متاح حتى انقضاء موعد السعي الثاني (${deadlines.c2Date})"
            ReportStage.ALL -> "غير متاح حتى انقضاء موعد الامتحان الثاني (${deadlines.e2Date})"
            ReportStage.AUTO -> ""
        }
    }

    val primaryNavy = Color(0xFF1E3A8A)
    val lightBlueBg = Color(0xFFF0F7FF)
    val borderGray = Color(0xFFE2E8F0)
    val pageBg = Color(0xFFF8FAFC)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pageBg)
            .verticalScroll(scrollState)
    ) {
        // --- Web Page Browser Mockup Bar ---
        Surface(
            color = Color(0xFF0F172A),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Window controls dots (Web Page design mockup)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                }

                // Simulated URL Bar
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (studentReport != null) "portal.emis-school.edu.lb/student/report-card" else "portal.emis-school.edu.lb/student/results",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )
                    }
                }

                // Back / Close action
                IconButton(
                    onClick = {
                        if (studentReport != null) {
                            viewModel.clearStudentPortalReport()
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.size(28.dp).testTag("web_portal_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // --- Web Page Header Banner ---
        Surface(
            color = primaryNavy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "الجمهورية اللبنانية - وزارة التربية والتعليم العالي",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = if (studentReport != null) "بطاقة العلامات المدرسية الرسمية" else "بوابة كشف علامات الطلاب الرسمية",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                val currentSchoolDisplay = studentReport?.schoolName?.ifBlank { schoolName } ?: schoolName

                if (currentSchoolDisplay.isNotBlank()) {
                    Text(
                        text = if (studentReport != null) "$currentSchoolDisplay - المديرية العامة للتعليم المهني والتقني"
                        else currentSchoolDisplay,
                        color = Color(0xFFBAE6FD),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // --- Main Web Content Container ---
        if (studentReport == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            // Web Card containing the Form
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_web_portal_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderGray)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card Title & Instruction
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "استعلام عن بطاقة العلامات الرسمية",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryNavy
                        )
                        Text(
                            text = "أدخل رقم هاتفك وتاريخ ميلادك المسجل لعرض بطاقة كشف الدرجات الرسمية فوراً:",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    if (c1HasDueDate && !c1Passed) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.fillMaxWidth().testTag("c1_not_passed_banner")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "لم يصدر اي تقرير حتى الان",
                                    color = Color(0xFF92400E),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "موعد صدور أول تقرير (السعي الأول): ${deadlines.c1Date}",
                                    color = Color(0xFFB45309),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Field 1: Label "رقم الهاتف" underneath it the input field
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "رقم الهاتف",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "*",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            placeholder = { Text("أدخل رقم هاتفك (6 أرقام)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = primaryNavy
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryNavy,
                                unfocusedBorderColor = borderGray,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFFAFAFA)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_web_phone_input")
                        )
                    }

                    // Field 2: Label "تاريخ الميلاد" underneath it the input field
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "تاريخ الميلاد",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "*",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        OutlinedTextField(
                            value = dobInput,
                            onValueChange = { newVal ->
                                dobInput = if (newVal.length < dobInput.length) {
                                    newVal
                                } else if (newVal.contains("/") || newVal.contains("-")) {
                                    newVal
                                } else {
                                    val digits = newVal.filter { it.isDigit() }.take(8)
                                    when {
                                        digits.length > 4 -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4)}"
                                        digits.length > 2 -> "${digits.substring(0, 2)}/${digits.substring(2)}"
                                        else -> digits
                                    }
                                }
                            },
                            placeholder = { Text("DD/MM/YYYY (مثال: 12/04/2002)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = primaryNavy
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (phoneInput.isNotBlank() && dobInput.isNotBlank()) {
                                        viewModel.queryStudentPortal(phoneInput, dobInput)
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryNavy,
                                unfocusedBorderColor = borderGray,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFFAFAFA)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_web_dob_input")
                        )
                    }

                    // Quick Sample Buttons (Convenience for testing)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                phoneInput = "196593"
                                dobInput = "12/4/2002"
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("sample_student_button")
                        ) {
                            Text("عينة: ايلي دوميط", fontSize = 11.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                phoneInput = ""
                                dobInput = ""
                                viewModel.clearStudentPortalReport()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("clear_inputs_button")
                        ) {
                            Text("مسح الخانات", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    // Exam Stage Selection
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderGray),
                        modifier = Modifier.fillMaxWidth().testTag("student_form_stage_selector")
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "نوع الامتحان المطلوب:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = if (selectedStage == ReportStage.AUTO) "حسب التاريخ (تلقائي)" else selectedStage.cardTitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryNavy
                                )
                            }

                            val stages = listOf(
                                Triple(ReportStage.AUTO, "حسب التاريخ\n(تلقائي)", "stage_auto"),
                                Triple(ReportStage.C1_ONLY, "السعي الأول", "stage_c1"),
                                Triple(ReportStage.C1_E1, "الفصل الأول", "stage_c1_e1"),
                                Triple(ReportStage.C2_ONLY, "السعي الثاني", "stage_c2"),
                                Triple(ReportStage.ALL, "التقرير السنوي", "stage_all")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                stages.forEach { (st, label, tag) ->
                                    val isSelected = selectedStage == st
                                    val isEnabled = isStudentStageEnabled(st)

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag(tag)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (isEnabled) {
                                                    selectedStage = st
                                                } else {
                                                    Toast.makeText(context, getStudentStageDisabledReason(st), Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        color = when {
                                            isSelected -> primaryNavy
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
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp, horizontal = 2.dp),
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
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }
                                            Text(
                                                text = label,
                                                fontSize = 9.sp,
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

                    // Field 3: Submit Button underneath
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.queryStudentPortal(phoneInput, dobInput)
                        },
                        enabled = !isQuerying && phoneInput.isNotBlank() && dobInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryNavy,
                            disabledContainerColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("student_web_submit_button")
                    ) {
                        if (isQuerying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "جاري التحقق من السجلات والدرجات...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "عرض كشف العلامات الرسمي ❯",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Error Notice Box
                    if (queryError != null) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth().testTag("student_web_error_alert")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = queryError ?: "",
                                    color = Color(0xFF991B1B),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Web Page Footer Notice
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderGray),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔒 استعلام آمن ومباشر دون حساب Google",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryNavy
                    )
                    Text(
                        text = "يتم استخراج البيانات الرسمية مباشرة للطالب فقط، وتظهر البطاقة الرسمية المعتمدة مطابقة تماماً لما تراه إدارة المعهد.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    } else {
        // ================= PAGE 2: OPEN OFFICIAL REPORT CARD DIALOG (IMAGE 4) =================
        studentReport?.let { report ->
            val (reportRow, courses) = viewModel.getStudentReportRowForPortal(report, selectedStage)
            val effectiveSchoolName = report.schoolName.ifBlank { schoolName }
            val effectiveSchoolYear = report.schoolYear.ifBlank { schoolYear.ifBlank { "2025-2026" } }

            OfficialReportCardDialog(
                row = reportRow,
                courses = courses,
                className = report.className.ifBlank { reportRow.student.className },
                schoolName = effectiveSchoolName,
                schoolYear = effectiveSchoolYear,
                onDismiss = { viewModel.clearStudentPortalReport() },
                onRefresh = { viewModel.queryStudentPortal(phoneInput, dobInput) },
                isSyncing = isQuerying,
                selectedStage = selectedStage,
                onStageSelected = { newStage -> selectedStage = newStage },
                isStageEnabled = { st -> isStudentStageEnabled(st) },
                stageDisabledMessage = { st -> getStudentStageDisabledReason(st) },
                showStageSelector = true
            )
        }
    }
}
}

/**
 * Renders the Official Lebanese Technical School Report Card exactly as the admin sees it,
 * 100% identical copy (حسب التاريخ) with zero deviations or flipping.
 */
@Composable
private fun StudentOfficialReportCardView(
    report: StudentPortalReport,
    reportRow: StudentReportRow,
    courses: List<Course>,
    schoolName: String,
    schoolYear: String,
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val primaryNavy = Color(0xFF1E3A8A)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Official Certificate Paper - 100% EXACT COPY OF ADMIN REPORT CARD
        OfficialCertificatePaper(
            row = reportRow,
            courses = courses,
            className = report.className.ifBlank { reportRow.student.className },
            schoolName = schoolName,
            schoolYear = schoolYear
        )

        // Action Buttons (Print / Share PDF)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    PdfReportGenerator.generateAndSharePdf(
                        context = context,
                        row = reportRow,
                        courses = courses,
                        className = report.className.ifBlank { reportRow.student.className },
                        schoolName = schoolName,
                        schoolYear = schoolYear
                    )
                    onShare()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("share_official_report_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مشاركة / طباعة البطاقة الرسمية (PDF)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

