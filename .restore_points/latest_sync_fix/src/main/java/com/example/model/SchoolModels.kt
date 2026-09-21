package com.example.model

enum class ExamType(
    val sheetName: String,
    val displayName: String,
    val shortCode: String,
    val alternativeSheetNames: List<String> = emptyList()
) {
    C1("علامات السعي الاول", "السعي ١", "C1", listOf("علامات السعي الأول", "السعي الاول", "السعي الأول", "علامات التلاميذ", "C1")),
    E1("علامات الامتحان الاول", "الامتحان ١", "E1", listOf("علامات الامتحان الأول", "الامتحان الاول", "الامتحان الأول", "علامات الفصل الاول", "E1", "exam 1", "Exam 1", "exam1", "Exam1", "امتحان 1", "امتحان ١")),
    C2("علامات السعي الثاني", "السعي ٢", "C2", listOf("علامات السعي الثاني", "السعي الثاني", "C2")),
    E2("علامات الامتحان الثاني", "الامتحان ٢", "E2", listOf("علامات الامتحان الثاني", "الامتحان الثاني", "علامات الفصل الاخير", "E2", "exam 2", "Exam 2", "exam2", "Exam2", "امتحان 2", "امتحان ٢"))
}

enum class ReportStage(val displayName: String, val cardTitle: String) {
    AUTO("حسب التاريخ", "بطاقة علامات الطالب"),
    C1_ONLY("السعي الأول", "بطاقة علامات السعي الاول"),
    C1_E1("الفصل الأول", "بطاقة علامات الفصل الاول"),
    C2_ONLY("السعي الثاني", "بطاقة علامات السعي الثاني"),
    ALL("التقرير السنوي", "بطاقة علامات الطالب للعام")
}

data class Teacher(
    val id: String,
    val name: String,
    val code: String,
    val isAdmin: Boolean = false
)

data class Course(
    val id: String,
    val classIndex: Int = 1,
    val name: String,
    val coefficient: Int,
    val teacherName: String,
    val columnLetter: String,
    val columnIndex: Int, // 0-indexed column in sheet (C is 2, AA is 26)
    val isRedHighlighted: Boolean = false // Set if cell in Google Sheets is highlighted in red
) {
    /**
     * Row in Google Sheets where this course header is located:
     * Class 1: Row 3
     * Class 2: Row 27
     * Class 3: Row 51
     * ...
     * Class 12: Row 267
     * Formula: 3 + (classIndex - 1) * 24
     */
    val sheetRow: Int
        get() = 3 + (classIndex - 1) * 24

    /**
     * Exact cell reference in the sheet (e.g. C3, D3, C27, AA267)
     */
    val cellReference: String
        get() = "$columnLetter$sheetRow"

    companion object {
        fun getCourseRowForClass(classIndex: Int): Int = 3 + (classIndex - 1) * 24
    }
}

data class GradeCellUpdate(
    val row: Int,
    val col: Int,
    val value: Double?,
    val studentName: String = ""
)

data class Student(
    val id: String,
    val classIndex: Int, // 1 to 12
    val className: String,
    val name: String,
    val rowIndex: Int, // Sheet row (default/C1)
    val examRowMap: Map<String, Int> = emptyMap() // ExamType -> exact row in that specific sheet
) {
    fun getRowForExam(examType: ExamType): Int {
        return examRowMap[examType.shortCode]
            ?: examRowMap[examType.name]
            ?: examRowMap[examType.sheetName]
            ?: examType.alternativeSheetNames.firstNotNullOfOrNull { examRowMap[it] }
            ?: rowIndex
    }
}

data class ClassBlock(
    val index: Int, // 1 to 12
    val name: String,
    val headerRowLabel: String, // "B2", "B23", etc.
    val startRow: Int,
    val endRow: Int
)

data class StudentCourseGrade(
    val studentId: String,
    val courseId: String,
    val examType: ExamType,
    val grade: Double?
)

data class SheetDeadlines(
    val c1Date: String = "2026-10-15",
    val e1Date: String = "2026-11-20",
    val c2Date: String = "2027-02-15",
    val e2Date: String = "2027-04-30",
    val exam1UploadEnabled: Boolean = true,
    val exam2UploadEnabled: Boolean = false
) {
    fun getDate(examType: ExamType): String = when (examType) {
        ExamType.C1 -> c1Date
        ExamType.E1 -> e1Date
        ExamType.C2 -> c2Date
        ExamType.E2 -> e2Date
    }
}

data class ContractHourEntry(
    val id: String,
    val teacherCode: String,
    val monthIndex: Int, // 1..12
    val day: Int,
    val hours: Double,
    val notes: String = "",
    val teacherName: String = "",
    val isTraining: Boolean = false
)

data class TeacherContractQuota(
    val teacherName: String = "",
    val teacherCode: String = "",
    val regularHours: Double? = null,
    val trainingHours: Double? = null
)

data class ContractHoursParseResult(
    val entries: List<ContractHourEntry>,
    val quotas: Map<String, TeacherContractQuota>,
    val discoveredTeachers: List<Teacher>
)

