package com.example.data

import com.example.model.ClassBlock
import com.example.model.ContractHourEntry
import com.example.model.ContractHoursParseResult
import com.example.model.Course
import com.example.model.ExamType
import com.example.model.FullContractHoursRow
import com.example.model.GradeCellUpdate
import com.example.model.MasterSchool
import com.example.model.SheetDeadlines
import com.example.model.Student
import com.example.model.StudentCoursePortalGrade
import com.example.model.StudentExamPortalResult
import com.example.model.StudentPortalReport
import com.example.model.Teacher
import com.example.model.TeacherContractQuota
import com.example.model.isTeacherNameMatch
import com.example.model.normalizeTeacherName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleSheetsService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // 12 class blocks as defined in the Google Sheet:
    // Class 1: B2 (students: B4 to B23)
    // Class 2: B26 (students: B28 to B47)
    // Class 3: B50 (students: B52 to B71)
    // Class 4: B74 (students: B76 to B95)
    // Class 5: B98 (students: B100 to B119)
    // Class 6: B122 (students: B124 to B143)
    // Class 7: B146 (students: B148 to B167)
    // Class 8: B170 (students: B172 to B191)
    // Class 9: B194 (students: B196 to B215)
    // Class 10: B218 (students: B220 to B239)
    // Class 11: B242 (students: B244 to B263)
    // Class 12: B266 (students: B268 to B287)
    val classBlocks: List<ClassBlock> = listOf(
        ClassBlock(1, "TS1 INF", "B2", 4, 23),
        ClassBlock(2, "TS1 EXP", "B26", 28, 47),
        ClassBlock(3, "TS1 HOT", "B50", 52, 71),
        ClassBlock(4, "TS1 EDU", "B74", 76, 95),
        ClassBlock(5, "TS1 CLI", "B98", 100, 119),
        ClassBlock(6, "TS1 ELI", "B122", 124, 143),
        ClassBlock(7, "TS2 INFO", "B146", 148, 167),
        ClassBlock(8, "TS2 EXP", "B170", 172, 191),
        ClassBlock(9, "TS2 HOT", "B194", 196, 215),
        ClassBlock(10, "TS2 EDU", "B218", 220, 239),
        ClassBlock(11, "TS2 CLI", "B242", 244, 263),
        ClassBlock(12, "TS2 ELI", "B266", 268, 287)
    )

    companion object {
        const val DEFAULT_MASTER_SHEET_ID = "1lqbi4ArzITb3z6jINq5OUHjzPo0_Gkcla_Og6iMGwUs"
        const val MASTER_SHEET_TAB = "Master"

        fun extractSheetId(input: String): String {
            val trimmed = input.trim()
            return if (trimmed.contains("/d/")) {
                trimmed.substringAfter("/d/").substringBefore("/").substringBefore("?").substringBefore("#")
            } else {
                trimmed.substringBefore("?").substringBefore("#")
            }
        }

        fun sanitizeMasterSheetId(input: String): String {
            val clean = extractSheetId(input).trim()
            if (clean == "1lqbi4ArzITb3z6jINq5OUHjzPo0_Gkcla_Og6iMGwU") {
                return DEFAULT_MASTER_SHEET_ID
            }
            return clean.ifBlank { DEFAULT_MASTER_SHEET_ID }
        }
    }

    /**
     * Fetches the list of schools from the Master Google Sheet.
     * Column A: School Sheet ID
     * Column B: School Name (shown in app dropdown)
     * Column C: Status (active vs inactive)
     * Rule: If Column C is "active", the school is included in the dropdown list.
     *       If Column C is "inactive", it is excluded.
     */
    suspend fun fetchMasterSchools(
        masterSheetId: String = DEFAULT_MASTER_SHEET_ID,
        tabName: String = MASTER_SHEET_TAB,
        webAppUrl: String? = null
    ): Result<List<MasterSchool>> = withContext(Dispatchers.IO) {
        val cleanMasterId = sanitizeMasterSheetId(masterSheetId)
        val candidateParams = mutableListOf<String>()
        if (tabName.isNotBlank()) candidateParams.add("sheet=" + URLEncoder.encode(tabName, "UTF-8"))
        candidateParams.add("sheet=master")
        candidateParams.add("sheet=Master")
        candidateParams.add("gid=0")
        candidateParams.add("") // default first sheet
        candidateParams.add("sheet=Sheet1")
        candidateParams.add("sheet=" + URLEncoder.encode("المدارس", "UTF-8"))
        candidateParams.add("sheet=" + URLEncoder.encode("ورقة1", "UTF-8"))
        val distinctParams = candidateParams.distinct()

        var csvRows: List<List<String>>? = null
        var lastError: Exception? = null

        // 1. Try GViz CSV export with candidate params
        for (param in distinctParams) {
            val paramStr = if (param.isNotBlank()) "&$param" else ""
            val timestamp = System.currentTimeMillis()
            val url = "https://docs.google.com/spreadsheets/d/$cleanMasterId/gviz/tq?tqx=out:csv$paramStr&_=$timestamp"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "EMIS-SchoolManager/1.0")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .build()
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    if (response.code == 401 || response.code == 403) {
                        lastError = Exception("خطأ في الصلاحيات (HTTP ${response.code}): يرجى مشاركة جدول Google لـ «أي شخص لديه الرابط» (Anyone with the link can view)")
                    } else if (response.code == 404) {
                        lastError = Exception("خطأ (HTTP 404): جدول Master غير موجود. يرجى التأكد من صحة معرف الجدول (Sheet ID)")
                    }
                    continue
                }
                val body = response.body?.string() ?: ""
                if (body.contains("accounts.google.com") || body.contains("ServiceLogin") ||
                    body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) ||
                    body.trimStart().startsWith("<html", ignoreCase = true)) {
                    lastError = Exception("خطأ في الصلاحيات (HTTP 401): يرجى مشاركة جدول Google لـ «أي شخص لديه الرابط» (Anyone with the link can view)")
                    continue
                }
                val rows = parseCsv(body)
                if (rows.any { it.size >= 2 }) {
                    csvRows = rows
                    break
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        // 2. If GViz failed, try export?format=csv
        if (csvRows.isNullOrEmpty()) {
            for (param in distinctParams) {
                try {
                    val paramStr = if (param.isNotBlank()) "&$param" else ""
                    val timestamp = System.currentTimeMillis()
                    val url = "https://docs.google.com/spreadsheets/d/$cleanMasterId/export?format=csv$paramStr&_=$timestamp"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "EMIS-SchoolManager/1.0")
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (!body.contains("accounts.google.com") && !body.contains("ServiceLogin") &&
                            !body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) &&
                            !body.trimStart().startsWith("<html", ignoreCase = true)) {
                            val rows = parseCsv(body)
                            if (rows.any { it.size >= 2 }) {
                                csvRows = rows
                                break
                            }
                        } else {
                            lastError = Exception("خطأ في الصلاحيات (HTTP 401): يرجى مشاركة جدول Google لـ «أي شخص لديه الرابط» (Anyone with the link can view)")
                        }
                    } else if (response.code == 401 || response.code == 403) {
                        lastError = Exception("خطأ في الصلاحيات (HTTP ${response.code}): يرجى مشاركة جدول Google لـ «أي شخص لديه الرابط» (Anyone with the link can view)")
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }

        // 3. If Web App URL is provided, try querying it via getMasterSchools
        if (csvRows.isNullOrEmpty() && !webAppUrl.isNullOrBlank()) {
            try {
                val trimmed = webAppUrl.trim()
                val delimiter = if (trimmed.contains("?")) "&" else "?"
                val url = "${trimmed}${delimiter}action=getMasterSchools&masterSheetId=${URLEncoder.encode(cleanMasterId, "UTF-8")}&sheetName=${URLEncoder.encode(tabName, "UTF-8")}"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: ""
                    val jsonObj = JSONObject(jsonStr)
                    if (jsonObj.optString("status") == "success") {
                        val array = jsonObj.optJSONArray("schools") ?: JSONArray()
                        val list = mutableListOf<MasterSchool>()
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val sId = extractSheetId(item.optString("sheetId"))
                            val sName = item.optString("schoolName").trim()
                            val sStatus = item.optString("status").trim()
                            val school = MasterSchool(sId, sName, sStatus)
                            if (school.isActive && sId.isNotBlank() && sName.isNotBlank()) {
                                list.add(school)
                            }
                        }
                        if (list.isNotEmpty()) {
                            return@withContext Result.success(list)
                        }
                    }
                } else if (response.code == 401 || response.code == 403) {
                    lastError = Exception("خطأ في Web App (HTTP ${response.code}): يرجى ضبط الصلاحية في Apps Script على Anyone")
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (csvRows.isNullOrEmpty()) {
            return@withContext Result.failure(lastError ?: Exception("تعذر قراءة جدول المدارس (Master Sheet)."))
        }

        Result.success(parseMasterSchoolsCsv(csvRows))
    }

    /**
     * Parses the CSV rows of the Master Spreadsheet:
     * - Column A: Google Sheet ID
     * - Column B: School Name (strictly taken from here, never hardcoded)
     * - Column C: Status (must be "active" to be shown in the app)
     */
    fun parseMasterSchoolsCsv(rows: List<List<String>>): List<MasterSchool> {
        val resultList = mutableListOf<MasterSchool>()
        for ((index, row) in rows.withIndex()) {
            if (row.size < 2) continue
            val colA = row.getOrNull(0)?.trim()?.removeSurrounding("\"")?.trim() ?: ""
            val colB = row.getOrNull(1)?.trim()?.removeSurrounding("\"")?.trim() ?: ""
            val colC = row.getOrNull(2)?.trim()?.removeSurrounding("\"")?.trim() ?: ""

            // Skip empty rows
            if (colA.isBlank() && colB.isBlank()) continue

            val cleanId = extractSheetId(colA)
            val isColAId = cleanId.length >= 20 && !cleanId.contains(" ") && !cleanId.contains("\n")

            // Skip header row ONLY if Column A is NOT already a valid Google Sheet ID
            if (index == 0 && !isColAId && (
                colA.equals("sheet id", ignoreCase = true) ||
                colA.equals("sheet", ignoreCase = true) ||
                colA.equals("id", ignoreCase = true) ||
                colA.contains("معرف", ignoreCase = true) ||
                colA.contains("رابط", ignoreCase = true) ||
                colB.equals("school", ignoreCase = true) ||
                colB.equals("name", ignoreCase = true) ||
                colB.equals("اسم", ignoreCase = true) ||
                colB.equals("المدرسة", ignoreCase = true) ||
                colB.equals("اسم المدرسة", ignoreCase = true) ||
                colC.equals("status", ignoreCase = true) ||
                colC.equals("حالة", ignoreCase = true)
            )) {
                continue
            }

            if (cleanId.isBlank() || colB.isBlank()) continue

            val school = MasterSchool(sheetId = cleanId, schoolName = colB, status = colC)
            // Filter: Column C must be active to be shown
            if (school.isActive) {
                resultList.add(school)
            }
        }
        return resultList
    }

    private val tabsWithGidCache = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    fun invalidateTabsCache(sheetId: String? = null) {
        if (sheetId != null) tabsWithGidCache.remove(sheetId) else tabsWithGidCache.clear()
    }

    suspend fun fetchSheetCsv(
        sheetId: String,
        tabTitle: String,
        explicitGid: String? = null
    ): Result<List<List<String>>> = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()

        // 1. First priority: Use Google Sheets Direct CSV Export (export?format=csv&gid=...).
        // Unlike GViz (/gviz/tq?tqx=out:csv), which performs SQL type inference and drops text cells
        // (such as teacher and course names) in columns heavily populated with numeric grades,
        // direct export guarantees 100% cell fidelity and preserves all header texts and grades.
        var gid = explicitGid?.trim()
        if (gid.isNullOrBlank() && tabTitle.isNotBlank()) {
            val cachedMap = tabsWithGidCache[sheetId] ?: fetchSheetTabsWithGid(sheetId)
            val normTarget = normalizeTabName(tabTitle)
            gid = cachedMap.entries.firstOrNull { (k, _) -> normalizeTabName(k) == normTarget }?.value
                ?: cachedMap[tabTitle]
                ?: cachedMap.entries.firstOrNull { (k, _) ->
                    val normK = normalizeTabName(k)
                    normK.contains(normTarget) || normTarget.contains(normK)
                }?.value
        }

        if (!gid.isNullOrBlank()) {
            try {
                val exportUrl = "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv&gid=$gid&_=$timestamp"
                val request = Request.Builder()
                    .url(exportUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (!body.contains("accounts.google.com") &&
                        !body.contains("ServiceLogin") &&
                        !body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) &&
                        !body.trimStart().startsWith("<html", ignoreCase = true)
                    ) {
                        val rows = parseCsv(body)
                        if (rows.isNotEmpty()) {
                            return@withContext Result.success(rows)
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall back to GViz
            }
        }

        // 2. Secondary fallback: Google Visualization API
        try {
            val encodedTab = URLEncoder.encode(tabTitle, "UTF-8")
            val sheetParam = if (tabTitle.isNotBlank()) "&sheet=$encodedTab" else ""
            val gvizUrl = "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:csv$sheetParam&_=$timestamp"
            val request = Request.Builder()
                .url(gvizUrl)
                .header("User-Agent", "EMIS-SchoolManager/1.0")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) {
                    return@withContext Result.failure(Exception("خطأ في الصلاحيات (HTTP ${response.code}): يرجى مشاركة الجدول لـ «أي شخص لديه الرابط» (Anyone with the link can view)"))
                }
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
            val body = response.body?.string() ?: ""
            if (body.contains("accounts.google.com") || body.contains("ServiceLogin") ||
                body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) ||
                body.trimStart().startsWith("<html", ignoreCase = true)) {
                return@withContext Result.failure(Exception("يتطلب جدول Google إذن وصول. يرجى التأكد من تغيير خيار المشاركة إلى «أي شخص لديه الرابط» (Anyone with the link can view)"))
            }
            val rows = parseCsv(body)
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches exact tab names and their GIDs directly from the Google Spreadsheet HTML view.
     */
    suspend fun fetchSheetTabsWithGid(sheetId: String): Map<String, String> = withContext(Dispatchers.IO) {
        val cached = tabsWithGidCache[sheetId]
        if (!cached.isNullOrEmpty()) return@withContext cached
        try {
            val timestamp = System.currentTimeMillis()
            val url = "https://docs.google.com/spreadsheets/d/$sheetId/htmlview?_=$timestamp"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyMap()
            val body = response.body?.string() ?: ""
            val regex = Regex("""name:\s*"([^"]+)"[^}]*?gid:\s*"(\d+)"""")
            val map = mutableMapOf<String, String>()
            regex.findAll(body).forEach { match ->
                val name = match.groupValues[1]
                val gid = match.groupValues[2]
                map[name] = gid
            }
            if (map.isNotEmpty()) {
                tabsWithGidCache[sheetId] = map
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Fetches exact tab names directly from the Google Spreadsheet HTML view.
     */
    suspend fun fetchSheetTabNames(sheetId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val tabsWithGid = fetchSheetTabsWithGid(sheetId)
            if (tabsWithGid.isNotEmpty()) {
                return@withContext tabsWithGid.keys.toList()
            }
            val timestamp = System.currentTimeMillis()
            val url = "https://docs.google.com/spreadsheets/d/$sheetId/htmlview?_=$timestamp"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: ""
            val matches = Regex("name:\\s*\"([^\"]+)\"").findAll(body)
            matches.map { it.groupValues[1] }.distinct().toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun normalizeTabName(name: String): String {
        return name
            .lowercase()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ٱ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Attempts to fetch sheet CSV by trying the primary sheet name (e.g. "علامات السعي الاول")
     * followed by any fallback/alternative sheet names (e.g. "علامات السعي الأول", "السعي الاول").
     * Returns Pair(matchedTabTitle, rows).
     */
    suspend fun fetchExamSheetCsv(
        sheetId: String,
        examType: ExamType,
        knownTabs: List<String> = emptyList(),
        tabsWithGid: Map<String, String> = emptyMap()
    ): Result<Pair<String, List<List<String>>>> = withContext(Dispatchers.IO) {
        val gidMap = if (tabsWithGid.isNotEmpty()) {
            tabsWithGidCache[sheetId] = tabsWithGid
            tabsWithGid
        } else {
            tabsWithGidCache[sheetId] ?: fetchSheetTabsWithGid(sheetId)
        }

        val candidates = listOf(examType.sheetName) + examType.alternativeSheetNames

        // If known tabs exist in the spreadsheet, prioritize the one that matches
        val resolvedTab = if (knownTabs.isNotEmpty()) {
            candidates.firstOrNull { candidate ->
                val normCand = normalizeTabName(candidate)
                knownTabs.any { normalizeTabName(it) == normCand }
            }?.let { matchedCandidate ->
                val normMatched = normalizeTabName(matchedCandidate)
                knownTabs.find { normalizeTabName(it) == normMatched } ?: matchedCandidate
            }
        } else null

        val searchList = if (resolvedTab != null) listOf(resolvedTab) + candidates.filter { it != resolvedTab } else candidates

        var lastError: Exception? = null
        for (candidate in searchList) {
            val normCand = normalizeTabName(candidate)
            val explicitGid = gidMap.entries.firstOrNull { (k, _) -> normalizeTabName(k) == normCand }?.value
                ?: gidMap[candidate]
            val res = fetchSheetCsv(sheetId, candidate, explicitGid)
            if (res.isSuccess && res.getOrThrow().isNotEmpty()) {
                return@withContext Result.success(Pair(candidate, res.getOrThrow()))
            } else if (res.isFailure) {
                lastError = res.exceptionOrNull() as? Exception ?: Exception(res.exceptionOrNull()?.message)
            }
        }
        Result.failure(lastError ?: Exception("التبويب غير موجود: ${examType.sheetName}"))
    }

    fun parseCsv(csvText: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var insideQuotes = false
        var i = 0

        while (i < csvText.length) {
            val c = csvText[i]
            when {
                c == '\"' -> {
                    if (insideQuotes && i + 1 < csvText.length && csvText[i + 1] == '\"') {
                        currentField.append('\"')
                        i++ // skip escaped quote
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }
                c == ',' && !insideQuotes -> {
                    currentRow.add(currentField.toString().trim())
                    currentField.clear()
                }
                (c == '\r' || c == '\n') && !insideQuotes -> {
                    if (c == '\r' && i + 1 < csvText.length && csvText[i + 1] == '\n') {
                        i++
                    }
                    currentRow.add(currentField.toString().trim())
                    currentField.clear()
                    rows.add(currentRow)
                    currentRow = mutableListOf()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            rows.add(currentRow)
        }

        return rows
    }

    fun extractSheetId(input: String): String = Companion.extractSheetId(input)

    /**
     * Data class holding School Name (Cell C1), School Year (Cell C2),
     * and optional Web App URL (Cell C3 or any cell containing script.google.com)
     * read from sheet "الإعدادات".
     */
    data class SchoolSettingsInfo(
        val schoolName: String = "",
        val schoolYear: String = "2025-2026",
        val webAppUrl: String = ""
    )

    /**
     * Parses School Name (cell C1), School Year (cell C2), and Web App URL (cell C3) from sheet "الإعدادات".
     * In 0-indexed CSV rows:
     * - Cell C1 is row index 0, column index 2
     * - Cell C2 is row index 1, column index 2
     * - Cell C3 is row index 2, column index 2
     * Also searches all cells in the settings sheet for any Google Apps Script Web App URL (script.google.com/macros/s/.../exec).
     */
    fun parseSchoolSettingsInfo(rows: List<List<String>>): SchoolSettingsInfo {
        val cellC1 = rows.getOrNull(0)?.getOrNull(2)?.trim()?.removeSurrounding("\"")?.trim() ?: ""
        val cellC2 = rows.getOrNull(1)?.getOrNull(2)?.trim()?.removeSurrounding("\"")?.trim() ?: ""
        val cellC3 = rows.getOrNull(2)?.getOrNull(2)?.trim()?.removeSurrounding("\"")?.trim() ?: ""

        val defaultName = ""
        val defaultYear = "2025-2026"

        val schoolName = if (cellC1.isNotBlank()) cellC1 else defaultName
        val schoolYear = if (cellC2.isNotBlank()) cellC2 else defaultYear

        // Look for Google Apps Script Web App URL in Cell C3, or anywhere across rows
        var parsedWebAppUrl = ""
        if (cellC3.contains("script.google.com/macros/s/") || cellC3.startsWith("http://") || cellC3.startsWith("https://")) {
            parsedWebAppUrl = cellC3
        } else {
            // Scan cells in settings rows to find any script.google.com URL
            for (row in rows) {
                for (cell in row) {
                    val trimmed = cell.trim().removeSurrounding("\"").trim()
                    if (trimmed.contains("script.google.com/macros/s/")) {
                        parsedWebAppUrl = trimmed
                        break
                    }
                }
                if (parsedWebAppUrl.isNotBlank()) break
            }
        }

        return SchoolSettingsInfo(schoolName = schoolName, schoolYear = schoolYear, webAppUrl = parsedWebAppUrl)
    }

    /**
     * Parses "الإعدادات" tab for teachers and codes:
     * Column A: Teacher Name, Column B: Code/Password, Column C: Role (optional)
     */
    fun parseTeachers(rows: List<List<String>>, autoAddDefaultAdmin: Boolean = false): List<Teacher> {
        val teachers = mutableListOf<Teacher>()
        for (row in rows) {
            if (row.size >= 2) {
                val name = row[0].trim().removeSurrounding("\"")
                val code = row[1].trim().removeSurrounding("\"")
                val role = if (row.size >= 3) row[2].trim().removeSurrounding("\"") else ""

                val isExplicitAdminLabel = name.contains("رمز الإدارة") ||
                        name.contains("كود الإدارة") ||
                        name.contains("رمز المدير") ||
                        name.contains("كود المدير") ||
                        name.contains("admin_code", ignoreCase = true)

                val isHeader = (name.contains("المعلم") || name.contains("الاستاذ") || name.contains("اسم") || name.equals("Name", ignoreCase = true)) && !isExplicitAdminLabel

                if (name.isNotBlank() && code.isNotBlank() && !isHeader) {
                    val isAdmin = isExplicitAdminLabel ||
                            name.equals("admin", ignoreCase = true) ||
                            code.equals("admin", ignoreCase = true) ||
                            code.equals("admin123", ignoreCase = true) ||
                            name.contains("المدير") || name.contains("مدير") || name.contains("مديرة") ||
                            name.contains("الإدارة") || name.contains("إدارة") || name.contains("ادارة") ||
                            name.contains("مسؤول") || name.contains("مشرف") ||
                            role.contains("admin", ignoreCase = true) ||
                            role.contains("مدير") || role.contains("إدارة") || role.contains("ادارة")
                    teachers.add(
                        Teacher(
                            id = "t_${teachers.size + 1}",
                            name = if (isExplicitAdminLabel) "إدارة المدرسة (Admin)" else name,
                            code = code,
                            isAdmin = isAdmin
                        )
                    )
                }
            }
        }
        if (autoAddDefaultAdmin && teachers.none { it.isAdmin }) {
            teachers.add(Teacher("t_admin", "admin", "admin123", isAdmin = true))
        }
        return teachers
    }

    private val standardClassNames = listOf(
        Pair(1, "TS1 INF"),
        Pair(2, "TS1 EXP"),
        Pair(3, "TS1 HOT"),
        Pair(4, "TS1 EDU"),
        Pair(5, "TS1 CLI"),
        Pair(6, "TS1 ELI"),
        Pair(7, "TS2 INFO"),
        Pair(8, "TS2 EXP"),
        Pair(9, "TS2 HOT"),
        Pair(10, "TS2 EDU"),
        Pair(11, "TS2 CLI"),
        Pair(12, "TS2 ELI")
    )

    fun determineClassIndex(name: String, headerRow0Based: Int): Int {
        val clean = name.uppercase().replace(" ", "").replace("_", "").replace("-", "")
        for ((idx, sName) in standardClassNames) {
            val sClean = sName.uppercase().replace(" ", "").replace("_", "").replace("-", "")
            if (clean == sClean) return idx
        }
        for ((idx, sName) in standardClassNames) {
            val sClean = sName.uppercase().replace(" ", "").replace("_", "").replace("-", "")
            if (clean.startsWith(sClean) || sClean.startsWith(clean)) return idx
        }

        val estimatedFromRow = kotlin.math.round((headerRow0Based - 1) / 24.0).toInt() + 1
        if (estimatedFromRow in 1..12) {
            val expectedRow0Based = 1 + (estimatedFromRow - 1) * 24
            if (kotlin.math.abs(headerRow0Based - expectedRow0Based) <= 5) {
                return estimatedFromRow
            }
        }
        return estimatedFromRow.coerceIn(1, 12)
    }

    data class DetectedClass(
        val classIndex: Int,
        val name: String,
        val headerRow0Based: Int,
        val studentsStart0Based: Int,
        val studentsEnd0Based: Int
    )

    /**
     * Dynamically detects all class header rows in the given sheet rows.
     * In the user's sheets, each class has:
     * - Column B containing the class code/name (e.g. TS1 INF, TS1 EXP... or BT3 MEC)
     * - Row directly below containing course names across Columns C to AA.
     * Student rows (with numbers 1..60 in Col A and grades in Cols C..AA) are strictly excluded.
     */
    fun detectClassHeaders(rows: List<List<String>>): List<DetectedClass> {
        if (rows.isEmpty()) return emptyList()

        val detected = mutableListOf<Pair<Int, String>>() // 0-based row, name
        for (rIdx in rows.indices) {
            // Row 0 in exam sheets contains the deadline in A1 and coefficients, never a class header
            if (rIdx == 0) continue

            val row = rows[rIdx]
            val col0 = row.getOrNull(0)?.trim()?.removeSurrounding("\"") ?: ""
            val col0Int = col0.toIntOrNull()
            // Student rows ALWAYS have their serial number (1, 2, 3.. 60) in Column A.
            // A class header row NEVER has a student serial number in Column A.
            if (col0Int != null && col0Int in 1..60) {
                continue
            }

            // Also check if the NEXT row has student serial number >= 2 in Column A
            // (meaning this row was student 1 and next is student 2)
            val nextRowCol0Int = rows.getOrNull(rIdx + 1)?.getOrNull(0)?.trim()?.removeSurrounding("\"")?.toIntOrNull()
            if (nextRowCol0Int != null && nextRowCol0Int in 2..60) {
                continue
            }

            val candidateB = row.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: ""
            if (candidateB.isBlank() ||
                candidateB.contains("اسم الطالب") ||
                candidateB.contains("الاسم") ||
                candidateB.contains("Course", ignoreCase = true) ||
                candidateB.contains("المواد") ||
                candidateB.contains("المعامل") ||
                candidateB.contains("ملاحظات") ||
                candidateB.contains("السعي") ||
                candidateB.contains("الامتحان") ||
                candidateB.toIntOrNull() != null
            ) {
                continue
            }

            // Minimum distance check: Two class headers are separated by ~24 rows, never < 12 rows apart
            if (detected.isNotEmpty() && (rIdx - detected.last().first) < 12) {
                continue
            }

            val isTsPrefix = candidateB.startsWith("TS", ignoreCase = true) || candidateB.startsWith("ts", ignoreCase = true)
            val isKnownPrefix = isTsPrefix ||
                candidateB.startsWith("BT", ignoreCase = true) ||
                candidateB.startsWith("BP", ignoreCase = true) ||
                candidateB.startsWith("LT", ignoreCase = true) ||
                candidateB.startsWith("LET", ignoreCase = true) ||
                candidateB.startsWith("الصف", ignoreCase = true) ||
                candidateB.startsWith("صف", ignoreCase = true) ||
                candidateB.startsWith("Class", ignoreCase = true)

            val matchesStandardClass = standardClassNames.any { (_, sName) ->
                val sClean = sName.uppercase().replace(" ", "").replace("_", "").replace("-", "")
                val cClean = candidateB.uppercase().replace(" ", "").replace("_", "").replace("-", "")
                cClean == sClean || cClean.startsWith(sClean) || sClean.startsWith(cClean) ||
                    listOf("INF", "EXP", "HOT", "EDU", "CLI", "ELI", "INFO", "MEC", "ELEC").any { cClean.contains(it) }
            }

            // Standard step rows: 1, 25, 49, 73, etc. (Row 2, 26, 50 in Excel)
            val isStandardStepRow = (rIdx - 1) >= 0 && (rIdx - 1) % 24 == 0

            if (isKnownPrefix || matchesStandardClass || isStandardStepRow) {
                detected.add(Pair(rIdx, candidateB))
            }
        }

        // If dynamic detection found nothing, check known 24-row offsets (Class 1 at row 2, Class 2 at row 26, etc.)
        if (detected.isEmpty()) {
            val step24Rows = listOf(1, 25, 49, 73, 97, 121, 145, 169, 193, 217, 241, 265)
            step24Rows.forEachIndexed { _, rIdx ->
                val row = rows.getOrNull(rIdx)
                val name = row?.getOrNull(1)?.trim()?.removeSurrounding("\"")
                val col0Int = row?.getOrNull(0)?.trim()?.removeSurrounding("\"")?.toIntOrNull()
                if (!name.isNullOrBlank() && !name.contains("اسم الطالب") && name.toIntOrNull() == null && (col0Int == null || col0Int !in 1..60)) {
                    detected.add(Pair(rIdx, name))
                }
            }
        }

        val result = mutableListOf<DetectedClass>()
        for (i in detected.indices) {
            val (headerRow0Based, name) = detected[i]
            val sStart = (headerRow0Based + 2).coerceAtMost(rows.size)
            val sEnd = (headerRow0Based + 21).coerceAtLeast(sStart).coerceAtMost((rows.size - 1).coerceAtLeast(0))
            val classIndex = determineClassIndex(name, headerRow0Based)
            result.add(
                DetectedClass(
                    classIndex = classIndex,
                    name = name,
                    headerRow0Based = headerRow0Based,
                    studentsStart0Based = sStart,
                    studentsEnd0Based = sEnd
                )
            )
        }
        return result.distinctBy { it.classIndex }.sortedBy { it.classIndex }
    }

    /**
     * Parses and synchronizes the class names from their header rows in the sheet.
     * Uses dynamic class detection, correctly locating all 12 classes without assuming
     * rigid offsets that misidentify student rows as class names.
     */
    fun parseClassBlocks(rows: List<List<String>>): List<ClassBlock> {
        if (rows.isEmpty()) return classBlocks
        val detected = detectClassHeaders(rows)
        if (detected.isNotEmpty()) {
            return detected.map { d ->
                ClassBlock(
                    index = d.classIndex,
                    name = d.name,
                    headerRowLabel = "B${d.headerRow0Based + 1}",
                    startRow = d.studentsStart0Based + 1,
                    endRow = d.studentsEnd0Based + 1
                )
            }.sortedBy { it.index }
        }
        return classBlocks
    }

    /**
     * Parses courses, teachers, and coefficients for all classes from the grade sheet.
     * Dynamically finds the class header rows, reads teacher names from the header row (Cols C to AA),
     * course names from the row directly below it, and coefficients from the row directly above it.
     * If an exam sheet has empty header cells for a column (e.g. teacher or course name), it falls back
     * to the master definition in [fallbackRows] (typically C1 "علامات السعي الاول").
     */
    fun parseExamCourses(
        examRows: List<List<String>>,
        fallbackRows: List<List<String>> = emptyList()
    ): List<Course> {
        val fallbacks = if (fallbackRows.isNotEmpty()) listOf(fallbackRows) else emptyList()
        return parseExamCoursesMulti(examRows, fallbacks)
    }

    /**
     * Parses courses dynamically across multiple exam tabs (e.g. C1, E1, C2, E2).
     * If an exam tab (such as C1) has blank cells in its course or teacher header row for higher columns (e.g. col 12-17),
     * but other tabs (such as E1 or E2) have the course names and teachers, or if coefficients / student grades exist,
     * this method unifies the subject definitions so no courses or student grades are missed or dropped.
     */
    fun parseExamCoursesMulti(
        examRows: List<List<String>>,
        allFallbackRows: List<List<List<String>>> = emptyList()
    ): List<Course> {
        val courses = mutableListOf<Course>()
        val allSheets = (listOf(examRows) + allFallbackRows).filter { it.isNotEmpty() }
        if (allSheets.isEmpty()) return emptyList()

        val sheetDetections = allSheets.map { sheet -> sheet to detectClassHeaders(sheet) }
        val allClasses = sheetDetections
            .flatMap { it.second.map { d -> d.classIndex } }
            .distinct()
            .sorted()

        val effectiveClassIndices = if (allClasses.isNotEmpty()) allClasses else (1..12).toList()

        effectiveClassIndices.forEach { classIndex ->
            val sheetInfos = sheetDetections.mapNotNull { (sheet, detections) ->
                val d = detections.find { it.classIndex == classIndex }
                if (d != null) {
                    val header = sheet.getOrNull(d.headerRow0Based) ?: emptyList()
                    val next = sheet.getOrNull(d.headerRow0Based + 1) ?: emptyList()
                    val coeff = if (d.headerRow0Based > 0) sheet.getOrNull(d.headerRow0Based - 1) ?: emptyList() else emptyList()
                    val students = if (d.studentsEnd0Based >= d.studentsStart0Based && d.studentsStart0Based < sheet.size) {
                        sheet.subList(d.studentsStart0Based, minOf(d.studentsEnd0Based + 1, sheet.size))
                    } else emptyList()
                    Triple(header, next, coeff) to students
                } else null
            }

            val effectiveInfos = if (sheetInfos.isNotEmpty()) {
                sheetInfos
            } else {
                val blockIdx = classIndex - 1
                val headerRow1Based = 2 + blockIdx * 24
                allSheets.map { sheet ->
                    val header = sheet.getOrNull(headerRow1Based - 1) ?: emptyList()
                    val next = sheet.getOrNull(headerRow1Based) ?: emptyList()
                    val coeff = if (headerRow1Based >= 2) sheet.getOrNull(headerRow1Based - 2) ?: emptyList() else emptyList()
                    Triple(header, next, coeff) to emptyList<List<String>>()
                }
            }

            var maxCol = 20
            effectiveInfos.forEach { (meta, _) ->
                val (header, next, coeff) = meta
                maxCol = maxOf(maxCol, header.size, next.size, coeff.size)
            }

            for (col in 2 until minOf(maxCol, 27)) {
                var courseName = ""
                for ((meta, _) in effectiveInfos) {
                    val candidate = meta.second.getOrNull(col)?.trim()?.removeSurrounding("\"") ?: ""
                    if (candidate.isNotBlank()) {
                        courseName = candidate
                        break
                    }
                }

                var teacherName = ""
                for ((meta, _) in effectiveInfos) {
                    val candidate = meta.first.getOrNull(col)?.trim()?.removeSurrounding("\"") ?: ""
                    if (candidate.isNotBlank()) {
                        teacherName = candidate
                        break
                    }
                }

                var coeffStr = ""
                for ((meta, _) in effectiveInfos) {
                    val candidate = meta.third.getOrNull(col)?.trim()?.removeSurrounding("\"") ?: ""
                    if (candidate.isNotBlank()) {
                        coeffStr = candidate
                        break
                    }
                }

                // A valid course requires at least a course name or teacher name defined across the exam sheets.
                // If there is no course name and no teacher name (no course, no teacher, no coefficient), it is strictly NOT a course.
                val isCourseDefined = courseName.isNotBlank() || teacherName.isNotBlank()
                if (isCourseDefined) {
                    val colLetter = getColumnLetter(col)
                    val finalCourseName = courseName.ifBlank { "مادة عمود $colLetter" }
                    val finalTeacher = teacherName.ifBlank { "أستاذ المادة" }
                    val coeff = parseLocalizedInt(coeffStr) ?: 4

                    courses.add(
                        Course(
                            id = "c_${classIndex}_${col}",
                            classIndex = classIndex,
                            name = finalCourseName,
                            coefficient = coeff,
                            teacherName = finalTeacher,
                            columnLetter = colLetter,
                            columnIndex = col
                        )
                    )
                }
            }
        }

        return courses
    }

    /**
     * Parses grade sheet metadata:
     * Cell A1: Deadline date
     */
    fun parseDeadline(rows: List<List<String>>): String {
        if (rows.isNotEmpty() && rows[0].isNotEmpty()) {
            val cellA1 = rows[0][0].trim().removeSurrounding("\"")
            if (cellA1.isNotBlank()) return cellA1
        }
        return "2026-12-31"
    }

    /**
     * Parses exam upload permission from sheet cell A2 (Row 2, Column A in Google Sheets, index rows[1][0]):
     * Returns true if cell A2 is equal to "1" (or "1.0", "١"), false otherwise.
     */
    fun parseCellA2IsOne(rows: List<List<String>>): Boolean {
        if (rows.size > 1 && rows[1].isNotEmpty()) {
            val cellA2 = rows[1][0].trim().removeSurrounding("\"").trim()
            return cellA2 == "1" || cellA2 == "1.0" || cellA2 == "١"
        }
        return false
    }

    /**
     * Parses student names from Column B across the class blocks,
     * dynamically locating each student's exact line in the specified exam tab,
     * and reads any existing grades from columns C to AA for the specified exam tab.
     */
    fun parseStudentsAndGrades(
        rows: List<List<String>>,
        courses: List<Course>,
        examType: ExamType,
        blocks: List<ClassBlock> = classBlocks
    ): Pair<List<Student>, Map<String, Double>> {
        val students = mutableListOf<Student>()
        val parsedGrades = mutableMapOf<String, Double>()
        if (rows.isEmpty()) return Pair(emptyList(), emptyMap())

        // Dynamically detect class boundaries in the CURRENT exam sheet
        val detected = detectClassHeaders(rows)
        val effectiveBlocks = if (detected.isNotEmpty()) {
            detected.map { d ->
                val matchingBlock = blocks.find { it.index == d.classIndex }
                val className = matchingBlock?.name?.ifBlank { d.name } ?: d.name
                ClassBlock(
                    index = d.classIndex,
                    name = className,
                    headerRowLabel = "B${d.headerRow0Based + 1}",
                    startRow = d.studentsStart0Based + 1,
                    endRow = d.studentsEnd0Based + 1
                )
            }
        } else {
            blocks
        }

        effectiveBlocks.forEach { block ->
            val classCourses = courses.filter { it.classIndex == block.index }
            val rStart = (block.startRow - 1).coerceAtLeast(0)
            val rEnd = block.endRow.coerceAtMost(rows.size)

            for (r in rStart until rEnd) {
                val row = rows.getOrNull(r) ?: continue
                val rawNameB = row.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: ""
                val studentName = if (
                    rawNameB.isNotBlank() &&
                    !rawNameB.contains("اسم الطالب") &&
                    !rawNameB.contains("الاسم") &&
                    !rawNameB.contains("Course", ignoreCase = true) &&
                    !rawNameB.startsWith("TS", ignoreCase = true) &&
                    !rawNameB.startsWith("ts", ignoreCase = true) &&
                    rawNameB.toIntOrNull() == null
                ) {
                    rawNameB
                } else {
                    ""
                }

                if (studentName.isNotBlank()) {
                    val exactRow1Based = r + 1
                    val cleanName = studentName.trim().replace("\\s+".toRegex(), "_")
                    val studentId = "s_${block.index}_$cleanName"
                    val examRowMap = mutableMapOf(
                        examType.name to exactRow1Based,
                        examType.shortCode to exactRow1Based,
                        examType.sheetName to exactRow1Based
                    )
                    examType.alternativeSheetNames.forEach { alt ->
                        examRowMap[alt] = exactRow1Based
                    }
                    val student = Student(
                        id = studentId,
                        classIndex = block.index,
                        className = block.name,
                        name = studentName,
                        rowIndex = exactRow1Based,
                        examRowMap = examRowMap
                    )
                    students.add(student)

                    // Read grades for each course in this class at this exact row
                    classCourses.forEach { course ->
                        val cellVal = row.getOrNull(course.columnIndex)?.trim()?.removeSurrounding("\"") ?: ""
                        val gradeVal = parseLocalizedDouble(cellVal)
                        if (gradeVal != null) {
                            val gradeKey = "${studentId}_${course.id}_${examType.name}"
                            parsedGrades[gradeKey] = gradeVal
                        }
                    }
                }
            }
        }

        return Pair(students, parsedGrades)
    }

    /**
     * Pushes grade updates directly to Google Sheets via Google Apps Script Web App
     */
    suspend fun pushGradesToSheet(
        webAppUrl: String,
        sheetId: String,
        tabName: String,
        updates: List<GradeCellUpdate>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = webAppUrl.trim()
            if (trimmedUrl.isBlank()) {
                return@withContext Result.failure(Exception("لم يتم تحديد رابط تطبيق الويب (Apps Script)"))
            }
            if (trimmedUrl.contains("docs.google.com/spreadsheets")) {
                return@withContext Result.failure(
                    Exception("الرابط المدخل هو رابط ملف Google Sheet العادي وليس رابط تطبيق الويب. يتطلب التعديل المباشر رابط تطبيق ويب (Apps Script) ينتهي بـ /exec.")
                )
            }
            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                return@withContext Result.failure(Exception("رابط تطبيق الويب غير صالح. يجب أن يبدأ بـ https://"))
            }
            if (updates.isEmpty()) {
                return@withContext Result.success(0)
            }

            val jsonArray = JSONArray()
            updates.forEach { u ->
                val obj = JSONObject()
                obj.put("row", u.row)
                obj.put("col", u.col)
                obj.put("studentName", u.studentName)
                if (u.value != null) {
                    val v = if (u.value % 1.0 == 0.0) u.value.toInt() else u.value
                    obj.put("value", v)
                } else {
                    obj.put("value", "")
                }
                jsonArray.put(obj)
            }

            val payload = JSONObject()
            payload.put("action", "updateGrades")
            payload.put("sheetId", sheetId)
            payload.put("tab", tabName)
            payload.put("updates", jsonArray)

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = payload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(trimmedUrl)
                .post(requestBody)
                .build()

            var response: okhttp3.Response? = null
            var lastEx: Exception? = null
            for (attempt in 1..2) {
                try {
                    response = client.newCall(request).execute()
                    lastEx = null
                    break
                } catch (e: Exception) {
                    lastEx = e
                    if (attempt == 1) {
                        kotlinx.coroutines.delay(1000)
                    }
                }
            }

            if (response == null) {
                val msg = lastEx?.message ?: "خطأ في الاتصال"
                val friendly = if (msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true)) {
                    "استغرقت الاستجابة وقتاً أطول من المعتاد (مهلة الاتصال). تم حفظ البيانات محلياً في هاتفك."
                } else {
                    msg
                }
                return@withContext Result.failure(Exception(friendly))
            }
            val responseCode = response.code
            val bodyString = response.body?.string()?.trim() ?: ""

            if (!response.isSuccessful) {
                if (responseCode == 401 || responseCode == 403) {
                    return@withContext Result.failure(
                        Exception("تم رفض الإذن (HTTP $responseCode). يرجى التأكد من ضبط 'Who has access' على 'Anyone' (أي شخص) في Apps Script.")
                    )
                }
                return@withContext Result.failure(Exception("فشل الاتصال بـ Google Sheets (HTTP $responseCode)"))
            }

            if (bodyString.contains("accounts.google.com") || bodyString.startsWith("<!DOCTYPE html>") || bodyString.contains("<html")) {
                return@withContext Result.failure(
                    Exception("طلب تسجيل دخول Google. يرجى إعادة نشر تطبيق الويب وضبط (من لديه حق الوصول / Who has access) على (Anyone / أي شخص لديه الرابط).")
                )
            }

            try {
                val json = JSONObject(bodyString)
                val status = json.optString("status")
                if (status == "success") {
                    val count = json.optInt("updated", updates.size)
                    Result.success(count)
                } else {
                    val msg = json.optString("message", "فشل الحفظ في Google Sheets")
                    Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                Result.failure(Exception("استجابة غير متوقعة من خادم Google: ${bodyString.take(120)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Tests connectivity to the Google Apps Script Web App
     */
    suspend fun testWebAppConnection(webAppUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmed = webAppUrl.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(Exception("رابط تطبيق الويب فارغ"))
            }
            if (trimmed.contains("docs.google.com/spreadsheets")) {
                return@withContext Result.failure(
                    Exception("هذا رابط ملف Google Sheet العادي وليس رابط تطبيق الويب. يلزم رابط ينتهي بـ /exec من إضافات -> Apps Script -> نشر.")
                )
            }
            val request = Request.Builder()
                .url(trimmed)
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()?.trim() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("فشل الاتصال: HTTP ${response.code}"))
            }
            if (body.contains("accounts.google.com") || body.startsWith("<!DOCTYPE html>") || body.contains("<html")) {
                return@withContext Result.failure(
                    Exception("التطبيق يتطلب تسجيل الدخول. يرجى إعادة النشر وضبط 'Who has access' على 'Anyone' (أي شخص).")
                )
            }
            try {
                val json = JSONObject(body)
                val status = json.optString("status")
                if (status == "ready" || status == "success") {
                    Result.success("تم التحقق بنجاح! تطبيق الويب متصل ومستعد لكتابة وتعديل العلامات في Google Sheets مباشرة.")
                } else {
                    Result.failure(Exception("رد تطبيق الويب: ${json.optString("message", body)}"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("الرابط لا يرجع بيانات صالحة: ${body.take(100)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculates the exact row in Google Sheets where courses are defined for a given class:
     * - Class 1: Row 3
     * - Class 2: Row 27
     * - Class 3: Row 51
     * - Class 4: Row 75
     * - Class 5: Row 99
     * - Class 6: Row 123
     * - Class 7: Row 147
     * - Class 8: Row 171
     * - Class 9: Row 195
     * - Class 10: Row 219
     * - Class 11: Row 243
     * - Class 12: Row 267
     * Formula: 3 + (classIndex - 1) * 24
     * Columns: C to AA (columns 3 to 27, 1-based)
     */
    fun getCourseRowForClass(classIndex: Int): Int {
        return 3 + (classIndex - 1) * 24
    }

    /**
     * Fetches courses that are highlighted in red in Google Sheets via the Apps Script Web App.
     * Returns a set of locked keys (e.g. "c_1_2", "C1_c_1_2").
     */
    suspend fun fetchLockedCoursesFromWebApp(
        webAppUrl: String,
        sheetId: String
    ): Result<Set<String>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = webAppUrl.trim()
            if (trimmed.isBlank() || trimmed.contains("docs.google.com/spreadsheets")) {
                return@withContext Result.failure(Exception("رابط تطبيق الويب غير محدد"))
            }
            val delimiter = if (trimmed.contains("?")) "&" else "?"
            val url = "${trimmed}${delimiter}action=getLockedCourses&sheetId=${URLEncoder.encode(sheetId, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()?.trim() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("فشل الاتصال: HTTP ${response.code}"))
            }
            val json = JSONObject(body)
            val lockedObj = json.optJSONObject("lockedCourses")
            val lockedSet = mutableSetOf<String>()
            if (lockedObj != null) {
                val keys = lockedObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (lockedObj.optBoolean(k, false)) {
                        lockedSet.add(k)
                    }
                }
            }
            Result.success(lockedSet)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getGoogleAppsScriptCode(): String {
        return """
function doPost(e) {
  var lock = LockService.getScriptLock();
  lock.tryLock(20000);
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return ContentService.createTextOutput(JSON.stringify({status: "error", message: "لم يتم استلام بيانات POST"}))
        .setMimeType(ContentService.MimeType.JSON);
    }
    
    var data = JSON.parse(e.postData.contents);
    var ss = openTargetSpreadsheet(data.sheetId);
    
    if (!ss) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "error", 
        message: "تعذر فتح ملف Google Sheet. تأكد من إضافة السكريبت داخل الملف عبر ملحقات -> Apps Script"
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // فحص طلب المواد المقفلة المحددة باللون الأحمر
    if (data.action === "getLockedCourses") {
      var lockedMap = getCourseRedHighlights(ss);
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        lockedCourses: lockedMap
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // فحص طلب استعلام نتائج الطالب
    if (data.action === "getStudentGrades") {
      return handleGetStudentGrades(data.phone, data.dob, data.sheetId);
    }

    // 2. مطابقة اسم التبويب بمرونة (يتعامل مع الهمزات وإضافة/حذف كلمة علامات: علامات السعي الاول / السعي الاول / السعي الأول)
    var targetTabName = (data.tab || "").toString().trim();
    var sheet = ss.getSheetByName(targetTabName);
    
    if (!sheet) {
      var allSheets = ss.getSheets();
      var cleanTarget = normalizeArabicText(targetTabName);
      var cleanTargetNoPrefix = cleanTarget.replace(/^علامات\s+/, "");
      for (var s = 0; s < allSheets.length; s++) {
        var sClean = normalizeArabicText(allSheets[s].getName());
        var sCleanNoPrefix = sClean.replace(/^علامات\s+/, "");
        if (sClean === cleanTarget || sCleanNoPrefix === cleanTargetNoPrefix) {
          sheet = allSheets[s];
          break;
        }
      }
    }
    
    if (!sheet) {
      var available = ss.getSheets().map(function(s) { return s.getName(); }).join(", ");
      return ContentService.createTextOutput(JSON.stringify({
        status: "error", 
        message: "التبويب المطلوب (" + targetTabName + ") غير موجود. التبويبات المتوفرة: " + available
      })).setMimeType(ContentService.MimeType.JSON);
    }

    var updates = data.updates || [];
    var maxRow = Math.max(sheet.getLastRow(), 270);
    var colBValues = sheet.getRange("B1:B" + maxRow).getValues();

    // فحص الخلايا المقفلة بالأحمر لمنع تعديلها
    var redLocks = getCourseRedHighlights(ss);

    // تجميع التعديلات حسب العمود للكتابة المجمعة السريعة دفعة واحدة (Batch Update)
    var byCol = {};
    for (var i = 0; i < updates.length; i++) {
      var u = updates[i];
      var targetRow = parseInt(u.row, 10);
      var targetCol = parseInt(u.col, 10);
      if (isNaN(targetRow) || isNaN(targetCol) || targetRow < 1 || targetCol < 1) continue;
      
      // التحقق من اسم الطالب في العمود B لضمان دقة السطر 100%
      if (u.studentName && u.studentName.toString().trim() !== "") {
        var cleanTargetName = normalizeArabicText(u.studentName);
        var currentSheetName = (targetRow <= colBValues.length) ? normalizeArabicText(colBValues[targetRow - 1][0]) : "";
        if (currentSheetName !== cleanTargetName) {
          for (var r = 0; r < colBValues.length; r++) {
            if (normalizeArabicText(colBValues[r][0]) === cleanTargetName) {
              targetRow = r + 1;
              break;
            }
          }
        }
      }
      
      var valToSet = "";
      if (u.value !== null && u.value !== undefined && u.value !== "") {
        var numVal = Number(u.value);
        valToSet = isNaN(numVal) ? u.value : numVal;
      }
      
      if (!byCol[targetCol]) byCol[targetCol] = [];
      byCol[targetCol].push({ row: targetRow, val: valToSet });
    }

    var updatedCount = 0;
    for (var c in byCol) {
      var colUpdates = byCol[c];
      var colNum = parseInt(c, 10);
      if (colUpdates.length === 1) {
        var single = colUpdates[0];
        var valToSet = single.val;
        sheet.getRange(single.row, colNum).setValue(valToSet);
        updatedCount++;
      } else {
        var minR = colUpdates[0].row;
        var maxR = colUpdates[0].row;
        for (var j = 1; j < colUpdates.length; j++) {
          if (colUpdates[j].row < minR) minR = colUpdates[j].row;
          if (colUpdates[j].row > maxR) maxR = colUpdates[j].row;
        }
        var numR = maxR - minR + 1;
        var range = sheet.getRange(minR, colNum, numR, 1);
        var colSlice = range.getValues();
        for (var j = 0; j < colUpdates.length; j++) {
          var offset = colUpdates[j].row - minR;
          if (offset >= 0 && offset < colSlice.length) {
            colSlice[offset][0] = colUpdates[j].val;
          }
        }
        range.setValues(colSlice);
        updatedCount += colUpdates.length;
      }
    }

    return ContentService.createTextOutput(JSON.stringify({
      status: "success", 
      tab: sheet.getName(),
      updated: updatedCount
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error", 
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  } finally {
    lock.releaseLock();
  }
}

function doGet(e) {
  var action = (e && e.parameter && e.parameter.action) ? e.parameter.action : "";
  if (action === "getMasterSchools") {
    try {
      var mId = (e && e.parameter && e.parameter.masterSheetId) ? e.parameter.masterSheetId : "1lqbi4ArzITb3z6jINq5OUHjzPo0_Gkcla_Og6iMGwUs";
      var tab = (e && e.parameter && e.parameter.sheetName) ? e.parameter.sheetName : "Master";
      var ss = SpreadsheetApp.openById(mId.toString().trim());
      var sh = ss.getSheetByName(tab);
      if (!sh) sh = ss.getSheets()[0];
      var data = sh.getDataRange().getValues();
      var list = [];
      for (var i = 1; i < data.length; i++) {
        var sId = (data[i][0] || "").toString().trim();
        var sName = (data[i][1] || "").toString().trim();
        var sStatus = (data[i][2] || "").toString().trim();
        if (sId && sName && (sStatus.toLowerCase() === "active" || sStatus === "نشط")) {
          list.push({ sheetId: sId, schoolName: sName, status: sStatus });
        }
      }
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        schools: list
      })).setMimeType(ContentService.MimeType.JSON);
    } catch (err) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "error",
        message: err.toString()
      })).setMimeType(ContentService.MimeType.JSON);
    }
  }

  if (action === "getLockedCourses") {
    var sheetId = (e && e.parameter && e.parameter.sheetId) ? e.parameter.sheetId : null;
    var ss = openTargetSpreadsheet(sheetId);
    if (!ss) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "error", 
        message: "تعذر فتح ملف Google Sheet"
      })).setMimeType(ContentService.MimeType.JSON);
    }
    var locked = getCourseRedHighlights(ss);
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      lockedCourses: locked
    })).setMimeType(ContentService.MimeType.JSON);
  }

  if (action === "getStudentGrades") {
    var phone = (e && e.parameter && e.parameter.phone) ? e.parameter.phone : "";
    var dob = (e && e.parameter && e.parameter.dob) ? e.parameter.dob : "";
    var sheetId = (e && e.parameter && e.parameter.sheetId) ? e.parameter.sheetId : null;
    var stage = (e && e.parameter && e.parameter.stage) ? e.parameter.stage : null;
    return handleGetStudentGrades(phone, dob, sheetId, stage);
  }

  if (action === "test") {
    return ContentService.createTextOutput(JSON.stringify({
      status: "ready", 
      message: "EMIS School Grade Sync & Student Portal Service is active and connected to Google Sheets"
    })).setMimeType(ContentService.MimeType.JSON);
  }

  // DEFAULT: Render Student Grade Portal Web Page directly for students who scan the QR Code!
  var portalTargetSs = openTargetSpreadsheet((e && e.parameter && e.parameter.sheetId) ? e.parameter.sheetId : null);
  var c1NotIssued = false;
  var c1DueDateStr = "";
  if (portalTargetSs) {
    var c1Sheet = portalTargetSs.getSheetByName("C1") || portalTargetSs.getSheetByName("السعي الاول") || portalTargetSs.getSheetByName("السعي الأول");
    if (c1Sheet) {
      var c1Val = c1Sheet.getRange(1, 1).getValue();
      if (c1Val && !isExamDeadlinePassedJs(c1Val)) {
        c1NotIssued = true;
        c1DueDateStr = c1Val.toString().trim();
      }
    }
  }
  return HtmlService.createHtmlOutput(getStudentPortalHtml(c1NotIssued, c1DueDateStr))
    .setTitle("بوابة نتائج الطلاب | EMIS School Portal")
    .addMetaTag("viewport", "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no")
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

function handleGetStudentGradesJson(phone, dob, sheetId, requestedStage) {
  var res = handleGetStudentGrades(phone, dob, sheetId, requestedStage);
  return res.getContent();
}

function handleGetStudentGrades(phone, dob, sheetId, requestedStage) {
  var ss = openTargetSpreadsheet(sheetId);
  if (!ss) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: "تعذر فتح ملف Google Sheet الخاص بالمدرسة"
    })).setMimeType(ContentService.MimeType.JSON);
  }

  var settingsSheet = ss.getSheetByName("الإعدادات (التلاميذ)");
  if (!settingsSheet) {
    var all = ss.getSheets();
    for (var s = 0; s < all.length; s++) {
      var n = normalizeArabicText(all[s].getName());
      if (n.indexOf("اعدادات") !== -1 && (n.indexOf("تلاميذ") !== -1 || n.indexOf("طلاب") !== -1)) {
        settingsSheet = all[s];
        break;
      }
    }
  }

  if (!settingsSheet) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: "لم يتم العثور على تبويب الإعدادات (التلاميذ) في ملف المدرسة"
    })).setMimeType(ContentService.MimeType.JSON);
  }

  var cleanPhone = cleanDigitsJs(phone);
  if (!cleanPhone || cleanPhone.length < 5) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: "يرجى إدخال رقم هاتف مكون من 6 أرقام"
    })).setMimeType(ContentService.MimeType.JSON);
  }

  var sData = settingsSheet.getDataRange().getValues();
  var matchedStudent = null;
  var currentClass = "";

  for (var r = 0; r < sData.length; r++) {
    var row = sData[r];
    var colB = (row[1] || "").toString().trim();
    var colC = cleanDigitsJs((row[2] || "").toString());
    var colD = (row[3] || "").toString().trim();

    if (colB !== "" && colC === "" && (colD === "" || isNaN(Date.parse(colD)))) {
      currentClass = colB;
    }

    if (colB !== "" && colC !== "") {
      if (colC === cleanPhone && isDobMatchJs(colD, dob)) {
        matchedStudent = {
          name: colB,
          phone: colC,
          dob: colD,
          className: currentClass
        };
        break;
      }
    }
  }

  if (!matchedStudent) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: "لم يتم العثور على طالب يطابق رقم الهاتف (" + cleanPhone + ") وتاريخ الميلاد المدخل. يرجى التأكد من البيانات المسجلة في المدرسة."
    })).setMimeType(ContentService.MimeType.JSON);
  }

  // School name and year from sheet "Setting" or "الإعدادات" C1 and C2
  var schoolSettingsSheet = ss.getSheetByName("Setting") || ss.getSheetByName("setting") || ss.getSheetByName("Settings") || ss.getSheetByName("settings") || ss.getSheetByName("الإعدادات") || ss.getSheetByName("الاعدادات");
  if (!schoolSettingsSheet) {
    var allSheets = ss.getSheets();
    for (var s = 0; s < allSheets.length; s++) {
      var sName = (allSheets[s].getName() || "").toLowerCase().trim();
      var normS = normalizeArabicText(sName);
      if (sName === "setting" || sName === "settings" || normS === "اعدادات" || normS === "الاعدادات") {
        schoolSettingsSheet = allSheets[s];
        break;
      }
    }
  }
  var schoolNameVal = "";
  var schoolYearVal = "2025-2026";
  if (schoolSettingsSheet) {
    try {
      var c1Val = (schoolSettingsSheet.getRange(1, 3).getValue() || "").toString().trim();
      var c2Val = (schoolSettingsSheet.getRange(2, 3).getValue() || "").toString().trim();
      if (c1Val) schoolNameVal = c1Val;
      if (c2Val) schoolYearVal = c2Val;
    } catch(e) {}
  }

  // Find Exam Sheets
  var examTabDefs = [
    { code: "C1", candidates: ["علامات السعي الاول", "علامات السعي الأول", "السعي الاول", "السعي الأول"] },
    { code: "E1", candidates: ["علامات الامتحان الاول", "علامات الامتحان الأول", "الامتحان الاول", "الامتحان الأول", "علامات الفصل الاول", "الفصل الاول"] },
    { code: "C2", candidates: ["علامات السعي الثاني", "السعي الثاني"] },
    { code: "E2", candidates: ["علامات الامتحان الثاني", "الامتحان الثاني", "علامات الفصل الاخير", "الفصل الاخير"] }
  ];

  var allSheets = ss.getSheets();
  function getSheetByCandidates(cands) {
    for (var i = 0; i < cands.length; i++) {
      var s = ss.getSheetByName(cands[i]);
      if (s) return s;
    }
    for (var sIdx = 0; sIdx < allSheets.length; sIdx++) {
      var normS = normalizeArabicText(allSheets[sIdx].getName());
      for (var j = 0; j < cands.length; j++) {
        if (normS === normalizeArabicText(cands[j])) return allSheets[sIdx];
      }
    }
    return null;
  }

  var c1Sheet = getSheetByCandidates(examTabDefs[0].candidates);
  var e1Sheet = getSheetByCandidates(examTabDefs[1].candidates);
  var c2Sheet = getSheetByCandidates(examTabDefs[2].candidates);
  var e2Sheet = getSheetByCandidates(examTabDefs[3].candidates);

  var c1Data = c1Sheet ? c1Sheet.getDataRange().getValues() : [];
  var e1Data = e1Sheet ? e1Sheet.getDataRange().getValues() : [];
  var c2Data = c2Sheet ? c2Sheet.getDataRange().getValues() : [];
  var e2Data = e2Sheet ? e2Sheet.getDataRange().getValues() : [];

  var normStudentName = normalizeArabicText(matchedStudent.name);
  var sRowIdxC1 = -1, sRowIdxE1 = -1, sRowIdxC2 = -1, sRowIdxE2 = -1;

  function findStudentRow(sheetData) {
    for (var r = 0; r < sheetData.length; r++) {
      if (sheetData[r].length > 1 && normalizeArabicText(sheetData[r][1]) === normStudentName) {
        return r;
      }
    }
    return -1;
  }

  sRowIdxC1 = findStudentRow(c1Data);
  sRowIdxE1 = findStudentRow(e1Data);
  sRowIdxC2 = findStudentRow(c2Data);
  sRowIdxE2 = findStudentRow(e2Data);

  var primaryData = (sRowIdxC1 !== -1) ? c1Data : ((sRowIdxE1 !== -1) ? e1Data : ((sRowIdxC2 !== -1) ? c2Data : e2Data));
  var primaryRowIdx = (sRowIdxC1 !== -1) ? sRowIdxC1 : ((sRowIdxE1 !== -1) ? sRowIdxE1 : ((sRowIdxC2 !== -1) ? sRowIdxC2 : sRowIdxE2));

  if (primaryRowIdx === -1) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: "تم التحقق من هوية الطالب (" + matchedStudent.name + ") ولكن لم يتم العثور على اسمه في جداول العلامات والامتحانات."
    })).setMimeType(ContentService.MimeType.JSON);
  }

  function hasGrades(grid, rIdx) {
    if (rIdx < 0 || rIdx >= grid.length) return false;
    for (var c = 2; c < Math.min(grid[rIdx].length, 25); c++) {
      var v = grid[rIdx][c];
      if (v !== "" && v !== null && !isNaN(Number(v))) return true;
    }
    return false;
  }

  // Determine stage based on deadline dates (حسب التاريخ)
  var c1Val = c1Sheet ? (c1Sheet.getRange(1, 1).getValue() || "") : "";
  var e1Val = e1Sheet ? (e1Sheet.getRange(1, 1).getValue() || "") : "";
  var c2Val = c2Sheet ? (c2Sheet.getRange(1, 1).getValue() || "") : "";
  var e2Val = e2Sheet ? (e2Sheet.getRange(1, 1).getValue() || "") : "";

  var isC1Closed = c1Val ? isExamDeadlinePassedJs(c1Val) : true;
  var isE1Closed = e1Val ? isExamDeadlinePassedJs(e1Val) : false;
  var isC2Closed = c2Val ? isExamDeadlinePassedJs(c2Val) : false;
  var isE2Closed = e2Val ? isExamDeadlinePassedJs(e2Val) : false;

  if (c1Val && !isC1Closed) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: "لم يصدر اي تقرير حتى الان"
    })).setMimeType(ContentService.MimeType.JSON);
  }

  var autoStage = "C1_ONLY";
  if (isE2Closed) {
    autoStage = "ALL";
  } else if (isC2Closed) {
    autoStage = "C2_ONLY";
  } else if (isE1Closed) {
    autoStage = "C1_E1";
  } else {
    autoStage = "C1_ONLY";
  }

  var stage = requestedStage || autoStage;
  var stageCardTitle = "بطاقة علامات السعي الاول";
  var stageLines = ["بطاقة", "علامات", "السعي", "الاول"];

  if (stage === "ALL") {
    stageCardTitle = "بطاقة علامات الطالب للعام";
    stageLines = ["بطاقة", "علامات", "الطالب", "للعام"];
  } else if (stage === "C2_ONLY") {
    stageCardTitle = "بطاقة علامات السعي الثاني";
    stageLines = ["بطاقة", "علامات", "السعي", "الثاني"];
  } else if (stage === "C1_E1") {
    stageCardTitle = "بطاقة علامات الفصل الاول";
    stageLines = ["بطاقة", "علامات", "الفصل", "الاول"];
  } else {
    stage = "C1_ONLY";
    stageCardTitle = "بطاقة علامات السعي الاول";
    stageLines = ["بطاقة", "علامات", "السعي", "الاول"];
  }

  // Detect courseRow and coeffRow in primaryData
  var classBlock = Math.floor(primaryRowIdx / 24);
  var courseRowIdx = classBlock * 24 + 2;
  var coeffRowIdx = classBlock * 24;

  for (var r = primaryRowIdx - 1; r >= Math.max(0, primaryRowIdx - 25); r--) {
    var strCount = 0;
    for (var c = 2; c < Math.min(primaryData[r].length, 22); c++) {
      var s = (primaryData[r][c] || "").toString().trim();
      if (s && isNaN(Number(s)) && s.indexOf("طالب") === -1 && s.indexOf("اسم") === -1) strCount++;
    }
    if (strCount >= 2) {
      courseRowIdx = r;
      if (r >= 2) coeffRowIdx = r - 2;
      else if (r >= 1) coeffRowIdx = r - 1;
      break;
    }
  }

  var courseRow = (courseRowIdx >= 0 && courseRowIdx < primaryData.length) ? primaryData[courseRowIdx] : [];
  var coeffRow = (coeffRowIdx >= 0 && coeffRowIdx < primaryData.length) ? primaryData[coeffRowIdx] : [];

  var defaultCoeffMap = {
    "الاحصاء": 6, "الإحصاء": 6,
    "الاقتصاد": 4, "الإقتصاد": 4,
    "الرياضيات": 4,
    "الرياضيات المالية": 6,
    "الغروتيم و هيكلية المعطيات": 8,
    "الغروتيم و هيكلية المعطيات TP": 6,
    "القانون": 4,
    "اللغة الاجنبية الثانية": 4, "اللغة الأجنبية الثانية": 4,
    "برمجة VB TP": 8,
    "طرائق التحليل": 10
  };

  var courses = [];
  var maxTotal = 0;
  var c1Total = 0;
  var e1Total = 0;
  var c2Total = 0;
  var e2Total = 0;
  var finalTotal = 0;

  var maxCol = Math.min(courseRow.length, 27);
  for (var col = 2; col < maxCol; col++) {
    var cName = (courseRow[col] || "").toString().trim();
    if (cName !== "" && cName !== "المجموع" && cName !== "المعدل" && cName !== "النتيجة" && cName.indexOf("اسم") === -1) {
      var rawCoeff = coeffRow[col];
      var coeff = (rawCoeff !== "" && !isNaN(Number(rawCoeff)) && Number(rawCoeff) > 0) ? Number(rawCoeff) : (defaultCoeffMap[cName] || 4);
      var maxScore = coeff * 20;

      // C1
      var rawC1 = (sRowIdxC1 !== -1 && sRowIdxC1 < c1Data.length) ? c1Data[sRowIdxC1][col] : null;
      var numC1 = (rawC1 !== "" && rawC1 !== null && !isNaN(Number(rawC1))) ? Number(rawC1) : null;
      var c1Pts = (numC1 !== null) ? ((numC1 <= 20 && coeff > 1) ? (numC1 * coeff) : numC1) : null;

      // E1
      var rawE1 = (sRowIdxE1 !== -1 && sRowIdxE1 < e1Data.length) ? e1Data[sRowIdxE1][col] : null;
      var numE1 = (rawE1 !== "" && rawE1 !== null && !isNaN(Number(rawE1))) ? Number(rawE1) : null;
      var e1Pts = (numE1 !== null) ? ((numE1 <= 20 && coeff > 1) ? (numE1 * coeff) : numE1) : null;

      // C2
      var rawC2 = (sRowIdxC2 !== -1 && sRowIdxC2 < c2Data.length) ? c2Data[sRowIdxC2][col] : null;
      var numC2 = (rawC2 !== "" && rawC2 !== null && !isNaN(Number(rawC2))) ? Number(rawC2) : null;
      var c2Pts = (numC2 !== null) ? ((numC2 <= 20 && coeff > 1) ? (numC2 * coeff) : numC2) : null;

      // E2
      var rawE2 = (sRowIdxE2 !== -1 && sRowIdxE2 < e2Data.length) ? e2Data[sRowIdxE2][col] : null;
      var numE2 = (rawE2 !== "" && rawE2 !== null && !isNaN(Number(rawE2))) ? Number(rawE2) : null;
      var e2Pts = (numE2 !== null) ? ((numE2 <= 20 && coeff > 1) ? (numE2 * coeff) : numE2) : null;

      // In C1_E1 stage, mirror if one exam has values
      if (stage === "C1_E1") {
        if (e1Pts === null && c1Pts !== null) e1Pts = c1Pts;
        if (c1Pts === null && e1Pts !== null) c1Pts = e1Pts;
      }

      var crFinal = null;
      if (stage === "C1_E1") {
        crFinal = (c1Pts !== null && e1Pts !== null) ? ((c1Pts * 0.20) + (e1Pts * 0.80)) : (c1Pts !== null ? c1Pts : e1Pts);
      } else if (stage === "ALL") {
        crFinal = ((c1Pts||0)*0.10) + ((e1Pts||0)*0.40) + ((c2Pts||0)*0.10) + ((e2Pts||0)*0.40);
      } else if (stage === "C2_ONLY") {
        crFinal = c2Pts;
      } else {
        crFinal = c1Pts;
      }

      maxTotal += maxScore;
      if (c1Pts !== null) c1Total += c1Pts;
      if (e1Pts !== null) e1Total += e1Pts;
      if (c2Pts !== null) c2Total += c2Pts;
      if (e2Pts !== null) e2Total += e2Pts;
      if (crFinal !== null) finalTotal += crFinal;

      courses.push({
        courseName: cName,
        coefficient: coeff,
        maxScore: maxScore,
        c1Score: c1Pts,
        e1Score: e1Pts,
        c2Score: c2Pts,
        e2Score: e2Pts,
        finalGrade: crFinal
      });
    }
  }

  var c1Avg = (maxTotal > 0) ? Math.round((c1Total / maxTotal) * 2000) / 100 : 0;
  var e1Avg = (maxTotal > 0) ? Math.round((e1Total / maxTotal) * 2000) / 100 : 0;
  var c2Avg = (maxTotal > 0) ? Math.round((c2Total / maxTotal) * 2000) / 100 : 0;
  var e2Avg = (maxTotal > 0) ? Math.round((e2Total / maxTotal) * 2000) / 100 : 0;
  var finalAvg = (maxTotal > 0) ? Math.round((finalTotal / maxTotal) * 2000) / 100 : 0;

  var isPassed = finalAvg >= 10.0;
  var resultText = isPassed ? "ناجح" : "راسب";

  // Calculate Rank and Class Student Count
  var studentCountInClass = 0;
  var rank = 1;
  var startR = courseRowIdx + 1;
  var endR = Math.min(primaryData.length, startR + 20);

  for (var r = startR; r < endR; r++) {
    var sName = (primaryData[r][1] || "").toString().trim();
    if (sName !== "" && sName.indexOf("اسم") === -1 && sName.indexOf("TS") === -1 && sName.indexOf("BT") === -1 && isNaN(Number(sName))) {
      studentCountInClass++;
      var peerTotal = 0;
      for (var c = 2; c < maxCol; c++) {
        var v = primaryData[r][c];
        if (v !== "" && v !== null && !isNaN(Number(v))) {
          var n = Number(v);
          var cf = (coeffRow[c] !== "" && !isNaN(Number(coeffRow[c]))) ? Number(coeffRow[c]) : 4;
          var pt = (n <= 20 && cf > 1) ? (n * cf) : n;
          peerTotal += pt;
        }
      }
      if (peerTotal > finalTotal) {
        rank++;
      }
    }
  }

  if (studentCountInClass === 0) studentCountInClass = 20;

  function resolveDegreeAndYearJs(cls) {
    var u = (cls || "").toUpperCase();
    if (u.indexOf("TS1") !== -1) return "الامتياز الفني TS1";
    if (u.indexOf("TS2") !== -1) return "الامتياز الفني TS2";
    if (u.indexOf("TS3") !== -1) return "الامتياز الفني TS3";
    if (u.indexOf("BT1") !== -1) return "البكالوريا الفنية BT1";
    if (u.indexOf("BT2") !== -1) return "البكالوريا الفنية BT2";
    if (u.indexOf("BT3") !== -1) return "البكالوريا الفنية BT3";
    if (u.indexOf("BP1") !== -1) return "البكالوريا المهنية BP1";
    if (u.indexOf("BP2") !== -1) return "البكالوريا المهنية BP2";
    if (u.indexOf("LT") !== -1) return "الإجازة الفنية LT";
    return "الامتياز الفني TS1";
  }

  function resolveSpecialtyJs(cls) {
    var u = (cls || "").toUpperCase();
    if (u.indexOf("EDU") !== -1 || u.indexOf("حضانية") !== -1 || u.indexOf("تربية") !== -1) return "التربية الحضانية والإبتدائية";
    if (u.indexOf("HOT") !== -1 || u.indexOf("فندقية") !== -1) return "الادارة الفندقية";
    if (u.indexOf("INF") !== -1 || u.indexOf("INFO") !== -1 || u.indexOf("معلوماتية") !== -1 || u.indexOf("TS1") !== -1) return "المعلوماتية الإدارية";
    if (u.indexOf("EXP") !== -1 || u.indexOf("محاسبة") !== -1) return "المحاسبة والمعلوماتية";
    if (u.indexOf("CLI") !== -1 || u.indexOf("تمريض") !== -1) return "العناية التمريضية والعلوم المخبرية";
    if (u.indexOf("ELI") !== -1 || u.indexOf("كهرباء") !== -1) return "الالكتروتكنيك والكهرباء الصناعية";
    if (u.indexOf("MECA") !== -1 || u.indexOf("ميكانيك") !== -1) return "ميكانيك السيارات والآليات";
    return "المعلوماتية الإدارية";
  }

  var classNameRaw = matchedStudent.className || "TS1";
  var classOnly = classNameRaw.split(" ")[0] || "TS1";
  var degreeAndYear = resolveDegreeAndYearJs(classNameRaw);
  var specialty = resolveSpecialtyJs(classNameRaw);

  return ContentService.createTextOutput(JSON.stringify({
    status: "success",
    studentName: matchedStudent.name,
    className: classNameRaw,
    classOnly: classOnly,
    section: "F1",
    degreeAndYear: degreeAndYear,
    specialty: specialty,
    schoolName: schoolNameVal,
    schoolYear: schoolYearVal,
    stage: stage,
    stageCardTitle: stageCardTitle,
    stageLines: stageLines,
    deadlines: {
      c1Date: c1Val ? c1Val.toString() : "",
      e1Date: e1Val ? e1Val.toString() : "",
      c2Date: c2Val ? c2Val.toString() : "",
      e2Date: e2Val ? e2Val.toString() : "",
      c1Passed: isC1Closed,
      e1Passed: isE1Closed,
      c2Passed: isC2Closed,
      e2Passed: isE2Closed
    },
    courses: courses,
    totals: {
      maxTotal: maxTotal,
      c1Total: Math.round(c1Total * 100) / 100,
      e1Total: Math.round(e1Total * 100) / 100,
      c2Total: Math.round(c2Total * 100) / 100,
      e2Total: Math.round(e2Total * 100) / 100,
      finalTotal: Math.round(finalTotal * 100) / 100
    },
    averages: {
      c1Avg: c1Avg,
      e1Avg: e1Avg,
      c2Avg: c2Avg,
      e2Avg: e2Avg,
      finalAvg: finalAvg
    },
    isPassed: isPassed,
    resultText: resultText,
    classStudentCount: studentCountInClass,
    rank: rank
  })).setMimeType(ContentService.MimeType.JSON);
}

function cleanDigitsJs(str) {
  if (!str) return "";
  var arabicIndic = "٠١٢٣٤٥٦٧٨٩";
  var res = "";
  for (var i = 0; i < str.length; i++) {
    var ch = str.charAt(i);
    var idx = arabicIndic.indexOf(ch);
    if (idx !== -1) res += idx;
    else if (ch >= '0' && ch <= '9') res += ch;
  }
  return res;
}

function isDobMatchJs(sheetDob, inputDob) {
  if (!sheetDob || !inputDob) return false;
  var cleanS = cleanDigitsJs(sheetDob);
  var cleanI = cleanDigitsJs(inputDob);
  if (cleanS === cleanI && cleanS !== "") return true;

  if (sheetDob instanceof Date) {
    var d = sheetDob.getDate();
    var m = sheetDob.getMonth() + 1;
    var y = sheetDob.getFullYear();
    var iPartsD = inputDob.toString().split(/[^0-9]+/).map(function(p){ return parseInt(cleanDigitsJs(p), 10); }).filter(function(n){ return !isNaN(n); });
    if (iPartsD.length >= 3) {
      var sSortedD = [d, m, y].sort(function(a,b){ return a - b; });
      var iSortedD = iPartsD.slice(0, 3).sort(function(a,b){ return a - b; });
      if (sSortedD.join("-") === iSortedD.join("-")) return true;
    }
  }

  var sParts = sheetDob.toString().split(/[^0-9]+/).map(function(p){ return parseInt(cleanDigitsJs(p), 10); }).filter(function(n){ return !isNaN(n); });
  var iParts = inputDob.toString().split(/[^0-9]+/).map(function(p){ return parseInt(cleanDigitsJs(p), 10); }).filter(function(n){ return !isNaN(n); });

  if (sParts.length >= 3 && iParts.length >= 3) {
    var sSorted = sParts.slice(0, 3).sort(function(a,b){ return a - b; });
    var iSorted = iParts.slice(0, 3).sort(function(a,b){ return a - b; });
    if (sSorted.join("-") === iSorted.join("-")) return true;
  }

  if (cleanI.length === 8 && sParts.length >= 3) {
    var d1 = parseInt(cleanI.substring(0, 2), 10);
    var m1 = parseInt(cleanI.substring(2, 4), 10);
    var y1 = parseInt(cleanI.substring(4), 10);
    var iArr = [d1, m1, y1].sort(function(a,b){ return a - b; });
    var sArr = sParts.slice(0, 3).sort(function(a,b){ return a - b; });
    if (iArr.join("-") === sArr.join("-")) return true;
  }

  return (sheetDob.toString().indexOf(inputDob.toString()) !== -1 || inputDob.toString().indexOf(sheetDob.toString()) !== -1);
}

function onOpen() {
  try {
    var ui = SpreadsheetApp.getUi();
    ui.createMenu("🏫 بوابة نتائج الطلاب")
      .addItem("📱 إدراج رمز QR في الجدول (مباشر دون حساب Google)", "insertStudentPortalQrCode")
      .addItem("ℹ️ تعليمات النشر دون طلب حساب Google", "showDeploymentHelp")
      .addToUi();
  } catch(e) {}
}

function insertStudentPortalQrCode() {
  var sheet = SpreadsheetApp.getActiveSheet();
  var url = ScriptApp.getService().getUrl();
  if (!url || url.indexOf("/exec") === -1) {
    SpreadsheetApp.getUi().alert("تنبيه النشر:", "يرجى أولاً نشر السكريبت كتطبيق ويب (Deploy -> New deployment -> Web app) واختيار:\n• Execute as: Me\n• Who has access: Anyone (أي شخص)\nثم انسخ الرابط.", SpreadsheetApp.getUi().ButtonSet.OK);
    return;
  }
  var qrFormula = '=IMAGE("https://api.qrserver.com/v1/create-qr-code/?size=260x260&data=' + encodeURIComponent(url) + '")';
  sheet.getRange("F1").setFormula(qrFormula);
  SpreadsheetApp.getUi().alert("تم بنجاح!", "تم إدراج رمز QR في الخلية F1 بنجاح.\nهذا الرمز مرتبط برابط Web App المباشر، وسيفتح لجميع الطلاب دون أي حساب Google!", SpreadsheetApp.getUi().ButtonSet.OK);
}

function showDeploymentHelp() {
  SpreadsheetApp.getUi().alert("كيفية النشر بدون طلب حساب Google:", "لجعل الرابط يفتح مباشرة للطلاب دون أي تسجيل دخول:\n1. اضغط على نشر (Deploy) -> نشر جديد (New deployment)\n2. اختر تطبيق ويب (Web app)\n3. اضبط (تنفيذ كـ / Execute as) على: Me (حسابي)\n4. اضبط (من لديه حق الوصول / Who has access) على: Anyone (أي شخص / الجميع)\n5. اضغط Deploy وشارك الرابط أو رمز QR الناتج مع الطلاب.", SpreadsheetApp.getUi().ButtonSet.OK);
}

function isExamDeadlinePassedJs(val) {
  if (!val) return false;
  if (val instanceof Date) {
    var deadline = new Date(val.getTime());
    deadline.setHours(23, 59, 59, 999);
    return (new Date()).getTime() > deadline.getTime();
  }
  var parts = val.toString().trim().split(/[-/.\s]+/);
  if (parts.length >= 3) {
    var p0 = parseInt(cleanDigitsJs(parts[0]), 10);
    var p1 = parseInt(cleanDigitsJs(parts[1]), 10);
    var p2 = parseInt(cleanDigitsJs(parts[2]), 10);
    var y, m, d;
    if (p0 > 1000) {
      y = p0; m = p1 - 1; d = p2;
    } else {
      y = (p2 < 100) ? (p2 + 2000) : p2;
      d = p0; m = p1 - 1;
    }
    var target = new Date(y, m, d, 23, 59, 59, 999);
    return (new Date()).getTime() > target.getTime();
  }
  return false;
}

function getStudentPortalHtml(c1NotIssued, c1DueDateStr) {
  var isNotIssued = (c1NotIssued === true);
  var c1DateTxt = c1DueDateStr ? c1DueDateStr.toString().trim() : "";
  var noticeBanner = isNotIssued ? (
    '<div id="c1-not-issued-banner" class="no-print" style="background: #FEF3C7; border: 1.5px solid #F59E0B; border-radius: 10px; padding: 14px 12px; margin-bottom: 12px; text-align: center;">' +
    '  <div style="font-size: 20px; margin-bottom: 4px;">⏳</div>' +
    '  <div style="font-size: 15px; font-weight: 800; color: #92400E; margin-bottom: 2px;">لم يصدر اي تقرير حتى الان</div>' +
    '  <div style="font-size: 11px; color: #B45309; font-weight: 600;">موعد صدور أول تقرير (السعي الأول): ' + (c1DateTxt || 'المحدد بالمدرسة') + '</div>' +
    '</div>'
  ) : '';
  return '<!DOCTYPE html>' +
'<html lang="ar" dir="rtl">' +
'<head>' +
'  <meta charset="UTF-8">' +
'  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">' +
'  <title>بطاقة كشف العلامات الرسمية | EMIS</title>' +
'  <link rel="preconnect" href="https://fonts.googleapis.com">' +
'  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>' +
'  <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;600;700;800;900&display=swap" rel="stylesheet">' +
'  <script src="https://cdnjs.cloudflare.com/ajax/libs/html2pdf.js/0.10.1/html2pdf.bundle.min.js"></script>' +
'  <style>' +
'    * { box-sizing: border-box; margin: 0; padding: 0; font-family: "Cairo", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; -webkit-text-size-adjust: 100% !important; text-size-adjust: 100% !important; }' +
'    html, body { background-color: #0F172A; color: #0F172A; min-height: 100vh; max-height: 9999999px; }' +
'    .no-print { }' +
'    .portal-wrap { max-width: 480px; width: 100%; margin: 0 auto; padding: 6px 4px 20px; }' +
'    .search-card { background: #FFFFFF; border-radius: 12px; border: 1px solid #CBD5E1; padding: 18px 14px; margin-top: 10px; box-shadow: 0 4px 14px rgba(0,0,0,0.15); }' +
'    .portal-header-pill { display: inline-block; background: #EFF6FF; color: #1E3A8A; font-size: 11px; font-weight: 700; padding: 3px 10px; border-radius: 20px; margin-bottom: 8px; }' +
'    .form-group { margin-bottom: 12px; }' +
'    .form-label { display: block; font-weight: 700; font-size: 12px; margin-bottom: 4px; color: #1E293B; }' +
'    .form-input { width: 100%; padding: 10px 12px; border-radius: 8px; border: 1.5px solid #CBD5E1; font-size: 14px; outline: none; background: #FAFAFA; direction: ltr; text-align: right; }' +
'    .form-input:focus { border-color: #1E3A8A; background: #FFF; box-shadow: 0 0 0 3px rgba(30,58,138,0.15); }' +
'    .btn-submit { width: 100%; background: #1E3A8A; color: white; border: none; border-radius: 8px; padding: 12px; font-size: 14px; font-weight: 700; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 8px; }' +
'    .btn-submit:disabled { background: #94A3B8; cursor: not-allowed; }' +
'    .alert-err { background: #FEF2F2; color: #991B1B; border: 1px solid #FECACA; padding: 10px 12px; border-radius: 8px; margin-bottom: 12px; font-size: 12px; font-weight: 600; }' +
'    .spinner { border: 2.5px solid rgba(255,255,255,0.3); border-radius: 50%; border-top: 2.5px solid #FFF; width: 18px; height: 18px; animation: spin 0.8s linear infinite; }' +
'    @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }' +
'    .result-modal-card { background: #FFFFFF; border-radius: 12px; border: 1px solid #CBD5E1; padding: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.2); }' +
'    .dialog-top-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; padding: 2px 4px; direction: ltr; }' +
'    .btn-top-action { display: inline-flex; align-items: center; gap: 4px; background: transparent; border: 1px solid #72777A; border-radius: 18px; padding: 4px 10px; font-size: 11px; font-weight: 700; color: #1E293B; cursor: pointer; text-decoration: none; }' +
'    .btn-top-action:hover { background: #F1F5F9; }' +
'    .btn-top-close { background: transparent; border: none; cursor: pointer; color: #334155; display: inline-flex; align-items: center; justify-content: center; padding: 4px; border-radius: 50%; }' +
'    .btn-top-close:hover { background: #F1F5F9; }' +
'    .cert-container { border: 1.5px solid #1E293B; background: #FFFFFF; padding: 8px 6px; direction: rtl; }' +
'    .cert-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px; }' +
'    .cert-rep { font-size: 11px; font-weight: 700; color: #000; line-height: 1.25; }' +
'    .cert-min { font-size: 10px; font-weight: 700; color: #000; line-height: 1.25; }' +
'    .cert-dir { font-size: 10px; font-weight: 700; color: #000; line-height: 1.25; }' +
'    .cert-school { font-size: 12px; font-weight: 900; color: #000; line-height: 1.3; margin-top: 1px; }' +
'    .cert-badge-box { display: flex; border: 1.5px solid #1E293B; overflow: hidden; background: #FFFFFF; white-space: nowrap; }' +
'    .badge-year-cell { padding: 4px 8px; font-size: 12px; font-weight: 800; color: #000; border-left: 1.5px solid #1E293B; display: flex; align-items: center; justify-content: center; }' +
'    .badge-stage-cell { padding: 3px 6px; font-size: 10px; font-weight: 900; color: #1E3A8A; background: #EFF6FF; line-height: 1.2; text-align: center; }' +
'    .cert-meta-grid { border: 1px solid #1E293B; margin-bottom: 6px; font-size: 10px; overflow: hidden; }' +
'    .meta-row { display: flex; }' +
'    .meta-row-1 { border-bottom: 1px solid #1E293B; }' +
'    .meta-cell { padding: 3px 5px; display: flex; align-items: center; gap: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }' +
'    .meta-cell:not(:last-child) { border-left: 1px solid #1E293B; }' +
'    .meta-deg { flex: 1.2; }' +
'    .meta-spec { flex: 1; }' +
'    .meta-class { width: 55px; min-width: 50px; }' +
'    .meta-sec { width: 55px; min-width: 50px; }' +
'    .meta-name { flex: 1; }' +
'    .meta-lbl { font-weight: 700; color: #000; }' +
'    .meta-val { font-weight: 500; color: #000; }' +
'    .meta-name-hl { font-weight: 900; color: #1E3A8A; }' +
'    .cert-table { width: 100%; table-layout: fixed; border-collapse: collapse; border: 1px solid #334155; font-size: 10px; }' +
'    .cert-table th { background: #E2E8F0; color: #000; font-weight: 800; border: 1px solid #334155; padding: 4px 2px; text-align: center; font-size: 10px; }' +
'    .cert-table th.th-course { text-align: right; padding-right: 6px; }' +
'    .cert-table td { border: 0.5px solid #CBD5E1; padding: 3px 2px; text-align: center; font-size: 10px; color: #000; }' +
'    .td-course { text-align: right !important; padding-right: 6px !important; font-weight: 500 !important; color: #000; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }' +
'    .td-final { font-weight: 800 !important; color: #1E3A8A; }' +
'    .tr-sum { font-weight: 800; background: #F1F5F9; border-top: 1px solid #334155; }' +
'    .td-sum-final { color: #DC2626 !important; font-weight: 900 !important; font-size: 10.5px; }' +
'    .tr-avg { font-weight: 800; background: #FFFFFF; }' +
'    .td-avg-final { color: #16A34A !important; font-weight: 900 !important; font-size: 10.5px; }' +
'    .tr-res { font-weight: 800; color: #16A34A; background: #F0FDF4; }' +
'    .tr-rank { font-weight: 700; background: #F8FAFC; }' +
'    .cert-sigs { display: flex; justify-content: space-between; align-items: flex-start; text-align: center; margin-top: 10px; padding: 0 4px; }' +
'    .sig-col { flex: 1; }' +
'    .sig-t { font-size: 9.5px; font-weight: 800; color: #000; }' +
'    .sig-s { font-size: 8.5px; color: #64748B; margin-top: 1px; }' +
'    .stage-box { background: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 8px; padding: 8px 10px; margin-bottom: 8px; direction: rtl; }' +
'    .stage-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }' +
'    .stage-header-label { font-size: 11px; font-weight: 700; color: #334155; }' +
'    .stage-header-active { font-size: 11px; font-weight: 800; color: #1E3A8A; }' +
'    .stage-btn-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }' +
'    .st-btn { height: 48px; border-radius: 8px; border: 1px solid #CBD5E1; background: #FFFFFF; color: #1E293B; cursor: pointer; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 2px 4px; font-size: 9.5px; font-weight: 600; line-height: 1.25; text-align: center; transition: all 0.15s ease; outline: none; font-family: inherit; }' +
'    .st-btn.active { background: #1E3A8A !important; color: #FFFFFF !important; border-color: #172554 !important; font-weight: 800 !important; }' +
'    .st-btn.disabled { background: #F1F5F9 !important; color: #94A3B8 !important; border-color: #E2E8F0 !important; cursor: not-allowed; }' +
'    .st-lock { font-size: 10px; margin-bottom: 1px; display: inline-block; }' +
'    @media print {' +
'      body { background: white !important; }' +
'      .no-print { display: none !important; }' +
'      .portal-wrap { max-width: 100% !important; margin: 0 !important; padding: 0 !important; }' +
'      .result-modal-card { border: none !important; box-shadow: none !important; padding: 0 !important; }' +
'      .cert-container { border: 1.5px solid #000 !important; }' +
'    }' +
'  </style>' +
'</head>' +
'<body>' +
'  <main class="portal-wrap">' +
'    <div class="search-card no-print" id="login-box">' +
'      <div style="text-align: center; margin-bottom: 12px;">' +
'        <div class="portal-header-pill">الجمهورية اللبنانية - وزارة التربية والتعليم العالي</div>' +
'        <h2 style="font-size: 16px; font-weight: 900; color: #1E3A8A; margin-bottom: 2px;">بوابة كشف علامات الطلاب الرسمية</h2>' +
'        <p style="font-size: 11px; color: #64748B;" id="portal-sub-school"></p>' +
'      </div>' +
'      <div id="err-box" class="alert-err" style="display: none;"></div>' +
'      ' + noticeBanner +
'      <form id="form" onsubmit="doSubmit(event)">' +
'        <div class="form-group">' +
'          <label class="form-label">رقم الهاتف (6 أرقام المسجل بالمدرسة) *</label>' +
'          <input type="text" id="phone" class="form-input" placeholder="مثال: 196593" maxlength="10" required>' +
'        </div>' +
'        <div class="form-group">' +
'          <label class="form-label">تاريخ الميلاد *</label>' +
'          <input type="text" id="dob" class="form-input" placeholder="DD/MM/YYYY (مثال: 12/04/2002)" maxlength="10" inputmode="numeric" required>' +
'        </div>' +
'        <button type="submit" id="sbtn" class="btn-submit"><span id="stxt">عرض كشف العلامات الرسمي ❯</span><div id="sspin" class="spinner" style="display:none;"></div></button>' +
'      </form>' +
'    </div>' +
'    <div class="result-modal-card" id="res-box" style="display: none;">' +
'      <div class="dialog-top-bar no-print">' +
'        <div style="display: flex; gap: 6px; align-items: center;">' +
'          <button type="button" class="btn-top-action" onclick="doShareOrPrint()">' +
'            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92c0-1.61-1.31-2.92-2.92-2.92z"/></svg>' +
'            <span>مشاركة / طباعة</span>' +
'          </button>' +
'          <button type="button" class="btn-top-action" id="r-refresh-btn" onclick="doRefreshGrades()">' +
'            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg>' +
'            <span id="r-refresh-txt">تحديث العلامات</span>' +
'          </button>' +
'        </div>' +
'        <button type="button" class="btn-top-close" onclick="doReset()" title="إغلاق">' +
'          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>' +
'        </button>' +
'      </div>' +
'      <div class="stage-box no-print" id="r-stage-box">' +
'        <div class="stage-header-row" style="justify-content: center;">' +
'          <span class="stage-header-active" id="r-stage-card-title">بطاقة علامات السعي الاول</span>' +
'        </div>' +
'        <div class="stage-btn-grid">' +
'          <button type="button" id="btn-st-c1" class="st-btn active" onclick="switchReportStage(\'C1_ONLY\')">' +
'            <span id="st-lock-c1" class="st-lock" style="display:none;">🔒</span>' +
'            <span class="st-lbl">السعي الأول</span>' +
'          </button>' +
'          <button type="button" id="btn-st-c1e1" class="st-btn" onclick="switchReportStage(\'C1_E1\')">' +
'            <span id="st-lock-c1e1" class="st-lock" style="display:none;">🔒</span>' +
'            <span class="st-lbl">الفصل الأول</span>' +
'          </button>' +
'          <button type="button" id="btn-st-c2" class="st-btn" onclick="switchReportStage(\'C2_ONLY\')">' +
'            <span id="st-lock-c2" class="st-lock" style="display:none;">🔒</span>' +
'            <span class="st-lbl">السعي الثاني</span>' +
'          </button>' +
'          <button type="button" id="btn-st-all" class="st-btn" onclick="switchReportStage(\'ALL\')">' +
'            <span id="st-lock-all" class="st-lock" style="display:none;">🔒</span>' +
'            <span class="st-lbl">التقرير السنوي</span>' +
'          </button>' +
'        </div>' +
'      </div>' +
'      <div class="cert-container">' +
'        <div class="cert-header">' +
'          <div style="text-align: right;">' +
'            <div class="cert-rep">الجمهورية اللبنانية</div>' +
'            <div class="cert-min">وزارة التربية و التعليم العالي</div>' +
'            <div class="cert-dir">المديرية العامة للتعليم المهني والتقني</div>' +
'            <div class="cert-school" id="r-school-name"></div>' +
'          </div>' +
'          <div class="cert-badge-box">' +
'            <div class="badge-year-cell" id="r-school-year">2027-2028</div>' +
'            <div class="badge-stage-cell" id="r-stage-badge">' +
'              <div>بطاقة</div>' +
'              <div>علامات</div>' +
'              <div>السعي</div>' +
'              <div>الاول</div>' +
'            </div>' +
'          </div>' +
'        </div>' +
'        <div class="cert-meta-grid">' +
'          <div class="meta-row meta-row-1">' +
'            <div class="meta-cell meta-deg" style="flex: 1;">' +
'              <span class="meta-lbl">الشهادة و السنة و الاختصاص:</span>' +
'              <span class="meta-val" id="r-degree">الامتياز الفني TS1 - المعلوماتية الإدارية</span>' +
'            </div>' +
'          </div>' +
'          <div class="meta-row">' +
'            <div class="meta-cell meta-class">' +
'              <span class="meta-lbl">الصف:</span>' +
'              <span class="meta-val" id="r-class">TS1</span>' +
'            </div>' +
'            <div class="meta-cell meta-sec">' +
'              <span class="meta-lbl">الشعبة:</span>' +
'              <span class="meta-val" id="r-sec">F1</span>' +
'            </div>' +
'            <div class="meta-cell meta-name">' +
'              <span class="meta-lbl">اسم الطالب:</span>' +
'              <span class="meta-val meta-name-hl" id="r-name">...</span>' +
'            </div>' +
'          </div>' +
'        </div>' +
'        <table class="cert-table" id="r-table">' +
'          <thead id="r-thead"></thead>' +
'          <tbody id="r-tbody"></tbody>' +
'          <tfoot id="r-tfoot"></tfoot>' +
'        </table>' +
'        <div class="cert-sigs">' +
'          <div class="sig-col">' +
'            <div class="sig-t">رئيس الدروس النظرية</div>' +
'          </div>' +
'          <div class="sig-col">' +
'            <div class="sig-t">رئيس الدروس التطبيقية</div>' +
'          </div>' +
'          <div class="sig-col">' +
'            <div class="sig-t">اسم المدير:</div>' +
'            <div class="sig-s">التوقيع والختم الرسمي</div>' +
'          </div>' +
'        </div>' +
'      </div>' +
'    </div>' +
'  </main>' +
'  <script>' +
'    var dobEl = document.getElementById("dob");' +
'    dobEl.addEventListener("input", function() {' +
'      var v = this.value;' +
'      var digits = v.replace(/\\D/g, "").slice(0, 8);' +
'      if (digits.length > 4) {' +
'        this.value = digits.slice(0, 2) + "/" + digits.slice(2, 4) + "/" + digits.slice(4);' +
'      } else if (digits.length > 2) {' +
'        this.value = digits.slice(0, 2) + "/" + digits.slice(2);' +
'      } else {' +
'        this.value = digits;' +
'      }' +
'    });' +
'    function doSubmit(e) {' +
'      if(e && e.preventDefault) e.preventDefault();' +
'      var err = document.getElementById("err-box"); err.style.display="none";' +
'      var phone = document.getElementById("phone").value.trim();' +
'      var dob = document.getElementById("dob").value.trim();' +
'      if(!phone || !dob) return;' +
'      if(' + isNotIssued + ') {' +
'        err.textContent = "لم يصدر اي تقرير حتى الان";' +
'        err.style.display = "block";' +
'        return;' +
'      }' +
'      var sbtn = document.getElementById("sbtn"); sbtn.disabled = true;' +
'      document.getElementById("stxt").style.display = "none";' +
'      document.getElementById("sspin").style.display = "inline-block";' +
'      if(typeof google !== "undefined" && google.script && google.script.run) {' +
'        google.script.run.withSuccessHandler(function(raw){' +
'          sbtn.disabled = false;' +
'          document.getElementById("stxt").style.display = "inline";' +
'          document.getElementById("sspin").style.display = "none";' +
'          var data = typeof raw === "string" ? JSON.parse(raw) : raw;' +
'          if(data.status === "success") renderData(data, phone, dob); else { err.textContent = data.message; err.style.display = "block"; }' +
'        }).withFailureHandler(function(e){' +
'          sbtn.disabled = false;' +
'          document.getElementById("stxt").style.display = "inline";' +
'          document.getElementById("sspin").style.display = "none";' +
'          err.textContent = e.message || e; err.style.display = "block";' +
'        }).handleGetStudentGradesJson(phone, dob, null);' +
'      } else {' +
'        var u = window.location.pathname + "?action=getStudentGrades&phone=" + encodeURIComponent(phone) + "&dob=" + encodeURIComponent(dob);' +
'        fetch(u).then(function(r){ return r.json(); }).then(function(data){' +
'          sbtn.disabled = false;' +
'          document.getElementById("stxt").style.display = "inline";' +
'          document.getElementById("sspin").style.display = "none";' +
'          if(data.status === "success") renderData(data, phone, dob); else { err.textContent = data.message; err.style.display = "block"; }' +
'        }).catch(function(e){' +
'          sbtn.disabled = false;' +
'          document.getElementById("stxt").style.display = "inline";' +
'          document.getElementById("sspin").style.display = "none";' +
'          err.textContent = e.message || e; err.style.display = "block";' +
'        });' +
'      }' +
'    }' +
'    function doRefreshGrades() {' +
'      var rbtn = document.getElementById("r-refresh-btn");' +
'      var rtxt = document.getElementById("r-refresh-txt");' +
'      if(rbtn) rbtn.disabled = true;' +
'      if(rtxt) rtxt.textContent = "جاري التحديث...";' +
'      var phone = document.getElementById("phone").value.trim();' +
'      var dob = document.getElementById("dob").value.trim();' +
'      if(!phone || !dob) { if(rbtn) rbtn.disabled=false; return; }' +
'      if(typeof google !== "undefined" && google.script && google.script.run) {' +
'        google.script.run.withSuccessHandler(function(raw){' +
'          if(rbtn) rbtn.disabled = false;' +
'          if(rtxt) rtxt.textContent = "تحديث العلامات";' +
'          var data = typeof raw === "string" ? JSON.parse(raw) : raw;' +
'          if(data.status === "success") renderData(data, phone, dob);' +
'        }).withFailureHandler(function(){' +
'          if(rbtn) rbtn.disabled = false;' +
'          if(rtxt) rtxt.textContent = "تحديث العلامات";' +
'        }).handleGetStudentGradesJson(phone, dob, null);' +
'      } else {' +
'        var u = window.location.pathname + "?action=getStudentGrades&phone=" + encodeURIComponent(phone) + "&dob=" + encodeURIComponent(dob);' +
'        fetch(u).then(function(r){ return r.json(); }).then(function(data){' +
'          if(rbtn) rbtn.disabled = false;' +
'          if(rtxt) rtxt.textContent = "تحديث العلامات";' +
'          if(data.status === "success") renderData(data, phone, dob);' +
'        }).catch(function(){' +
'          if(rbtn) rbtn.disabled = false;' +
'          if(rtxt) rtxt.textContent = "تحديث العلامات";' +
'        });' +
'      }' +
'    }' +
'    function doShareOrPrint() {' +
'      var cert = document.querySelector(".cert-container");' +
'      var s = (document.getElementById("r-school-name") ? document.getElementById("r-school-name").textContent.trim() : "") || "المدرسة";' +
'      var n = (document.getElementById("r-name") ? document.getElementById("r-name").textContent.trim() : "") || "طالب";' +
'      var c = (document.getElementById("r-class") ? document.getElementById("r-class").textContent.trim() : "") || "صف";' +
'      var safeName = n.replace(/[/\\?%*:|"<>]/g, "_").replace(/\s+/g, "_");' +
'      var fileName = "كشف_علامات_" + safeName + "_" + c + ".pdf";' +
'      var btn = (typeof event !== "undefined" && event && event.currentTarget) ? event.currentTarget : null;' +
'      var originalHtml = btn ? btn.innerHTML : "";' +
'      if (btn) {' +
'        btn.innerHTML = \'<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg><span>جاري تصدير PDF...</span>\';' +
'        btn.style.pointerEvents = "none";' +
'      }' +
'      function restoreBtn() {' +
'        if (btn) {' +
'          btn.innerHTML = originalHtml;' +
'          btn.style.pointerEvents = "auto";' +
'        }' +
'      }' +
'      if (typeof html2pdf !== "undefined" && cert) {' +
'        var opt = {' +
'          margin: [3, 3, 3, 3],' +
'          filename: fileName,' +
'          image: { type: "jpeg", quality: 0.98 },' +
'          html2canvas: { scale: 2, useCORS: true, logging: false, scrollY: 0 },' +
'          jsPDF: { unit: "mm", format: "a4", orientation: "portrait" }' +
'        };' +
'        html2pdf().set(opt).from(cert).save().then(function() {' +
'          restoreBtn();' +
'        }).catch(function(err) {' +
'          restoreBtn();' +
'          window.print();' +
'        });' +
'      } else {' +
'        restoreBtn();' +
'        window.print();' +
'      }' +
'    }' +
'    function fmt(val) {' +
'      if (val === null || val === undefined || isNaN(val)) return "—";' +
'      var num = Number(val);' +
'      if (Math.abs(num - Math.round(num)) < 0.001) return num.toString();' +
'      return num.toFixed(2);' +
'    }' +
'    function fmtFinal(val) {' +
'      if (val === null || val === undefined || isNaN(val)) return "—";' +
'      var num = Number(val);' +
'      return num.toFixed(2);' +
'    }' +
'    var cachedStudentData = null;' +
'    var currentActiveStage = "AUTO";' +
'    function switchReportStage(st, force) {' +
'      if (!cachedStudentData) return;' +
'      var btnMap = {' +
'        "C1_ONLY": "btn-st-c1",' +
'        "C1_E1": "btn-st-c1e1",' +
'        "C2_ONLY": "btn-st-c2",' +
'        "ALL": "btn-st-all"' +
'      };' +
'      var targetBtn = document.getElementById(btnMap[st]);' +
'      if (!force && targetBtn && targetBtn.getAttribute("data-locked") === "1") {' +
'        var dDate = targetBtn.getAttribute("data-deadline") || "";' +
'        alert("هذا الامتحان غير متاح حتى انقضاء الموعد المحدد" + (dDate ? " (" + dDate + ")" : ""));' +
'        return;' +
'      }' +
'      currentActiveStage = st;' +
'      Object.keys(btnMap).forEach(function(k){' +
'        var b = document.getElementById(btnMap[k]);' +
'        if (b) {' +
'          if (k === st) b.classList.add("active");' +
'          else b.classList.remove("active");' +
'        }' +
'      });' +
'      renderStageTable(st);' +
'    }' +
'    function renderStageTable(st) {' +
'      if (!cachedStudentData) return;' +
'      var data = cachedStudentData;' +
'      var isC1 = (st === "C1_ONLY");' +
'      var isC1E1 = (st === "C1_E1");' +
'      var isC2 = (st === "C2_ONLY");' +
'      var isAll = (st === "ALL");' +
'      var titleMap = {' +
'        "C1_ONLY": "بطاقة علامات السعي الاول",' +
'        "C1_E1": "بطاقة علامات الفصل الاول",' +
'        "C2_ONLY": "بطاقة علامات السعي الثاني",' +
'        "ALL": "بطاقة علامات الطالب للعام"' +
'      };' +
'      var badgeMap = {' +
'        "C1_ONLY": ["بطاقة", "علامات", "السعي", "الاول"],' +
'        "C1_E1": ["بطاقة", "علامات", "الفصل", "الاول"],' +
'        "C2_ONLY": ["بطاقة", "علامات", "السعي", "الثاني"],' +
'        "ALL": ["بطاقة", "علامات", "التقرير", "السنوي"]' +
'      };' +
'      var activeTitleEl = document.getElementById("r-stage-card-title");' +
'      if (activeTitleEl) activeTitleEl.textContent = titleMap[st] || "بطاقة علامات";' +
'      var sLines = badgeMap[st] || ["بطاقة", "علامات"];' +
'      var sHtml = "";' +
'      sLines.forEach(function(l){ sHtml += "<div>" + l + "</div>"; });' +
'      var badgeEl = document.getElementById("r-stage-badge");' +
'      if (badgeEl) badgeEl.innerHTML = sHtml;' +
'      var thead = document.getElementById("r-thead");' +
'      var tbody = document.getElementById("r-tbody");' +
'      var tfoot = document.getElementById("r-tfoot");' +
'      if (isC1) {' +
'        thead.innerHTML = "<tr>" +' +
'          "<th class=\'th-course\' style=\'width: 32%;\'>اسم المادة</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>العلامة القصوى</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>علامة السعي</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>علامة الفصل الاول</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>المعدل النهائي</th>" +' +
'        "</tr>";' +
'      } else if (isC2) {' +
'        thead.innerHTML = "<tr>" +' +
'          "<th class=\'th-course\' style=\'width: 32%;\'>اسم المادة</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>العلامة القصوى</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>علامة السعي</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>علامة الفصل الثاني</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>المعدل النهائي</th>" +' +
'        "</tr>";' +
'      } else if (isC1E1) {' +
'        thead.innerHTML = "<tr>" +' +
'          "<th class=\'th-course\' style=\'width: 32%;\'>اسم المادة</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>العلامة القصوى</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>علامة السعي</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>علامة الفصل الاول</th>" +' +
'          "<th style=\'width: 17%; font-size: 9.5px;\'>المعدل النهائي</th>" +' +
'        "</tr>";' +
'      } else {' +
'        thead.innerHTML = "<tr>" +' +
'          "<th class=\'th-course\' style=\'width: 26%;\'>اسم المادة</th>" +' +
'          "<th style=\'width: 12%; font-size: 9px;\'>القصوى</th>" +' +
'          "<th style=\'width: 12%; font-size: 9px;\'>سعي 1</th>" +' +
'          "<th style=\'width: 12%; font-size: 9px;\'>فصل 1</th>" +' +
'          "<th style=\'width: 12%; font-size: 9px;\'>سعي 2</th>" +' +
'          "<th style=\'width: 12%; font-size: 9px;\'>فصل 2</th>" +' +
'          "<th style=\'width: 14%; font-size: 9px;\'>النهائي</th>" +' +
'        "</tr>";' +
'      }' +
'      tbody.innerHTML = "";' +
'      var maxTotal = 0;' +
'      var c1Total = 0;' +
'      var e1Total = 0;' +
'      var c2Total = 0;' +
'      var e2Total = 0;' +
'      var finalTotal = 0;' +
'      (data.courses || []).forEach(function(cr, idx){' +
'        var tr = document.createElement("tr");' +
'        if (idx % 2 === 1) tr.style.backgroundColor = "#F8FAFC";' +
'        var cMax = cr.maxScore || 20;' +
'        maxTotal += cMax;' +
'        var c1 = (cr.c1Score !== null && cr.c1Score !== undefined) ? cr.c1Score : null;' +
'        var e1 = (cr.e1Score !== null && cr.e1Score !== undefined) ? cr.e1Score : null;' +
'        var c2 = (cr.c2Score !== null && cr.c2Score !== undefined) ? cr.c2Score : null;' +
'        var e2 = (cr.e2Score !== null && cr.e2Score !== undefined) ? cr.e2Score : null;' +
'        if (c1 !== null) c1Total += c1;' +
'        if (e1 !== null) e1Total += e1;' +
'        if (c2 !== null) c2Total += c2;' +
'        if (e2 !== null) e2Total += e2;' +
'        var fin = 0;' +
'        if (isC1) fin = c1;' +
'        else if (isC2) fin = c2;' +
'        else if (isC1E1) fin = (c1 !== null && e1 !== null) ? (c1 * 0.2 + e1 * 0.8) : (c1 || e1);' +
'        else fin = (c1 || 0) * 0.1 + (e1 || 0) * 0.4 + (c2 || 0) * 0.1 + (e2 || 0) * 0.4;' +
'        if (fin !== null && fin !== undefined) finalTotal += fin;' +
'        if (isC1) {' +
'          tr.innerHTML = "<td class=\'td-course\'>" + cr.courseName + "</td>" +' +
'            "<td>" + cMax + "</td>" +' +
'            "<td>" + fmt(c1) + "</td>" +' +
'            "<td></td>" +' +
'            "<td class=\'td-final\'>" + fmtFinal(fin) + "</td>";' +
'        } else if (isC2) {' +
'          tr.innerHTML = "<td class=\'td-course\'>" + cr.courseName + "</td>" +' +
'            "<td>" + cMax + "</td>" +' +
'            "<td>" + fmt(c2) + "</td>" +' +
'            "<td></td>" +' +
'            "<td class=\'td-final\'>" + fmtFinal(fin) + "</td>";' +
'        } else if (isC1E1) {' +
'          tr.innerHTML = "<td class=\'td-course\'>" + cr.courseName + "</td>" +' +
'            "<td>" + cMax + "</td>" +' +
'            "<td>" + fmt(c1) + "</td>" +' +
'            "<td>" + fmt(e1) + "</td>" +' +
'            "<td class=\'td-final\'>" + fmtFinal(fin) + "</td>";' +
'        } else {' +
'          tr.innerHTML = "<td class=\'td-course\'>" + cr.courseName + "</td>" +' +
'            "<td>" + cMax + "</td>" +' +
'            "<td>" + fmt(c1) + "</td>" +' +
'            "<td>" + fmt(e1) + "</td>" +' +
'            "<td>" + fmt(c2) + "</td>" +' +
'            "<td>" + fmt(e2) + "</td>" +' +
'            "<td class=\'td-final\'>" + fmtFinal(fin) + "</td>";' +
'        }' +
'        tbody.appendChild(tr);' +
'      });' +
'      var c1Avg = (maxTotal > 0) ? (c1Total / maxTotal) * 20 : 0;' +
'      var e1Avg = (maxTotal > 0) ? (e1Total / maxTotal) * 20 : 0;' +
'      var c2Avg = (maxTotal > 0) ? (c2Total / maxTotal) * 20 : 0;' +
'      var e2Avg = (maxTotal > 0) ? (e2Total / maxTotal) * 20 : 0;' +
'      var finalAvg = (maxTotal > 0) ? (finalTotal / maxTotal) * 20 : 0;' +
'      var isPassed = finalAvg >= 10.0;' +
'      var resText = isPassed ? "ناجح" : "راسب";' +
'      var totHtml = "";' +
'      var avgHtml = "";' +
'      var resHtml = "";' +
'      if (isC1) {' +
'        totHtml = "<tr class=\'tr-sum\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>المجموع</td>" +' +
'          "<td>" + maxTotal + "</td>" +' +
'          "<td>" + fmt(c1Total) + "</td>" +' +
'          "<td></td>" +' +
'          "<td class=\'td-sum-final\'>" + fmtFinal(finalTotal) + "</td>" +' +
'        "</tr>";' +
'        avgHtml = "<tr class=\'tr-avg\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>المعدل العام 20/</td>" +' +
'          "<td style=\'font-size: 8px; color: #475569; line-height: 1.1;\'>معدل النجاح: 10 \\ 20</td>" +' +
'          "<td>" + fmt(c1Avg) + "</td>" +' +
'          "<td></td>" +' +
'          "<td class=\'td-avg-final\' style=\'color:" + (isPassed ? "#16A34A" : "#DC2626") + " !important;\'>" + fmt(finalAvg) + "</td>" +' +
'        "</tr>";' +
'        resHtml = "<tr class=\'tr-res\' style=\'color:" + (isPassed ? "#16A34A" : "#DC2626") + "; background:" + (isPassed ? "#F0FDF4" : "#FEF2F2") + ";\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>النتيجة</td>" +' +
'          "<td></td>" +' +
'          "<td>" + (c1Avg >= 10.0 ? "ناجح" : "راسب") + "</td>" +' +
'          "<td></td>" +' +
'          "<td>" + resText + "</td>" +' +
'        "</tr>";' +
'      } else if (isC2) {' +
'        totHtml = "<tr class=\'tr-sum\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>المجموع</td>" +' +
'          "<td>" + maxTotal + "</td>" +' +
'          "<td>" + fmt(c2Total) + "</td>" +' +
'          "<td></td>" +' +
'          "<td class=\'td-sum-final\'>" + fmtFinal(finalTotal) + "</td>" +' +
'        "</tr>";' +
'        avgHtml = "<tr class=\'tr-avg\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>المعدل العام 20/</td>" +' +
'          "<td style=\'font-size: 8px; color: #475569; line-height: 1.1;\'>معدل النجاح: 10 \\ 20</td>" +' +
'          "<td>" + fmt(c2Avg) + "</td>" +' +
'          "<td></td>" +' +
'          "<td class=\'td-avg-final\' style=\'color:" + (isPassed ? "#16A34A" : "#DC2626") + " !important;\'>" + fmt(finalAvg) + "</td>" +' +
'        "</tr>";' +
'        resHtml = "<tr class=\'tr-res\' style=\'color:" + (isPassed ? "#16A34A" : "#DC2626") + "; background:" + (isPassed ? "#F0FDF4" : "#FEF2F2") + ";\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>النتيجة</td>" +' +
'          "<td></td>" +' +
'          "<td>" + (c2Avg >= 10.0 ? "ناجح" : "راسب") + "</td>" +' +
'          "<td></td>" +' +
'          "<td>" + resText + "</td>" +' +
'        "</tr>";' +
'      } else if (isC1E1) {' +
'        totHtml = "<tr class=\'tr-sum\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>المجموع</td>" +' +
'          "<td>" + maxTotal + "</td>" +' +
'          "<td>" + fmt(c1Total) + "</td>" +' +
'          "<td>" + fmt(e1Total) + "</td>" +' +
'          "<td class=\'td-sum-final\'>" + fmtFinal(finalTotal) + "</td>" +' +
'        "</tr>";' +
'        avgHtml = "<tr class=\'tr-avg\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>المعدل العام 20/</td>" +' +
'          "<td style=\'font-size: 8px; color: #475569; line-height: 1.1;\'>معدل النجاح: 10 \\ 20</td>" +' +
'          "<td>" + fmt(c1Avg) + "</td>" +' +
'          "<td>" + fmt(e1Avg) + "</td>" +' +
'          "<td class=\'td-avg-final\' style=\'color:" + (isPassed ? "#16A34A" : "#DC2626") + " !important;\'>" + fmt(finalAvg) + "</td>" +' +
'        "</tr>";' +
'        resHtml = "<tr class=\'tr-res\' style=\'color:" + (isPassed ? "#16A34A" : "#DC2626") + "; background:" + (isPassed ? "#F0FDF4" : "#FEF2F2") + ";\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>النتيجة</td>" +' +
'          "<td></td>" +' +
'          "<td>" + (c1Avg >= 10.0 ? "ناجح" : "راسب") + "</td>" +' +
'          "<td>" + (e1Total > 0 ? (e1Avg >= 10.0 ? "ناجح" : "راسب") : "") + "</td>" +' +
'          "<td>" + resText + "</td>" +' +
'        "</tr>";' +
'      } else {' +
'        totHtml = "<tr class=\'tr-sum\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>المجموع</td>" +' +
'          "<td>" + maxTotal + "</td>" +' +
'          "<td>" + fmt(c1Total) + "</td>" +' +
'          "<td>" + fmt(e1Total) + "</td>" +' +
'          "<td>" + fmt(c2Total) + "</td>" +' +
'          "<td>" + fmt(e2Total) + "</td>" +' +
'          "<td class=\'td-sum-final\'>" + fmtFinal(finalTotal) + "</td>" +' +
'        "</tr>";' +
'        avgHtml = "<tr class=\'tr-avg\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>المعدل العام 20/</td>" +' +
'          "<td style=\'font-size: 8px; color: #475569; line-height: 1.1;\'>معدل النجاح: 10 \\ 20</td>" +' +
'          "<td>" + fmt(c1Avg) + "</td>" +' +
'          "<td>" + fmt(e1Avg) + "</td>" +' +
'          "<td>" + fmt(c2Avg) + "</td>" +' +
'          "<td>" + fmt(e2Avg) + "</td>" +' +
'          "<td class=\'td-avg-final\' style=\'color:" + (isPassed ? "#16A34A" : "#DC2626") + " !important;\'>" + fmt(finalAvg) + "</td>" +' +
'        "</tr>";' +
'        resHtml = "<tr class=\'tr-res\' style=\'color:" + (isPassed ? "#16A34A" : "#DC2626") + "; background:" + (isPassed ? "#F0FDF4" : "#FEF2F2") + ";\'>" +' +
'          "<td class=\'td-course\' style=\'font-weight:700;\'>النتيجة</td>" +' +
'          "<td></td>" +' +
'          "<td>" + (c1Total > 0 ? (c1Avg >= 10.0 ? "ناجح" : "راسب") : "") + "</td>" +' +
'          "<td>" + (e1Total > 0 ? (e1Avg >= 10.0 ? "ناجح" : "راسب") : "") + "</td>" +' +
'          "<td>" + (c2Total > 0 ? (c2Avg >= 10.0 ? "ناجح" : "راسب") : "") + "</td>" +' +
'          "<td>" + (e2Total > 0 ? (e2Avg >= 10.0 ? "ناجح" : "راسب") : "") + "</td>" +' +
'          "<td>" + resText + "</td>" +' +
'        "</tr>";' +
'      }' +
'      var colSpanTotal = (isC1 || isC2) ? 5 : ((isC1E1) ? 5 : 7);' +
'      var cs1 = Math.floor(colSpanTotal / 2);' +
'      var cs2 = colSpanTotal - cs1;' +
'      var rnkHtml = "<tr class=\'tr-rank\'>" +' +
'        "<td colspan=\'" + cs1 + "\' style=\'text-align:right; padding-right:6px;\'>عدد الطلاب: <strong>" + (data.classStudentCount || 20) + "</strong></td>" +' +
'        "<td colspan=\'" + cs2 + "\' style=\'text-align:left; padding-left:6px; color:#1E3A8A;\'>المرتبة: <strong>" + (data.rank || 1) + "</strong></td>" +' +
'      "</tr>";' +
'      tfoot.innerHTML = totHtml + avgHtml + resHtml + rnkHtml;' +
'    }' +
'    function renderData(data, phone, dob) {' +
'      cachedStudentData = data;' +
'      document.getElementById("login-box").style.display = "none";' +
'      document.getElementById("res-box").style.display = "block";' +
'      document.getElementById("r-school-name").textContent = data.schoolName || "";' +
'      document.getElementById("r-school-year").textContent = data.schoolYear || "2027-2028";' +
'      var degSpec = data.className || ((data.degreeAndYear ? data.degreeAndYear + " - " : "") + (data.specialty || ""));' +
'      var rDeg = document.getElementById("r-degree"); if (rDeg) rDeg.textContent = degSpec;' +
'      var rSpec = document.getElementById("r-spec"); if (rSpec) rSpec.textContent = data.specialty || "المعلوماتية الإدارية";' +
'      document.getElementById("r-class").textContent = data.classOnly || "TS1";' +
'      document.getElementById("r-sec").textContent = data.section || "F1";' +
'      document.getElementById("r-name").textContent = data.studentName || "طالب معتمد";' +
'      var d = data.deadlines || {};' +
'      var c1Ok = (d.c1Passed !== undefined) ? d.c1Passed : true;' +
'      var e1Ok = (d.e1Passed !== undefined) ? d.e1Passed : false;' +
'      var c2Ok = (d.c2Passed !== undefined) ? d.c2Passed : false;' +
'      var allOk = (d.e2Passed !== undefined) ? d.e2Passed : false;' +
'      if (!c1Ok) {' +
'        document.getElementById("res-box").style.display = "none";' +
'        document.getElementById("login-box").style.display = "block";' +
'        var err = document.getElementById("err-box");' +
'        if (err) {' +
'          err.textContent = "لم يصدر اي تقرير حتى الان";' +
'          err.style.display = "block";' +
'        }' +
'        return;' +
'      }' +
'      function setupStBtn(btnId, lockId, isOk, dDate) {' +
'        var btn = document.getElementById(btnId);' +
'        var lk = document.getElementById(lockId);' +
'        if (isOk) {' +
'          if (lk) lk.style.display = "none";' +
'          btn.classList.remove("disabled");' +
'          btn.setAttribute("data-locked", "0");' +
'        } else {' +
'          if (lk) lk.style.display = "inline-block";' +
'          btn.classList.add("disabled");' +
'          btn.setAttribute("data-locked", "1");' +
'          btn.setAttribute("data-deadline", dDate || "");' +
'        }' +
'      }' +
'      setupStBtn("btn-st-c1", "st-lock-c1", c1Ok, d.c1Date);' +
'      setupStBtn("btn-st-c1e1", "st-lock-c1e1", e1Ok, d.e1Date);' +
'      setupStBtn("btn-st-c2", "st-lock-c2", c2Ok, d.c2Date);' +
'      setupStBtn("btn-st-all", "st-lock-all", allOk, d.e2Date);' +
'      var initStage = (currentActiveStage && currentActiveStage !== "AUTO") ? currentActiveStage : (data.stage || "C1_ONLY");' +
'      switchReportStage(initStage, true);' +
'    }' +
'    function doReset() {' +
'      cachedStudentData = null;' +
'      currentActiveStage = "AUTO";' +
'      document.getElementById("res-box").style.display = "none";' +
'      document.getElementById("login-box").style.display = "block";' +
'      document.getElementById("phone").value = "";' +
'      document.getElementById("dob").value = "";' +
'    }' +
'  </script>' +
'</body>' +
'</html>';
}

function openTargetSpreadsheet(sheetId) {
  if (sheetId && sheetId.toString().trim() !== "") {
    try {
      return SpreadsheetApp.openById(sheetId.toString().trim());
    } catch (err) {
      return SpreadsheetApp.getActiveSpreadsheet();
    }
  }
  return SpreadsheetApp.getActiveSpreadsheet();
}

/**
 * يفحص خلايا المواد في الصفوف 3، 27، 51... 267 (من العمود C إلى AA)
 * إذا كانت أي خلية مميزة باللون الأحمر، يتم تسجيلها كمادة مقفلة
 */
function getCourseRedHighlights(ss) {
  var examTabs = [
    "علامات السعي الاول", "علامات الامتحان الاول", "علامات السعي الثاني", "علامات الامتحان الثاني",
    "علامات السعي الأول", "علامات الامتحان الأول",
    "السعي الاول", "الامتحان الاول", "السعي الثاني", "الامتحان الثاني"
  ];
  var locked = {};
  
  examTabs.forEach(function(tabName) {
    var sheet = ss.getSheetByName(tabName);
    if (!sheet) {
      var all = ss.getSheets();
      var clean = normalizeArabicText(tabName);
      for (var s = 0; s < all.length; s++) {
        if (normalizeArabicText(all[s].getName()) === clean) {
          sheet = all[s];
          break;
        }
      }
    }
    if (!sheet) return;
    
    var examCode = "";
    if (tabName.indexOf("الاول") !== -1 || tabName.indexOf("الأول") !== -1) {
      examCode = (tabName.indexOf("السعي") !== -1) ? "C1" : "E1";
    } else if (tabName.indexOf("الثاني") !== -1) {
      examCode = (tabName.indexOf("السعي") !== -1) ? "C2" : "E2";
    }

    // 12 صفاً للمواد:
    // الشعبة 1: السطر 3
    // الشعبة 2: السطر 27
    // الشعبة 3: السطر 51
    // ...
    // الشعبة 12: السطر 267
    for (var classIdx = 1; classIdx <= 12; classIdx++) {
      var courseRow = 3 + (classIdx - 1) * 24;
      if (courseRow > sheet.getLastRow()) continue;
      
      // الأعمدة من C إلى AA: العمود 3 إلى 27 (عددها 25 عموداً)
      var range = sheet.getRange(courseRow, 3, 1, 25);
      var backgrounds = range.getBackgrounds()[0];
      
      for (var colIdx = 0; colIdx < backgrounds.length; colIdx++) {
        var col0Based = 2 + colIdx; // 2 corresponds to Column C (0-indexed)
        var bg = backgrounds[colIdx] || "";
        
        if (isRedColor(bg)) {
          var courseId = "c_" + classIdx + "_" + col0Based;
          // تمييز القفل لكل صفحة امتحان على حدة
          if (examCode) {
            locked[examCode + "_" + courseId] = true;
          }
          locked[tabName + "_" + courseId] = true;
        }
      }
    }
  });
  
  return locked;
}

function isRedColor(hex) {
  if (!hex || hex === "" || hex === "#ffffff") return false;
  var h = hex.toString().toLowerCase().trim();
  if (h === "red") return true;
  if (h.charAt(0) === '#') {
    var color = h.substring(1);
    if (color.length === 3) {
      color = color[0] + color[0] + color[1] + color[1] + color[2] + color[2];
    }
    if (color.length === 6) {
      var r = parseInt(color.substring(0, 2), 16);
      var g = parseInt(color.substring(2, 4), 16);
      var b = parseInt(color.substring(4, 6), 16);
      // التحقق من درجات اللون الأحمر (Google Sheets Red palette: #ea4335, #e06666, #cc0000, #f4c7c3, etc.)
      if (r > 160 && g < 140 && b < 140) return true;
      if (r > 180 && r > g * 1.3 && r > b * 1.3) return true;
      if (r > 200 && (r - g > 30) && (r - b > 30)) return true;
    }
  }
  return false;
}

function normalizeArabicText(text) {
  if (!text) return "";
  return text.toString()
    .trim()
    .replace(/[أإآ]/g, "ا")
    .replace(/[ة]/g, "ه")
    .replace(/[ـ]/g, "")
    .replace(/\s+/g, " ");
}
""".trimIndent()
    }

    fun parseLocalizedInt(str: String): Int? {
        val normalized = str
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
            .trim()
        return normalized.toIntOrNull()
    }

    fun parseLocalizedDouble(str: String): Double? {
        val trimmed = str.trim().removeSurrounding("\"").removeSurrounding("'")
        if (trimmed.isEmpty()) return null
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
            .replace(',', '.')
            .replace('٫', '.')
            .replace('،', '.')
            .replace("\u00A0", "")
            .trim()
        return normalized.toDoubleOrNull()
    }

    private fun getColumnLetter(zeroIndexedCol: Int): String {
        var n = zeroIndexedCol
        var result = ""
        while (n >= 0) {
            result = ('A'.code + (n % 26)).toChar() + result
            n = (n / 26) - 1
        }
        return result
    }

    /**
     * Generates an authentic complete initial dataset mirroring the exact requirements
     * so that the application is 100% operational immediately.
     */
    fun createDefaultDataset(): InitialSchoolData {
        val teachers = listOf(
            Teacher("t_admin", "admin", "admin123", isAdmin = true),
            Teacher("t_1", "عبير فريز غضبان", "2643", isAdmin = false),
            Teacher("t_2", "وليد حنا فرسان", "3866", isAdmin = false),
            Teacher("t_3", "بسام سجعان الحاج", "7690", isAdmin = false),
            Teacher("t_4", "كارلوس يوسف خشان", "5582", isAdmin = false),
            Teacher("t_5", "دامرس فرج الله خيرالله", "9846", isAdmin = false),
            Teacher("t_6", "شربل اسعد بارود", "1198", isAdmin = false),
            Teacher("t_7", "أ. محمد السعيد", "teach101", isAdmin = false),
            Teacher("t_8", "أ. أحمد خليل", "teach102", isAdmin = false)
        )

        // Generate courses for the classes, including Abeer's 6 authentic courses
        val defaultCourses = mutableListOf<Course>()

        // 1. Class 1 (TS1 INF): Complete authentic curriculum with all 16 courses
        defaultCourses.add(Course("c_1_2", 1, "الاحصاء", 6, "عبير فريز غضبان", "C", 2))
        defaultCourses.add(Course("c_1_3", 1, "الاقتصاد", 4, "عبير فريز غضبان", "D", 3))
        defaultCourses.add(Course("c_1_4", 1, "الرياضيات", 4, "ايلي جورج جبور", "E", 4))
        defaultCourses.add(Course("c_1_5", 1, "الرياضيات المالية", 6, "كوكب عزيز القديسي", "F", 5))
        defaultCourses.add(Course("c_1_6", 1, "الغروتيم و هيكلية المعطيات", 8, "وليد حنا فرسان", "G", 6))
        defaultCourses.add(Course("c_1_7", 1, "الغروتيم و هيكلية المعطيات TP", 6, "وليد حنا فرسان", "H", 7))
        defaultCourses.add(Course("c_1_8", 1, "القانون", 4, "انطوان جورج الخوري", "I", 8))
        defaultCourses.add(Course("c_1_9", 1, "اللغة الاجنبية الثانية", 4, "رابيكا جان ابي شبل", "J", 9))
        defaultCourses.add(Course("c_1_10", 1, "برمجة VB TP", 8, "وليد حنا فرسان", "K", 10))
        defaultCourses.add(Course("c_1_11", 1, "طرائق التحليل", 10, "وليد حنا فرسان", "L", 11))
        defaultCourses.add(Course("c_1_12", 1, "قواعد المعطيات", 8, "وليد حنا فرسان", "M", 12))
        defaultCourses.add(Course("c_1_13", 1, "قواعد المعطيات TP", 6, "وليد حنا فرسان", "N", 13))
        defaultCourses.add(Course("c_1_14", 1, "لغة اجنبية اولى", 4, "منى كمال الياس حبيب", "O", 14))
        defaultCourses.add(Course("c_1_15", 1, "مبادئ الادارة", 4, "سعاد طوني ادو", "P", 15))
        defaultCourses.add(Course("c_1_16", 1, "محاسبة عامة", 10, "عبير فريز غضبان", "Q", 16))
        defaultCourses.add(Course("c_1_17", 1, "هندسة انظمة المعلوماتية", 8, "وليد حنا فرسان", "R", 17))

        // Class 3 (TS1 HOT): مبادىء الإقتصاد الجزئي, مدخل الى علم الاحصاء
        defaultCourses.add(Course("c_3_10", 3, "مبادىء الإقتصاد الجزئي", 3, "عبير فريز غضبان", "K", 10))
        defaultCourses.add(Course("c_3_15", 3, "مدخل الى علم الاحصاء", 3, "عبير فريز غضبان", "P", 15))

        // Class 11 (TS2 CLI): planification
        defaultCourses.add(Course("c_11_17", 11, "planification", 2, "عبير فريز غضبان", "R", 17))

        // Other courses across classes for other teachers
        classBlocks.forEach { block ->
            if (block.index != 1) {
                defaultCourses.add(Course("c_${block.index}_2", block.index, "الرياضيات / Math", 4, "وليد حنا فرسان", "C", 2))
            }
            if (block.index != 1) {
                defaultCourses.add(Course("c_${block.index}_3", block.index, "الفيزياء / Physique", 4, "كارلوس يوسف خشان", "D", 3))
            }
            defaultCourses.add(Course("c_${block.index}_4", block.index, "الكيمياء / Chimie", 3, "دامرس فرج الله خيرالله", "E", 4))
            defaultCourses.add(Course("c_${block.index}_7", block.index, "الكمبيوتر والبرمجة", 4, "شربل اسعد بارود", "H", 7))
            defaultCourses.add(Course("c_${block.index}_8", block.index, "الصحة العامة", 2, "بسام سجعان الحاج", "I", 8))
        }

        val firstNames = listOf(
            "أحمد", "عمر", "يوسف", "علي", "محمود", "إبراهيم", "طارق", "كريم", "خالد", "حسن",
            "مريم", "فاطمة", "نور", "سارة", "آية", "زينب", "هدى", "ليلى", "شهد", "رنا"
        )
        val lastNames = listOf(
            "الغامدي", "القحطاني", "الحسني", "النجار", "العمري", "القرشي", "الزبيدي", "الدوسري",
            "السيد", "الخطيب", "المصري", "الطه", "الحريري", "الباز", "المهدي", "الشريف"
        )

        val students = mutableListOf<Student>()
        classBlocks.forEachIndexed { blockIndex, block ->
            val count = block.endRow - block.startRow + 1
            for (idx in 0 until count) {
                val fn = firstNames[(blockIndex * 3 + idx) % firstNames.size]
                val ln = lastNames[(blockIndex * 2 + idx) % lastNames.size]
                val fullName = "$fn $ln"
                val cleanName = fullName.trim().replace("\\s+".toRegex(), "_")
                val studentId = "s_${block.index}_$cleanName"
                val rowIndex = block.startRow + idx
                val examRowMap = mutableMapOf(
                    ExamType.C1.name to rowIndex,
                    ExamType.C1.shortCode to rowIndex,
                    ExamType.C1.sheetName to rowIndex,
                    ExamType.E1.name to rowIndex + 1,
                    ExamType.E1.shortCode to rowIndex + 1,
                    ExamType.E1.sheetName to rowIndex + 1,
                    ExamType.C2.name to rowIndex + 1,
                    ExamType.C2.shortCode to rowIndex + 1,
                    ExamType.C2.sheetName to rowIndex + 1,
                    ExamType.E2.name to rowIndex + 1,
                    ExamType.E2.shortCode to rowIndex + 1,
                    ExamType.E2.sheetName to rowIndex + 1
                )
                ExamType.values().forEach { et ->
                    val r = if (et == ExamType.C1) rowIndex else rowIndex + 1
                    et.alternativeSheetNames.forEach { alt ->
                        examRowMap[alt] = r
                    }
                }
                students.add(
                    Student(
                        id = studentId,
                        classIndex = block.index,
                        className = block.name,
                        name = fullName,
                        rowIndex = rowIndex,
                        examRowMap = examRowMap
                    )
                )
            }
        }

        // Deadlines in A1:
        // C1 passed (e.g. 2026-08-01)
        // E1 deadline (e.g. 2026-11-15)
        // C2 deadline (e.g. 2027-02-15)
        // E2 deadline (e.g. 2027-05-30)
        val deadlines = SheetDeadlines(
            c1Date = "2026-08-15",
            e1Date = "2026-11-20",
            c2Date = "2027-02-28",
            e2Date = "2027-05-15",
            exam1UploadEnabled = true,
            exam2UploadEnabled = false
        )

        // Grades are initially completely empty per school requirements and user intent.
        // Each exam is separate, and actual grades are only populated when synced from Google Sheets or entered by teachers.
        val initialGrades = emptyMap<String, Double>()

        return InitialSchoolData(
            teachers = teachers,
            courses = defaultCourses,
            students = students,
            deadlines = deadlines,
            grades = initialGrades
        )
    }

    /**
     * Parses the "ساعات التعاقد" sheet.
     * In this sheet:
     * - Month names are placed in cell A1, A51, A101, ... A401 (from October till June)
     * - Days of the month are in columns B to AF (index 1 to 31)
     * - Teacher names are in column A (e.g., rows 3 to 49 for October, rows 53 to 99 for November, etc.)
     */
    fun parseContractHoursSheet(
        rows: List<List<String>>,
        teachers: List<Teacher>
    ): List<ContractHourEntry> {
        return parseContractHoursSheetDetailed(rows, teachers).entries
    }

    /**
     * Parses the full "ساعات التعاقد" sheet, including:
     * 1. Calendar hours entries (Days 1 to 31, Columns B to AF)
     * 2. Total contract hours quotas from Column AG (index 32, rows 3 to 41 for October / first month)
     * 3. Discovered teachers (teachers who only have "تدريب" or only appear in contract hours)
     */
    fun parseContractHoursSheetDetailed(
        rows: List<List<String>>,
        teachers: List<Teacher>
    ): ContractHoursParseResult {
        if (rows.isEmpty()) return ContractHoursParseResult(emptyList(), emptyMap(), emptyList())

        val entries = mutableListOf<ContractHourEntry>()
        val quotas = mutableMapOf<String, TeacherContractQuota>()
        val discoveredTeachers = mutableListOf<Teacher>()
        var currentMonthIndex: Int? = null

        for (row in rows) {
            if (row.isEmpty()) continue
            val col0 = row[0].trim().removeSurrounding("\"").removeSurrounding("'")

            // 1. Check if this row is a month header
            val detectedMonth = getMonthIndexFromContractHeader(col0)
            if (detectedMonth != null) {
                currentMonthIndex = detectedMonth
                continue
            }

            // If no month encountered yet, skip
            val monthIndex = currentMonthIndex ?: continue

            // 2. Check if this row is empty or a non-teacher day header
            val lowerCol0 = col0.lowercase()
            val isDayHeaderRow = (row.getOrNull(1)?.trim() == "1" && row.getOrNull(2)?.trim() == "2")
            if (col0.isBlank() ||
                isDayHeaderRow ||
                lowerCol0 == "day" ||
                lowerCol0 == "days" ||
                lowerCol0 == "name" ||
                lowerCol0 == "teacher" ||
                col0 == "الاسم" ||
                col0 == "اسم الأستاذ" ||
                col0 == "اسم الاستاذ" ||
                col0 == "اسم المعلم" ||
                lowerCol0.all { it.isDigit() } ||
                col0.contains("مجموع") ||
                lowerCol0.contains("total")
            ) {
                continue
            }

            // 3. This is a teacher row!
            val rawTeacherName = col0
            val isTraining = rawTeacherName.contains("تدريب")
            val baseTeacherName = rawTeacherName
                .replace("تدريب", "")
                .replace("(", "")
                .replace(")", "")
                .replace("-", " ")
                .replace("–", " ")
                .trim()

            // Look up teacher in known teachers list using base name, raw name, or direct match
            val matchedTeacher = teachers.firstOrNull { t ->
                isTeacherNameMatch(baseTeacherName, t.name) ||
                isTeacherNameMatch(rawTeacherName, t.name) ||
                (baseTeacherName.isNotBlank() && t.name.trim().equals(baseTeacherName, ignoreCase = true)) ||
                (baseTeacherName.isNotBlank() && normalizeTeacherName(t.name) == normalizeTeacherName(baseTeacherName))
            }
            val teacherCode = matchedTeacher?.code ?: (if (baseTeacherName.isNotBlank()) baseTeacherName else rawTeacherName)

            // Discover teachers who only have "تدريب" or only exist in contract hours
            if (baseTeacherName.isNotBlank() &&
                teachers.none { isTeacherNameMatch(baseTeacherName, it.name) } &&
                discoveredTeachers.none { isTeacherNameMatch(baseTeacherName, it.name) }
            ) {
                discoveredTeachers.add(
                    Teacher(
                        id = "t_contract_${kotlin.math.abs(baseTeacherName.hashCode())}",
                        name = baseTeacherName,
                        code = baseTeacherName,
                        isAdmin = false
                    )
                )
            }

            // 4. Column AG (index 32: A=0, B=1..AF=31, AG=32)
            // Column AG contains total contract hours (e.g. 600 regular, 800 training)
            val agCellVal = if (row.size > 32) row[32].trim().removeSurrounding("\"") else ""
            val agQuota = parseLocalizedDouble(agCellVal)

            if (agQuota != null && agQuota > 0.0) {
                val existing = quotas[baseTeacherName]
                    ?: quotas[teacherCode]
                    ?: quotas[normalizeTeacherName(baseTeacherName)]
                    ?: TeacherContractQuota(teacherName = baseTeacherName, teacherCode = teacherCode)

                val updated = if (isTraining) {
                    existing.copy(trainingHours = agQuota)
                } else {
                    existing.copy(regularHours = agQuota)
                }

                quotas[baseTeacherName] = updated
                if (teacherCode.isNotBlank()) {
                    quotas[teacherCode] = updated
                }
                val norm = normalizeTeacherName(baseTeacherName)
                if (norm.isNotBlank()) {
                    quotas[norm] = updated
                }
            }

            // 5. Columns B to AF correspond to Day 1 to Day 31 (0-indexed col 1 to 31)
            for (day in 1..31) {
                if (day >= row.size) break
                val cellVal = row[day].trim().removeSurrounding("\"")
                if (cellVal.isBlank()) continue

                val hours = parseLocalizedDouble(cellVal)
                if (hours != null && hours > 0.0) {
                    val trainingSuffix = if (isTraining) "_tr" else ""
                    val id = "ch_${monthIndex}_${day}_${rawTeacherName.hashCode()}$trainingSuffix"
                    entries.add(
                        ContractHourEntry(
                            id = id,
                            teacherCode = teacherCode,
                            monthIndex = monthIndex,
                            day = day,
                            hours = hours,
                            notes = if (isTraining) "ساعات تدريب" else "ساعات تعاقد",
                            teacherName = if (isTraining) "$baseTeacherName تدريب" else baseTeacherName,
                            isTraining = isTraining
                        )
                    )
                }
            }
        }

        return ContractHoursParseResult(
            entries = entries,
            quotas = quotas,
            discoveredTeachers = discoveredTeachers
        )
    }

    /**
     * Matches month names from cell A1, A51, A101... (October to June)
     */
    fun getMonthIndexFromContractHeader(cell: String): Int? {
        val clean = cell.trim().lowercase().removeSurrounding("\"").removeSurrounding("'")
        return when {
            clean.startsWith("oct") || clean.contains("تشرين الاول") || clean.contains("تشرين الأول") || clean.contains("تشرين 1") -> 10
            clean.startsWith("nov") || clean.contains("تشرين الثاني") || clean.contains("تشرين 2") -> 11
            clean.startsWith("dec") || clean.contains("كانون الاول") || clean.contains("كانون الأول") || clean.contains("كانون 1") -> 12
            clean.startsWith("jan") || clean.contains("كانون الثاني") || clean.contains("كانون 2") -> 1
            clean.startsWith("feb") || clean.contains("شباط") -> 2
            clean.startsWith("mar") || clean.contains("اذار") || clean.contains("آذار") -> 3
            clean.startsWith("apr") || clean.contains("نيسان") -> 4
            clean.startsWith("may") || clean.contains("ايار") || clean.contains("أيار") -> 5
            clean.startsWith("jun") || clean.contains("حزيران") -> 6
            else -> null
        }
    }

    /**
     * Parses the "كامل ساعات التعاقد" sheet for the admin:
     * - Row 2 (B2 to J2): month numbers (10, 11, 12, 1, 2, 3, 4, 5, 6)
     * - Rows 3 to 49: Teacher names in column A
     * - Columns B to J: Hours done by the teacher for each month
     * - Column K: Original contract hours (ساعات العقد الأصلية e.g. 100)
     * - Column L: Remaining hours (ساعات العقد الأصلية ناقص مجموع ساعات الأشهر من B إلى J)
     */
    fun parseFullContractHoursSheet(
        rows: List<List<String>>
    ): List<FullContractHoursRow> {
        if (rows.size < 2) return emptyList()

        val defaultMonths = listOf(10, 11, 12, 1, 2, 3, 4, 5, 6)
        val monthColMap = mutableMapOf<Int, Int>() // colIndex -> monthNumber

        // Row 2 is at index 1 (or check row 0 or 1 for month headers)
        val headerRowIdx = if (rows.size > 1 && rows[1].any { cell ->
                val num = parseLocalizedDouble(cell)?.toInt()
                num in defaultMonths
            }) 1 else 0

        val headerRow = rows[headerRowIdx]
        for (c in 1 until minOf(headerRow.size, 11)) {
            val cellVal = headerRow[c].trim().removeSurrounding("\"")
            val num = parseLocalizedDouble(cellVal)?.toInt()
            val m = if (num in defaultMonths) num else getMonthIndexFromContractHeader(cellVal)
            if (m != null) {
                monthColMap[c] = m
            }
        }

        // Fill standard column positions if not explicitly mapped
        for (i in defaultMonths.indices) {
            val col = i + 1 // B is 1, C is 2... J is 9
            if (!monthColMap.containsKey(col)) {
                monthColMap[col] = defaultMonths[i]
            }
        }

        val result = mutableListOf<FullContractHoursRow>()
        // Teacher rows start at row 3 (0-indexed row 2) up to row 49 (0-indexed row 48)
        val startRowIdx = headerRowIdx + 1
        val endRowIdx = minOf(rows.size - 1, 48)

        for (r in startRowIdx..endRowIdx) {
            val row = rows[r]
            if (row.isEmpty()) continue
            val teacherName = row.getOrNull(0)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
            if (teacherName.isBlank()) continue
            val lowerName = teacherName.lowercase()
            if (lowerName.contains("total") || lowerName.contains("مجموع") || lowerName.contains("name") || lowerName.contains("الاسم") || lowerName.contains("الأستاذ")) continue

            val monthlyMap = mutableMapOf<Int, Double>()
            var sumDone = 0.0

            defaultMonths.forEach { m ->
                val col = monthColMap.entries.firstOrNull { it.value == m }?.key ?: -1
                val cellVal = if (col >= 0 && col < row.size) row[col] else ""
                val hours = parseLocalizedDouble(cellVal) ?: 0.0
                monthlyMap[m] = hours
                sumDone += hours
            }

            // Column K is 0-indexed 10 (A=0, B=1..J=9, K=10)
            val colKVal = if (row.size > 10) row[10] else ""
            val parsedQuota = parseLocalizedDouble(colKVal)
            val originalQuota = if (parsedQuota != null && parsedQuota > 0.0) parsedQuota else 100.0

            // Column L is 0-indexed 11
            val colLVal = if (row.size > 11) row[11] else ""
            val parsedL = parseLocalizedDouble(colLVal)
            val remainingHours = parsedL ?: (originalQuota - sumDone)

            result.add(
                FullContractHoursRow(
                    rowIndex = r + 1, // Sheet row number (e.g. 3 to 49)
                    teacherName = teacherName,
                    monthlyHours = monthlyMap,
                    totalDone = sumDone,
                    originalQuota = originalQuota,
                    remainingHours = remainingHours
                )
            )
        }

        return result
    }

    suspend fun fetchStudentPortalReport(
        sheetId: String,
        phone: String,
        dob: String,
        webAppUrl: String? = null
    ): Result<StudentPortalReport> = withContext(Dispatchers.IO) {
        val cleanPhone = cleanDigits(phone)
        val cleanDob = dob.trim()

        if (cleanPhone.length < 5) {
            return@withContext Result.failure(Exception("يرجى إدخال رقم الهاتف المكون من 6 أرقام"))
        }

        // 1. If webAppUrl is provided and valid Apps Script Web App, query it first
        val trimmedWebApp = webAppUrl?.trim() ?: ""
        if (trimmedWebApp.isNotBlank() && trimmedWebApp.contains("/exec")) {
            try {
                val encPhone = URLEncoder.encode(cleanPhone, "UTF-8")
                val encDob = URLEncoder.encode(cleanDob, "UTF-8")
                val encSheet = URLEncoder.encode(sheetId.trim(), "UTF-8")
                val queryUrl = "$trimmedWebApp?action=getStudentGrades&phone=$encPhone&dob=$encDob&sheetId=$encSheet"
                val request = Request.Builder().url(queryUrl).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    if (json.optString("status") == "success") {
                        val studentName = json.optString("studentName")
                        val className = json.optString("className")
                        val mobile = json.optString("mobile")
                        val dobStr = json.optString("dateOfBirth")
                        val examsArray = json.optJSONArray("exams") ?: JSONArray()
                        val examList = mutableListOf<StudentExamPortalResult>()
                        for (i in 0 until examsArray.length()) {
                            val eObj = examsArray.getJSONObject(i)
                            val code = eObj.optString("examCode")
                            val examType = ExamType.values().firstOrNull { it.shortCode == code || it.name == code } ?: ExamType.C1
                            val examName = eObj.optString("examName")
                            val deadlineDate = eObj.optString("deadlineDate")
                            val isClosed = eObj.optBoolean("isClosed")
                            val statusMessage = eObj.optString("statusMessage")
                            val coursesArray = eObj.optJSONArray("courses") ?: JSONArray()
                            val courseList = mutableListOf<StudentCoursePortalGrade>()
                            for (c in 0 until coursesArray.length()) {
                                val cObj = coursesArray.getJSONObject(c)
                                courseList.add(
                                    StudentCoursePortalGrade(
                                        courseName = cObj.optString("courseName"),
                                        score = if (cObj.isNull("score")) null else cObj.optDouble("score"),
                                        maxScore = cObj.optDouble("maxScore", 20.0)
                                    )
                                )
                            }
                            examList.add(
                                StudentExamPortalResult(
                                    examType = examType,
                                    examName = examName,
                                    deadlineDate = deadlineDate,
                                    isClosed = isClosed,
                                    courses = courseList,
                                    totalScore = eObj.optDouble("totalScore"),
                                    maxTotalScore = eObj.optDouble("maxTotalScore"),
                                    averageScore = eObj.optDouble("averageScore"),
                                    statusMessage = statusMessage
                                )
                            )
                        }
                        val schoolName = json.optString("schoolName", "").ifBlank { "" }
                        val schoolYear = json.optString("schoolYear", "2025-2026").ifBlank { "2025-2026" }
                        return@withContext Result.success(
                            StudentPortalReport(
                                studentName = studentName,
                                className = className,
                                mobile = mobile,
                                dateOfBirth = dobStr,
                                exams = examList,
                                schoolName = schoolName,
                                schoolYear = schoolYear
                            )
                        )
                    } else {
                        val msg = json.optString("message", "لم يتم العثور على الطالب")
                        return@withContext Result.failure(Exception(msg))
                    }
                }
            } catch (_: Exception) {
                // Fall back to direct sheet reading
            }
        }

        // 2. Direct Google Sheets CSV reader
        try {
            val candidateTabs = listOf("الإعدادات (التلاميذ)", "الاعدادات (التلاميذ)", "الإعدادات(التلاميذ)", "الطلاب", "التلاميذ")
            var settingsRows: List<List<String>>? = null
            for (cand in candidateTabs) {
                val res = fetchSheetCsv(sheetId, cand)
                if (res.isSuccess && res.getOrThrow().isNotEmpty()) {
                    settingsRows = res.getOrThrow()
                    break
                }
            }
            if (settingsRows == null) {
                return@withContext Result.failure(Exception("تعذر تحميل تبويب 'الإعدادات (التلاميذ)' من جدول Google Sheets."))
            }

            var matchedStudentName: String? = null
            var matchedClassName = ""
            var matchedMobile = ""
            var matchedDob = ""

            var currentClass = ""
            for (r in settingsRows) {
                val colB = if (r.size > 1) r[1].trim().removeSurrounding("\"") else ""
                val colC = if (r.size > 2) cleanDigits(r[2]) else ""
                val colD = if (r.size > 3) r[3].trim().removeSurrounding("\"") else ""

                if (colB.isNotBlank() && colC.isBlank() && (colD.isBlank() || !colD.any { it.isDigit() })) {
                    currentClass = colB
                }

                if (colB.isNotBlank() && colC.isNotBlank()) {
                    if (colC == cleanPhone && isDobMatchKotlin(colD, cleanDob)) {
                        matchedStudentName = colB
                        matchedClassName = currentClass
                        matchedMobile = colC
                        matchedDob = colD
                        break
                    }
                }
            }

            if (matchedStudentName == null) {
                return@withContext Result.failure(
                    Exception("لم يتم العثور على طالب يطابق رقم الهاتف ($cleanPhone) وتاريخ الميلاد المدخل.")
                )
            }

            val examTypes = listOf(ExamType.C1, ExamType.E1, ExamType.C2, ExamType.E2)
            val examResults = mutableListOf<StudentExamPortalResult>()

            // Check C1 deadline: if C1 hasn't passed, no report is issued yet
            val c1CsvRes = fetchExamSheetCsv(sheetId, ExamType.C1)
            if (c1CsvRes.isSuccess && c1CsvRes.getOrThrow().second.isNotEmpty()) {
                val c1CellA1 = c1CsvRes.getOrThrow().second[0].firstOrNull()?.trim()?.removeSurrounding("\"") ?: ""
                if (c1CellA1.isNotBlank() && !isDeadlinePassed(c1CellA1)) {
                    return@withContext Result.failure(Exception("لم يصدر اي تقرير حتى الان"))
                }
            }

            for (examType in examTypes) {
                val csvRes = fetchExamSheetCsv(sheetId, examType)
                if (csvRes.isFailure) continue
                val (_, rows) = csvRes.getOrThrow()
                if (rows.isEmpty()) continue

                val cellA1 = if (rows[0].isNotEmpty()) rows[0][0].trim().removeSurrounding("\"") else ""
                val isClosed = isDeadlinePassed(cellA1)

                if (!isClosed) {
                    examResults.add(
                        StudentExamPortalResult(
                            examType = examType,
                            examName = examType.sheetName,
                            deadlineDate = cellA1,
                            isClosed = false,
                            statusMessage = "الامتحان غير مقفل بعد. النتائج ستكون متاحة فور انتهاء موعد الامتحان: $cellA1"
                        )
                    )
                } else {
                    val normTargetName = normalizeTeacherName(matchedStudentName)
                    var sRowIdx = -1
                    for (rIdx in rows.indices) {
                        val row = rows[rIdx]
                        if (row.size > 1 && normalizeTeacherName(row[1]) == normTargetName) {
                            sRowIdx = rIdx
                            break
                        }
                    }

                    val courses = mutableListOf<StudentCoursePortalGrade>()
                    var total = 0.0
                    var maxTotal = 0.0

                    if (sRowIdx != -1) {
                        val block = sRowIdx / 24
                        val headerRowIdx = block * 24 + 2
                        val maxRowIdx = block * 24
                        val headerRow = if (headerRowIdx < rows.size) rows[headerRowIdx] else emptyList()
                        val maxRow = if (maxRowIdx < rows.size) rows[maxRowIdx] else emptyList()
                        val studentRow = rows[sRowIdx]

                        for (col in 2 until minOf(studentRow.size, 27)) {
                            val cName = if (col < headerRow.size) headerRow[col].trim().removeSurrounding("\"") else ""
                            if (cName.isNotBlank() && cName != "المجموع" && cName != "المعدل" && cName != "النتيجة") {
                                val score = if (col < studentRow.size) parseLocalizedDouble(studentRow[col]) else null
                                val maxScore = if (col < maxRow.size) parseLocalizedDouble(maxRow[col]) ?: 20.0 else 20.0
                                if (score != null) {
                                    total += score
                                }
                                maxTotal += maxScore
                                courses.add(
                                    StudentCoursePortalGrade(
                                        courseName = cName,
                                        score = score,
                                        maxScore = maxScore
                                    )
                                )
                            }
                        }
                    }

                    val avg = if (maxTotal > 0) Math.round((total / maxTotal) * 2000.0) / 100.0 else 0.0
                    examResults.add(
                        StudentExamPortalResult(
                            examType = examType,
                            examName = examType.sheetName,
                            deadlineDate = cellA1,
                            isClosed = true,
                            courses = courses,
                            totalScore = Math.round(total * 100.0) / 100.0,
                            maxTotalScore = Math.round(maxTotal * 100.0) / 100.0,
                            averageScore = avg,
                            statusMessage = "الامتحان مقفل - النتائج معلنة رسمياً"
                        )
                    )
                }
            }

            var directSchoolName = ""
            var directSchoolYear = "2025-2026"
            try {
                val candidateSettingsTabs = listOf("Setting", "setting", "Settings", "settings", "الإعدادات", "الاعدادات", "الضبط")
                for (cand in candidateSettingsTabs) {
                    val sRes = fetchSheetCsv(sheetId, cand)
                    if (sRes.isSuccess && !sRes.getOrNull().isNullOrEmpty()) {
                        val sRows = sRes.getOrThrow()
                        val sInfo = parseSchoolSettingsInfo(sRows)
                        if (sInfo.schoolName.isNotBlank()) {
                            directSchoolName = sInfo.schoolName
                        }
                        if (sInfo.schoolYear.isNotBlank()) {
                            directSchoolYear = sInfo.schoolYear
                        }
                        break
                    }
                }
            } catch (_: Exception) {}

            Result.success(
                StudentPortalReport(
                    studentName = matchedStudentName,
                    className = matchedClassName,
                    mobile = matchedMobile,
                    dateOfBirth = matchedDob,
                    exams = examResults,
                    schoolName = directSchoolName,
                    schoolYear = directSchoolYear
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanDigits(str: String): String {
        val arabicIndic = "٠١٢٣٤٥٦٧٨٩"
        val sb = StringBuilder()
        for (ch in str) {
            val idx = arabicIndic.indexOf(ch)
            if (idx != -1) {
                sb.append(idx)
            } else if (ch.isDigit()) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun isDobMatchKotlin(sheetDob: String, inputDob: String): Boolean {
        val s = cleanDigits(sheetDob)
        val i = cleanDigits(inputDob)
        if (s == i && s.isNotBlank()) return true

        // Parse day, month, year as integers so 04 is identical to 4
        val sParts = sheetDob.split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }
        val iParts = inputDob.split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }

        if (sParts.size >= 3 && iParts.size >= 3) {
            // Numeric sorted comparison: 04 and 4 both become integer 4!
            if (sParts.take(3).sorted() == iParts.take(3).sorted()) return true
        }

        // Handle contiguous 8 digits input like 12042002
        if (i.length == 8 && sParts.size >= 3) {
            val d1 = i.substring(0, 2).toIntOrNull()
            val m1 = i.substring(2, 4).toIntOrNull()
            val y1 = i.substring(4).toIntOrNull()
            if (d1 != null && m1 != null && y1 != null) {
                if (listOf(d1, m1, y1).sorted() == sParts.take(3).sorted()) return true
            }
        }
        if (s.length == 8 && iParts.size >= 3) {
            val d1 = s.substring(0, 2).toIntOrNull()
            val m1 = s.substring(2, 4).toIntOrNull()
            val y1 = s.substring(4).toIntOrNull()
            if (d1 != null && m1 != null && y1 != null) {
                if (listOf(d1, m1, y1).sorted() == iParts.take(3).sorted()) return true
            }
        }

        return sheetDob.isNotBlank() && inputDob.isNotBlank() &&
                (sheetDob.contains(inputDob) || inputDob.contains(sheetDob))
    }

    private fun isDeadlinePassed(deadlineDateStr: String): Boolean {
        val trimmed = deadlineDateStr.trim().removeSurrounding("\"")
        if (trimmed.isBlank()) return false
        val parts = trimmed.split(Regex("[-/.]"))
        if (parts.size >= 3) {
            val p0 = cleanDigits(parts[0]).toIntOrNull() ?: return false
            val p1 = cleanDigits(parts[1]).toIntOrNull() ?: return false
            val p2 = cleanDigits(parts[2]).toIntOrNull() ?: return false
            val y = if (p0 > 1000) p0 else if (p2 < 100) (p2 + 2000) else p2
            val m = if (p0 > 1000) p1 else p1
            val d = if (p0 > 1000) p2 else p0
            val cal = java.util.Calendar.getInstance().apply {
                set(y, m - 1, d, 23, 59, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }
            return java.util.Date().after(cal.time)
        }
        return false
    }
}

data class InitialSchoolData(
    val teachers: List<Teacher>,
    val courses: List<Course>,
    val students: List<Student>,
    val deadlines: SheetDeadlines,
    val grades: Map<String, Double>
)
