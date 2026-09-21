package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SchoolRepository
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
import com.example.model.StudentCoursePortalGrade
import com.example.model.StudentPortalReport
import com.example.model.StudentReportRow
import com.example.model.Teacher
import com.example.model.TeacherContractQuota
import com.example.model.UploadedExamFile
import com.example.model.isTeacherNameMatch
import com.example.model.normalizeTeacherName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen {
    LOGIN,
    WELCOME,
    DASHBOARD,
    GRADES,
    CONTRACT_HOURS,
    EXAM_UPLOAD,
    MISSING_GRADES,
    STUDENT_REPORTS,
    SETTINGS,
    STUDENT_PORTAL_QR,
    STUDENT_WEB_PORTAL
}

enum class AutoSyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    SAVED_LOCAL
}

class EmisViewModel(application: Application) : AndroidViewModel(application) {

    val repository = SchoolRepository(application)

    private val _currentScreen = MutableStateFlow(AppScreen.LOGIN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _currentTeacher = MutableStateFlow<Teacher?>(null)
    val currentTeacher: StateFlow<Teacher?> = _currentTeacher.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    val teachers: StateFlow<List<Teacher>> = repository.teachers
    val courses: StateFlow<List<Course>> = repository.courses
    val students: StateFlow<List<Student>> = repository.students
    val deadlines: StateFlow<SheetDeadlines> = repository.deadlines
    val sheetId: StateFlow<String> = repository.sheetId
    val webAppUrl: StateFlow<String> = repository.webAppUrl
    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val syncMessage: StateFlow<String?> = repository.syncMessage
    val syncPercentage: StateFlow<Int> = repository.syncProgressPercentage
    val classBlocks: StateFlow<List<ClassBlock>> = repository.classBlocks
    val grades: StateFlow<Map<String, Double>> = repository.grades
    val schoolName: StateFlow<String> = repository.schoolName
    val schoolYear: StateFlow<String> = repository.schoolYear

    val masterSchools: StateFlow<List<MasterSchool>> = repository.masterSchools
    val isLoadingMasterSchools: StateFlow<Boolean> = repository.isLoadingMasterSchools
    val masterSchoolsError: StateFlow<String?> = repository.masterSchoolsError
    val masterSheetId: StateFlow<String> = repository.masterSheetId

    fun updateMasterSheetId(newId: String) {
        repository.updateMasterSheetId(newId)
        refreshMasterSchools()
    }

    fun refreshMasterSchools(customMasterId: String? = null) {
        viewModelScope.launch {
            repository.refreshMasterSchools(customMasterId)
        }
    }

    fun selectMasterSchool(school: MasterSchool) {
        repository.selectMasterSchool(school)
        refreshSchoolSettings()
        refreshTeachersOnly()
    }

    fun refreshSchoolSettings() {
        viewModelScope.launch {
            repository.syncSchoolSettings()
        }
    }

    private val _rememberMe = MutableStateFlow(repository.getRememberMe())
    val rememberMe: StateFlow<Boolean> = _rememberMe.asStateFlow()

    fun updateRememberMe(enabled: Boolean) {
        _rememberMe.value = enabled
        repository.setRememberMe(enabled)
    }

    fun saveTeacherCode(code: String) {
        if (_rememberMe.value && code.isNotBlank()) {
            repository.saveLoggedInTeacherCode(code.trim())
        } else {
            repository.clearLoggedInTeacherCode()
        }
    }

    fun clearTeacherCode() {
        repository.clearLoggedInTeacherCode()
    }

    // Tracks students whose grades are currently being edited locally by the user
    private val _userEditedStudentIds = MutableStateFlow<Set<String>>(emptySet())
    val userEditedStudentIds: StateFlow<Set<String>> = _userEditedStudentIds.asStateFlow()

    // Synchronization state with Google Sheet
    private val _autoSyncStatus = MutableStateFlow(AutoSyncStatus.IDLE)
    val autoSyncStatus: StateFlow<AutoSyncStatus> = _autoSyncStatus.asStateFlow()

    private val _autoSyncMessage = MutableStateFlow<String?>("متصل بـ Google Sheet")
    val autoSyncMessage: StateFlow<String?> = _autoSyncMessage.asStateFlow()

    // Welcome Screen & Startup Synchronization state
    private val _welcomeStatusMessage = MutableStateFlow("جاري الاتصال بـ Google Sheets وتحديث بياناتك...")
    val welcomeStatusMessage: StateFlow<String> = _welcomeStatusMessage.asStateFlow()

    private val _isWelcomeSyncComplete = MutableStateFlow(false)
    val isWelcomeSyncComplete: StateFlow<Boolean> = _isWelcomeSyncComplete.asStateFlow()

    private val _isRefreshingTeachers = MutableStateFlow(false)
    val isRefreshingTeachers: StateFlow<Boolean> = _isRefreshingTeachers.asStateFlow()

    private val _teachersRefreshStatus = MutableStateFlow<String?>(null)
    val teachersRefreshStatus: StateFlow<String?> = _teachersRefreshStatus.asStateFlow()

    private var periodicSyncJob: kotlinx.coroutines.Job? = null

    // Grade Screen State
    private val _selectedExamType = MutableStateFlow(ExamType.C1)
    val selectedExamType: StateFlow<ExamType> = _selectedExamType.asStateFlow()

    val currentExamCourses: StateFlow<List<Course>> = combine(
        repository.examCourses,
        _selectedExamType,
        repository.courses
    ) { examMap, examType, fallbackCourses ->
        val examSpecific = examMap[examType] ?: emptyList()
        if (examSpecific.isEmpty()) {
            fallbackCourses
        } else {
            (examSpecific + fallbackCourses).distinctBy { it.id }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.courses.value)

    private val _selectedClassIndex = MutableStateFlow(1)
    val selectedClassIndex: StateFlow<Int> = _selectedClassIndex.asStateFlow()

    private val _selectedCourseId = MutableStateFlow<String?>(null)
    val selectedCourseId: StateFlow<String?> = _selectedCourseId.asStateFlow()

    // Transient grade inputs keyed by studentId
    private val _editedGrades = MutableStateFlow<Map<String, String>>(emptyMap())
    val editedGrades: StateFlow<Map<String, String>> = _editedGrades.asStateFlow()

    private val _saveStatusMessage = MutableStateFlow<String?>(null)
    val saveStatusMessage: StateFlow<String?> = _saveStatusMessage.asStateFlow()

    private val _isSavingToSheet = MutableStateFlow(false)
    val isSavingToSheet: StateFlow<Boolean> = _isSavingToSheet.asStateFlow()

    private val _showWebAppDialog = MutableStateFlow(false)
    val showWebAppDialog: StateFlow<Boolean> = _showWebAppDialog.asStateFlow()

    // Contract Hours State
    private val _selectedMonth = MutableStateFlow(10) // Starts in October (تشرين الأول)
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

    val contractHours: StateFlow<List<ContractHourEntry>> = repository.contractHours
    val fullContractHoursTable: StateFlow<List<FullContractHoursRow>> = repository.fullContractHoursTable
    val uploadedExams: StateFlow<List<UploadedExamFile>> = repository.uploadedExams

    // Missing grades view filter (true = by teacher, false = by class)
    private val _isMissingByTeacher = MutableStateFlow(true)
    val isMissingByTeacher: StateFlow<Boolean> = _isMissingByTeacher.asStateFlow()

    fun isDatePassed(dateStr: String): Boolean = repository.isDatePassed(dateStr)

    private val _missingGradeExamType = MutableStateFlow<ExamType>(ExamType.C1)
    val missingGradeExamType: StateFlow<ExamType> = _missingGradeExamType.asStateFlow()

    val missingGrades: StateFlow<List<MissingGradeItem>> = combine(
        _missingGradeExamType,
        repository.grades,
        repository.courses,
        repository.students,
        repository.examCourses
    ) { examType, _, _, _, _ ->
        repository.getMissingGrades(examType)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        repository.getMissingGrades(_missingGradeExamType.value)
    )

    // Reports selected class
    private val _reportsClassIndex = MutableStateFlow(1)
    val reportsClassIndex: StateFlow<Int> = _reportsClassIndex.asStateFlow()

    private val _studentSearchQuery = MutableStateFlow("")
    val studentSearchQuery: StateFlow<String> = _studentSearchQuery.asStateFlow()

    // Month hours calculations for current teacher (total)
    val currentTeacherMonthHours: StateFlow<Double> = combine(
        contractHours,
        _currentTeacher,
        _selectedMonth
    ) { hours, teacher, month ->
        val code = teacher?.code ?: ""
        val name = teacher?.name ?: ""
        hours.filter { entry ->
            val cleanName = entry.teacherName.replace("تدريب", "").replace("(", "").replace(")", "").trim()
            val matchesTeacher = entry.teacherCode.equals(code, ignoreCase = true) ||
                    (name.isNotBlank() && isTeacherNameMatch(entry.teacherName, name)) ||
                    (name.isNotBlank() && cleanName.isNotBlank() && isTeacherNameMatch(cleanName, name)) ||
                    (entry.teacherName.isNotBlank() && isTeacherNameMatch(entry.teacherName, code)) ||
                    (cleanName.isNotBlank() && isTeacherNameMatch(cleanName, code))
            matchesTeacher && entry.monthIndex == month
        }.sumOf { it.hours }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Regular courses hours for current teacher in selected month
    val currentTeacherMonthRegularHours: StateFlow<Double> = combine(
        contractHours,
        _currentTeacher,
        _selectedMonth
    ) { hours, teacher, month ->
        val code = teacher?.code ?: ""
        val name = teacher?.name ?: ""
        hours.filter { entry ->
            val cleanName = entry.teacherName.replace("تدريب", "").replace("(", "").replace(")", "").trim()
            val matchesTeacher = entry.teacherCode.equals(code, ignoreCase = true) ||
                    (name.isNotBlank() && isTeacherNameMatch(entry.teacherName, name)) ||
                    (name.isNotBlank() && cleanName.isNotBlank() && isTeacherNameMatch(cleanName, name)) ||
                    (entry.teacherName.isNotBlank() && isTeacherNameMatch(entry.teacherName, code)) ||
                    (cleanName.isNotBlank() && isTeacherNameMatch(cleanName, code))
            matchesTeacher && entry.monthIndex == month && !entry.isTraining && !entry.notes.contains("تدريب")
        }.sumOf { it.hours }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Training ("تدريب") hours for current teacher in selected month
    val currentTeacherMonthTrainingHours: StateFlow<Double> = combine(
        contractHours,
        _currentTeacher,
        _selectedMonth
    ) { hours, teacher, month ->
        val code = teacher?.code ?: ""
        val name = teacher?.name ?: ""
        hours.filter { entry ->
            val cleanName = entry.teacherName.replace("تدريب", "").replace("(", "").replace(")", "").trim()
            val matchesTeacher = entry.teacherCode.equals(code, ignoreCase = true) ||
                    (name.isNotBlank() && isTeacherNameMatch(entry.teacherName, name)) ||
                    (name.isNotBlank() && cleanName.isNotBlank() && isTeacherNameMatch(cleanName, name)) ||
                    (entry.teacherName.isNotBlank() && isTeacherNameMatch(entry.teacherName, code)) ||
                    (cleanName.isNotBlank() && isTeacherNameMatch(cleanName, code))
            matchesTeacher && entry.monthIndex == month && (entry.isTraining || entry.notes.contains("تدريب"))
        }.sumOf { it.hours }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentTeacherCumulativeHours: StateFlow<Double> = combine(
        contractHours,
        _currentTeacher
    ) { hours, teacher ->
        val code = teacher?.code ?: ""
        val name = teacher?.name ?: ""
        hours.filter { entry ->
            val cleanName = entry.teacherName.replace("تدريب", "").replace("(", "").replace(")", "").trim()
            val matchesTeacher = entry.teacherCode.equals(code, ignoreCase = true) ||
                    (name.isNotBlank() && isTeacherNameMatch(entry.teacherName, name)) ||
                    (name.isNotBlank() && cleanName.isNotBlank() && isTeacherNameMatch(cleanName, name)) ||
                    (entry.teacherName.isNotBlank() && isTeacherNameMatch(entry.teacherName, code)) ||
                    (cleanName.isNotBlank() && isTeacherNameMatch(cleanName, code))
            matchesTeacher
        }.sumOf { it.hours }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentTeacherCumulativeRegularHours: StateFlow<Double> = combine(
        contractHours,
        _currentTeacher
    ) { hours, teacher ->
        val code = teacher?.code ?: ""
        val name = teacher?.name ?: ""
        hours.filter { entry ->
            val cleanName = entry.teacherName.replace("تدريب", "").replace("(", "").replace(")", "").trim()
            val matchesTeacher = entry.teacherCode.equals(code, ignoreCase = true) ||
                    (name.isNotBlank() && isTeacherNameMatch(entry.teacherName, name)) ||
                    (name.isNotBlank() && cleanName.isNotBlank() && isTeacherNameMatch(cleanName, name)) ||
                    (entry.teacherName.isNotBlank() && isTeacherNameMatch(entry.teacherName, code)) ||
                    (cleanName.isNotBlank() && isTeacherNameMatch(cleanName, code))
            matchesTeacher && !entry.isTraining && !entry.notes.contains("تدريب")
        }.sumOf { it.hours }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentTeacherCumulativeTrainingHours: StateFlow<Double> = combine(
        contractHours,
        _currentTeacher
    ) { hours, teacher ->
        val code = teacher?.code ?: ""
        val name = teacher?.name ?: ""
        hours.filter { entry ->
            val cleanName = entry.teacherName.replace("تدريب", "").replace("(", "").replace(")", "").trim()
            val matchesTeacher = entry.teacherCode.equals(code, ignoreCase = true) ||
                    (name.isNotBlank() && isTeacherNameMatch(entry.teacherName, name)) ||
                    (name.isNotBlank() && cleanName.isNotBlank() && isTeacherNameMatch(cleanName, name)) ||
                    (entry.teacherName.isNotBlank() && isTeacherNameMatch(entry.teacherName, code)) ||
                    (cleanName.isNotBlank() && isTeacherNameMatch(cleanName, code))
            matchesTeacher && (entry.isTraining || entry.notes.contains("تدريب"))
        }.sumOf { it.hours }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val contractQuotas: StateFlow<Map<String, TeacherContractQuota>> = repository.contractQuotas

    fun getQuotaForTeacher(teacher: Teacher?): TeacherContractQuota? {
        return repository.getQuotaForTeacher(teacher)
    }

    init {
        // Collect repository grades reactively so changes made on Google Sheets reflect directly on the screen
        viewModelScope.launch {
            repository.grades.collect {
                loadGradesForCurrentSelection(force = false)
            }
        }

        refreshMasterSchools()
        refreshTeachersOnly()
    }

    fun refreshTeachersOnly() {
        viewModelScope.launch {
            _isRefreshingTeachers.value = true
            _teachersRefreshStatus.value = "جاري تحديث رموز المعلمين من Google Sheets..."
            val success = repository.syncTeachersOnly()
            _isRefreshingTeachers.value = false
            if (success) {
                _teachersRefreshStatus.value = "تم تحديث رموز وأسماء المعلمين ✓"
            } else {
                _teachersRefreshStatus.value = null
            }
        }
    }

    fun getSavedTeacherCode(): String? {
        return repository.getSavedLoggedInTeacherCode()
    }

    fun dismissWebAppDialog() {
        _showWebAppDialog.value = false
    }

    fun openWebAppDialog() {
        _showWebAppDialog.value = true
    }

    fun clearSaveStatusMessage() {
        _saveStatusMessage.value = null
    }

    fun refreshFromGoogleSheet() {
        viewModelScope.launch {
            val success = repository.syncWithGoogleSheets(silent = false)
            if (success) {
                _userEditedStudentIds.value = emptySet()
            }
            loadGradesForCurrentSelection(force = true)
            if (!success) {
                _saveStatusMessage.value = "تعذر الاتصال بـ Google Sheet، تم عرض أحدث البيانات المحفوظة محلياً."
            } else {
                _saveStatusMessage.value = null
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        _saveStatusMessage.value = null
        if (screen == AppScreen.GRADES) {
            loadGradesForCurrentSelection()
        }
    }

    fun isAdminCode(code: String): Boolean {
        val clean = code.trim()
        val teacher = repository.findTeacherByCode(clean)
        if (teacher != null) {
            return teacher.isAdmin
        }
        return clean.equals("admin", ignoreCase = true) || clean.equals("admin123", ignoreCase = true)
    }

    suspend fun verifyAndLoginAdminWithSheet(adminCode: String, sheetIdInput: String): Result<Teacher> {
        val result = repository.verifyAndApplyAdminFromSheet(sheetIdInput, adminCode)
        if (result.isSuccess) {
            val adminTeacher = result.getOrThrow()
            _currentTeacher.value = adminTeacher
            saveTeacherCode(adminTeacher.code)
            _loginError.value = null
            startTeacherWelcomeAndSync(adminTeacher)
        }
        return result
    }

    fun loginAsAdminWithSheetId(adminCode: String, sheetIdInput: String) {
        val cleanSheetId = sheetIdInput.trim()
        if (cleanSheetId.isNotBlank()) {
            repository.updateSheetId(cleanSheetId)
        }
        val cleanCode = adminCode.trim().ifBlank { "admin" }
        val existingTeacher = repository.teachers.value.find { it.code.equals(cleanCode, ignoreCase = true) }
            ?: repository.teachers.value.find { it.isAdmin }
        val adminTeacher = existingTeacher?.copy(isAdmin = true, code = cleanCode)
            ?: Teacher(
                id = "t_admin",
                name = "إدارة المدرسة (Admin)",
                code = cleanCode,
                isAdmin = true
            )
        _currentTeacher.value = adminTeacher
        saveTeacherCode(adminTeacher.code)
        _loginError.value = null
        startTeacherWelcomeAndSync(adminTeacher)
    }

    fun loginAsAdminWithSheetId(sheetIdInput: String) {
        loginAsAdminWithSheetId("admin", sheetIdInput)
    }

    fun loginWithCode(code: String): Boolean {
        val cleanCode = code.trim()
        val teacher = repository.findTeacherByCode(cleanCode)
        return if (teacher != null) {
            _currentTeacher.value = teacher
            saveTeacherCode(cleanCode)
            _loginError.value = null
            startTeacherWelcomeAndSync(teacher)
            true
        } else if (cleanCode.equals("admin", ignoreCase = true) || cleanCode.equals("admin123", ignoreCase = true)) {
            saveTeacherCode(cleanCode)
            loginAsAdminWithSheetId(cleanCode, repository.sheetId.value)
            true
        } else {
            if (repository.sheetId.value.isBlank()) {
                _loginError.value = "لم يتم ربط جدول المدرسة بعد. يرجى من إدارة المدرسة (Admin) إدخال معرّف جدول بيانات Google Sheet."
                return false
            }
            viewModelScope.launch {
                val refreshed = repository.syncTeachersOnly()
                if (refreshed) {
                    val freshTeacher = repository.findTeacherByCode(cleanCode)
                    if (freshTeacher != null) {
                        _currentTeacher.value = freshTeacher
                        saveTeacherCode(cleanCode)
                        _loginError.value = null
                        startTeacherWelcomeAndSync(freshTeacher)
                        return@launch
                    }
                }
                _loginError.value = "رمز الاستاذ(ة) غير صحيح، يرجى التأكد من الرمز والمحاولة مجدداً."
            }
            false
        }
    }

    fun startTeacherWelcomeAndSync(teacher: Teacher) {
        _currentScreen.value = AppScreen.WELCOME
        _isWelcomeSyncComplete.value = false
        _welcomeStatusMessage.value = "أهلاً بك أستاذ(ة) ${teacher.name}، جاري تحديث بيانات العلامات وساعات التعاقد..."

        viewModelScope.launch {
            try {
                _autoSyncStatus.value = AutoSyncStatus.SYNCING
                val syncSuccess = repository.syncWithGoogleSheets(silent = false)

                val teacherCourses = repository.courses.value.filter {
                    teacher.isAdmin || isTeacherNameMatch(it.teacherName, teacher.name)
                }
                if (teacherCourses.isNotEmpty()) {
                    val firstCourse = teacherCourses.first()
                    _selectedClassIndex.value = firstCourse.classIndex
                    _selectedCourseId.value = firstCourse.id
                } else {
                    val validClasses = repository.classBlocks.value.filter { it.name.isNotBlank() }
                    if (validClasses.isNotEmpty() && validClasses.none { it.index == _selectedClassIndex.value }) {
                        _selectedClassIndex.value = validClasses.first().index
                    }
                }

                loadGradesForCurrentSelection(force = true)
                _autoSyncStatus.value = if (syncSuccess) AutoSyncStatus.SYNCED else AutoSyncStatus.SAVED_LOCAL
                _isWelcomeSyncComplete.value = true
                _welcomeStatusMessage.value = if (syncSuccess) {
                    "✓ تم تحديث جميع العلامات وساعات التعاقد بنجاح!"
                } else {
                    "تم فتح التطبيق بأحدث نسخة محفوظة محلياً"
                }

                delay(350)
                if (_currentScreen.value == AppScreen.WELCOME) {
                    _currentScreen.value = AppScreen.DASHBOARD
                }
            } catch (_: Exception) {
                _isWelcomeSyncComplete.value = true
                _welcomeStatusMessage.value = "تم فتح التطبيق بأحدث نسخة محفوظة محلياً"
                delay(350)
                if (_currentScreen.value == AppScreen.WELCOME) {
                    _currentScreen.value = AppScreen.DASHBOARD
                }
            }
        }
    }

    fun proceedFromWelcomeToDashboard() {
        if (_currentScreen.value == AppScreen.WELCOME) {
            _currentScreen.value = AppScreen.DASHBOARD
        }
    }

    fun logout() {
        _currentTeacher.value = null
        if (!_rememberMe.value) {
            repository.clearLoggedInTeacherCode()
        }
        _currentScreen.value = AppScreen.LOGIN
        _editedGrades.value = emptyMap()
        _saveStatusMessage.value = null
        _isWelcomeSyncComplete.value = false
    }

    fun setSelectedExamType(type: ExamType) {
        _selectedExamType.value = type
        _userEditedStudentIds.value = emptySet()
        loadGradesForCurrentSelection(force = true)
    }

    fun setSelectedClassIndex(index: Int) {
        _selectedClassIndex.value = index
        _userEditedStudentIds.value = emptySet()
        val teacher = _currentTeacher.value
        val availableForClass = repository.courses.value.filter {
            it.classIndex == index && (teacher?.isAdmin == true || isTeacherNameMatch(it.teacherName, teacher?.name))
        }
        _selectedCourseId.value = availableForClass.firstOrNull()?.id
        loadGradesForCurrentSelection(force = true)
    }

    fun setSelectedCourseId(courseId: String) {
        _selectedCourseId.value = courseId
        _userEditedStudentIds.value = emptySet()
        loadGradesForCurrentSelection(force = true)
    }

    fun loadGradesForCurrentSelection(force: Boolean = true) {
        val courseId = _selectedCourseId.value
        if (courseId == null) {
            _editedGrades.value = emptyMap()
            return
        }
        val examType = _selectedExamType.value
        val classIdx = _selectedClassIndex.value
        val teacher = _currentTeacher.value
        val course = repository.courses.value.find { it.id == courseId }

        // If course doesn't belong to this class or teacher has no course here, do not show grades
        if (course == null || course.classIndex != classIdx || (teacher?.isAdmin != true && !isTeacherNameMatch(course.teacherName, teacher?.name))) {
            _editedGrades.value = emptyMap()
            return
        }

        val classStudents = repository.students.value.filter { it.classIndex == classIdx }

        val newInputs = _editedGrades.value.toMutableMap()
        classStudents.forEach { student ->
            if (force || student.id !in _userEditedStudentIds.value) {
                val grade = repository.getGrade(student.id, courseId, examType)
                if (grade != null) {
                    newInputs[student.id] = if (grade % 1.0 == 0.0) grade.toInt().toString() else grade.toString()
                } else {
                    newInputs.remove(student.id)
                }
            }
        }
        _editedGrades.value = newInputs
        if (force) {
            _saveStatusMessage.value = null
        }
    }

    fun normalizeGradeDigits(text: String): String {
        return text.trim()
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
            .replace('٫', '.')
            .replace(',', '.')
    }

    fun isValidGrade(text: String): Boolean {
        if (text.isBlank()) return true
        val normalized = normalizeGradeDigits(text)
        if (normalized.endsWith(".")) return false
        val num = normalized.toDoubleOrNull() ?: return false
        if (num < 0.0 || num > 20.0) return false
        val quarterSteps = Math.round(num * 4.0)
        val diff = Math.abs(num - (quarterSteps / 4.0))
        return diff < 0.001
    }

    fun isAllowedTypingGrade(text: String): Boolean {
        if (text.isEmpty()) return true
        val normalized = normalizeGradeDigits(text)

        val dotCount = normalized.count { it == '.' }
        if (dotCount > 1) return false
        if (!normalized.all { it.isDigit() || it == '.' }) return false

        if (dotCount == 1) {
            val parts = normalized.split('.')
            val integerPart = parts[0]
            val decimalPart = parts[1]
            if (decimalPart.length > 2) return false
            if (integerPart.length > 2) return false
            if (integerPart.isNotEmpty()) {
                val intVal = integerPart.toIntOrNull() ?: return false
                if (intVal > 20) return false
                if (intVal == 20 && decimalPart.isNotEmpty()) {
                    val decVal = decimalPart.toIntOrNull() ?: 0
                    if (decVal > 0) return false
                }
            }
        } else {
            if (normalized.length > 2) return false
            val intVal = normalized.toIntOrNull() ?: return false
            if (intVal > 20) return false
        }
        return true
    }

    fun hasInvalidGrades(): Boolean {
        val classIdx = _selectedClassIndex.value
        val classStudents = repository.students.value.filter { it.classIndex == classIdx }
        return classStudents.any { s ->
            val str = _editedGrades.value[s.id]?.trim() ?: ""
            str.isNotBlank() && !isValidGrade(str)
        }
    }

    private fun parseGradeString(text: String): Double? {
        val normalized = normalizeGradeDigits(text)
        return normalized.toDoubleOrNull()
    }

    /**
     * Updates the transient grade input for a student.
     * The teacher can modify grades in the UI.
     * Nothing is pushed to Google Sheets until the teacher explicitly clicks "Save".
     * If the teacher leaves the grades as they are, Google Sheet remains unchanged.
     */
    fun updateGradeInput(studentId: String, text: String) {
        val isAdmin = _currentTeacher.value?.isAdmin == true
        val courseId = _selectedCourseId.value
        val examType = _selectedExamType.value
        if (!isAdmin && courseId != null && repository.isCourseRedLocked(courseId, examType, _currentTeacher.value)) {
            val course = repository.courses.value.find { it.id == courseId }
            val cellRef = course?.cellReference ?: "الخلية"
            _saveStatusMessage.value = "عذراً! درجات هذه المادة مقفلة لأن خلية المادة ($cellRef) محددة باللون الأحمر في Google Sheet."
            return
        }

        if (!isAdmin && isCurrentExamLocked()) {
            _saveStatusMessage.value = "عذراً! الدرجات مقفلة نظراً لانتهاء المهلة المحددة."
            return
        }

        val current = _editedGrades.value.toMutableMap()
        current[studentId] = text
        _editedGrades.value = current
        _userEditedStudentIds.value = _userEditedStudentIds.value + studentId

        _autoSyncStatus.value = AutoSyncStatus.IDLE
        if (text.isNotBlank() && !isValidGrade(text)) {
            _saveStatusMessage.value = "تنبيه: العلامة يجب أن تكون بين 0 و 20 ومضاعفات (0.25، 0.5، 0.75) فقط."
        } else {
            _saveStatusMessage.value = null
        }
    }

    fun hasUnsavedEdits(): Boolean {
        if (isCurrentSelectionLocked()) return false
        val courseId = _selectedCourseId.value ?: return false
        val examType = _selectedExamType.value
        val classIdx = _selectedClassIndex.value
        val classStudents = repository.students.value.filter { it.classIndex == classIdx }
        for (s in classStudents) {
            val edited = _editedGrades.value[s.id]?.trim() ?: ""
            val stored = repository.getGrade(s.id, courseId, examType)
            val storedStr = if (stored != null) {
                if (stored % 1.0 == 0.0) stored.toInt().toString() else stored.toString()
            } else ""
            if (edited != storedStr) return true
        }
        return false
    }

    fun discardEdits() {
        _userEditedStudentIds.value = emptySet()
        loadGradesForCurrentSelection(force = true)
        _saveStatusMessage.value = "تم إلغاء التعديلات واستعادة درجات Google Sheet المسجلة."
    }

    fun startPeriodicAutoSync() {
        periodicSyncJob?.cancel()
        // Disabled: refresh is strictly manual per user request to maximize speed and responsiveness
    }

    fun silentRefreshFromGoogleSheet() {
        viewModelScope.launch {
            if (!_isSavingToSheet.value) {
                repository.syncWithGoogleSheets(silent = true)
            }
        }
    }

    fun isCurrentExamLocked(): Boolean {
        if (_currentTeacher.value?.isAdmin == true) return false
        return repository.isExamTypeLocked(_selectedExamType.value, _currentTeacher.value)
    }

    fun isCurrentSelectionLocked(): Boolean {
        if (_currentTeacher.value?.isAdmin == true) return false
        val courseId = _selectedCourseId.value
        val examType = _selectedExamType.value
        return if (courseId != null) {
            repository.isGradeModificationLocked(courseId, examType, _currentTeacher.value)
        } else {
            repository.isExamTypeLocked(examType, _currentTeacher.value)
        }
    }

    fun isCourseRedHighlighted(courseId: String, examType: ExamType = _selectedExamType.value): Boolean {
        return repository.isCourseRedLocked(courseId, examType)
    }

    fun isCourseLocked(courseId: String, examType: ExamType = _selectedExamType.value): Boolean {
        if (_currentTeacher.value?.isAdmin == true) return false
        return repository.isGradeModificationLocked(courseId, examType, _currentTeacher.value)
    }

    fun getSelectedCourse(): Course? {
        return repository.courses.value.find { it.id == _selectedCourseId.value }
    }

    fun isExamLocked(examType: ExamType): Boolean {
        if (_currentTeacher.value?.isAdmin == true) return false
        return repository.isExamTypeLocked(examType, _currentTeacher.value)
    }

    fun setDeadlineForTesting(examType: ExamType, dateStr: String) {
        repository.setDeadline(examType, dateStr)
        loadGradesForCurrentSelection()
    }

    fun setCurrentAppDateForTesting(dateStr: String) {
        repository.setCurrentAppDate(dateStr)
        loadGradesForCurrentSelection()
    }

    fun setCourseRedLockedForTesting(courseId: String, examType: ExamType? = null, locked: Boolean) {
        repository.setCourseRedLocked(courseId, examType, locked)
        loadGradesForCurrentSelection()
    }

    fun saveCurrentGrades() {
        val courseId = _selectedCourseId.value
        if (courseId == null) {
            _saveStatusMessage.value = "يرجى اختيار المادة الدراسية أولاً."
            return
        }
        val examType = _selectedExamType.value
        val isAdmin = _currentTeacher.value?.isAdmin == true
        if (!isAdmin && repository.isCourseRedLocked(courseId, examType, _currentTeacher.value)) {
            val course = repository.courses.value.find { it.id == courseId }
            val cellRef = course?.cellReference ?: "الخلية"
            _saveStatusMessage.value = "عذراً! درجات هذه المادة مقفلة لأن خلية المادة ($cellRef) محددة باللون الأحمر في Google Sheet."
            return
        }

        if (!isAdmin && isCurrentExamLocked()) {
            _saveStatusMessage.value = "عذراً! الدرجات مقفلة نظراً لانتهاء المهلة المحددة."
            return
        }
        val course = repository.courses.value.find { it.id == courseId }
        if (course == null) {
            _saveStatusMessage.value = "المادة الدراسية غير موجودة."
            return
        }

        val allCurrentInputs = _editedGrades.value
        val hasInvalid = allCurrentInputs.any { (_, strVal) ->
            strVal.isNotBlank() && !isValidGrade(strVal)
        }
        if (hasInvalid) {
            _saveStatusMessage.value = "عذراً! العلامة يجب أن تكون بين 0 و 20 وبقيم ربعية فقط (0.25، 0.5، 0.75)."
            return
        }

        // 1. Persist all inputs locally to repository
        allCurrentInputs.forEach { (sId, strVal) ->
            val p = parseGradeString(strVal)
            repository.saveGrade(sId, courseId, examType, p)
        }

        var webAppUrl = repository.webAppUrl.value.trim()
        if (webAppUrl.isBlank() || webAppUrl.contains("docs.google.com/spreadsheets")) {
            // Attempt to fetch latest webAppUrl from Google Sheet settings tab
            viewModelScope.launch {
                repository.syncTeachersOnly()
                val refreshedUrl = repository.webAppUrl.value.trim()
                if (refreshedUrl.isNotBlank() && !refreshedUrl.contains("docs.google.com/spreadsheets")) {
                    saveCurrentGrades()
                } else {
                    _userEditedStudentIds.value = emptySet()
                    _autoSyncStatus.value = AutoSyncStatus.SAVED_LOCAL
                    _saveStatusMessage.value = "تم الحفظ محلياً في هاتفك. للكتابة والتعديل المباشر في Google Sheet، يرجى تفعيل رابط تطبيق الويب (Apps Script)."
                    _showWebAppDialog.value = true
                }
            }
            return
        }

        val editedIds = _userEditedStudentIds.value
        val inputsToSend = if (editedIds.isNotEmpty()) {
            allCurrentInputs.filterKeys { it in editedIds }
        } else {
            allCurrentInputs
        }

        if (inputsToSend.isEmpty() && _autoSyncStatus.value == AutoSyncStatus.SYNCED) {
            _saveStatusMessage.value = "✓ كافة العلامات محفوظة ومحدثة بالفعل في Google Sheet"
            return
        }

        val batch = mutableMapOf<String, Double?>()
        inputsToSend.forEach { (sId, strVal) ->
            val p = parseGradeString(strVal)
            batch["${sId}_${courseId}_${examType.name}"] = p
        }

        _isSavingToSheet.value = true
        _autoSyncStatus.value = AutoSyncStatus.SYNCING
        _saveStatusMessage.value = "⚡ جاري إرسال وتعديل العلامات في Google Sheet..."

        viewModelScope.launch {
            try {
                val result = repository.pushGradesToGoogleSheet(course, examType, batch)
                if (result.isSuccess) {
                    _userEditedStudentIds.value = emptySet()
                    _autoSyncStatus.value = AutoSyncStatus.SYNCED
                    _saveStatusMessage.value = "✓ تم حفظ وتحديث العلامات بنجاح في Google Sheet!"
                    _autoSyncMessage.value = "متزامن مع Google Sheet"
                    // Re-sync with Google Sheet so that the app and sheet are fully synchronized
                    repository.syncWithGoogleSheets(silent = true)
                    loadGradesForCurrentSelection(force = true)
                } else {
                    _autoSyncStatus.value = AutoSyncStatus.SAVED_LOCAL
                    val rawErr = result.exceptionOrNull()?.message ?: "خطأ في الاتصال"
                    val err = if (rawErr.contains("timeout", ignoreCase = true) || rawErr.contains("timed out", ignoreCase = true)) {
                        "استغرقت الاستجابة وقتاً أطول من المعتاد (مهلة الاتصال)"
                    } else {
                        rawErr
                    }
                    _saveStatusMessage.value = "❌ تم الحفظ محلياً فقط! تعذر التعديل في Google Sheet: $err"
                    _autoSyncMessage.value = "محفوظ محلياً فقط"
                }
            } finally {
                _isSavingToSheet.value = false
            }
        }
    }

    fun updateWebAppUrl(url: String) {
        repository.setWebAppUrl(url)
    }

    suspend fun testWebAppConnection(customUrl: String? = null): Result<String> {
        return repository.testWebAppConnection(customUrl)
    }

    fun getGoogleAppsScriptCode(): String {
        return repository.getGoogleAppsScriptCode()
    }

    fun setSelectedMonth(month: Int) {
        _selectedMonth.value = month
    }

    fun addContractHour(day: Int, hours: Double, notes: String) {
        val teacher = _currentTeacher.value
        val teacherCode = teacher?.code ?: return
        val teacherName = teacher.name
        repository.addContractHour(teacherCode, _selectedMonth.value, day, hours, notes, teacherName)
    }

    fun deleteContractHour(id: String) {
        repository.deleteContractHour(id)
    }

    fun addUploadedExam(classIndex: Int, courseName: String, examType: String, fileName: String, extension: String, sizeStr: String) {
        val teacherCode = _currentTeacher.value?.code ?: "general"
        val record = UploadedExamFile(
            id = java.util.UUID.randomUUID().toString(),
            teacherCode = teacherCode,
            classIndex = classIndex,
            courseName = courseName,
            examType = examType,
            fileName = fileName,
            fileExtension = extension,
            fileSizeFormatted = sizeStr,
            uploadDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        )
        repository.addUploadedExam(record)
    }

    fun deleteUploadedExam(id: String) {
        repository.deleteUploadedExam(id)
    }

    fun toggleMissingFilter(byTeacher: Boolean) {
        _isMissingByTeacher.value = byTeacher
    }

    fun setReportsClassIndex(index: Int) {
        _reportsClassIndex.value = index
    }

    fun setStudentSearchQuery(query: String) {
        _studentSearchQuery.value = query
    }

    fun updateSheetId(newId: String, triggerFullSync: Boolean = false) {
        repository.updateSheetId(newId)
        if (triggerFullSync) {
            syncWithGoogleSheets()
        }
    }

    fun syncWithGoogleSheets() {
        viewModelScope.launch {
            val success = repository.syncWithGoogleSheets()
            if (success) {
                _userEditedStudentIds.value = emptySet()
                loadGradesForCurrentSelection(force = true)
            }
        }
    }

    private val _reportStage = MutableStateFlow(ReportStage.AUTO)
    val reportStage: StateFlow<ReportStage> = _reportStage.asStateFlow()

    fun setReportStage(stage: ReportStage) {
        _reportStage.value = stage
    }

    fun calculateReportsForClass(
        classIndex: Int,
        stage: ReportStage = _reportStage.value
    ): List<StudentReportRow> {
        val classStudents = repository.students.value.filter { it.classIndex == classIndex }
        val classCourses = repository.courses.value.filter { it.classIndex == classIndex }
            .ifEmpty { repository.courses.value }
        val query = _studentSearchQuery.value.trim()

        val allRows = classStudents.map { student ->
            repository.calculateStudentReport(student, classCourses, stage)
        }

        val sortedByScore = allRows.sortedByDescending { it.finalScore }
        val rankedRows = allRows.map { row ->
            val rankIdx = sortedByScore.indexOfFirst { it.student.id == row.student.id }
            val rank = if (rankIdx >= 0) rankIdx + 1 else 1
            row.copy(
                rank = rank,
                classStudentCount = classStudents.size
            )
        }

        return if (query.isNotBlank()) {
            rankedRows.filter { it.student.name.contains(query, ignoreCase = true) }
        } else {
            rankedRows
        }
    }

    fun setMissingGradeExamType(examType: ExamType) {
        _missingGradeExamType.value = examType
    }

    fun getMissingGrades(examType: ExamType = _missingGradeExamType.value): List<MissingGradeItem> {
        return repository.getMissingGrades(examType)
    }

    val studentPortalCustomUrl: StateFlow<String> = repository.studentPortalCustomUrl

    private val _studentPortalReport = MutableStateFlow<StudentPortalReport?>(null)
    val studentPortalReport: StateFlow<StudentPortalReport?> = _studentPortalReport.asStateFlow()

    private val _studentPortalError = MutableStateFlow<String?>(null)
    val studentPortalError: StateFlow<String?> = _studentPortalError.asStateFlow()

    private val _isQueryingStudentPortal = MutableStateFlow(false)
    val isQueryingStudentPortal: StateFlow<Boolean> = _isQueryingStudentPortal.asStateFlow()

    private val _isRefreshingQrSettings = MutableStateFlow(false)
    val isRefreshingQrSettings: StateFlow<Boolean> = _isRefreshingQrSettings.asStateFlow()

    fun refreshStudentPortalSettings(onFinished: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isRefreshingQrSettings.value = true
            val success = repository.syncSchoolSettings()
            _isRefreshingQrSettings.value = false
            onFinished?.invoke(success)
        }
    }

    fun getStudentPortalUrl(): String {
        return repository.getStudentPortalUrl()
    }

    fun setCustomStudentPortalUrl(url: String) {
        repository.setCustomStudentPortalUrl(url)
    }

    fun queryStudentPortal(phone: String, dob: String) {
        val d = repository.deadlines.value
        val c1HasDueDate = d.c1Date.isNotBlank()
        val c1Passed = !c1HasDueDate || isDatePassed(d.c1Date)
        if (c1HasDueDate && !c1Passed) {
            _studentPortalError.value = "لم يصدر اي تقرير حتى الان"
            _studentPortalReport.value = null
            return
        }

        viewModelScope.launch {
            _isQueryingStudentPortal.value = true
            _studentPortalError.value = null
            try {
                // Ensure data and exam pass dates are always refreshed first before displaying report
                try {
                    repository.syncWithGoogleSheets(silent = true)
                } catch (ignored: Exception) {
                    // Fallback to queryStudentPortal which connects directly to the script
                }
                val res = repository.queryStudentPortal(phone, dob)
                if (res.isSuccess) {
                    _studentPortalReport.value = res.getOrNull()
                } else {
                    _studentPortalError.value = res.exceptionOrNull()?.message ?: "لم يتم العثور على الطالب"
                    _studentPortalReport.value = null
                }
            } catch (e: Exception) {
                _studentPortalError.value = e.message ?: "حدث خطأ أثناء الاستعلام"
                _studentPortalReport.value = null
            } finally {
                _isQueryingStudentPortal.value = false
            }
        }
    }

    fun clearStudentPortalReport() {
        _studentPortalReport.value = null
        _studentPortalError.value = null
    }

    fun getStudentReportRowForPortal(
        report: StudentPortalReport,
        stage: ReportStage = ReportStage.AUTO
    ): Pair<StudentReportRow, List<Course>> {
        val allStudents = repository.students.value
        val normTarget = normalizeTeacherName(report.studentName)
        val matchedStudent = allStudents.firstOrNull {
            val sNorm = normalizeTeacherName(it.name)
            sNorm == normTarget || (normTarget.isNotBlank() && sNorm.contains(normTarget))
        }

        if (matchedStudent != null) {
            val classCourses = repository.courses.value.filter { it.classIndex == matchedStudent.classIndex }
                .ifEmpty { repository.courses.value }
            val classStudents = allStudents.filter { it.classIndex == matchedStudent.classIndex }
            val allRows = classStudents.map { repository.calculateStudentReport(it, classCourses, stage) }
            val sorted = allRows.sortedByDescending { it.finalScore }
            val rankIdx = sorted.indexOfFirst { it.student.id == matchedStudent.id }
            val rank = if (rankIdx >= 0) rankIdx + 1 else 1
            val baseRow = repository.calculateStudentReport(matchedStudent, classCourses, stage)
            val finalRow = baseRow.copy(rank = rank, classStudentCount = classStudents.size)
            return Pair(finalRow, classCourses)
        }

        // Synthesize a complete StudentReportRow and List<Course> from report.exams
        val syntheticCourses = mutableListOf<Course>()
        val c1Raw = mutableMapOf<String, Double?>()
        val e1Raw = mutableMapOf<String, Double?>()
        val c2Raw = mutableMapOf<String, Double?>()
        val e2Raw = mutableMapOf<String, Double?>()
        val courseFinals = mutableMapOf<String, Double?>()

        val allCourseGrades = mutableMapOf<String, StudentCoursePortalGrade>()
        report.exams.forEach { exam ->
            exam.courses.forEach { cg ->
                allCourseGrades[cg.courseName] = cg
            }
        }

        var idx = 0
        var totalFinal = 0.0
        var maxTotal = 0.0
        var c1WeightedTotal = 0.0
        var e1WeightedTotal = 0.0
        var c2WeightedTotal = 0.0
        var e2WeightedTotal = 0.0

        val effectiveStage = when (stage) {
            ReportStage.AUTO -> {
                val e2Passed = repository.isDatePassed(repository.deadlines.value.e2Date)
                val c2Passed = repository.isDatePassed(repository.deadlines.value.c2Date)
                val e1Passed = repository.isDatePassed(repository.deadlines.value.e1Date)
                val hasC1 = report.exams.any { it.examType == ExamType.C1 && it.courses.isNotEmpty() }
                val hasE1 = report.exams.any { it.examType == ExamType.E1 && it.courses.isNotEmpty() }
                val hasC2 = report.exams.any { it.examType == ExamType.C2 && it.courses.isNotEmpty() }
                val hasE2 = report.exams.any { it.examType == ExamType.E2 && it.courses.isNotEmpty() }
                when {
                    e2Passed && (hasE2 || (!hasC1 && !hasE1 && !hasC2)) -> ReportStage.ALL
                    c2Passed && hasC2 && !hasE2 -> ReportStage.C2_ONLY
                    e1Passed && hasE1 -> ReportStage.C1_E1
                    hasC1 && !hasE1 -> ReportStage.C1_ONLY // If admin put C1 grade (even if date of E1 passed show only C1)
                    e1Passed -> ReportStage.C1_E1
                    else -> ReportStage.C1_ONLY
                }
            }
            else -> stage
        }

        allCourseGrades.forEach { (cName, cg) ->
            idx++
            val cId = "portal_c_$idx"
            val coeff = (cg.maxScore / 20.0).toInt().coerceAtLeast(1)
            val course = Course(
                id = cId,
                classIndex = 1,
                name = cName,
                coefficient = coeff,
                teacherName = "أستاذ المادة",
                columnLetter = "C",
                columnIndex = idx + 2
            )
            syntheticCourses.add(course)

            val c1Grade = report.exams.firstOrNull { it.examType == ExamType.C1 }?.courses?.firstOrNull { it.courseName == cName }?.score
            val e1Grade = report.exams.firstOrNull { it.examType == ExamType.E1 }?.courses?.firstOrNull { it.courseName == cName }?.score
            val c2Grade = report.exams.firstOrNull { it.examType == ExamType.C2 }?.courses?.firstOrNull { it.courseName == cName }?.score
            val e2Grade = report.exams.firstOrNull { it.examType == ExamType.E2 }?.courses?.firstOrNull { it.courseName == cName }?.score

            c1Raw[cId] = c1Grade
            e1Raw[cId] = e1Grade
            c2Raw[cId] = c2Grade
            e2Raw[cId] = e2Grade

            if (c1Grade != null) c1WeightedTotal += c1Grade
            if (e1Grade != null) e1WeightedTotal += e1Grade
            if (c2Grade != null) c2WeightedTotal += c2Grade
            if (e2Grade != null) e2WeightedTotal += e2Grade

            val courseMax = (coeff * 20.0).coerceAtLeast(20.0)
            maxTotal += courseMax

            val c1Val = c1Grade ?: 0.0
            val e1Val = e1Grade ?: 0.0
            val c2Val = c2Grade ?: 0.0
            val e2Val = e2Grade ?: 0.0

            val f = when (effectiveStage) {
                ReportStage.ALL -> (c1Val * 0.10) + (e1Val * 0.40) + (c2Val * 0.10) + (e2Val * 0.40)
                ReportStage.C2_ONLY -> c2Val
                ReportStage.C1_E1 -> (c1Val * 0.20) + (e1Val * 0.80)
                ReportStage.C1_ONLY, ReportStage.AUTO -> c1Val
            }
            courseFinals[cId] = f
            totalFinal += f
        }

        val avg20 = if (maxTotal > 0) (totalFinal / maxTotal) * 20.0 else 0.0
        val isPass = avg20 >= 10.0

        val syntheticStudent = Student(
            id = "portal_${report.studentName.replace(" ", "_")}",
            classIndex = 1,
            className = report.className.ifBlank { "الصف المهني" },
            name = report.studentName,
            rowIndex = 1
        )

        val row = StudentReportRow(
            student = syntheticStudent,
            courseGrades = courseFinals,
            c1WeightedTotal = c1WeightedTotal,
            e1WeightedTotal = e1WeightedTotal,
            c2WeightedTotal = c2WeightedTotal,
            e2WeightedTotal = e2WeightedTotal,
            semester1Score = (c1WeightedTotal * 0.20) + (e1WeightedTotal * 0.80),
            semester2Score = (c2WeightedTotal * 0.20) + (e2WeightedTotal * 0.80),
            finalScore = totalFinal,
            isPassed = isPass,
            c1RawGrades = c1Raw,
            e1RawGrades = e1Raw,
            c2RawGrades = c2Raw,
            e2RawGrades = e2Raw,
            maxPointsTotal = if (maxTotal > 0) maxTotal else 1000.0,
            stage = effectiveStage,
            rank = 1,
            classStudentCount = 1,
            generalAverage20 = avg20
        )

        return Pair(row, syntheticCourses)
    }
}