data class FullContractHoursRow(
    val rowIndex: Int, // Sheet row (rows 3 to 49)
    val teacherName: String,
    val monthlyHours: Map<Int, Double> = emptyMap(), // month numbers 10, 11, 12, 1, 2, 3, 4, 5, 6 (columns B to J)
    val totalDone: Double = monthlyHours.values.sum(), // sum of all months (columns B to J)
    val originalQuota: Double = 0.0, // column K: original contract hours
    val remainingHours: Double = originalQuota - totalDone // column L: originalQuota - totalDone
)

data class UploadedExamFile(
    val id: String,
    val teacherCode: String,
    val classIndex: Int,
    val courseName: String,
    val examType: String, // "منتصف العام (الامتحان الأول)" or "نهاية العام (الامتحان الثاني)"
    val fileName: String,
    val fileExtension: String,
    val fileSizeFormatted: String,
    val uploadDate: String,
    val driveFolder: String = "Exam Uploads"
)

data class StudentReportRow(
    val student: Student,
    val courseGrades: Map<String, Double?>, // courseId -> grade (or calculated final)
    val c1WeightedTotal: Double,
    val e1WeightedTotal: Double,
    val c2WeightedTotal: Double,
    val e2WeightedTotal: Double,
    val semester1Score: Double?,
    val semester2Score: Double?,
    val finalScore: Double,
    val isPassed: Boolean, // threshold >= 1000
    val c1RawGrades: Map<String, Double?> = emptyMap(),
    val e1RawGrades: Map<String, Double?> = emptyMap(),
    val c2RawGrades: Map<String, Double?> = emptyMap(),
    val e2RawGrades: Map<String, Double?> = emptyMap(),
    val maxPointsTotal: Double = 2000.0,
    val stage: ReportStage = ReportStage.AUTO,
    val rank: Int = 1,
    val classStudentCount: Int = 1,
    val generalAverage20: Double = if (maxPointsTotal > 0) (finalScore / maxPointsTotal) * 20.0 else 0.0
)

fun resolveClassSpecialty(className: String): String {
    val trimmed = className.trim()
    if (trimmed.isBlank()) return ""

    // If the class name contains multiple tokens (e.g. "BT3 MEC" or "TS1 INFO" or "TS1 EDU")
    // return the specialty part (second part or everything after the degree token)
    val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.size >= 2) {
        val degreePattern = Regex("^(TS[1-3]|BT[1-3]|BP[1-3]|LT|TS|BT|BP)", RegexOption.IGNORE_CASE)
        val nonDegreeTokens = tokens.filter { !degreePattern.matches(it) }
        if (nonDegreeTokens.isNotEmpty()) {
            return nonDegreeTokens.joinToString(" ")
        }
        return tokens.drop(1).joinToString(" ")
    }

    // Fallback if only 1 token
    val upper = trimmed.uppercase()
    val match = Regex("(?:TS[1-3]?|BT[1-3]?|BP[1-3]?|LT)?([A-Z]{2,})", RegexOption.IGNORE_CASE).find(upper)
    if (match != null && match.groupValues[1].isNotBlank()) {
        return match.groupValues[1]
    }
    return trimmed
}

fun resolveDegreeAndYear(className: String): String {
    val trimmed = className.trim()
    if (trimmed.isBlank()) return ""

    // Extract exact degree and year code (e.g. "BT3", "TS1", "BP2", "LT")
    val upper = trimmed.uppercase()
    val match = Regex("\\b(TS[1-3]|BT[1-3]|BP[1-3]|LT|TS|BT|BP)\\b", RegexOption.IGNORE_CASE).find(upper)
    if (match != null) {
        return match.value.uppercase()
    }

    // If no standard token matched, take the first token
    val firstToken = trimmed.split(Regex("\\s+")).firstOrNull() ?: ""
    return firstToken.ifBlank { trimmed }
}

data class MissingGradeItem(
    val teacherName: String,
    val courseName: String,
    val classIndex: Int,
    val className: String,
    val examType: ExamType,
    val missingCount: Int,
    val totalStudents: Int,
    val studentNamesWithMissingGrades: List<String>
)

