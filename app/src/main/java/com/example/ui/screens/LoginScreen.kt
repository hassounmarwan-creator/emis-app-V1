package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmisBlueContainer
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.theme.EmisError
import com.example.ui.theme.EmisTextPrimary
import com.example.ui.theme.EmisTextSecondary
import com.example.viewmodel.EmisViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: EmisViewModel) {
    val isRefreshingTeachers by viewModel.isRefreshingTeachers.collectAsState()
    val teachersRefreshStatus by viewModel.teachersRefreshStatus.collectAsState()
    val rememberMe by viewModel.rememberMe.collectAsState()
    val savedCode = remember(rememberMe) { viewModel.getSavedTeacherCode() ?: "" }
    var teacherCode by remember { mutableStateOf(savedCode) }
    val loginError by viewModel.loginError.collectAsState()
    val sheetId by viewModel.sheetId.collectAsState()
    val schoolName by viewModel.schoolName.collectAsState()

    val masterSchools by viewModel.masterSchools.collectAsState()
    val isLoadingMasterSchools by viewModel.isLoadingMasterSchools.collectAsState()
    val masterSchoolsError by viewModel.masterSchoolsError.collectAsState()
    val masterSheetId by viewModel.masterSheetId.collectAsState()

    val activeSchools = remember(masterSchools) {
        masterSchools.filter { it.isActive }
    }
    val currentSelectedSchool = remember(activeSchools, sheetId) {
        activeSchools.find { it.sheetId == sheetId }
            ?: activeSchools.firstOrNull()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshMasterSchools()
    }

    val handleLogin = {
        if (teacherCode.isNotBlank()) {
            if (rememberMe) {
                viewModel.saveTeacherCode(teacherCode.trim())
            } else {
                viewModel.clearTeacherCode()
            }
            if (sheetId.isBlank()) {
                val firstActive = activeSchools.firstOrNull()
                if (firstActive != null) {
                    viewModel.selectMasterSchool(firstActive)
                }
            }
            viewModel.loginWithCode(teacherCode)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .testTag("login_card"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Circular Graduation Cap Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFFE2F0F7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "شعار المدرسة",
                    tint = Color(0xFF2C7DA0),
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Titles
            Text(
                text = "نظام إدارة المعلومات التربوية",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 21.sp,
                color = Color(0xFF014F86),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "EMIS",
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 30.sp,
                color = Color(0xFF2C7DA0),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "بوابة المعلمين والإدارة التربوية",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFECFDF5),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isRefreshingTeachers) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = EmisBluePrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "جاري تحديث رموز المعلمين من Google Sheets...",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmisBlueDark,
                            fontSize = 13.sp
                        )
                    } else {
                        Text(
                            text = teachersRefreshStatus ?: "تم تحديث رموز وأسماء المعلمين ✓",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Teacher Code Input Field (Lock on left, centered placeholder)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = teacherCode,
                    onValueChange = { teacherCode = it },
                    placeholder = {
                        Text(
                            text = "أدخل رمز الاستاذ(ة)",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF64748B),
                            fontSize = 16.sp
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        textDirection = TextDirection.Ltr,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmisTextPrimary
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF2C7DA0),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { handleLogin() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2C7DA0),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("teacher_code_input")
                )
            }

            if (loginError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = loginError ?: "",
                    color = EmisError,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Login Button
            Button(
                onClick = { handleLogin() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_submit_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C7DA0))
            ) {
                Text(
                    text = "تسجيل الدخول",
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // School Selection Card (Dynamic Dropdown from Master Sheet)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFEDF6F9),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("school_selection_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "المدرسة / المعهد الفني",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF014F86)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color(0xFF2C7DA0),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dropdown menu to select school (Displays Column B, takes Sheet ID from Column A)
                    var schoolDropdownExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = schoolDropdownExpanded,
                        onExpandedChange = { schoolDropdownExpanded = !schoolDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentSelectedSchool?.schoolName
                                ?: if (activeSchools.isNotEmpty()) activeSchools.first().schoolName
                                else if (isLoadingMasterSchools) "جاري تحميل قائمة المدارس..."
                                else if (masterSchoolsError != null) "تعذر جلب المدارس من Master"
                                else "اختر المدرسة أو المعهد",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = schoolDropdownExpanded)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocationCity,
                                    contentDescription = null,
                                    tint = Color(0xFF2C7DA0),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Start,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF014F86)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2C7DA0),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("school_dropdown_selector")
                        )

                        ExposedDropdownMenu(
                            expanded = schoolDropdownExpanded,
                            onDismissRequest = { schoolDropdownExpanded = false },
                            modifier = Modifier
                                .background(Color.White)
                                .testTag("school_dropdown_menu")
                        ) {
                            if (activeSchools.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isLoadingMasterSchools) "جاري تحميل قائمة المدارس..." else "لا توجد مدارس نشطة",
                                            color = Color(0xFF64748B),
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = { schoolDropdownExpanded = false }
                                )
                            } else {
                                activeSchools.forEach { school ->
                                    val isSelected = school.sheetId == currentSelectedSchool?.sheetId
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = school.schoolName,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color(0xFF014F86) else Color(0xFF1E293B),
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "تم الاختيار",
                                                        tint = Color(0xFF059669),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectMasterSchool(school)
                                            schoolDropdownExpanded = false
                                        },
                                        modifier = Modifier.testTag("school_option_${school.sheetId.take(8)}")
                                    )
                                }
                            }
                        }
                    }

                    if (masterSchoolsError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = masterSchoolsError ?: "",
                            color = EmisError,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "تُجلب قائمة المدارس المعتمدة النشطة تلقائياً من جدول Master",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Remember Me Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val newVal = !rememberMe
                        viewModel.updateRememberMe(newVal)
                        if (newVal && teacherCode.isNotBlank()) {
                            viewModel.saveTeacherCode(teacherCode.trim())
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { checked ->
                        viewModel.updateRememberMe(checked)
                        if (checked && teacherCode.isNotBlank()) {
                            viewModel.saveTeacherCode(teacherCode.trim())
                        }
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF2C7DA0),
                        uncheckedColor = Color(0xFF64748B)
                    ),
                    modifier = Modifier.testTag("remember_me_checkbox")
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "تذكرني (حفظ رمز الأستاذ والمدرسة المحددة)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.5.sp,
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

