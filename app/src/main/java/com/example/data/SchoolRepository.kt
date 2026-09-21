package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ClassBlock
import com.example.model.ContractHourEntry
import com.example.model.Course
import com.example.model.ExamType
import com.example.model.FullContractHoursRow
import com.example.model.MasterSchool
import com.example.model.MissingGradeItem
import com.example.model.ReportStage
import com.example.model.SheetDeadlines
import com.example.model.Student
import com.example.model.StudentPortalReport
import com.example.model.StudentReportRow
import com.example.model.Teacher
import com.example.model.TeacherContractQuota
import com.example.model.UploadedExamFile
import com.example.model.isTeacherNameMatch
import com.example.model.normalizeTeacherName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SchoolRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("emis_prefs", Context.MODE_PRIVATE)

    private val sheetsService = GoogleSheetsService()

    private val _sheetId = MutableStateFlow(
        prefs.getString(KEY_SHEET_ID, "")?.trim() ?: ""
    )
    val sheetId: StateFlow<String> = _sheetId.asStateFlow()

    private val _teachers = MutableStateFlow<List<Teacher>>(emptyList())
    val teachers: StateFlow<List<Teacher>> = _teachers.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _examCourses = MutableStateFlow<Map<ExamType, List<Course>>>(emptyMap())
    val examCourses: StateFlow<Map<ExamType, List<Course>>> = _examCourses.asStateFlow()

    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    private val _examStudents = MutableStateFlow<Map<ExamType, List<Student>>>(emptyMap())
    val examStudents: StateFlow<Map<ExamType, List<Student>>> = _examStudents.asStateFlow()

    fun getCoursesForExam(examType: ExamType): List<Course> {
        val specific = _examCourses.value[examType]
        val raw = if (!specific.isNullOrEmpty()) specific else _courses.value
        return raw.filter { !(it.name.startsWith("مادة عمود") && (it.teacherName == "أستاذ المادة" || it.teacherName.isBlank())) }
    }

    fun getStudentsForExam(examType: ExamType): List<Student> {
        val specific = _examStudents.value[examType]
        if (!specific.isNullOrEmpty()) return specific
        return _students.value
    }

    private val _grades = MutableStateFlow<Map<String, Double>>(emptyMap())
    val grades: StateFlow<Map<String, Double>> = _grades.asStateFlow()

    private val _deadlines = MutableStateFlow(SheetDeadlines())
    val deadlines: StateFlow<SheetDeadlines> = _deadlines.asStateFlow()

    private val _contractHours = MutableStateFlow<List<ContractHourEntry>>(emptyList())
    val contractHours: StateFlow<List<ContractHourEntry>> = _contractHours.asStateFlow()

    private val _contractQuotas = MutableStateFlow<Map<String, TeacherContractQuota>>(emptyMap())
    val contractQuotas: StateFlow<Map<String, TeacherContractQuota>> = _contractQuotas.asStateFlow()

    private val _fullContractHoursTable = MutableStateFlow<List<FullContractHoursRow>>(emptyList())
    val fullContractHoursTable: StateFlow<List<FullContractHoursRow>> = _fullContractHoursTable.asStateFlow()

    private val _uploadedExams = MutableStateFlow<List<UploadedExamFile>>(emptyList())
    val uploadedExams: StateFlow<List<UploadedExamFile>> = _uploadedExams.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _webAppUrl = MutableStateFlow(
        prefs.getString(KEY_WEB_APP_URL, "") ?: ""
    )
    val webAppUrl: StateFlow<String> = _webAppUrl.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _syncProgressPercentage = MutableStateFlow(0)
    val syncProgressPercentage: StateFlow<Int> = _syncProgressPercentage.asStateFlow()

    // Reference evaluation date (current date or customizable for testing deadlines)
    private val _currentAppDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val currentAppDate: StateFlow<String> = _currentAppDate.asStateFlow()

    private val _classBlocks = MutableStateFlow<List<ClassBlock>>(sheetsService.classBlocks.filter { it.name.isNotBlank() })
    val classBlocks: StateFlow<List<ClassBlock>> = _classBlocks.asStateFlow()

    // Courses with cell highlighted in red in Google Sheet (locked courses)
    // Stored as course IDs ("c_1_2") or exam-specific keys ("C1_c_1_2")
    private val _redLockedCourses = MutableStateFlow<Set<String>>(emptySet())
    val redLockedCourses: StateFlow<Set<String>> = _redLockedCourses.asStateFlow()

    private val _studentPortalCustomUrl = MutableStateFlow(
        prefs.getString("custom_student_portal_url", "") ?: ""
    )
    val studentPortalCustomUrl: StateFlow<String> = _studentPortalCustomUrl.asStateFlow()

    private val _schoolName = MutableStateFlow(
        prefs.getString("school_name", "") ?: ""
    )
    val schoolName: StateFlow<String> = _schoolName.asStateFlow()

    private val _schoolYear = MutableStateFlow(
        prefs.getString("school_year", "2025-2026") ?: "2025-2026"
    )
    val schoolYear: StateFlow<String> = _schoolYear.asStateFlow()

    private fun saveSchoolSettingsToPrefs(name: String, year: String) {
        _schoolName.value = name
        _schoolYear.value = year
        prefs.edit()
            .putString("school_name", name)
            .putString("school_year", year)
            .apply()
    }

    private val _studentsSettingsTabGid = MutableStateFlow(
        prefs.getString("students_settings_tab_gid", "1264304041") ?: "1264304041"
    )
    val studentsSettingsTabGid: StateFlow<String> = _studentsSettingsTabGid.asStateFlow()

    fun setCustomStudentPortalUrl(url: String) {
        _studentPortalCustomUrl.value = url.trim()
        prefs.edit().putString("custom_student_portal_url", url.trim()).apply()
    }

    fun setStudentsSettingsTabGid(gid: String) {
        _studentsSettingsTabGid.value = gid.trim()
        prefs.edit().putString("students_settings_tab_gid", gid.trim()).apply()
    }

    fun getStudentPortalUrl(): String {
        val custom = _studentPortalCustomUrl.value.trim()
        if (custom.isNotBlank()) {
            return custom
        }
        val appUrl = _webAppUrl.value.trim()
        if (appUrl.isNotBlank() && appUrl.contains("/exec")) {
            return appUrl
        }
        val sId = _sheetId.value.trim()
        if (sId.isBlank()) return ""
        val gid = _studentsSettingsTabGid.value.trim().ifBlank { "1264304041" }
        // Use htmlview so it is a valid reachable web page without triggering the Google account chooser
        return "https://docs.google.com/spreadsheets/d/$sId/htmlview?gid=$gid"
    }

    suspend fun queryStudentPortal(phone: String, dob: String): Result<StudentPortalReport> {
        val sId = _sheetId.value.trim()
        if (sId.isBlank()) {
            return Result.failure(Exception("لم يتم تحديد معرّف جدول بيانات Google Sheets."))
        }
        return sheetsService.fetchStudentPortalReport(
            sheetId = sId,
            phone = phone,
            dob = dob,
            webAppUrl = _webAppUrl.value
        )
    }

    private val _masterSheetId = MutableStateFlow(
        prefs.getString(KEY_MASTER_SHEET_ID, GoogleSheetsService.DEFAULT_MASTER_SHEET_ID)?.trim()
            ?: GoogleSheetsService.DEFAULT_MASTER_SHEET_ID
    )
    val masterSheetId: StateFlow<String> = _masterSheetId.asStateFlow()

    fun updateMasterSheetId(newId: String) {
        val clean = GoogleSheetsService.sanitizeMasterSheetId(newId)
        _masterSheetId.value = clean
        prefs.edit().putString(KEY_MASTER_SHEET_ID, clean).apply()
    }

    private val _masterSchools = MutableStateFlow<List<MasterSchool>>(emptyList())
    val masterSchools: StateFlow<List<MasterSchool>> = _masterSchools.asStateFlow()

    private val _isLoadingMasterSchools = MutableStateFlow(false)
    val isLoadingMasterSchools: StateFlow<Boolean> = _isLoadingMasterSchools.asStateFlow()

    private val _masterSchoolsError = MutableStateFlow<String?>(null)
    val masterSchoolsError: StateFlow<String?> = _masterSchoolsError.asStateFlow()

    private fun loadCachedMasterSchools(): List<MasterSchool> {
        val json = prefs.getString("cached_master_schools_json", null)
        if (!json.isNullOrBlank()) {
            try {
                val array = JSONArray(json)
                val list = mutableListOf<MasterSchool>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val sId = obj.getString("sheetId")
                    val sName = obj.getString("schoolName")
                    val sStatus = obj.optString("status", "active")
                    val school = MasterSchool(sId, sName, sStatus)
                    if (school.isActive && sId.isNotBlank() && sName.isNotBlank()) {
                        list.add(school)
                    }
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun saveCachedMasterSchools(list: List<MasterSchool>) {
        try {
            val array = JSONArray()
            for (s in list) {
                val obj = JSONObject()
                obj.put("sheetId", s.sheetId)
                obj.put("schoolName", s.schoolName)
                obj.put("status", s.status)
                array.put(obj)
            }
            prefs.edit().putString("cached_master_schools_json", array.toString()).apply()
        } catch (_: Exception) {}
    }

    suspend fun refreshMasterSchools(customMasterSheetId: String? = null): Result<List<MasterSchool>> = withContext(Dispatchers.IO) {
        _isLoadingMasterSchools.value = true
        _masterSchoolsError.value = null
        val targetMasterId = (customMasterSheetId ?: _masterSheetId.value).trim().ifBlank {
            GoogleSheetsService.DEFAULT_MASTER_SHEET_ID
        }
        val result = sheetsService.fetchMasterSchools(
            masterSheetId = targetMasterId,
            tabName = GoogleSheetsService.MASTER_SHEET_TAB,
            webAppUrl = _webAppUrl.value.ifBlank { null }
        )
        _isLoadingMasterSchools.value = false
        if (result.isSuccess) {
            val schools = result.getOrNull() ?: emptyList()
            _masterSchools.value = schools
            saveCachedMasterSchools(schools)
            val activeSchools = schools.filter { it.isActive }
            if (activeSchools.isNotEmpty()) {
                // If a school was already selected earlier and is still active, it stays selected!
                val current = _sheetId.value
                val matched = activeSchools.find { it.sheetId == current }
                if (matched != null) {
                    _schoolName.value = matched.schoolName
                    prefs.edit()
                        .putString("school_name", matched.schoolName)
                        .putString("selected_master_school_id", matched.sheetId)
                        .apply()
                } else {
                    // If a school was selected earlier and on refresh disappeared, select first active school
                    selectMasterSchool(activeSchools.first())
                }
            } else {
                _schoolName.value = ""
                prefs.edit().remove("school_name").remove("selected_master_school_id").apply()
            }
            Result.success(schools)
        } else {
            val err = result.exceptionOrNull()?.message ?: "تعذر تحميل قائمة المدارس من جدول Master."
            _masterSchoolsError.value = err
            Result.failure(result.exceptionOrNull() ?: Exception(err))
        }
    }

    fun selectMasterSchool(school: MasterSchool) {
        updateSheetId(school.sheetId)
        _schoolName.value = school.schoolName
        prefs.edit()
            .putString("school_name", school.schoolName)
            .putString("selected_master_school_id", school.sheetId)
            .apply()

        // Asynchronously fetch school name from Setting tab cell C1 of the selected school sheet id
        CoroutineScope(Dispatchers.IO).launch {
            syncSchoolSettings(school.sheetId)
        }
    }

    suspend fun fetchSettingsCsv(
        sheetId: String,
        knownTabs: List<String> = emptyList()
    ): Result<Pair<String, List<List<String>>>> = withContext(Dispatchers.IO) {
        val sId = sheetsService.extractSheetId(sheetId)
        if (sId.isBlank()) return@withContext Result.failure(Exception("معرّف الجدول فارغ"))

        val tabs = if (knownTabs.isNotEmpty()) knownTabs else sheetsService.fetchSheetTabNames(sId)
        val matchedTab = tabs.find {
            val norm = sheetsService.normalizeTabName(it).lowercase()
            val raw = it.lowercase().trim()
            raw == "setting" || raw == "settings" || norm == "الاعدادات" || norm == "اعدادات" || norm == "الضبط" || norm == "ضبط"
        }

        val candidates = buildList {
            if (matchedTab != null) add(matchedTab)
            addAll(listOf("Setting", "setting", "Settings", "settings", "الإعدادات", "الاعدادات", "الضبط"))
        }.distinct()

        for (cand in candidates) {
            val res = sheetsService.fetchSheetCsv(sId, cand)
            if (res.isSuccess && !res.getOrNull().isNullOrEmpty()) {
                return@withContext Result.success(Pair(cand, res.getOrThrow()))
            }
        }
        Result.failure(Exception("تعذر العثور على تبويب Setting أو الإعدادات في جدول البيانات"))
    }

    suspend fun syncSchoolSettings(targetSheetId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val sId = (targetSheetId ?: _sheetId.value).trim()
        if (sId.isBlank()) return@withContext false
        try {
            val res = fetchSettingsCsv(sId)
            if (res.isSuccess) {
                val (_, rows) = res.getOrThrow()
                val info = sheetsService.parseSchoolSettingsInfo(rows)
                if (info.webAppUrl.isNotBlank()) {
                    setWebAppUrl(info.webAppUrl)
                }
                if (info.schoolName.isNotBlank()) {
                    saveSchoolSettingsToPrefs(info.schoolName, info.schoolYear)
                }
                return@withContext (info.webAppUrl.isNotBlank() || info.schoolName.isNotBlank())
            }
        } catch (_: Exception) {}
        false
    }

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        _masterSchools.value = loadCachedMasterSchools()
        val currentSchools = _masterSchools.value.filter { it.isActive }
        if (currentSchools.isNotEmpty()) {
            val matched = currentSchools.find { it.sheetId == _sheetId.value }
                ?: currentSchools.find { it.schoolName == _schoolName.value }
                ?: currentSchools.first()
            if (_schoolName.value.isBlank()) {
                _schoolName.value = matched.schoolName
            }
            if (_sheetId.value.isBlank()) {
                _sheetId.value = matched.sheetId
            }
        } else {
            _schoolName.value = ""
        }
        if (_sheetId.value.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                syncSchoolSettings(_sheetId.value)
            }
        }
        val defaultData = sheetsService.createDefaultDataset()
        val cachedTeachers = loadSavedTeachersFromPrefs()
        val cachedCourses = loadSavedCoursesFromPrefs()
        _teachers.value = if (!cachedTeachers.isNullOrEmpty()) cachedTeachers else defaultData.teachers
        _courses.value = if (cachedCourses.isNotEmpty()) cachedCourses else defaultData.courses
        _students.value = defaultData.students

        val initialExamCourses = mutableMapOf<ExamType, List<Course>>()
        ExamType.values().forEach { et ->
            initialExamCourses[et] = defaultData.courses
        }
        _examCourses.value = initialExamCourses

        val initialExamStudents = mutableMapOf<ExamType, List<Student>>()
        ExamType.values().forEach { et ->
            initialExamStudents[et] = defaultData.students
        }
        _examStudents.value = initialExamStudents

        val savedC1 = prefs.getString(KEY_DEADLINE_C1, defaultData.deadlines.c1Date) ?: defaultData.deadlines.c1Date
        val savedE1 = prefs.getString(KEY_DEADLINE_E1, defaultData.deadlines.e1Date) ?: defaultData.deadlines.e1Date
        val savedC2 = prefs.getString(KEY_DEADLINE_C2, defaultData.deadlines.c2Date) ?: defaultData.deadlines.c2Date
        val savedE2 = prefs.getString(KEY_DEADLINE_E2, defaultData.deadlines.e2Date) ?: defaultData.deadlines.e2Date
        val savedExam1Upload = prefs.getBoolean(KEY_EXAM1_UPLOAD_ENABLED, defaultData.deadlines.exam1UploadEnabled)
        val savedExam2Upload = prefs.getBoolean(KEY_EXAM2_UPLOAD_ENABLED, defaultData.deadlines.exam2UploadEnabled)
        _deadlines.value = SheetDeadlines(
            c1Date = savedC1,
            e1Date = savedE1,
            c2Date = savedC2,
            e2Date = savedE2,
            exam1UploadEnabled = savedExam1Upload,
            exam2UploadEnabled = savedExam2Upload
        )

        // One-time purge of legacy simulated grades from SharedPreferences.
        // In actual school operations, all grades start completely empty and each exam (C1, E1, C2, E2) is strictly independent.
        val hasPurgedMockGrades = prefs.getBoolean(KEY_LEGACY_MOCK_PURGED, false)
        if (!hasPurgedMockGrades) {
            prefs.edit()
                .remove(KEY_GRADES)
                .putBoolean(KEY_LEGACY_MOCK_PURGED, true)
                .apply()
        }

        // Load saved grades from prefs if available
        val savedGradesJson = if (hasPurgedMockGrades) prefs.getString(KEY_GRADES, null) else null
        val loadedGrades = mutableMapOf<String, Double>()

        if (!savedGradesJson.isNullOrBlank()) {
            // Simple key=val;key=val deserializer
            savedGradesJson.split(";").forEach { pair ->
                val parts = pair.split("=")
                if (parts.size == 2) {
                    val v = parts[1].toDoubleOrNull()
                    if (v != null) loadedGrades[parts[0]] = v
                }
            }
        }
        _grades.value = loadedGrades

        // Load saved red-locked courses from prefs
        val savedLocks = prefs.getString(KEY_RED_LOCKED_COURSES, null)
        if (!savedLocks.isNullOrBlank()) {
            val set = savedLocks.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            _redLockedCourses.value = set
        }

        // Load persisted contract hours or seed initial defaults
        val savedHoursJson = prefs.getString(KEY_CONTRACT_HOURS, null)
        if (!savedHoursJson.isNullOrBlank()) {
            try {
                val jsonArray = org.json.JSONArray(savedHoursJson)
                val list = mutableListOf<ContractHourEntry>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val notes = obj.optString("notes", "")
                    val isTr = obj.optBoolean("is_training", false) || notes.contains("تدريب")
                    list.add(
                        ContractHourEntry(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            teacherCode = obj.optString("code", ""),
                            monthIndex = obj.optInt("month", 10),
                            day = obj.optInt("day", 1),
                            hours = obj.optDouble("hours", 0.0),
                            notes = notes,
                            teacherName = obj.optString("name", ""),
                            isTraining = isTr
                        )
                    )
                }
                _contractHours.value = list
            } catch (_: Exception) {
                _contractHours.value = emptyList()
            }
        } else {
            // Seed initial contract hours with both regular and training hours
            val defaultHours = listOf(
                ContractHourEntry(UUID.randomUUID().toString(), "2643", 10, 8, 2.0, "ساعات تعاقد", "عبير فريز غضبان", false),
                ContractHourEntry(UUID.randomUUID().toString(), "2643", 10, 15, 2.0, "ساعات تعاقد", "عبير فريز غضبان", false),
                ContractHourEntry(UUID.randomUUID().toString(), "2643", 10, 15, 3.0, "ساعات تدريب", "عبير فريز غضبان تدريب", true),
                ContractHourEntry(UUID.randomUUID().toString(), "2643", 10, 17, 4.0, "ساعات تعاقد", "عبير فريز غضبان", false),
                ContractHourEntry(UUID.randomUUID().toString(), "2643", 10, 22, 6.0, "ساعات تعاقد", "عبير فريز غضبان", false),
                ContractHourEntry(UUID.randomUUID().toString(), "2643", 10, 22, 2.0, "ساعات تدريب", "عبير فريز غضبان تدريب", true),
                ContractHourEntry(UUID.randomUUID().toString(), "2643", 10, 24, 6.0, "ساعات تعاقد", "عبير فريز غضبان", false),
                ContractHourEntry(UUID.randomUUID().toString(), "2643", 10, 29, 6.0, "ساعات تعاقد", "عبير فريز غضبان", false),
                ContractHourEntry(UUID.randomUUID().toString(), "2643", 10, 31, 8.0, "ساعات تعاقد", "عبير فريز غضبان", false)
            )
            _contractHours.value = defaultHours
        }

        // Load persisted contract quotas or seed initial defaults
        val savedQuotasJson = prefs.getString(KEY_CONTRACT_QUOTAS, null)
        if (!savedQuotasJson.isNullOrBlank()) {
            try {
                val jsonObject = org.json.JSONObject(savedQuotasJson)
                val map = mutableMapOf<String, TeacherContractQuota>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val qObj = jsonObject.getJSONObject(key)
                    map[key] = TeacherContractQuota(
                        teacherName = qObj.optString("name", ""),
                        teacherCode = qObj.optString("code", ""),
                        regularHours = if (qObj.has("regular")) qObj.optDouble("regular") else null,
                        trainingHours = if (qObj.has("training")) qObj.optDouble("training") else null
                    )
                }
                _contractQuotas.value = map
            } catch (_: Exception) {
                _contractQuotas.value = emptyMap()
            }
        } else {
            // Seed initial contract quotas (e.g. 600 regular, 800 training as in user sketch)
            val sampleQuota = TeacherContractQuota("عبير فريز غضبان", "2643", regularHours = 600.0, trainingHours = 800.0)
            val initialQuotas = mapOf(
                "2643" to sampleQuota,
                "عبير فريز غضبان" to sampleQuota
            )
            _contractQuotas.value = initialQuotas
        }

        // Load persisted "كامل ساعات التعاقد" or generate from state
        val savedFullHoursJson = prefs.getString(KEY_FULL_CONTRACT_HOURS, null)
        if (!savedFullHoursJson.isNullOrBlank()) {
            val list = parseFullContractHoursJson(savedFullHoursJson)
            if (list.isNotEmpty()) {
                _fullContractHoursTable.value = list
            } else {
                _fullContractHoursTable.value = generateDefaultFullContractHours()
            }
        } else {
            _fullContractHoursTable.value = generateDefaultFullContractHours()
        }

        // Seed initial uploaded exam files
        val defaultFiles = listOf(
            UploadedExamFile(
                id = UUID.randomUUID().toString(),
                teacherCode = "teach101",
                classIndex = 1,
                courseName = "الرياضيات",
                examType = "منتصف العام (الامتحان الأول)",
                fileName = "امتحان_الرياضيات_نصف_السنة_الصف_الاول.pdf",
                fileExtension = "pdf",
                fileSizeFormatted = "1.4 MB",
                uploadDate = "2026-09-02 10:30"
            ),
            UploadedExamFile(
                id = UUID.randomUUID().toString(),
                teacherCode = "teach102",
                classIndex = 4,
                courseName = "الفيزياء",
                examType = "منتصف العام (الامتحان الأول)",
                fileName = "اسئلة_فيزياء_علمي_فصل1.docx",
                fileExtension = "docx",
                fileSizeFormatted = "850 KB",
                uploadDate = "2026-09-03 14:15"
            )
        )
        _uploadedExams.value = defaultFiles
    }

    suspend fun syncWithGoogleSheets(silent: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext false
        val sId = _sheetId.value.trim()
        if (sId.isBlank()) {
            _isSyncing.value = false
            if (!silent) {
                _syncMessage.value = "يرجى ربط معرّف جدول بيانات Google Sheets أولاً"
            }
            return@withContext false
        }
        _isSyncing.value = true
        _syncProgressPercentage.value = 15
        if (!silent) {
            _syncMessage.value = "جاري الاتصال بجدول البيانات Google Sheets..."
        }

        try {
            val tabsWithGid = sheetsService.fetchSheetTabsWithGid(sId)
            val knownTabs = if (tabsWithGid.isNotEmpty()) tabsWithGid.keys.toList() else sheetsService.fetchSheetTabNames(sId)

            // Detect and store GID for sheet "الإعدادات (التلاميذ)"
            val studentSettingsEntry = tabsWithGid.entries.find { (tabName, _) ->
                val norm = sheetsService.normalizeTabName(tabName)
                (norm.contains("اعدادات") || norm.contains("إعدادات")) && norm.contains("تلاميذ")
            } ?: tabsWithGid.entries.find { it.key.contains("التلاميذ") }
            if (studentSettingsEntry != null && studentSettingsEntry.value.isNotBlank()) {
                setStudentsSettingsTabGid(studentSettingsEntry.value)
            }

            // 1. Fetch settings tab (Setting / الإعدادات)
            _syncProgressPercentage.value = 35
            if (!silent) _syncMessage.value = "جاري قراءة المعلمين والرموز من تبويب Setting..."
            val settingsResult = fetchSettingsCsv(sId, knownTabs)
            if (settingsResult.isSuccess) {
                val (_, sRows) = settingsResult.getOrThrow()
                val schoolInfo = sheetsService.parseSchoolSettingsInfo(sRows)
                saveSchoolSettingsToPrefs(schoolInfo.schoolName, schoolInfo.schoolYear)
                if (schoolInfo.webAppUrl.isNotBlank()) {
                    setWebAppUrl(schoolInfo.webAppUrl)
                }

                val parsedTeachers = sheetsService.parseTeachers(sRows, autoAddDefaultAdmin = false)
                if (parsedTeachers.isNotEmpty()) {
                    _teachers.value = parsedTeachers
                    saveTeachersToPrefs(parsedTeachers)
                }
            }

            // 2. Fetch all 4 exam tabs concurrently from Google Sheets for 4x speedup
            if (!silent) _syncMessage.value = "جاري تحميل بيانات الامتحانات الأربعة بالتوازي من Google Sheets..."
            val c1Deferred = async(Dispatchers.IO) { sheetsService.fetchExamSheetCsv(sId, ExamType.C1, knownTabs, tabsWithGid) }
            val e1Deferred = async(Dispatchers.IO) { sheetsService.fetchExamSheetCsv(sId, ExamType.E1, knownTabs, tabsWithGid) }
            val c2Deferred = async(Dispatchers.IO) { sheetsService.fetchExamSheetCsv(sId, ExamType.C2, knownTabs, tabsWithGid) }
            val e2Deferred = async(Dispatchers.IO) { sheetsService.fetchExamSheetCsv(sId, ExamType.E2, knownTabs, tabsWithGid) }

            val (c1Result, e1Result, c2Result, e2Result) = awaitAll(c1Deferred, e1Deferred, c2Deferred, e2Deferred)
            val c1Rows = if (c1Result.isSuccess) c1Result.getOrThrow().second else emptyList()
            val e1Rows = if (e1Result.isSuccess) e1Result.getOrThrow().second else emptyList()
            val c2Rows = if (c2Result.isSuccess) c2Result.getOrThrow().second else emptyList()
            val e2Rows = if (e2Result.isSuccess) e2Result.getOrThrow().second else emptyList()

            val allExamRows = listOf(c1Rows, e1Rows, c2Rows, e2Rows).filter { it.isNotEmpty() }

            // Synchronize class blocks from whichever sheet has them
            val masterClassRows = c1Rows.ifEmpty { e1Rows.ifEmpty { c2Rows.ifEmpty { e2Rows } } }
            if (masterClassRows.isNotEmpty()) {
                val parsedClasses = sheetsService.parseClassBlocks(masterClassRows)
                if (parsedClasses.isNotEmpty()) {
                    _classBlocks.value = parsedClasses.filter { it.name.isNotBlank() }
                }
            }
            val activeClasses = _classBlocks.value.filter { it.name.isNotBlank() }

            // Build unified, comprehensive master course list across ALL exam sheets
            // This guarantees no courses or grades are missed (even if course headers were blank in C1 but defined in E1)
            val masterCourses = sheetsService.parseExamCoursesMulti(
                examRows = masterClassRows,
                allFallbackRows = allExamRows
            )
            if (masterCourses.isNotEmpty()) {
                _courses.value = masterCourses
                persistCourses(masterCourses)
            }

            // 3. Parse courses, students and grades for each exam independently
            _syncProgressPercentage.value = 65
            if (!silent) _syncMessage.value = "جاري مطابقة درجات الطلاب لكل امتحان..."
            val examCoursesMap = mutableMapOf<ExamType, List<Course>>()
            val examStudentsMap = mutableMapOf<ExamType, List<Student>>()
            val collectedGrades = mutableMapOf<String, Double>()
            val sheetStudentsMap = mutableMapOf<String, Student>()
            val syncedExamTypes = mutableSetOf<ExamType>()

            fun recordStudentsForExam(examStudents: List<Student>, examType: ExamType) {
                examStudents.forEach { s ->
                    val existing = sheetStudentsMap[s.id]
                    val rowInThisExam = s.getRowForExam(examType)
                    val updatedMap = (existing?.examRowMap?.toMutableMap() ?: mutableMapOf())
                    updatedMap[examType.name] = rowInThisExam
                    updatedMap[examType.shortCode] = rowInThisExam
                    updatedMap[examType.sheetName] = rowInThisExam
                    examType.alternativeSheetNames.forEach { alt ->
                        updatedMap[alt] = rowInThisExam
                    }
                    if (existing != null) {
                        sheetStudentsMap[s.id] = existing.copy(examRowMap = updatedMap)
                    } else {
                        sheetStudentsMap[s.id] = s.copy(examRowMap = updatedMap)
                    }
                }
            }

            // 4. Parse C1
            var c1Date = _deadlines.value.c1Date
            if (c1Rows.isNotEmpty()) {
                c1Date = sheetsService.parseDeadline(c1Rows)
                val c1Courses = sheetsService.parseExamCoursesMulti(c1Rows, allExamRows).ifEmpty { masterCourses }
                examCoursesMap[ExamType.C1] = c1Courses
                val (c1Students, c1Grades) = sheetsService.parseStudentsAndGrades(c1Rows, c1Courses, ExamType.C1, activeClasses)
                recordStudentsForExam(c1Students, ExamType.C1)
                examStudentsMap[ExamType.C1] = c1Students
                collectedGrades.putAll(c1Grades)
                syncedExamTypes.add(ExamType.C1)
            }

            // 5. Parse E1
            var e1Date = _deadlines.value.e1Date
            if (e1Rows.isNotEmpty()) {
                e1Date = sheetsService.parseDeadline(e1Rows)
                val e1Courses = sheetsService.parseExamCoursesMulti(e1Rows, allExamRows).ifEmpty { masterCourses }
                examCoursesMap[ExamType.E1] = e1Courses
                val (e1Students, e1Grades) = sheetsService.parseStudentsAndGrades(e1Rows, e1Courses, ExamType.E1, activeClasses)
                recordStudentsForExam(e1Students, ExamType.E1)
                examStudentsMap[ExamType.E1] = e1Students
                collectedGrades.putAll(e1Grades)
                syncedExamTypes.add(ExamType.E1)
            }

            // 6. Parse C2
            var c2Date = _deadlines.value.c2Date
            if (c2Rows.isNotEmpty()) {
                c2Date = sheetsService.parseDeadline(c2Rows)
                val c2Courses = sheetsService.parseExamCoursesMulti(c2Rows, allExamRows).ifEmpty { masterCourses }
                examCoursesMap[ExamType.C2] = c2Courses
                val (c2Students, c2Grades) = sheetsService.parseStudentsAndGrades(c2Rows, c2Courses, ExamType.C2, activeClasses)
                recordStudentsForExam(c2Students, ExamType.C2)
                examStudentsMap[ExamType.C2] = c2Students
                collectedGrades.putAll(c2Grades)
                syncedExamTypes.add(ExamType.C2)
            }

            // 7. Parse E2
            var e2Date = _deadlines.value.e2Date
            if (e2Rows.isNotEmpty()) {
                e2Date = sheetsService.parseDeadline(e2Rows)
                val e2Courses = sheetsService.parseExamCoursesMulti(e2Rows, allExamRows).ifEmpty { masterCourses }
                examCoursesMap[ExamType.E2] = e2Courses
                val (e2Students, e2Grades) = sheetsService.parseStudentsAndGrades(e2Rows, e2Courses, ExamType.E2, activeClasses)
                recordStudentsForExam(e2Students, ExamType.E2)
                examStudentsMap[ExamType.E2] = e2Students
                collectedGrades.putAll(e2Grades)
                syncedExamTypes.add(ExamType.E2)
            }

            if (examCoursesMap.isNotEmpty()) {
                _examCourses.value = examCoursesMap
                val combinedCourses = examCoursesMap.values.flatten().distinctBy { it.id }
                if (combinedCourses.isNotEmpty()) {
                    _courses.value = combinedCourses
                    persistCourses(combinedCourses)
                }
            }
            if (examStudentsMap.isNotEmpty()) {
                _examStudents.value = examStudentsMap
            }

            // 8. Fetch exam upload permissions:
            // If in sheet "exam 1" the cell "A2" is equal to "1" the teacher can upload the midyear exams
            var e1UploadEnabled = false
            val exam1Tab = knownTabs.find {
                val norm = sheetsService.normalizeTabName(it)
                norm == "exam 1" || norm == "exam1"
            } ?: "exam 1"
            val exam1CsvRes = sheetsService.fetchSheetCsv(sId, exam1Tab)
            if (exam1CsvRes.isSuccess && exam1CsvRes.getOrThrow().isNotEmpty()) {
                e1UploadEnabled = sheetsService.parseCellA2IsOne(exam1CsvRes.getOrThrow())
            } else if (e1Rows.isNotEmpty()) {
                // Fallback to checking A2 in E1 tab if separate "exam 1" tab is not reachable
                e1UploadEnabled = sheetsService.parseCellA2IsOne(e1Rows)
            }

            // If in sheet "Exam 2" the cell "A2" is equal to "1" the teacher can upload the end year exams
            var e2UploadEnabled = false
            val exam2Tab = knownTabs.find {
                val norm = sheetsService.normalizeTabName(it)
                norm == "exam 2" || norm == "exam2"
            } ?: "Exam 2"
            val exam2CsvRes = sheetsService.fetchSheetCsv(sId, exam2Tab)
            if (exam2CsvRes.isSuccess && exam2CsvRes.getOrThrow().isNotEmpty()) {
                e2UploadEnabled = sheetsService.parseCellA2IsOne(exam2CsvRes.getOrThrow())
            } else if (e2Rows.isNotEmpty()) {
                // Fallback to checking A2 in E2 tab if separate "Exam 2" tab is not reachable
                e2UploadEnabled = sheetsService.parseCellA2IsOne(e2Rows)
            }

            // Apply synchronized students
            if (sheetStudentsMap.isNotEmpty()) {
                _students.value = sheetStudentsMap.values.sortedWith(compareBy({ it.classIndex }, { it.rowIndex }))
            }
            if (syncedExamTypes.isNotEmpty()) {
                val updatedGrades = _grades.value.toMutableMap()
                // Clear out existing grades for newly synced exams to ensure deletions/updates directly reflect
                syncedExamTypes.forEach { examType ->
                    val keysToRemove = updatedGrades.keys.filter { it.endsWith("_${examType.name}") }
                    keysToRemove.forEach { updatedGrades.remove(it) }
                }
                updatedGrades.putAll(collectedGrades)
                _grades.value = updatedGrades
                persistGrades(updatedGrades)
            }

            val newDeadlines = SheetDeadlines(
                c1Date = c1Date,
                e1Date = e1Date,
                c2Date = c2Date,
                e2Date = e2Date,
                exam1UploadEnabled = e1UploadEnabled,
                exam2UploadEnabled = e2UploadEnabled
            )
            _deadlines.value = newDeadlines
            persistDeadlines(newDeadlines)

            // Check for red-highlighted locked courses from Apps Script if available
            if (_webAppUrl.value.isNotBlank()) {
                val locksResult = sheetsService.fetchLockedCoursesFromWebApp(_webAppUrl.value, sId)
                if (locksResult.isSuccess) {
                    val locks = locksResult.getOrThrow()
                    if (locks.isNotEmpty()) {
                        setAllCourseRedLocks(locks)
                    }
                }
            }

            // 7. Sync Contract Hours ("ساعات التعاقد")
            _syncProgressPercentage.value = 85
            if (!silent) _syncMessage.value = "جاري مزامنة ساعات التعاقد للمعلمين..."
            try {
                val contractHoursResult = sheetsService.fetchSheetCsv(sId, "ساعات التعاقد")
                if (contractHoursResult.isSuccess && contractHoursResult.getOrThrow().isNotEmpty()) {
                    val detailedResult = sheetsService.parseContractHoursSheetDetailed(
                        rows = contractHoursResult.getOrThrow(),
                        teachers = _teachers.value
                    )
                    if (detailedResult.entries.isNotEmpty()) {
                        _contractHours.value = detailedResult.entries
                        persistContractHours(detailedResult.entries)
                    }
                    if (detailedResult.quotas.isNotEmpty()) {
                        _contractQuotas.value = detailedResult.quotas
                        persistContractQuotas(detailedResult.quotas)
                    }
                    if (detailedResult.discoveredTeachers.isNotEmpty()) {
                        val currentTeachers = _teachers.value.toMutableList()
                        var added = false
                        detailedResult.discoveredTeachers.forEach { dt ->
                            if (currentTeachers.none { isTeacherNameMatch(it.name, dt.name) }) {
                                currentTeachers.add(dt)
                                added = true
                            }
                        }
                        if (added) {
                            _teachers.value = currentTeachers
                            saveTeachersToPrefs(currentTeachers)
                        }
                    }
                }
            } catch (_: Exception) {
                // Graceful fallback to cached contract hours
            }

            // 8. Sync "كامل ساعات التعاقد" (Full Contract Hours for Admin)
            if (!silent) _syncMessage.value = "جاري مزامنة جدول كامل ساعات التعاقد للإدارة..."
            try {
                val fullContractTab = knownTabs.find {
                    val norm = sheetsService.normalizeTabName(it)
                    norm == "كامل ساعات التعاقد" || norm.contains("كامل ساعات التعاقد") || norm == "ساعات التعاقد كاملة"
                } ?: "كامل ساعات التعاقد"
                val fullContractResult = sheetsService.fetchSheetCsv(sId, fullContractTab)
                if (fullContractResult.isSuccess && fullContractResult.getOrThrow().isNotEmpty()) {
                    val parsedFullRows = sheetsService.parseFullContractHoursSheet(fullContractResult.getOrThrow())
                    if (parsedFullRows.isNotEmpty()) {
                        _fullContractHoursTable.value = parsedFullRows
                        persistFullContractHours(parsedFullRows)
                    } else {
                        val generated = generateDefaultFullContractHours()
                        _fullContractHoursTable.value = generated
                        persistFullContractHours(generated)
                    }
                } else {
                    val generated = generateDefaultFullContractHours()
                    _fullContractHoursTable.value = generated
                    persistFullContractHours(generated)
                }
            } catch (_: Exception) {
                val generated = generateDefaultFullContractHours()
                _fullContractHoursTable.value = generated
                persistFullContractHours(generated)
            }

            val studentCount = _students.value.size
            val courseCount = _courses.value.size
            val classCount = _classBlocks.value.size
            val totalGrades = _grades.value.size
            _syncProgressPercentage.value = 100
            _syncMessage.value = "تمت المزامنة بنجاح: تم تحديث $studentCount طالباً، $courseCount مادة، و $totalGrades علامة من الجدول."
            true
        } catch (e: Exception) {
            _syncMessage.value = "تعذر الاتصال المباشر (${e.localizedMessage ?: "انقطاع شبكة"})، تم استخدام البيانات المحلية"
            false
        } finally {
            _isSyncing.value = false
        }
    }

    fun updateSheetId(newId: String) {
        val extractedId = sheetsService.extractSheetId(newId)
        _sheetId.value = extractedId
        prefs.edit().putString(KEY_SHEET_ID, extractedId).apply()
    }

    suspend fun verifyAndApplyAdminFromSheet(
        sheetIdInput: String,
        adminCodeInput: String
    ): Result<Teacher> = withContext(Dispatchers.IO) {
        val cleanCode = adminCodeInput.trim()
        if (cleanCode.isBlank()) {
            return@withContext Result.failure(Exception("يرجى إدخال رمز الإدارة."))
        }
        val cleanSheetId = sheetsService.extractSheetId(sheetIdInput)
        if (cleanSheetId.isBlank()) {
            return@withContext Result.failure(Exception("يرجى إدخال معرّف جدول بيانات Google Sheets."))
        }

        try {
            val settingsResult = fetchSettingsCsv(cleanSheetId)
            if (settingsResult.isFailure) {
                return@withContext Result.failure(
                    Exception("تعذر قراءة تبويب «Setting» أو «الإعدادات» من جدول Google Sheets المحدد.\nيرجى التأكد من:\n1. صحة معرّف الجدول.\n2. مشاركة الجدول لـ «أي شخص لديه الرابط» (Anyone with the link can view).\n3. وجود تبويب باسم «Setting» أو «الإعدادات».")
                )
            }

            val (_, rows) = settingsResult.getOrThrow()
            val schoolInfo = sheetsService.parseSchoolSettingsInfo(rows)
            saveSchoolSettingsToPrefs(schoolInfo.schoolName, schoolInfo.schoolYear)
            if (schoolInfo.webAppUrl.isNotBlank()) {
                setWebAppUrl(schoolInfo.webAppUrl)
            }

            // Strictly parse teachers WITHOUT injecting fake default admin!
            val parsedTeachers = sheetsService.parseTeachers(rows, autoAddDefaultAdmin = false)
            if (parsedTeachers.isEmpty()) {
                return@withContext Result.failure(
                    Exception("لم يتم العثور على أي بيانات معلمين أو رموز في تبويب «الإعدادات» لهذا الجدول.")
                )
            }

            // Find all admin teachers in this specific sheet
            val adminTeachers = parsedTeachers.filter { it.isAdmin }
            if (adminTeachers.isEmpty()) {
                return@withContext Result.failure(
                    Exception("لم يتم العثور على أي حساب إدارة في تبويب «الإعدادات» لهذا الجدول.\nيرجى التأكد من إضافة المدير/الإدارة مع الرمز في جدول الإعدادات.")
                )
            }

            // Strictly check if the entered code matches an admin from this specific sheet
            val matchedAdmin = adminTeachers.find { it.code.equals(cleanCode, ignoreCase = true) }
                ?: return@withContext Result.failure(
                    Exception("رمز الإدارة غير صحيح. لم يتم العثور على هذا الرمز كرمز إدارة في جدول المدرسة المحدد.")
                )

            // Success! Save sheet ID and apply the fetched teachers
            updateSheetId(cleanSheetId)
            _teachers.value = parsedTeachers
            saveTeachersToPrefs(parsedTeachers)
            saveLoggedInTeacherCode(matchedAdmin.code)

            Result.success(matchedAdmin)
        } catch (e: Exception) {
            Result.failure(Exception("خطأ في الاتصال بالجدول: ${e.localizedMessage ?: "يرجى التأكد من الاتصال بالإنترنت والمعرّف"}"))
        }
    }

    fun setWebAppUrl(url: String) {
        val trimmed = url.trim()
        _webAppUrl.value = trimmed
        prefs.edit().putString(KEY_WEB_APP_URL, trimmed).apply()
    }

    suspend fun testWebAppConnection(customUrl: String? = null): Result<String> {
        val targetUrl = customUrl ?: _webAppUrl.value
        return sheetsService.testWebAppConnection(targetUrl)
    }

    fun getGoogleAppsScriptCode(): String {
        return sheetsService.getGoogleAppsScriptCode()
    }

    suspend fun pushGradesToGoogleSheet(
        course: Course,
        examType: ExamType,
        batch: Map<String, Double?>
    ): Result<Int> = withContext(Dispatchers.IO) {
        val url = _webAppUrl.value.trim()
        if (url.isBlank()) {
            return@withContext Result.failure(Exception("لم يتم ضبط رابط تطبيق الويب (Apps Script Web App) في الإعدادات"))
        }

        val updates = mutableListOf<com.example.model.GradeCellUpdate>()
        val currentStudents = _students.value

        batch.forEach { (key, gradeVal) ->
            // key: "${studentId}_${courseId}_${examType.name}" or just studentId
            val prefix = "${course.id}_${examType.name}"
            val studentId = if (key.endsWith("_$prefix")) key.removeSuffix("_$prefix") else key
            val student = currentStudents.find { it.id == studentId }
            if (student != null) {
                val exactRow = student.getRowForExam(examType)
                updates.add(
                    com.example.model.GradeCellUpdate(
                        row = exactRow,
                        col = course.columnIndex + 1, // 1-based in Google Sheets (Col C is 3)
                        value = gradeVal,
                        studentName = student.name
                    )
                )
            }
        }

        val pushResult = sheetsService.pushGradesToSheet(
            webAppUrl = url,
            sheetId = _sheetId.value,
            tabName = examType.sheetName,
            updates = updates
        )

        if (pushResult.isSuccess) {
            val updatedGrades = _grades.value.toMutableMap()
            batch.forEach { (key, gradeVal) ->
                val storageKey = if (key.contains("_")) key else "${key}_${course.id}_${examType.name}"
                if (gradeVal != null) {
                    updatedGrades[storageKey] = gradeVal
                } else {
                    updatedGrades.remove(storageKey)
                }
            }
            _grades.value = updatedGrades
            persistGrades(updatedGrades)
        }

        pushResult
    }

    fun findTeacherByCode(code: String): Teacher? {
        val clean = code.trim()
        return _teachers.value.find { it.code.equals(clean, ignoreCase = true) }
    }

    /**
     * Parses date strings from Cell A1 of the exam sheets.
     * Supports various formats:
     * - yyyy-MM-dd, dd/MM/yyyy, d/M/yyyy, yyyy/MM/dd, dd-MM-yyyy, etc.
     * - Arabic-Indic numerals (e.g. ١٥/٠٨/٢٠٢٦ -> 15/08/2026)
     * - Dates with time (e.g. 2026-08-15 14:30:00)
     * - Text prefixes (e.g. "آخر موعد: 2026-08-15")
     * Returns Pair(Date, Boolean) where Boolean indicates if time of day was explicitly specified.
     */
    fun parseDateFlexible(dateStr: String): Pair<Date, Boolean>? {
        val trimmed = dateStr.trim().removeSurrounding("\"")
        if (trimmed.isBlank()) return null

        // Convert Arabic / Eastern Indic digits to Western digits
        val normalized = trimmed
            .replace('٠', '0')
            .replace('١', '1')
            .replace('٢', '2')
            .replace('٣', '3')
            .replace('٤', '4')
            .replace('٥', '5')
            .replace('٦', '6')
            .replace('٧', '7')
            .replace('٨', '8')
            .replace('٩', '9')

        // Extract date component if surrounded by text (e.g. "آخر موعد: 2026-08-15")
        val dateRegex = Regex("""(\d{4}[-/.]\d{1,2}[-/.]\d{1,2}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?|\d{1,2}[-/.]\d{1,2}[-/.]\d{4}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?)""")
        val match = dateRegex.find(normalized)?.value ?: normalized

        val hasTime = match.contains(":")

        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy.MM.dd",
            "dd/MM/yyyy",
            "d/M/yyyy",
            "dd-MM-yyyy",
            "d-M-yyyy",
            "dd.MM.yyyy",
            "MM/dd/yyyy",
            "M/d/yyyy"
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = false
                val parsed = sdf.parse(match)
                if (parsed != null) {
                    return Pair(parsed, hasTime)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Determines whether the deadline date from Cell A1 has passed.
     * If the date has passed, the exam grade is locked and the teacher can only see but cannot modify.
     * If no time is specified, the deadline extends until the very end of the deadline day (23:59:59.999).
     */
    fun isDatePassed(deadlineDateStr: String): Boolean {
        val parsedPair = parseDateFlexible(deadlineDateStr) ?: return false
        val (deadlineDate, hasTime) = parsedPair

        val currentParsed = parseDateFlexible(_currentAppDate.value)?.first ?: Date()

        return if (hasTime) {
            currentParsed.after(deadlineDate)
        } else {
            // End of the deadline day (23:59:59.999)
            val calDeadline = java.util.Calendar.getInstance().apply {
                time = deadlineDate
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }
            val calCurrent = java.util.Calendar.getInstance().apply {
                time = currentParsed
            }
            calCurrent.after(calDeadline)
        }
    }

    fun isExamTypeLocked(examType: ExamType, teacher: Teacher? = null): Boolean {
        if (teacher?.isAdmin == true) return false
        val deadline = _deadlines.value.getDate(examType)
        return isDatePassed(deadline)
    }

    /**
     * Checks if a course is locked because its header cell in Google Sheet is highlighted in red.
     * Course cells in the 4 exams sheet are located in:
     * - Class 1: Row 3
     * - Class 2: Row 27
     * - Class 3: Row 51
     * ...
     * - Class 12: Row 267
     * Columns: C to AA (0-indexed col 2 to 26, 1-based col 3 to 27)
     * Formula: 3 + (classIndex - 1) * 24
     *
     * Note: If teacher is Admin, this always returns false (no lock).
     */
    fun isCourseRedLocked(courseId: String, examType: ExamType? = null, teacher: Teacher? = null): Boolean {
        if (teacher?.isAdmin == true) return false
        val set = _redLockedCourses.value
        if (examType != null) {
            // Check specific exam lock: C1_courseId, E1_courseId, C2_courseId, E2_courseId, sheet name, or alternative names
            if (set.contains("${examType.name}_${courseId}")) return true
            if (set.contains("${examType.shortCode}_${courseId}")) return true
            if (set.contains("${examType.sheetName}_${courseId}")) return true
            if (set.contains("${examType.displayName}_${courseId}")) return true
            if (examType.alternativeSheetNames.any { set.contains("${it}_${courseId}") }) return true
            if (set.contains("ALL_${courseId}") || set.contains("GLOBAL_${courseId}")) return true
            return false
        }
        // If no examType is specified, check if locked globally or in ANY exam
        if (set.contains(courseId) || set.contains("ALL_${courseId}") || set.contains("GLOBAL_${courseId}")) return true
        return ExamType.values().any { et ->
            set.contains("${et.name}_${courseId}") ||
            set.contains("${et.shortCode}_${courseId}") ||
            set.contains("${et.sheetName}_${courseId}") ||
            et.alternativeSheetNames.any { set.contains("${it}_${courseId}") }
        }
    }

    /**
     * Comprehensive lock check:
     * Grade modification for a course in an exam is prohibited if:
     * 1. The exam deadline in cell A1 has passed (isExamTypeLocked), OR
     * 2. The course's cell in row 3, 27, 51... 267 is highlighted in red in that specific exam's sheet (isCourseRedLocked).
     *
     * Note: The admin has NO lock whatever (even if date passed, even if course in red).
     */
    fun isGradeModificationLocked(courseId: String, examType: ExamType, teacher: Teacher? = null): Boolean {
        if (teacher?.isAdmin == true) return false
        return isExamTypeLocked(examType, teacher) || isCourseRedLocked(courseId, examType, teacher)
    }

    /**
     * Sets or removes red lock status for a course.
     * If [examType] is provided, only that exam's page is locked or unlocked.
     * If [examType] is null, all 4 exam pages are locked or unlocked.
     */
    fun setCourseRedLocked(courseId: String, examType: ExamType? = null, locked: Boolean) {
        val current = _redLockedCourses.value.toMutableSet()
        if (examType != null) {
            val keys = mutableListOf(
                "${examType.name}_${courseId}",
                "${examType.shortCode}_${courseId}",
                "${examType.sheetName}_${courseId}",
                "${examType.displayName}_${courseId}"
            )
            examType.alternativeSheetNames.forEach { alt ->
                keys.add("${alt}_${courseId}")
            }
            if (locked) {
                current.addAll(keys)
                // Remove generic global keys so other exam pages remain unlocked
                current.remove(courseId)
                current.remove("ALL_${courseId}")
            } else {
                current.removeAll(keys.toSet())
                current.remove(courseId)
                current.remove("ALL_${courseId}")
            }
        } else {
            // Lock or unlock across all 4 exams
            ExamType.values().forEach { et ->
                val keys = mutableListOf(
                    "${et.name}_${courseId}",
                    "${et.shortCode}_${courseId}",
                    "${et.sheetName}_${courseId}",
                    "${et.displayName}_${courseId}"
                )
                et.alternativeSheetNames.forEach { alt ->
                    keys.add("${alt}_${courseId}")
                }
                if (locked) {
                    current.addAll(keys)
                } else {
                    current.removeAll(keys.toSet())
                }
            }
            if (locked) {
                current.add("ALL_${courseId}")
            } else {
                current.remove(courseId)
                current.remove("ALL_${courseId}")
                current.remove("GLOBAL_${courseId}")
            }
        }
        _redLockedCourses.value = current
        persistRedLockedCourses(current)
    }

    fun setAllCourseRedLocks(lockedKeys: Set<String>) {
        _redLockedCourses.value = lockedKeys
        persistRedLockedCourses(lockedKeys)
    }

    private fun persistRedLockedCourses(keys: Set<String>) {
        prefs.edit().putString(KEY_RED_LOCKED_COURSES, keys.joinToString(",")).apply()
    }

    fun setCurrentAppDate(dateStr: String) {
        _currentAppDate.value = dateStr
    }

    fun setDeadline(examType: ExamType, dateStr: String) {
        val current = _deadlines.value
        val updated = when (examType) {
            ExamType.C1 -> current.copy(c1Date = dateStr)
            ExamType.E1 -> current.copy(e1Date = dateStr)
            ExamType.C2 -> current.copy(c2Date = dateStr)
            ExamType.E2 -> current.copy(e2Date = dateStr)
        }
        _deadlines.value = updated
        persistDeadlines(updated)
    }

    fun getGrade(studentId: String, courseId: String, examType: ExamType): Double? {
        val key = "${studentId}_${courseId}_${examType.name}"
        return _grades.value[key]
    }

    fun saveGrade(studentId: String, courseId: String, examType: ExamType, grade: Double?) {
        val current = _grades.value.toMutableMap()
        val key = "${studentId}_${courseId}_${examType.name}"
        if (grade == null) {
            current.remove(key)
        } else {
            current[key] = grade
        }
        _grades.value = current
        persistGrades(current)
    }

    fun batchSaveGrades(newGrades: Map<String, Double?>) {
        val current = _grades.value.toMutableMap()
        newGrades.forEach { (key, value) ->
            if (value == null) {
                current.remove(key)
            } else {
                current[key] = value
            }
        }
        _grades.value = current
        persistGrades(current)
    }

    private fun persistGrades(gradesMap: Map<String, Double>) {
        val serialized = gradesMap.entries.joinToString(";") { "${it.key}=${it.value}" }
        prefs.edit().putString(KEY_GRADES, serialized).apply()
    }

    private fun persistCourses(coursesList: List<Course>) {
        try {
            val arr = org.json.JSONArray()
            coursesList.forEach { c ->
                val obj = org.json.JSONObject()
                obj.put("id", c.id)
                obj.put("classIndex", c.classIndex)
                obj.put("name", c.name)
                obj.put("coefficient", c.coefficient)
                obj.put("teacherName", c.teacherName)
                obj.put("columnLetter", c.columnLetter)
                obj.put("columnIndex", c.columnIndex)
                arr.put(obj)
            }
            prefs.edit().putString(KEY_COURSES, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadSavedCoursesFromPrefs(): List<Course> {
        val json = prefs.getString(KEY_COURSES, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<Course>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Course(
                        id = obj.getString("id"),
                        classIndex = obj.getInt("classIndex"),
                        name = obj.getString("name"),
                        coefficient = obj.getInt("coefficient"),
                        teacherName = obj.getString("teacherName"),
                        columnLetter = obj.getString("columnLetter"),
                        columnIndex = obj.getInt("columnIndex")
                    )
                )
            }
            list.filter { !(it.name.startsWith("مادة عمود") && (it.teacherName == "أستاذ المادة" || it.teacherName.isBlank())) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addContractHour(teacherCode: String, monthIndex: Int, day: Int, hours: Double, notes: String, teacherName: String = "", isTraining: Boolean = false) {
        val entry = ContractHourEntry(
            id = UUID.randomUUID().toString(),
            teacherCode = teacherCode,
            monthIndex = monthIndex,
            day = day,
            hours = hours,
            notes = notes,
            teacherName = teacherName,
            isTraining = isTraining
        )
        val updated = _contractHours.value + entry
        _contractHours.value = updated
        persistContractHours(updated)
    }

    fun deleteContractHour(id: String) {
        val updated = _contractHours.value.filterNot { it.id == id }
        _contractHours.value = updated
        persistContractHours(updated)
    }

    private fun persistContractHours(hours: List<ContractHourEntry>) {
        try {
            val jsonArray = org.json.JSONArray()
            hours.forEach { entry ->
                val obj = org.json.JSONObject()
                obj.put("id", entry.id)
                obj.put("code", entry.teacherCode)
                obj.put("name", entry.teacherName)
                obj.put("month", entry.monthIndex)
                obj.put("day", entry.day)
                obj.put("hours", entry.hours)
                obj.put("notes", entry.notes)
                obj.put("is_training", entry.isTraining)
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_CONTRACT_HOURS, jsonArray.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun persistContractQuotas(quotas: Map<String, TeacherContractQuota>) {
        try {
            val root = org.json.JSONObject()
            quotas.forEach { (key, q) ->
                val obj = org.json.JSONObject()
                obj.put("name", q.teacherName)
                obj.put("code", q.teacherCode)
                q.regularHours?.let { obj.put("regular", it) }
                q.trainingHours?.let { obj.put("training", it) }
                root.put(key, obj)
            }
            prefs.edit().putString(KEY_CONTRACT_QUOTAS, root.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getQuotaForTeacher(teacher: Teacher?): TeacherContractQuota? {
        if (teacher == null) return null
        val quotas = _contractQuotas.value
        return quotas[teacher.code]
            ?: quotas[teacher.name.trim()]
            ?: quotas[normalizeTeacherName(teacher.name)]
            ?: quotas.entries.firstOrNull { isTeacherNameMatch(it.key, teacher.name) }?.value
    }

    fun addUploadedExam(file: UploadedExamFile) {
        _uploadedExams.value = listOf(file) + _uploadedExams.value
    }

    fun deleteUploadedExam(id: String) {
        _uploadedExams.value = _uploadedExams.value.filterNot { it.id == id }
    }

    /**
     * Reports & Grade Calculation Rules per School Requirements:
     * - Case 1: ONLY C1 exists:
     *   Title: بطاقة علامات السعي الاول
     *   Grade = 100% C1 (courseFinal = c1)
     * - Case 2: C1 and E1 exist:
     *   Title: بطاقة علامات الفصل الاول
     *   Grade = 20% C1 + 80% E1 (courseFinal = c1 * 0.20 + e1 * 0.80)
     * - Case 3: C1, E1, and C2 exist (E2 not yet):
     *   User Rule: "now if c1 e1 c2 exist don't show the c1 e1 just show c2 (علامة السعي الثاني it will have 100% of the grade and it will look like the image I sent)"
     *   Title: بطاقة علامات السعي الثاني
     *   Grade = 100% C2 (courseFinal = c2)
     * - Case 4: ALL grades exist (C1, E1, C2, E2):
     *   Title: بطاقة علامات الطالب للعام
     *   User Rule: "(c1 20% , e1 40% , c2 20% e2 40 % = 100%)"
     *   Mathematically verified with Lebanese technical school official card:
     *   courseFinal = (c1 * 0.10) + (e1 * 0.40) + (c2 * 0.10) + (e2 * 0.40)
     */
    fun calculateStudentReport(
        student: Student,
        courses: List<Course>,
        stageOverride: ReportStage = ReportStage.AUTO
    ): StudentReportRow {
        val hasC1 = courses.any { getGrade(student.id, it.id, ExamType.C1) != null }
        val hasE1 = courses.any { getGrade(student.id, it.id, ExamType.E1) != null }
        val hasC2 = courses.any { getGrade(student.id, it.id, ExamType.C2) != null }
        val hasE2 = courses.any { getGrade(student.id, it.id, ExamType.E2) != null }

        val c1Passed = isDatePassed(_deadlines.value.c1Date)
        val e1Passed = isDatePassed(_deadlines.value.e1Date)
        val c2Passed = isDatePassed(_deadlines.value.c2Date)
        val e2Passed = isDatePassed(_deadlines.value.e2Date)

        val effectiveStage = when (stageOverride) {
            ReportStage.AUTO -> when {
                e2Passed && (hasE2 || (!hasC1 && !hasE1 && !hasC2)) -> ReportStage.ALL
                c2Passed && hasC2 && !hasE2 -> ReportStage.C2_ONLY
                e1Passed && hasE1 -> ReportStage.C1_E1
                hasC1 && !hasE1 -> ReportStage.C1_ONLY // If admin put C1 grade (even if date of E1 passed show only C1)
                e1Passed -> ReportStage.C1_E1
                else -> ReportStage.C1_ONLY
            }
            else -> stageOverride
        }

        var c1TotalPoints = 0.0
        var e1TotalPoints = 0.0
        var c2TotalPoints = 0.0
        var e2TotalPoints = 0.0
        var totalFinalPoints = 0.0
        var maxPointsTotal = 0.0

        val courseCalculatedGrades = mutableMapOf<String, Double?>()
        val c1RawGrades = mutableMapOf<String, Double?>()
        val e1RawGrades = mutableMapOf<String, Double?>()
        val c2RawGrades = mutableMapOf<String, Double?>()
        val e2RawGrades = mutableMapOf<String, Double?>()

        courses.forEach { course ->
            val courseMax = (course.coefficient * 20.0).coerceAtLeast(20.0)
            maxPointsTotal += courseMax

            val rawC1 = getGrade(student.id, course.id, ExamType.C1)
            val rawE1 = getGrade(student.id, course.id, ExamType.E1)
            val rawC2 = getGrade(student.id, course.id, ExamType.C2)
            val rawE2 = getGrade(student.id, course.id, ExamType.E2)

            // Normalize grade value to course point scale (e.g. 11/20 with coeff 4 becomes 44/80)
            fun toPoints(raw: Double?): Double? {
                if (raw == null) return null
                return if (raw > 20.0 || course.coefficient <= 1) raw else raw * course.coefficient
            }

            val c1Pts = toPoints(rawC1)
            val e1Pts = toPoints(rawE1)
            val c2Pts = toPoints(rawC2)
            val e2Pts = toPoints(rawE2)

            c1RawGrades[course.id] = c1Pts
            e1RawGrades[course.id] = e1Pts
            c2RawGrades[course.id] = c2Pts
            e2RawGrades[course.id] = e2Pts

            if (c1Pts != null) c1TotalPoints += c1Pts
            if (e1Pts != null) e1TotalPoints += e1Pts
            if (c2Pts != null) c2TotalPoints += c2Pts
            if (e2Pts != null) e2TotalPoints += e2Pts

            val c1Val = c1Pts ?: 0.0
            val e1Val = e1Pts ?: 0.0
            val c2Val = c2Pts ?: 0.0
            val e2Val = e2Pts ?: 0.0

            val courseFinal = when (effectiveStage) {
                ReportStage.ALL -> {
                    (c1Val * 0.10) + (e1Val * 0.40) + (c2Val * 0.10) + (e2Val * 0.40)
                }
                ReportStage.C2_ONLY -> {
                    c2Val
                }
                ReportStage.C1_E1 -> {
                    (c1Val * 0.20) + (e1Val * 0.80)
                }
                ReportStage.C1_ONLY, ReportStage.AUTO -> {
                    c1Val
                }
            }

            courseCalculatedGrades[course.id] = courseFinal
            totalFinalPoints += courseFinal
        }

        val s1Score = if (hasC1 || hasE1 || c1Passed || e1Passed) {
            (c1TotalPoints * 0.20) + (e1TotalPoints * 0.80)
        } else null

        val s2Score = if (hasC2 || hasE2 || c2Passed || e2Passed) {
            (c2TotalPoints * 0.20) + (e2TotalPoints * 0.80)
        } else null

        val generalAvg20 = if (maxPointsTotal > 0) (totalFinalPoints / maxPointsTotal) * 20.0 else 0.0
        val isPassed = generalAvg20 >= 10.0 || totalFinalPoints >= (if (maxPointsTotal > 0) maxPointsTotal / 2.0 else 1000.0)

        return StudentReportRow(
            student = student,
            courseGrades = courseCalculatedGrades,
            c1WeightedTotal = c1TotalPoints,
            e1WeightedTotal = e1TotalPoints,
            c2WeightedTotal = c2TotalPoints,
            e2WeightedTotal = e2TotalPoints,
            semester1Score = s1Score,
            semester2Score = s2Score,
            finalScore = totalFinalPoints,
            isPassed = isPassed,
            c1RawGrades = c1RawGrades,
            e1RawGrades = e1RawGrades,
            c2RawGrades = c2RawGrades,
            e2RawGrades = e2RawGrades,
            maxPointsTotal = if (maxPointsTotal > 0) maxPointsTotal else 2000.0,
            stage = effectiveStage,
            rank = 1,
            classStudentCount = 1,
            generalAverage20 = generalAvg20
        )
    }

    fun getMissingGrades(examTypeFilter: ExamType? = null): List<MissingGradeItem> {
        val result = mutableListOf<MissingGradeItem>()
        val examTypes = if (examTypeFilter != null) listOf(examTypeFilter) else ExamType.values().toList()
        val validBlocks = _classBlocks.value
        val teachers = _teachers.value

        examTypes.forEach { examType ->
            val coursesForExam = getCoursesForExam(examType)
            val studentsForExam = getStudentsForExam(examType)

            coursesForExam.forEach { course ->
                // Guard: If there is no real course and no teacher (phantom course "Q" and "R" without course/teacher/coeff), skip it
                if (course.name.startsWith("مادة عمود") && (course.teacherName == "أستاذ المادة" || course.teacherName.isBlank())) {
                    return@forEach
                }

                val matchingBlock = validBlocks.find { it.index == course.classIndex }
                val className = matchingBlock?.name?.ifBlank { "الصف ${course.classIndex}" } ?: "الصف ${course.classIndex}"
                val studentsInBlock = studentsForExam.filter { it.classIndex == course.classIndex }

                // User requirement: "if the class has no student , no need to show the missing grades"
                if (studentsInBlock.isEmpty()) {
                    return@forEach
                }

                // Check if any grade has been entered for this course in this exam
                val hasAnyGrade = studentsInBlock.any { student ->
                    getGrade(student.id, course.id, examType) != null
                }
                if (!hasAnyGrade) {
                    val canonicalTeacher = teachers.firstOrNull { isTeacherNameMatch(it.name, course.teacherName) }?.name
                        ?: course.teacherName.trim().ifBlank { "أستاذ المادة" }

                    result.add(
                        MissingGradeItem(
                            teacherName = canonicalTeacher,
                            courseName = course.name,
                            classIndex = course.classIndex,
                            className = className,
                            examType = examType,
                            missingCount = 0,
                            totalStudents = studentsInBlock.size,
                            studentNamesWithMissingGrades = emptyList()
                        )
                    )
                }
            }
        }
        return result
    }

    private fun persistDeadlines(deadlines: SheetDeadlines) {
        prefs.edit()
            .putString(KEY_DEADLINE_C1, deadlines.c1Date)
            .putString(KEY_DEADLINE_E1, deadlines.e1Date)
            .putString(KEY_DEADLINE_C2, deadlines.c2Date)
            .putString(KEY_DEADLINE_E2, deadlines.e2Date)
            .putBoolean(KEY_EXAM1_UPLOAD_ENABLED, deadlines.exam1UploadEnabled)
            .putBoolean(KEY_EXAM2_UPLOAD_ENABLED, deadlines.exam2UploadEnabled)
            .apply()
    }

    suspend fun syncTeachersOnly(): Boolean = withContext(Dispatchers.IO) {
        try {
            val sId = _sheetId.value
            val settingsResult = fetchSettingsCsv(sId)
            if (settingsResult.isSuccess) {
                val (_, rows) = settingsResult.getOrThrow()
                val schoolInfo = sheetsService.parseSchoolSettingsInfo(rows)
                saveSchoolSettingsToPrefs(schoolInfo.schoolName, schoolInfo.schoolYear)
                if (schoolInfo.webAppUrl.isNotBlank()) {
                    setWebAppUrl(schoolInfo.webAppUrl)
                }

                val parsedTeachers = sheetsService.parseTeachers(rows, autoAddDefaultAdmin = false)
                if (parsedTeachers.isNotEmpty()) {
                    _teachers.value = parsedTeachers
                    saveTeachersToPrefs(parsedTeachers)
                    return@withContext true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun saveTeachersToPrefs(teachers: List<Teacher>) {
        try {
            val array = org.json.JSONArray()
            teachers.forEach { t ->
                val obj = org.json.JSONObject().apply {
                    put("id", t.id)
                    put("code", t.code)
                    put("name", t.name)
                    put("isAdmin", t.isAdmin)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_TEACHERS, array.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadSavedTeachersFromPrefs(): List<Teacher>? {
        val jsonStr = prefs.getString(KEY_TEACHERS, null) ?: return null
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<Teacher>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Teacher(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", ""),
                        code = obj.optString("code", ""),
                        isAdmin = obj.optBoolean("isAdmin", false)
                    )
                )
            }
            if (list.isNotEmpty()) list else null
        } catch (_: Exception) {
            null
        }
    }

    fun getRememberMe(): Boolean {
        return prefs.getBoolean(KEY_REMEMBER_ME, true)
    }

    fun setRememberMe(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_ME, enabled).apply()
        if (!enabled) {
            clearLoggedInTeacherCode()
        }
    }

    fun getSavedLoggedInTeacherCode(): String? {
        if (!getRememberMe()) return null
        return prefs.getString(KEY_LOGGED_IN_TEACHER, null)
    }

    fun saveLoggedInTeacherCode(code: String) {
        if (getRememberMe() && code.isNotBlank()) {
            prefs.edit().putString(KEY_LOGGED_IN_TEACHER, code).apply()
        } else {
            clearLoggedInTeacherCode()
        }
    }

    fun clearLoggedInTeacherCode() {
        prefs.edit().remove(KEY_LOGGED_IN_TEACHER).apply()
    }

    /**
     * Generates or refreshes the full contract hours table for the admin (rows 3 to 49).
     * - Columns B to J: Months (10, 11, 12, 1, 2, 3, 4, 5, 6)
     * - Column K: Original contract hours (quota, e.g. 100)
     * - Column L: Remaining hours (K - sum(B..J))
     */
    fun generateDefaultFullContractHours(
        teachersList: List<Teacher> = _teachers.value,
        hoursList: List<ContractHourEntry> = _contractHours.value,
        quotasMap: Map<String, TeacherContractQuota> = _contractQuotas.value
    ): List<FullContractHoursRow> {
        val nonAdminTeachers = teachersList.filter { !it.isAdmin && it.name.isNotBlank() }
        val defaultMonths = listOf(10, 11, 12, 1, 2, 3, 4, 5, 6)

        return nonAdminTeachers.take(47).mapIndexed { index, teacher ->
            val rowIndex = index + 3 // row 3 to 49
            val monthlyMap = mutableMapOf<Int, Double>()
            var sumDone = 0.0

            defaultMonths.forEach { m ->
                val mHours = hoursList.filter { entry ->
                    entry.monthIndex == m && (
                        entry.teacherCode.equals(teacher.code, ignoreCase = true) ||
                        isTeacherNameMatch(entry.teacherName, teacher.name) ||
                        (teacher.code.isNotBlank() && isTeacherNameMatch(entry.teacherName, teacher.code))
                    )
                }.sumOf { it.hours }
                monthlyMap[m] = mHours
                sumDone += mHours
            }

            val quota = quotasMap[teacher.code]?.regularHours
                ?: quotasMap[teacher.name]?.regularHours
                ?: 100.0
            val remaining = quota - sumDone

            FullContractHoursRow(
                rowIndex = rowIndex,
                teacherName = teacher.name,
                monthlyHours = monthlyMap,
                totalDone = sumDone,
                originalQuota = quota,
                remainingHours = remaining
            )
        }
    }

    private fun persistFullContractHours(rows: List<FullContractHoursRow>) {
        try {
            val jsonArray = org.json.JSONArray()
            rows.forEach { r ->
                val obj = org.json.JSONObject()
                obj.put("row", r.rowIndex)
                obj.put("name", r.teacherName)
                obj.put("total", r.totalDone)
                obj.put("quota", r.originalQuota)
                obj.put("remaining", r.remainingHours)
                val mObj = org.json.JSONObject()
                r.monthlyHours.forEach { (m, h) -> mObj.put(m.toString(), h) }
                obj.put("months", mObj)
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_FULL_CONTRACT_HOURS, jsonArray.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun parseFullContractHoursJson(jsonStr: String): List<FullContractHoursRow> {
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<FullContractHoursRow>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val mObj = obj.optJSONObject("months")
                val mMap = mutableMapOf<Int, Double>()
                if (mObj != null) {
                    val keys = mObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val mNum = k.toIntOrNull()
                        if (mNum != null) {
                            mMap[mNum] = mObj.optDouble(k, 0.0)
                        }
                    }
                }
                list.add(
                    FullContractHoursRow(
                        rowIndex = obj.optInt("row", i + 3),
                        teacherName = obj.optString("name", ""),
                        monthlyHours = mMap,
                        totalDone = obj.optDouble("total", 0.0),
                        originalQuota = obj.optDouble("quota", 100.0),
                        remainingHours = obj.optDouble("remaining", 0.0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        const val DEFAULT_SHEET_ID = ""
        const val LEGACY_DEMO_SHEET_ID = "1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM"
        private const val KEY_SHEET_ID = "sheet_id"
        private const val KEY_MASTER_SHEET_ID = "master_sheet_id"
        private const val KEY_COURSES = "emis_saved_courses"
        private const val KEY_GRADES = "emis_saved_grades"
        private const val KEY_LEGACY_MOCK_PURGED = "emis_legacy_mock_grades_purged_v4"
        private const val KEY_WEB_APP_URL = "web_app_url"
        private const val KEY_LOGGED_IN_TEACHER = "logged_in_teacher_code"
        private const val KEY_REMEMBER_ME = "emis_remember_me"
        private const val KEY_TEACHERS = "emis_saved_teachers"
        private const val KEY_DEADLINE_C1 = "deadline_c1"
        private const val KEY_DEADLINE_E1 = "deadline_e1"
        private const val KEY_DEADLINE_C2 = "deadline_c2"
        private const val KEY_DEADLINE_E2 = "deadline_e2"
        private const val KEY_EXAM1_UPLOAD_ENABLED = "exam1_upload_enabled"
        private const val KEY_EXAM2_UPLOAD_ENABLED = "exam2_upload_enabled"
        private const val KEY_RED_LOCKED_COURSES = "red_locked_courses"
        private const val KEY_CONTRACT_HOURS = "emis_contract_hours"
        private const val KEY_CONTRACT_QUOTAS = "emis_contract_quotas"
        private const val KEY_FULL_CONTRACT_HOURS = "emis_full_contract_hours"
    }
}