fun isTeacherNameMatch(courseTeacher: String?, loggedInTeacher: String?): Boolean {
    if (courseTeacher.isNullOrBlank() || loggedInTeacher.isNullOrBlank()) return false
    val c = normalizeTeacherName(courseTeacher)
    val l = normalizeTeacherName(loggedInTeacher)
    if (c.isBlank() || l.isBlank()) return false
    if (c == l) return true

    val cTokens = c.split(" ").filter { it.isNotBlank() }
    val lTokens = l.split(" ").filter { it.isNotBlank() }
    if (cTokens.isEmpty() || lTokens.isEmpty()) return false

    // Single token comparison
    if (cTokens.size == 1 && lTokens.size == 1) {
        return tokenMatches(cTokens.first(), lTokens.first())
    }

    // First name (Given name) MUST match strictly
    if (!tokenMatches(cTokens.first(), lTokens.first())) {
        return false
    }

    // Family / Last name MUST match strictly
    if (!tokenMatches(cTokens.last(), lTokens.last())) {
        return false
    }

    // If both have identical token count, every single token in the full name must match
    if (cTokens.size == lTokens.size) {
        return cTokens.indices.all { i -> tokenMatches(cTokens[i], lTokens[i]) }
    }

    // If token counts differ (e.g. 2 vs 3, or 3 vs 4 due to middle names):
    // Shorter must be a sub-sequence of the longer in order
    val (shortTokens, longTokens) = if (cTokens.size < lTokens.size) {
        Pair(cTokens, lTokens)
    } else {
        Pair(lTokens, cTokens)
    }

    // If short has only 2 tokens (First + Last), they already matched above
    if (shortTokens.size == 2) {
        return true
    }

    // Otherwise, every intermediate token in shortTokens must match in longTokens in order
    val shortMid = shortTokens.subList(1, shortTokens.size - 1)
    val longMid = longTokens.subList(1, longTokens.size - 1)
    var longIdx = 0
    for (sTok in shortMid) {
        var found = false
        while (longIdx < longMid.size) {
            if (tokenMatches(sTok, longMid[longIdx])) {
                found = true
                longIdx++
                break
            }
            longIdx++
        }
        if (!found) return false
    }
    return true
}

fun normalizeTeacherName(str: String): String {
    var s = str.trim().removeSurrounding("\"").removeSurrounding("'")
    // Remove honorific titles/prefixes like "أ.", "الاستاذ", "دكتور", etc.
    s = s.replace(
        Regex("^(الاستاذة|الاستاذه|الاستاذ|استاذة|استاذه|استاذ|الدكتورة|الدكتوره|الدكتور|دكتورة|دكتوره|دكتور|المهندسة|المهندسه|المهندس|مهندسة|مهندسه|مهندس|السيدة|السيده|السيد|سيدة|سيده|سيد|د\\.|أ\\.|ا\\.|م\\.|مس)\\s+"),
        ""
    )
    // Arabic character canonicalization
    s = s.replace(Regex("[أإآٱ]"), "ا")
        .replace(Regex("ة\\b"), "ه")
        .replace(Regex("ى\\b"), "ي")
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // Tashkeel / diacritics
        .replace(Regex("[\\u0640]"), "") // Tatweel
        .replace(Regex("[\"\'«»()\\[\\]{}.,;!?:\\-_/]"), " ") // Punctuation to space
    // Canonicalize compound name prefixes (e.g. عبد الله -> عبدالله, فرج الله -> فرجالله)
    s = s.replace(Regex("\\bعبد\\s+([ا-ي]+)"), "عبد$1")
        .replace(Regex("\\bفرج\\s+الله\\b"), "فرجالله")
        .replace(Regex("\\bخير\\s+الله\\b"), "خيرالله")
        .replace(Regex("\\bفضل\\s+الله\\b"), "فضلالله")
        .replace(Regex("\\bنعمه\\s+الله\\b"), "نعمهالله")
        .replace(Regex("\\bجاد\\s+الله\\b"), "جادالله")
    return s.replace(Regex("\\s+"), " ").trim()
}

private fun tokenMatches(t1: String, t2: String): Boolean {
    if (t1 == t2) return true
    // Allow optional definite article 'ال' for family names (e.g. الخوري vs خوري, الحاج vs حاج)
    val p1 = if (t1.startsWith("ال") && t1.length > 3) t1.removePrefix("ال") else t1
    val p2 = if (t2.startsWith("ال") && t2.length > 3) t2.removePrefix("ال") else t2
    return p1 == p2
}

data class StudentCoursePortalGrade(
    val courseName: String,
    val score: Double?,
    val maxScore: Double,
    val coefficient: Int = 1
)

data class StudentExamPortalResult(
    val examType: ExamType,
    val examName: String,
    val deadlineDate: String, // Cell A1
    val isClosed: Boolean, // Whether Cell A1 deadline date has passed
    val courses: List<StudentCoursePortalGrade> = emptyList(),
    val totalScore: Double = 0.0,
    val maxTotalScore: Double = 0.0,
    val averageScore: Double = 0.0,
    val statusMessage: String = ""
)

data class StudentPortalReport(
    val studentName: String,
    val className: String,
    val mobile: String,
    val dateOfBirth: String,
    val exams: List<StudentExamPortalResult> = emptyList(),
    val schoolName: String = "",
    val schoolYear: String = ""
)

/**
 * School entry fetched from the Master Sheet (ID: 1lqbi4ArzITb3z6jINq5OUHjzPo0_Gkcla_Og6iMGwUs, Tab: Master).
 * Column A: Sheet ID
 * Column B: School Name
 * Column C: Status (active / inactive)
 */
data class MasterSchool(
    val sheetId: String,
    val schoolName: String,
    val status: String = "active"
) {
    val isActive: Boolean
        get() {
            val s = status.trim().lowercase()
            return s == "active" ||
                   s == "نشط" ||
                   s == "مفعل" ||
                   s == "نعم" ||
                   s == "yes" ||
                   s == "true" ||
                   s == "1" ||
                   s == "1.0"
        }
}


