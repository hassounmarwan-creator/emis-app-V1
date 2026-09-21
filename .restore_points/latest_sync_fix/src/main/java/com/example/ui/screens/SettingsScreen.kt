package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SchoolRepository
import com.example.ui.components.EmisTopAppBar
import com.example.ui.theme.EmisBlueContainer
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.theme.EmisError
import com.example.ui.theme.EmisSuccess
import com.example.viewmodel.AppScreen
import com.example.viewmodel.EmisViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: EmisViewModel) {
    val sheetId by viewModel.sheetId.collectAsState()
    val schoolName by viewModel.schoolName.collectAsState()
    val webAppUrl by viewModel.webAppUrl.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    val masterSchools by viewModel.masterSchools.collectAsState()
    val isLoadingMasterSchools by viewModel.isLoadingMasterSchools.collectAsState()
    val activeSchools = remember(masterSchools) { masterSchools.filter { it.isActive } }
    val selectedSchool = remember(activeSchools, sheetId) {
        activeSchools.find { it.sheetId == sheetId }
            ?: activeSchools.firstOrNull()
    }

    var inputSheetId by remember(sheetId) { mutableStateOf(sheetId) }
    var inputWebAppUrl by remember(webAppUrl) { mutableStateOf(webAppUrl) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var showScriptDialog by remember { mutableStateOf(false) }
    var copyStatus by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        topBar = {
            EmisTopAppBar(
                title = "إعدادات الربط والنظام",
                subtitle = "ربط جداول بيانات Google Sheets",
                onBack = {
                    if (viewModel.currentTeacher.value != null) {
                        viewModel.navigateTo(AppScreen.DASHBOARD)
                    } else {
                        viewModel.navigateTo(AppScreen.LOGIN)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // School & Google Sheets Connection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = EmisBluePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "المدرسة المعتمدة (Master Sheet)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmisBlueDark
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refreshMasterSchools() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            if (isLoadingMasterSchools) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = EmisBluePrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "تحديث قائمة المدارس",
                                    tint = EmisBluePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "اختر المدرسة أو المعهد من القائمة المعتمدة في جدول Master:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // School Dropdown (Displays Column B, takes Sheet ID from Column A)
                    var schoolDropdownExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = schoolDropdownExpanded,
                        onExpandedChange = { schoolDropdownExpanded = !schoolDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedSchool?.schoolName ?: schoolName.ifBlank { "اختر المدرسة أو المعهد" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("المدرسة / المعهد") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = schoolDropdownExpanded)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocationCity,
                                    contentDescription = null,
                                    tint = EmisBluePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("settings_school_dropdown")
                        )

                        ExposedDropdownMenu(
                            expanded = schoolDropdownExpanded,
                            onDismissRequest = { schoolDropdownExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            if (activeSchools.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isLoadingMasterSchools) "جاري تحميل قائمة المدارس..." else "لا توجد مدارس نشطة",
                                            color = Color.Gray,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = { schoolDropdownExpanded = false }
                                )
                            } else {
                                activeSchools.forEach { school ->
                                    val isSelected = school.sheetId == selectedSchool?.sheetId
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
                                                    color = if (isSelected) EmisBluePrimary else Color.Unspecified
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = EmisSuccess,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectMasterSchool(school)
                                            inputSheetId = school.sheetId
                                            schoolDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputSheetId,
                        onValueChange = { inputSheetId = it },
                        label = { Text("معرف أو رابط Google Sheet") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_id_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.updateSheetId(inputSheetId) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmisBluePrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_sheet_id_btn")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ وتحديث")
                        }

                        OutlinedButton(
                            onClick = {
                                inputSheetId = ""
                                viewModel.updateSheetId("")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("مسح المعرّف")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sync button
                    Button(
                        onClick = { viewModel.syncWithGoogleSheets() },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = EmisBlueDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_sync_btn")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري المزامنة...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("مزامنة البيانات الآن من السحابة")
                        }
                    }

                    if (syncMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = syncMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (syncMessage?.contains("نجاح") == true) EmisSuccess else EmisBlueDark
                        )
                    }
                }
            }

            // Live Web App Sync & Write-Back Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = EmisBluePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "المزامنة الحية والكتابة على Google Sheets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmisBlueDark
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "لكي يتمكن المعلمون من حفظ العلامات مباشرة داخل ملف Google Sheets، يتم ربط تطبيق Google Apps Script Web App المخصص للمدرسة:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputWebAppUrl,
                        onValueChange = { inputWebAppUrl = it },
                        label = { Text("رابط تطبيق الويب (Apps Script Web App URL)") },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("web_app_url_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.updateWebAppUrl(inputWebAppUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmisBluePrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_web_app_url_btn")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ الرابط")
                        }

                        OutlinedButton(
                            onClick = {
                                isTestingConnection = true
                                testResult = null
                                coroutineScope.launch {
                                    val res = viewModel.testWebAppConnection()
                                    isTestingConnection = false
                                    testResult = if (res.isSuccess) {
                                        " الاتصال ناجح ومستعد للكتابة على الجدول!"
                                    } else {
                                        "❌ فشل الاتصال: ${res.exceptionOrNull()?.message ?: "تأكد من الرابط والأذونات"}"
                                    }
                                }
                            },
                            enabled = !isTestingConnection && inputWebAppUrl.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_web_app_btn")
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("فحص الاتصال")
                            }
                        }
                    }

                    if (testResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = testResult ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (testResult?.contains("نجاح") == true) EmisSuccess else EmisError,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Button to view & copy the Apps Script code
                    OutlinedButton(
                        onClick = { showScriptDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("view_apps_script_code_btn")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("عرض ونسخ كود Apps Script للجدول")
                    }
                }
            }

            // Card 3: Student Portal QR Code
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_portal_settings_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = EmisBlueContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = EmisBluePrimary)
                        Text(
                            text = "بوابة نتائج الطلاب (QR Code)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmisBlueDark
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "إنشاء وإدارة رمز QR لصفحة استعلام درجات الطلاب المربوطة بجدول Google Sheet مباشرة (دون الحاجة لتثبيت التطبيق).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.navigateTo(AppScreen.STUDENT_PORTAL_QR) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmisBluePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_student_portal_btn")
                    ) {
                        Text("فتح شاشة رمز QR وبوابة نتائج الطلاب")
                    }
                }
            }

            if (showScriptDialog) {
                val scriptCode = viewModel.getGoogleAppsScriptCode()
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showScriptDialog = false; copyStatus = false },
                    title = {
                        Text(
                            text = "كود Google Apps Script المعتمد",
                            fontWeight = FontWeight.Bold,
                            color = EmisBlueDark
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "خطوات التفعيل في دقيقة واحدة:\n" +
                                        "1. افتح جدول Google Sheets الخاص بك.\n" +
                                        "2. من القائمة العلوية اضغط Extensions ثم Apps Script.\n" +
                                        "3. احذف الكود الموجود والصق هذا الكود واضغط حفظ (Save).\n" +
                                        "4. اضغط Deploy ثم New deployment، اختر Web app.\n" +
                                        "5. اضبط 'Who has access' على 'Anyone'.\n" +
                                        "6. انسخ الرابط الناتج والصقه في التطبيق أعلاه.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = scriptCode,
                                    color = Color(0xFFF1F5F9),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier.padding(10.dp),
                                    fontSize = 11.sp
                                )
                            }
                            if (copyStatus) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "✓ تم نسخ الكود إلى الحافظة بنجاح!",
                                    color = EmisSuccess,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(scriptCode))
                                copyStatus = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmisBluePrimary)
                        ) {
                            Text("نسخ الكود")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showScriptDialog = false; copyStatus = false }) {
                            Text("إغلاق")
                        }
                    }
                )
            }
        }
    }
}
