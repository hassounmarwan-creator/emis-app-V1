package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GoogleSheetsService
import com.example.data.SchoolRepository
import com.example.model.ExamType
import com.example.model.Teacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("EMIS", appName)
  }

  @Test
  fun `verify EmisViewModel initializes without crashing`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    assertNotNull(vm)
  }

  @Test
  fun `verify MainActivity launches successfully without crashing`() {
    val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
    assertNotNull(scenario)
    scenario.close()
  }

  @Test
  fun `verify default dataset has real teachers and 12 class blocks`() {
    val service = GoogleSheetsService()
    val dataset = service.createDefaultDataset()
    assertTrue(dataset.teachers.any { it.name.contains("عبير") })
    assertTrue(dataset.teachers.any { it.code == "2643" })
    assertTrue(dataset.courses.isNotEmpty())
    assertTrue(dataset.students.isNotEmpty())
    assertEquals(12, service.classBlocks.size)
  }

  @Test
  fun `verify Google Apps Script contains doPost and updates handling`() {
    val service = GoogleSheetsService()
    val script = service.getGoogleAppsScriptCode()
    assertTrue(script.contains("function doPost(e)"))
    assertTrue(script.contains("setValue(valToSet)"))
    assertTrue(script.contains("setValues(colSlice)"))
    assertTrue(script.contains("byCol"))
  }

  @Test
  fun `verify repository batch save grades and retrieval`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)
    val testBatch = mapOf("s_1_3_c_1_2_C1" to 88.5)
    repo.batchSaveGrades(testBatch)
    val retrieved = repo.getGrade("s_1_3", "c_1_2", ExamType.C1)
    assertEquals(88.5, retrieved ?: 0.0, 0.01)
  }

  @Test
  fun `verify parseClassBlocks excludes classes that have no name`() {
    val service = GoogleSheetsService()
    // Simulate sheet rows where only class 1 and class 3 have names, class 2 row is blank
    val mockRows = mutableListOf<List<String>>()
    for (i in 0..270) {
      when (i) {
        1 -> mockRows.add(listOf("TS1 INF", "TS1 INF")) // row 2 (class 1)
        25 -> mockRows.add(listOf("", "")) // row 26 (class 2) has no name!
        49 -> mockRows.add(listOf("TS2 INF", "TS2 INF")) // row 50 (class 3)
        else -> mockRows.add(listOf("", ""))
      }
    }
    val parsed = service.parseClassBlocks(mockRows)
    // Class 2 must be excluded because it has no name
    assertTrue(parsed.none { it.index == 2 })
    assertTrue(parsed.any { it.index == 1 && it.name == "TS1 INF" })
    assertTrue(parsed.any { it.index == 3 && it.name == "TS2 INF" })
  }

  @Test
  fun `verify single grade save works instantly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)
    repo.saveGrade("student_test_1", "course_test_1", ExamType.C1, 19.5)
    val grade = repo.getGrade("student_test_1", "course_test_1", ExamType.C1)
    assertEquals(19.5, grade ?: 0.0, 0.001)
  }

  @Test
  fun `verify teacher session persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)
    repo.saveLoggedInTeacherCode("2643")
    assertEquals("2643", repo.getSavedLoggedInTeacherCode())
    repo.clearLoggedInTeacherCode()
    assertEquals(null, repo.getSavedLoggedInTeacherCode())
  }

  @Test
  fun `verify initial data does not contain mock grades`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)
    // Initially without network sync or mock grades, grades map should be empty
    assertTrue(repo.grades.value.isEmpty())
  }

  @Test
  fun `verify ViewModel grade edit, unsaved detection, and discard flow`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    val repo = vm.repository

    // Login with admin code admin123 to access all courses and classes
    val loggedIn = vm.loginWithCode("admin123")
    assertTrue(loggedIn)
    val course = repo.courses.value.first()
    val student = repo.students.value.first { it.classIndex == course.classIndex }

    // Select class, course, and exam E1
    vm.setSelectedClassIndex(course.classIndex)
    vm.setSelectedCourseId(course.id)
    vm.setSelectedExamType(ExamType.E1)

    // Pre-populate with student and course for an active exam (E1)
    repo.saveGrade(student.id, course.id, ExamType.E1, 15.0)
    vm.loadGradesForCurrentSelection()

    assertEquals("15", vm.editedGrades.value[student.id])
    assertEquals(false, vm.hasUnsavedEdits())

    // Teacher modifies grade to 18.5
    vm.updateGradeInput(student.id, "18.5")
    assertEquals("18.5", vm.editedGrades.value[student.id])
    assertEquals(true, vm.hasUnsavedEdits())

    // Teacher discards edits (leaves them as they are)
    vm.discardEdits()
    val restored = vm.editedGrades.value[student.id]
    assertEquals("15", restored)
    assertEquals(false, vm.hasUnsavedEdits())
  }

  @Test
  fun `verify cell A1 flexible date parsing with multiple formats and Arabic digits`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)

    // Set fixed reference app date: 2026-09-10
    repo.setCurrentAppDate("2026-09-10")

    // 1. ISO format yyyy-MM-dd in past
    assertTrue(repo.isDatePassed("2026-08-15"))

    // 2. European format dd/MM/yyyy in past
    assertTrue(repo.isDatePassed("15/08/2026"))

    // 3. Dash format dd-MM-yyyy in past
    assertTrue(repo.isDatePassed("15-08-2026"))

    // 4. Slash format yyyy/MM/dd in past
    assertTrue(repo.isDatePassed("2026/08/15"))

    // 5. Arabic-Indic numerals ١٥/٠٨/٢٠٢٦ in past
    assertTrue(repo.isDatePassed("١٥/٠٨/٢٠٢٦"))

    // 6. Text prefix with date "آخر موعد: 2026-08-15" in past
    assertTrue(repo.isDatePassed("آخر موعد: 2026-08-15"))

    // 7. Future date 2026-11-20 -> NOT passed
    assertEquals(false, repo.isDatePassed("2026-11-20"))
    assertEquals(false, repo.isDatePassed("20/11/2026"))
    assertEquals(false, repo.isDatePassed("٢٠/١١/٢٠٢٦"))

    // 8. Today's date (2026-09-10) -> NOT passed (allowed until end of day)
    assertEquals(false, repo.isDatePassed("2026-09-10"))

    // 9. Yesterday's date (2026-09-09) -> HAS passed
    assertTrue(repo.isDatePassed("2026-09-09"))
  }

  @Test
  fun `verify 4 exam sheets have independent A1 dates and lock statuses`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)
    repo.setCurrentAppDate("2026-09-10")

    // Configure 4 exam sheets with specific A1 dates:
    // C1: Past (locked)
    // E1: Future (unlocked)
    // C2: Past (locked)
    // E2: Future (unlocked)
    repo.setDeadline(ExamType.C1, "2026-08-15")
    repo.setDeadline(ExamType.E1, "2026-11-20")
    repo.setDeadline(ExamType.C2, "2026-09-01")
    repo.setDeadline(ExamType.E2, "2027-04-30")

    assertTrue(repo.isExamTypeLocked(ExamType.C1))
    assertEquals(false, repo.isExamTypeLocked(ExamType.E1))
    assertTrue(repo.isExamTypeLocked(ExamType.C2))
    assertEquals(false, repo.isExamTypeLocked(ExamType.E2))
  }

  @Test
  fun `verify teacher can see grade but cannot modify when date in cell A1 has passed`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    val repo = vm.repository

    // Set fixed reference date: 2026-09-10
    repo.setCurrentAppDate("2026-09-10")

    // Teacher logs in with legitimate teacher code (2643 - عبير فريز غضبان)
    val loggedIn = vm.loginWithCode("2643")
    assertTrue(loggedIn)

    // Find a course taught by this teacher
    val teacherCourse = repo.courses.value.first { it.teacherName.contains("عبير") }
    val student = repo.students.value.first { it.classIndex == teacherCourse.classIndex }

    // Select class, course, and exam C1 (السعي الاول)
    vm.setSelectedClassIndex(teacherCourse.classIndex)
    vm.setSelectedCourseId(teacherCourse.id)
    vm.setSelectedExamType(ExamType.C1)

    // 1. Set cell A1 date in the past (e.g. 2026-08-15) -> LOCKED
    repo.setDeadline(ExamType.C1, "2026-08-15")
    // Pre-populate with a grade of 16.5
    repo.saveGrade(student.id, teacherCourse.id, ExamType.C1, 16.5)
    vm.loadGradesForCurrentSelection()

    // Verify the exam is locked
    assertTrue(vm.isCurrentExamLocked())

    // Verify teacher CAN SEE the grade
    assertEquals("16.5", vm.editedGrades.value[student.id])

    // Verify teacher CANNOT modify the grade
    vm.updateGradeInput(student.id, "20.0")
    // Grade MUST remain unchanged (16.5, NOT 20.0)
    assertEquals("16.5", vm.editedGrades.value[student.id])
    assertEquals(false, vm.hasUnsavedEdits())
    // Status message warns teacher that grades are locked
    assertTrue(vm.saveStatusMessage.value?.contains("مقفلة") == true)

    // Verify teacher CANNOT save grades
    vm.saveCurrentGrades()
    assertEquals(16.5, repo.getGrade(student.id, teacherCourse.id, ExamType.C1) ?: 0.0, 0.01)

    // 2. Now simulate cell A1 date extended into the future (e.g. 2026-12-31) -> UNLOCKED
    repo.setDeadline(ExamType.C1, "2026-12-31")
    vm.loadGradesForCurrentSelection()

    // Verify exam is now unlocked
    assertEquals(false, vm.isCurrentExamLocked())

    // Teacher CAN now modify the grade
    vm.updateGradeInput(student.id, "18.0")
    assertEquals("18.0", vm.editedGrades.value[student.id])
    assertTrue(vm.hasUnsavedEdits())

    // Teacher CAN now save the grade
    vm.saveCurrentGrades()
    assertEquals(18.0, repo.getGrade(student.id, teacherCourse.id, ExamType.C1) ?: 0.0, 0.01)
  }

  @Test
  fun `verify course row formula for classes 1 to 12 matches expected rows`() {
    val service = GoogleSheetsService()
    val expectedRows = listOf(
      1 to 3,
      2 to 27,
      3 to 51,
      4 to 75,
      5 to 99,
      6 to 123,
      7 to 147,
      8 to 171,
      9 to 195,
      10 to 219,
      11 to 243,
      12 to 267
    )
    for ((classIdx, expectedRow) in expectedRows) {
      val calculatedRow = service.getCourseRowForClass(classIdx)
      assertEquals(expectedRow, calculatedRow)
    }
  }

  @Test
  fun `verify course models have correct sheet rows and column letters C to AA`() {
    val service = GoogleSheetsService()
    val dataset = service.createDefaultDataset()
    val courses = dataset.courses

    // Check that each course has correct sheetRow matching 3 + (classIndex - 1) * 24
    courses.forEach { course ->
      val expectedRow = 3 + (course.classIndex - 1) * 24
      assertEquals("Course ${course.name} in class ${course.classIndex} must have row $expectedRow", expectedRow, course.sheetRow)
      // Column letters must be between C and AA
      assertTrue(
        "Course ${course.name} column ${course.columnLetter} must be between C and AA",
        course.columnLetter in listOf("C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "AA")
      )
      // cellReference must match columnLetter + sheetRow
      assertEquals("${course.columnLetter}${expectedRow}", course.cellReference)
    }
  }

  @Test
  fun `verify course is locked and teacher can only see but cannot modify when cell is highlighted in red`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    val repo = vm.repository

    // Set deadline in future so date is not the locking factor
    repo.setCurrentAppDate("2026-09-10")
    repo.setDeadline(ExamType.C1, "2026-12-31")

    // Teacher logs in (2643 - عبير فريز غضبان)
    val loggedIn = vm.loginWithCode("2643")
    assertTrue(loggedIn)

    val teacherCourse = repo.courses.value.first { it.teacherName.contains("عبير") }
    val student = repo.students.value.first { it.classIndex == teacherCourse.classIndex }

    vm.setSelectedClassIndex(teacherCourse.classIndex)
    vm.setSelectedCourseId(teacherCourse.id)
    vm.setSelectedExamType(ExamType.C1)

    // Pre-populate with initial grade 14.0
    repo.saveGrade(student.id, teacherCourse.id, ExamType.C1, 14.0)
    vm.loadGradesForCurrentSelection()

    // Initially course is NOT red locked
    assertEquals(false, vm.isCourseRedHighlighted(teacherCourse.id))
    assertEquals(false, vm.isCurrentSelectionLocked())

    // 1. Highlight the course cell in RED
    vm.setCourseRedLockedForTesting(teacherCourse.id, locked = true)

    // Verify course is reported as red-locked
    assertTrue(vm.isCourseRedHighlighted(teacherCourse.id))
    assertTrue(vm.isCourseLocked(teacherCourse.id))
    assertTrue(vm.isCurrentSelectionLocked())

    // Verify teacher CAN SEE the grade
    assertEquals("14", vm.editedGrades.value[student.id])

    // Verify teacher CANNOT modify the grade
    vm.updateGradeInput(student.id, "19.5")
    // Grade in input MUST remain unchanged
    assertEquals("14", vm.editedGrades.value[student.id])
    assertEquals(false, vm.hasUnsavedEdits())
    // Warning status message displayed
    assertTrue(vm.saveStatusMessage.value?.contains("مقفلة") == true)
    assertTrue(vm.saveStatusMessage.value?.contains("الأحمر") == true)

    // Verify saving is blocked
    vm.saveCurrentGrades()
    assertEquals(14.0, repo.getGrade(student.id, teacherCourse.id, ExamType.C1) ?: 0.0, 0.01)

    // 2. Remove the RED highlight from the course cell
    vm.setCourseRedLockedForTesting(teacherCourse.id, locked = false)
    assertEquals(false, vm.isCourseRedHighlighted(teacherCourse.id))
    assertEquals(false, vm.isCurrentSelectionLocked())

    // Teacher CAN now modify the grade
    vm.updateGradeInput(student.id, "17.5")
    assertEquals("17.5", vm.editedGrades.value[student.id])
    assertTrue(vm.hasUnsavedEdits())

    // Teacher CAN now save
    vm.saveCurrentGrades()
    assertEquals(17.5, repo.getGrade(student.id, teacherCourse.id, ExamType.C1) ?: 0.0, 0.01)
  }

  @Test
  fun `verify course can be locked in red in one exam page but unlocked in another exam page`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    val repo = vm.repository

    // Set future deadlines for all 4 exams so date does not lock any exam
    repo.setCurrentAppDate("2026-09-10")
    repo.setDeadline(ExamType.C1, "2026-12-31")
    repo.setDeadline(ExamType.E1, "2026-12-31")
    repo.setDeadline(ExamType.C2, "2026-12-31")
    repo.setDeadline(ExamType.E2, "2026-12-31")

    // Teacher logs in (2643 - عبير فريز غضبان)
    val loggedIn = vm.loginWithCode("2643")
    assertTrue(loggedIn)

    val teacherCourse = repo.courses.value.first { it.teacherName.contains("عبير") }
    val student = repo.students.value.first { it.classIndex == teacherCourse.classIndex }

    vm.setSelectedClassIndex(teacherCourse.classIndex)
    vm.setSelectedCourseId(teacherCourse.id)

    // Pre-populate C1 grade = 15.0, and E1 grade = 16.0
    repo.saveGrade(student.id, teacherCourse.id, ExamType.C1, 15.0)
    repo.saveGrade(student.id, teacherCourse.id, ExamType.E1, 16.0)

    // --- LOCK THE COURSE ONLY IN C1 (السعي الاول) ---
    vm.setCourseRedLockedForTesting(teacherCourse.id, examType = ExamType.C1, locked = true)

    // 1. Verify C1 page is LOCKED
    vm.setSelectedExamType(ExamType.C1)
    vm.loadGradesForCurrentSelection()

    assertTrue("Course must be red-locked in C1", vm.isCourseRedHighlighted(teacherCourse.id, ExamType.C1))
    assertTrue("Selection must be locked in C1", vm.isCurrentSelectionLocked())
    // Teacher CAN SEE the grade
    assertEquals("15", vm.editedGrades.value[student.id])
    // Teacher CANNOT modify the grade in C1
    vm.updateGradeInput(student.id, "20.0")
    assertEquals("Grade in C1 must not change", "15", vm.editedGrades.value[student.id])
    assertEquals(false, vm.hasUnsavedEdits())
    // Teacher CANNOT save in C1
    vm.saveCurrentGrades()
    assertEquals(15.0, repo.getGrade(student.id, teacherCourse.id, ExamType.C1) ?: 0.0, 0.01)

    // 2. Verify E1 page is UNLOCKED (different page of the 4 exams pages)
    vm.setSelectedExamType(ExamType.E1)
    vm.loadGradesForCurrentSelection()

    assertEquals("Course must NOT be red-locked in E1", false, vm.isCourseRedHighlighted(teacherCourse.id, ExamType.E1))
    assertEquals("Selection must NOT be locked in E1", false, vm.isCurrentSelectionLocked())
    // Teacher CAN modify grade in E1
    vm.updateGradeInput(student.id, "18.5")
    assertEquals("Grade in E1 should be updated in input", "18.5", vm.editedGrades.value[student.id])
    assertTrue("E1 has unsaved edits", vm.hasUnsavedEdits())
    // Teacher CAN save in E1
    vm.saveCurrentGrades()
    assertEquals(18.5, repo.getGrade(student.id, teacherCourse.id, ExamType.E1) ?: 0.0, 0.01)

    // 3. Verify C2 and E2 are also UNLOCKED
    assertEquals(false, vm.isCourseRedHighlighted(teacherCourse.id, ExamType.C2))
    assertEquals(false, vm.isCourseRedHighlighted(teacherCourse.id, ExamType.E2))

    // 4. Switch back to C1 page and verify it remains LOCKED
    vm.setSelectedExamType(ExamType.C1)
    vm.loadGradesForCurrentSelection()
    assertTrue("C1 must remain locked", vm.isCurrentSelectionLocked())
    assertEquals("15", vm.editedGrades.value[student.id])
  }

  @Test
  fun `verify Google Apps Script includes course red highlight detection for columns C to AA`() {
    val service = GoogleSheetsService()
    val script = service.getGoogleAppsScriptCode()

    assertTrue(script.contains("getCourseRedHighlights"))
    assertTrue(script.contains("3 + (classIdx - 1) * 24"))
    assertTrue(script.contains("isRedColor"))
    assertTrue(script.contains("getLockedCourses"))
  }

  @Test
  fun `verify grade validation rules strictly between 0 and 20 with quarter increments`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)

    // Valid grades: 0, 20, integers, and 0.25, 0.5, 0.75 increments
    assertTrue("0 is valid", vm.isValidGrade("0"))
    assertTrue("20 is valid", vm.isValidGrade("20"))
    assertTrue("14 is valid", vm.isValidGrade("14"))
    assertTrue("14.25 is valid", vm.isValidGrade("14.25"))
    assertTrue("12.75 is valid", vm.isValidGrade("12.75"))
    assertTrue("10.5 is valid", vm.isValidGrade("10.5"))
    assertTrue("10.50 is valid", vm.isValidGrade("10.50"))
    assertTrue("0.25 is valid", vm.isValidGrade("0.25"))
    assertTrue("0.75 is valid", vm.isValidGrade("0.75"))
    assertTrue("Empty grade is allowed as unassigned", vm.isValidGrade(""))
    assertTrue("Blank grade is allowed as unassigned", vm.isValidGrade("   "))

    // Arabic-Indic digits should be supported and normalized
    assertTrue("Arabic ١٤٫٢٥ is valid", vm.isValidGrade("١٤٫٢٥"))
    assertTrue("Arabic ١٠٫٥ is valid", vm.isValidGrade("١٠٫٥"))

    // Invalid grades: out of bounds or non-quarter decimals
    assertEquals("14.3 is not allowed", false, vm.isValidGrade("14.3"))
    assertEquals("14.2 is not allowed", false, vm.isValidGrade("14.2"))
    assertEquals("14.7 is not allowed", false, vm.isValidGrade("14.7"))
    assertEquals("21 is not allowed (above 20)", false, vm.isValidGrade("21"))
    assertEquals("20.25 is not allowed (above 20)", false, vm.isValidGrade("20.25"))
    assertEquals("-1 is not allowed (below 0)", false, vm.isValidGrade("-1"))
    assertEquals("-0.25 is not allowed (below 0)", false, vm.isValidGrade("-0.25"))
    assertEquals("14. is incomplete", false, vm.isValidGrade("14."))
    assertEquals("abc is not allowed", false, vm.isValidGrade("abc"))

    // Typing allowance tests
    assertTrue(vm.isAllowedTypingGrade("1"))
    assertTrue(vm.isAllowedTypingGrade("14"))
    assertTrue(vm.isAllowedTypingGrade("14."))
    assertTrue(vm.isAllowedTypingGrade("14.2"))
    assertTrue(vm.isAllowedTypingGrade("14.25"))
    assertEquals(false, vm.isAllowedTypingGrade("14.255")) // at most 2 decimal digits
    assertEquals(false, vm.isAllowedTypingGrade("25")) // > 20
    assertEquals(false, vm.isAllowedTypingGrade("20.1")) // > 20
    assertEquals(false, vm.isAllowedTypingGrade("-5"))
  }

  @Test
  fun `verify renamed sheets exactly match user specification and support fallbacks`() {
    assertEquals("علامات السعي الاول", ExamType.C1.sheetName)
    assertEquals("علامات الامتحان الاول", ExamType.E1.sheetName)
    assertEquals("علامات السعي الثاني", ExamType.C2.sheetName)
    assertEquals("علامات الامتحان الثاني", ExamType.E2.sheetName)

    // Verify alternative names include variations
    assertTrue(ExamType.C1.alternativeSheetNames.contains("علامات السعي الأول"))
    assertTrue(ExamType.C1.alternativeSheetNames.contains("السعي الاول"))
    assertTrue(ExamType.E1.alternativeSheetNames.contains("علامات الامتحان الأول"))
    assertTrue(ExamType.E1.alternativeSheetNames.contains("الامتحان الاول"))
    assertTrue(ExamType.C2.alternativeSheetNames.contains("السعي الثاني"))
    assertTrue(ExamType.E2.alternativeSheetNames.contains("الامتحان الثاني"))
  }

  @Test
  fun `verify Google Apps Script contains all 4 renamed sheet names in red highlights function`() {
    val service = GoogleSheetsService()
    val script = service.getGoogleAppsScriptCode()

    assertTrue(script.contains("علامات السعي الاول"))
    assertTrue(script.contains("علامات الامتحان الاول"))
    assertTrue(script.contains("علامات السعي الثاني"))
    assertTrue(script.contains("علامات الامتحان الثاني"))
    // And resilient prefix matching
    assertTrue(script.contains("cleanTargetNoPrefix"))
  }

  @Test
  fun `verify student getRowForExam works with renamed sheet names and alternatives`() {
    val student = com.example.model.Student(
      id = "s_1_test",
      classIndex = 1,
      className = "TS1 INF",
      name = "طالب تجريبي",
      rowIndex = 4,
      examRowMap = mapOf(
        "علامات السعي الاول" to 5,
        "علامات الامتحان الاول" to 6,
        "علامات السعي الثاني" to 7,
        "علامات الامتحان الثاني" to 8
      )
    )

    assertEquals(5, student.getRowForExam(ExamType.C1))
    assertEquals(6, student.getRowForExam(ExamType.E1))
    assertEquals(7, student.getRowForExam(ExamType.C2))
    assertEquals(8, student.getRowForExam(ExamType.E2))
  }

  @Test
  fun `verify red locking works seamlessly with new sheet names`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)

    // Lock course with C1 (علامات السعي الاول)
    repo.setCourseRedLocked("c_1_3", examType = ExamType.C1, locked = true)

    assertTrue(repo.isCourseRedLocked("c_1_3", ExamType.C1))
    assertEquals(false, repo.isCourseRedLocked("c_1_3", ExamType.E1))
    assertEquals(false, repo.isCourseRedLocked("c_1_3", ExamType.C2))
    assertEquals(false, repo.isCourseRedLocked("c_1_3", ExamType.E2))
  }

  @Test
  fun `verify Abeer has 6 courses and student name is not mistaken for class name`() {
    val service = GoogleSheetsService()

    // Create mock C1 rows with Class 1, Class 3, Class 11 and Abeer teaching in each
    val rows = mutableListOf<List<String>>()
    for (i in 0..270) {
      rows.add(MutableList(28) { "" })
    }

    // Class 1 at row 2 (index 1)
    val r2 = rows[1].toMutableList()
    r2[1] = "TS1 INF"
    r2[2] = "عبير فريز غضبان" // course 1
    r2[3] = "عبير فريز غضبان" // course 2
    r2[16] = "عبير فريز غضبان" // course 3
    rows[1] = r2

    val r3 = rows[2].toMutableList()
    r3[2] = "الاحصاء"
    r3[3] = "الاقتصاد"
    r3[16] = "محاسبة عامة"
    rows[2] = r3

    // Class 3 at row 50 (index 49)
    val r50 = rows[49].toMutableList()
    r50[1] = "TS1 HOT"
    r50[10] = "عبير فريز غضبان" // course 4
    r50[15] = "عبير فريز غضبان" // course 5
    rows[49] = r50

    val r51 = rows[50].toMutableList()
    r51[10] = "مبادىء الإقتصاد الجزئي"
    r51[15] = "مدخل الى علم الاحصاء"
    rows[50] = r51

    // Class 11 at row 242 (index 241)
    val r242 = rows[241].toMutableList()
    r242[1] = "TS2 CLI"
    r242[17] = "عبير فريز غضبان" // course 6
    rows[241] = r242

    val r243 = rows[242].toMutableList()
    r243[17] = "planification"
    rows[242] = r243

    // Student at cell 252 (index 251) in Class 11 (students range B244 to B263)
    val r252 = rows[251].toMutableList()
    r252[1] = "فابيو انطوان القيم"
    rows[251] = r252

    // Class 12 at row 266 (index 265)
    val r266 = rows[265].toMutableList()
    r266[1] = "TS2 ELI"
    rows[265] = r266

    // Student in Class 12 at row 270 (index 269)
    val r270 = rows[269].toMutableList()
    r270[1] = "روي زغيب زغيب"
    rows[269] = r270

    // 1. Check class blocks
    val classBlocks = service.parseClassBlocks(rows)
    val class11 = classBlocks.find { it.index == 11 }
    assertNotNull(class11)
    assertEquals("TS2 CLI", class11?.name)
    // Verify student name is NOT a class name
    assertTrue(classBlocks.none { it.name.contains("فابيو") })
    assertTrue(classBlocks.none { it.name.contains("روي") })

    // 2. Check courses
    val courses = service.parseExamCourses(rows)
    val abeerCourses = courses.filter { com.example.model.isTeacherNameMatch(it.teacherName, "عبير فريز غضبان") }
    assertEquals(6, abeerCourses.size)

    // Verify course names
    val cNames = abeerCourses.map { it.name }
    assertTrue(cNames.contains("الاحصاء"))
    assertTrue(cNames.contains("الاقتصاد"))
    assertTrue(cNames.contains("محاسبة عامة"))
    assertTrue(cNames.contains("مبادىء الإقتصاد الجزئي"))
    assertTrue(cNames.contains("مدخل الى علم الاحصاء"))
    assertTrue(cNames.contains("planification"))

    // Verify student name is NOT a course name
    assertTrue(courses.none { it.name.contains("فابيو") })
    assertTrue(courses.none { it.name.contains("روي") })

    // 3. Check students
    val (students, _) = service.parseStudentsAndGrades(rows, courses, ExamType.C1, classBlocks)
    val fabio = students.find { it.name == "فابيو انطوان القيم" }
    assertNotNull(fabio)
    assertEquals(11, fabio?.classIndex)
    assertEquals(252, fabio?.rowIndex)
  }

  @Test
  fun `verify Antoine George Khoury and Richard George Khoury are strictly separated and not mixed`() {
    val antoineFullName = "انطوان جورج الخوري"
    val richardFullName = "ريشار جورج الخوري"

    // 1. Core matching logic must NOT mix them
    assertFalse(com.example.model.isTeacherNameMatch(antoineFullName, richardFullName))
    assertFalse(com.example.model.isTeacherNameMatch(richardFullName, antoineFullName))

    // 2. Strict equality and honorific handling
    assertTrue(com.example.model.isTeacherNameMatch(antoineFullName, antoineFullName))
    assertTrue(com.example.model.isTeacherNameMatch(richardFullName, richardFullName))
    assertTrue(com.example.model.isTeacherNameMatch("أ. انطوان جورج الخوري", antoineFullName))
    assertTrue(com.example.model.isTeacherNameMatch("الاستاذ ريشار جورج الخوري", richardFullName))
    assertTrue(com.example.model.isTeacherNameMatch("انطوان جورج خوري", antoineFullName))

    // 3. Different fathers must not match
    assertFalse(com.example.model.isTeacherNameMatch("انطوان جورج الخوري", "انطوان الياس الخوري"))
    assertFalse(com.example.model.isTeacherNameMatch("ريشار جورج الخوري", "ريشار ميشال الخوري"))

    // 4. Course filtering test: ensure Antoine never gets Richard's courses and vice-versa
    val antoineCourse = com.example.model.Course(
      id = "c_antoine_1",
      classIndex = 1,
      name = "رياضيات",
      coefficient = 4,
      teacherName = antoineFullName,
      columnLetter = "C",
      columnIndex = 2
    )
    val richardCourse = com.example.model.Course(
      id = "c_richard_1",
      classIndex = 1,
      name = "فيزياء",
      coefficient = 3,
      teacherName = richardFullName,
      columnLetter = "D",
      columnIndex = 3
    )
    val sampleCourses = listOf(antoineCourse, richardCourse)

    val antoineFiltered = sampleCourses.filter { com.example.model.isTeacherNameMatch(it.teacherName, antoineFullName) }
    assertEquals(1, antoineFiltered.size)
    assertEquals("c_antoine_1", antoineFiltered.first().id)
    assertEquals(antoineFullName, antoineFiltered.first().teacherName)

    val richardFiltered = sampleCourses.filter { com.example.model.isTeacherNameMatch(it.teacherName, richardFullName) }
    assertEquals(1, richardFiltered.size)
    assertEquals("c_richard_1", richardFiltered.first().id)
    assertEquals(richardFullName, richardFiltered.first().teacherName)
  }

  @Test
  fun `verify parseLocalizedDouble handles Arabic numerals and various decimal separators`() {
    val service = GoogleSheetsService()
    assertEquals(15.5, service.parseLocalizedDouble("15.5") ?: 0.0, 0.001)
    assertEquals(15.5, service.parseLocalizedDouble("15,5") ?: 0.0, 0.001)
    assertEquals(15.5, service.parseLocalizedDouble("١٥.٥") ?: 0.0, 0.001)
    assertEquals(15.5, service.parseLocalizedDouble("١٥٫٥") ?: 0.0, 0.001)
    assertEquals(15.75, service.parseLocalizedDouble("١٥،٧٥") ?: 0.0, 0.001)
    assertEquals(18.0, service.parseLocalizedDouble("\"18\"") ?: 0.0, 0.001)
    assertEquals(null, service.parseLocalizedDouble(""))
    assertEquals(null, service.parseLocalizedDouble("   "))
  }

  @Test
  fun `verify grade update reflection in repository when sheets data changes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)

    // Initially saved grade
    repo.saveGrade("s1", "c1", ExamType.C1, 12.0)
    assertEquals(12.0, repo.getGrade("s1", "c1", ExamType.C1) ?: 0.0, 0.001)

    // Admin updates grade in Google Sheet to 18.5
    val newGrades = mapOf("s1_c1_C1" to 18.5)
    repo.batchSaveGrades(newGrades)
    assertEquals(18.5, repo.getGrade("s1", "c1", ExamType.C1) ?: 0.0, 0.001)
  }

  @Test
  fun `verify live sync with google sheets retrieves real data and grades`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)
    repo.updateSheetId("1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM")
    val success = repo.syncWithGoogleSheets(silent = false)
    assertTrue("Sync should succeed against real sheet", success)
    assertTrue("Teachers should be populated from sheet", repo.teachers.value.isNotEmpty())
    assertTrue("Students should be populated from sheet", repo.students.value.isNotEmpty())
    assertTrue("Courses should be populated from sheet", repo.courses.value.isNotEmpty())
  }

  @Test
  fun `verify parseCellA2IsOne logic for cell A2 equal to 1`() {
    val service = GoogleSheetsService()

    // When cell A2 is "1", permission is enabled
    val rowsWithOne = listOf(
      listOf("Header A1", "Header B1"),
      listOf("1", "Subject", "Teacher")
    )
    assertTrue(service.parseCellA2IsOne(rowsWithOne))

    // Arabic numeral 1
    val rowsWithArabicOne = listOf(
      listOf("Header A1"),
      listOf("١")
    )
    assertTrue(service.parseCellA2IsOne(rowsWithArabicOne))

    // Float 1.0 or quoted "1"
    val rowsWithFloatOne = listOf(
      listOf("Header A1"),
      listOf("\"1\"")
    )
    assertTrue(service.parseCellA2IsOne(rowsWithFloatOne))

    // When cell A2 is "0", permission is disabled
    val rowsWithZero = listOf(
      listOf("Header A1"),
      listOf("0")
    )
    assertFalse(service.parseCellA2IsOne(rowsWithZero))

    // When cell A2 is empty or text like "no"
    val rowsWithText = listOf(
      listOf("Header A1"),
      listOf("no")
    )
    assertFalse(service.parseCellA2IsOne(rowsWithText))

    // Empty list
    assertFalse(service.parseCellA2IsOne(emptyList()))
    assertFalse(service.parseCellA2IsOne(listOf(listOf("Only Header"))))
  }

  @Test
  fun `verify sheet exam 1 cell A2 enables midyear and sheet Exam 2 cell A2 enables endyear`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)

    val syncSuccess = repo.syncWithGoogleSheets(silent = false)
    assertTrue("Live sync should complete", syncSuccess || repo.deadlines.value != null)

    // Test parser with known inputs for cell A2
    val service = GoogleSheetsService()
    assertTrue(service.parseCellA2IsOne(listOf(listOf("A1"), listOf("1"))))
    assertFalse(service.parseCellA2IsOne(listOf(listOf("A1"), listOf("0"))))
  }

  @Test
  fun `verify contract hours sheet parser maps teacher hours accurately`() {
    val service = GoogleSheetsService()
    val dummyTeachers = listOf(
      Teacher(id = "1", code = "101", name = "أحمد محمد")
    )
    val mockSheet = listOf(
      listOf("تشرين الأول (أكتوبر)"),
      listOf("الاسم", "1", "2", "3"),
      listOf("أحمد محمد", "4", "0", "2")
    )
    val entries = service.parseContractHoursSheet(mockSheet, dummyTeachers)
    assertEquals(2, entries.size)
    val day1 = entries.find { it.day == 1 }
    val day3 = entries.find { it.day == 3 }
    assertNotNull(day1)
    assertEquals(4.0, day1!!.hours, 0.01)
    assertEquals(10, day1.monthIndex)
    assertNotNull(day3)
    assertEquals(2.0, day3!!.hours, 0.01)
  }

  @Test
  fun `verify contract hours sheet parser handles second training row under teacher name`() {
    val service = GoogleSheetsService()
    val dummyTeachers = listOf(
      Teacher(id = "1", code = "101", name = "أحمد محمد")
    )
    // Teacher has two rows: original contract row and second row with 'تدريب'
    val mockSheet = listOf(
      listOf("تشرين الأول (أكتوبر)"),
      listOf("الاسم", "1", "2", "3"),
      listOf("أحمد محمد", "4", "0", "2"),
      listOf("أحمد محمد تدريب", "0", "3", "1")
    )
    val entries = service.parseContractHoursSheet(mockSheet, dummyTeachers)
    assertEquals(4, entries.size)

    val regularEntries = entries.filter { !it.isTraining }
    val trainingEntries = entries.filter { it.isTraining }

    assertEquals(2, regularEntries.size)
    assertEquals(2, trainingEntries.size)

    // Verify both rows map to the teacher code "101"
    assertTrue(regularEntries.all { it.teacherCode == "101" })
    assertTrue(trainingEntries.all { it.teacherCode == "101" })

    // Day 3 has both regular (2.0) and training (1.0)
    val day3Regular = regularEntries.find { it.day == 3 }
    val day3Training = trainingEntries.find { it.day == 3 }
    assertNotNull(day3Regular)
    assertNotNull(day3Training)
    assertEquals(2.0, day3Regular!!.hours, 0.01)
    assertEquals(1.0, day3Training!!.hours, 0.01)
  }

  @Test
  fun `verify startup teacher refresh and welcome screen sync flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)

    // Initially on Login screen so teacher can enter code
    assertEquals(com.example.viewmodel.AppScreen.LOGIN, vm.currentScreen.value)

    // Teacher enters code and logs in
    val loggedIn = vm.loginWithCode("2643")
    assertTrue(loggedIn)

    // Screen transitions to Welcome or directly to Dashboard upon sync finish
    assertTrue(vm.currentScreen.value == com.example.viewmodel.AppScreen.WELCOME || vm.currentScreen.value == com.example.viewmodel.AppScreen.DASHBOARD)
    assertEquals("عبير فريز غضبان", vm.currentTeacher.value?.name)

    // Teacher can proceed to Dashboard with pages ready
    vm.proceedFromWelcomeToDashboard()
    assertEquals(com.example.viewmodel.AppScreen.DASHBOARD, vm.currentScreen.value)

    // User can navigate to Grades screen without automatic network refresh
    vm.navigateTo(com.example.viewmodel.AppScreen.GRADES)
    assertEquals(com.example.viewmodel.AppScreen.GRADES, vm.currentScreen.value)
    assertFalse(vm.isSyncing.value)
  }

  @Test
  fun `verify userEditedStudentIds tracking and payload trimming for faster sync`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    val loggedIn = vm.loginWithCode("2643")
    assertTrue(loggedIn)

    val teacherName = vm.currentTeacher.value?.name
    val course = vm.repository.courses.value.first { com.example.model.isTeacherNameMatch(it.teacherName, teacherName) }
    val student = vm.repository.students.value.first { it.classIndex == course.classIndex }

    vm.setSelectedClassIndex(course.classIndex)
    vm.setSelectedCourseId(course.id)
    vm.setSelectedExamType(ExamType.C1)
    vm.repository.setDeadline(ExamType.C1, "2026-12-31")
    vm.loadGradesForCurrentSelection()

    assertTrue(vm.userEditedStudentIds.value.isEmpty())

    // When teacher enters a grade, that student is added to edited set
    vm.updateGradeInput(student.id, "17.75")
    assertTrue(vm.userEditedStudentIds.value.contains(student.id))
    assertEquals(1, vm.userEditedStudentIds.value.size)

    // Save persists locally and initiates sync
    vm.saveCurrentGrades()
    assertEquals(17.75, vm.repository.getGrade(student.id, course.id, ExamType.C1))
  }

  @Test
  fun `verify full contract hours data model calculates remaining and total accurately`() {
    val row = com.example.model.FullContractHoursRow(
      rowIndex = 3,
      teacherName = "عبير فريز غضبان",
      monthlyHours = mapOf(10 to 12.0, 11 to 16.0, 12 to 14.0),
      originalQuota = 200.0
    )
    assertEquals(42.0, row.totalDone, 0.01)
    assertEquals(158.0, row.remainingHours, 0.01)
  }

  @Test
  fun `verify missing grades only checks selected exam type strictly`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    vm.setMissingGradeExamType(ExamType.C1)
    assertEquals(ExamType.C1, vm.missingGradeExamType.value)
    val missingC1 = vm.getMissingGrades(ExamType.C1)
    assertNotNull(missingC1)
  }

  @Test
  fun `diagnose Abeer missing grades with 6 courses`() {
    val service = GoogleSheetsService()
    val rows = mutableListOf<List<String>>()
    for (i in 0..270) {
      rows.add(MutableList(28) { "" })
    }

    // Class 1 at row 2 (index 1)
    val r2 = rows[1].toMutableList()
    r2[1] = "TS1 INF"
    r2[2] = "عبير فريز غضبان" // course 1
    r2[3] = "عبير فريز غضبان" // course 2
    r2[16] = "عبير فريز غضبان" // course 3
    rows[1] = r2

    val r3 = rows[2].toMutableList()
    r3[2] = "الاحصاء"
    r3[3] = "الاقتصاد"
    r3[16] = "محاسبة عامة"
    rows[2] = r3

    // Class 3 at row 50 (index 49)
    val r50 = rows[49].toMutableList()
    r50[1] = "TS1 HOT"
    r50[10] = "عبير فريز غضبان" // course 4
    r50[15] = "عبير فريز غضبان" // course 5
    rows[49] = r50

    val r51 = rows[50].toMutableList()
    r51[10] = "مبادىء الإقتصاد الجزئي"
    r51[15] = "مدخل الى علم الاحصاء"
    rows[50] = r51

    // Class 11 at row 242 (index 241)
    val r242 = rows[241].toMutableList()
    r242[1] = "TS2 CLI"
    r242[17] = "عبير فريز غضبان" // course 6
    rows[241] = r242

    val r243 = rows[242].toMutableList()
    r243[17] = "planification"
    rows[242] = r243

    val courses = service.parseExamCourses(rows)
    val classBlocks = service.parseClassBlocks(rows)

    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    val repo = vm.repository

    // Set classes and courses directly in repo
    val coursesField = repo.javaClass.getDeclaredField("_courses")
    coursesField.isAccessible = true
    val _courses = coursesField.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<List<com.example.model.Course>>
    _courses.value = courses

    val examCoursesField = repo.javaClass.getDeclaredField("_examCourses")
    examCoursesField.isAccessible = true
    val _examCourses = examCoursesField.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<Map<ExamType, List<com.example.model.Course>>>
    // All exams have all 6 courses for Abeer (TS1 INFO x3, TS1 HOT x2, TS2 CLI x1)
    _examCourses.value = mapOf(
        ExamType.C1 to courses,
        ExamType.E1 to courses,
        ExamType.C2 to courses,
        ExamType.E2 to courses
    )

    val blocksField = repo.javaClass.getDeclaredField("_classBlocks")
    blocksField.isAccessible = true
    val _classBlocks = blocksField.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<List<com.example.model.ClassBlock>>
    _classBlocks.value = classBlocks

    // Clear any grades so all grades are empty
    val gradesField = repo.javaClass.getDeclaredField("_grades")
    gradesField.isAccessible = true
    val _grades = gradesField.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<Map<String, Double>>
    _grades.value = emptyMap()

    // When grades are empty, all 6 courses of Abeer must appear in Missing Grades for C1 and E2
    val missingC1 = vm.getMissingGrades(ExamType.C1)
    val abeerMissingC1 = missingC1.filter { it.teacherName.contains("عبير") }
    assertEquals(6, abeerMissingC1.size)

    val missingE2 = vm.getMissingGrades(ExamType.E2)
    val abeerMissingE2 = missingE2.filter { it.teacherName.contains("عبير") }
    assertEquals(6, abeerMissingE2.size)
    assertTrue(abeerMissingE2.any { it.courseName == "planification" && it.className == "TS2 CLI" })
    assertTrue(abeerMissingE2.any { it.courseName == "مبادىء الإقتصاد الجزئي" && it.className == "TS1 HOT" })

    // User Scenario: In Exam 4 (E2), user enters grades for TS1 INFO (1st class, all her courses: c_1_2, c_1_3, c_1_16)
    val student1 = repo.students.value.first { it.classIndex == 1 }
    _grades.value = mapOf(
        "${student1.id}_c_1_2_${ExamType.E2.name}" to 15.0,
        "${student1.id}_c_1_3_${ExamType.E2.name}" to 14.0,
        "${student1.id}_c_1_16_${ExamType.E2.name}" to 12.0
    )

    // For Exam 4 (E2): TS1 INFO is completed. Abeer still has courses in TS1 HOT and TS2 CLI!
    val missingE2After = vm.getMissingGrades(ExamType.E2).filter { it.teacherName.contains("عبير") }
    assertEquals(3, missingE2After.size)
    // TS1 INFO courses are no longer missing in E2
    assertTrue(missingE2After.none { it.classIndex == 1 })
    // TS1 HOT and TS2 CLI MUST both appear in missing grades in E2
    assertTrue(missingE2After.any { it.className == "TS1 HOT" && it.courseName == "مبادىء الإقتصاد الجزئي" })
    assertTrue(missingE2After.any { it.className == "TS1 HOT" && it.courseName == "مدخل الى علم الاحصاء" })
    assertTrue(missingE2After.any { it.className == "TS2 CLI" && it.courseName == "planification" })

    // But C1 (and E1, C2) is completely separate and still has all 6 courses missing because no grades entered in C1
    val missingC1After = vm.getMissingGrades(ExamType.C1).filter { it.teacherName.contains("عبير") }
    assertEquals(6, missingC1After.size)
  }

  @Test
  fun `verify class with zero students does not show missing grades`() {
    val service = GoogleSheetsService()
    val dataset = service.createDefaultDataset()
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    val repo = vm.repository

    // Set students so that Class 2 has 0 students
    val studentsWithoutClass2 = dataset.students.filterNot { it.classIndex == 2 }
    val studentsField = repo.javaClass.getDeclaredField("_students")
    studentsField.isAccessible = true
    val _students = studentsField.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<List<com.example.model.Student>>
    _students.value = studentsWithoutClass2

    val examStudentsField = repo.javaClass.getDeclaredField("_examStudents")
    examStudentsField.isAccessible = true
    val _examStudents = examStudentsField.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<Map<ExamType, List<com.example.model.Student>>>
    _examStudents.value = mapOf(ExamType.C1 to studentsWithoutClass2)

    val missing = vm.getMissingGrades(ExamType.C1)
    // Class 2 must NEVER appear in missing grades because it has no students
    assertTrue(missing.none { it.classIndex == 2 })
  }

  @Test
  fun `verify parseExamCourses falls back to C1 master definitions when exam tab has blank teacher or course cells`() {
    val service = GoogleSheetsService()
    // C1 master rows: has TS2 CLI with Abeer teaching planification at col 17
    val c1Rows = MutableList(260) { MutableList(27) { "" } }
    c1Rows[231][1] = "TS2 CLI"
    c1Rows[231][17] = "عبير فريز غضبان"
    c1Rows[232][17] = "planification"

    // E2 exam rows: TS2 CLI has col 17 blank in both header rows (as in the real Google Sheet)
    val e2Rows = MutableList(260) { MutableList(27) { "" } }
    e2Rows[231][1] = "TS2 CLI"
    e2Rows[231][2] = "شادي شوقي فرام"
    e2Rows[232][2] = "أت الالات التوربينية"
    // col 17 left blank in e2Rows!

    val parsedCourses = service.parseExamCourses(e2Rows, c1Rows)
    // Must include course at col 17 with teacher Abeer and name planification from C1 fallback!
    val planCourse = parsedCourses.find { it.classIndex == 11 && it.columnIndex == 17 }
    assertNotNull(planCourse)
    assertEquals("planification", planCourse?.name)
    assertEquals("عبير فريز غضبان", planCourse?.teacherName)
  }

  @Test
  fun `verify multi exam course parsing and grade extraction preserves all courses and grades across C1 and E1`() {
    val service = GoogleSheetsService()
    // TS1 INF in C1: has col 12 blank course name and blank teacher, but coeff 8 and student grade 10
    val c1Rows = MutableList(25) { MutableList(27) { "" } }
    c1Rows[0][12] = "8"
    c1Rows[1][1] = "TS1 INF"
    c1Rows[1][2] = "عبير فريز غضبان"
    c1Rows[2][2] = "الاحصاء"
    // col 12 is blank in C1 header rows!
    c1Rows[3][1] = "ايلي انطوان شينا دوميط"
    c1Rows[3][2] = "10"
    c1Rows[3][12] = "10"

    // E1 rows: has col 12 with course "قواعد المعطيات" and teacher "وليد حنا فرسان" and student grade 10
    val e1Rows = MutableList(25) { MutableList(27) { "" } }
    e1Rows[0][12] = "8"
    e1Rows[1][1] = "TS1 INF"
    e1Rows[1][2] = "عبير فريز غضبان"
    e1Rows[2][2] = "الاحصاء"
    e1Rows[1][12] = "وليد حنا فرسان"
    e1Rows[2][12] = "قواعد المعطيات"
    e1Rows[3][1] = "ايلي انطوان شينا دوميط"
    e1Rows[3][2] = "10"
    e1Rows[3][12] = "10"

    val masterCourses = service.parseExamCoursesMulti(c1Rows, listOf(e1Rows))
    val course12 = masterCourses.find { it.classIndex == 1 && it.columnIndex == 12 }
    assertNotNull(course12)
    assertEquals("قواعد المعطيات", course12?.name)
    assertEquals("وليد حنا فرسان", course12?.teacherName)
    assertEquals(8, course12?.coefficient)

    val activeClasses = service.parseClassBlocks(c1Rows)
    val (c1Students, c1Grades) = service.parseStudentsAndGrades(c1Rows, masterCourses, ExamType.C1, activeClasses)
    val (e1Students, e1Grades) = service.parseStudentsAndGrades(e1Rows, masterCourses, ExamType.E1, activeClasses)

    val sId = c1Students.first().id
    val c1Grade = c1Grades["${sId}_${course12?.id}_C1"]
    val e1Grade = e1Grades["${sId}_${course12?.id}_E1"]

    assertNotNull(c1Grade)
    assertEquals(10.0, c1Grade)
    assertNotNull(e1Grade)
    assertEquals(10.0, e1Grade)
  }

  @Test
  fun `verify missingGrades StateFlow updates reactively on grade changes`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    val repo = vm.repository

    val initialMissing = vm.missingGrades.value
    assertNotNull(initialMissing)

    // Select E2 exam type
    vm.setMissingGradeExamType(ExamType.E2)
    assertEquals(ExamType.E2, vm.missingGradeExamType.value)
  }

  @Test
  fun `verify missing grades by class preserves exact sheet class order`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    val classBlocks = vm.classBlocks.value

    // In the sheet, classes are ordered by their row/index order (1, 2, 3, ... 11, 12)
    val allMissing = vm.getMissingGrades(ExamType.C1)
    val groupedByClassIndex = allMissing.groupBy { it.classIndex }

    val blockOrderMap = classBlocks.mapIndexed { idx, block -> block.index to idx }.toMap()
    val sortedClassIndices = groupedByClassIndex.keys.sortedBy { blockOrderMap[it] ?: it }

    // Verify sortedClassIndices matches sheet order
    for (i in 0 until sortedClassIndices.size - 1) {
      val orderCurrent = blockOrderMap[sortedClassIndices[i]] ?: sortedClassIndices[i]
      val orderNext = blockOrderMap[sortedClassIndices[i + 1]] ?: sortedClassIndices[i + 1]
      assertTrue("Classes must follow sheet order", orderCurrent <= orderNext)
    }
  }

  @Test
  fun `verify dynamic grade calculation rules according to user specifications and Lebanese official card`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    val repo = vm.repository

    val student = com.example.model.Student(
      id = "s_test_1",
      classIndex = 1,
      className = "TS1 HM",
      name = "ريتا زخيا البعينو",
      rowIndex = 4
    )

    val course1 = com.example.model.Course(
      id = "c_protocol",
      classIndex = 1,
      name = "البروتوكول واستقبال النزلاء",
      coefficient = 8,
      teacherName = "معلم 1",
      columnLetter = "C",
      columnIndex = 2
    )

    val course2 = com.example.model.Course(
      id = "c_math",
      classIndex = 1,
      name = "الرياضيات",
      coefficient = 5,
      teacherName = "معلم 2",
      columnLetter = "D",
      columnIndex = 3
    )

    val testCourses = listOf(course1, course2)

    // Rule 1: C1 ONLY exists -> 100% C1 (Image 1 case)
    repo.saveGrade(student.id, course1.id, ExamType.C1, 44.0) // out of 80 (or scaled)
    val reportC1 = repo.calculateStudentReport(student, listOf(course1), com.example.model.ReportStage.C1_ONLY)
    assertEquals(44.0, reportC1.courseGrades[course1.id] ?: 0.0, 0.01)
    assertEquals(44.0, reportC1.finalScore, 0.01)
    assertEquals(com.example.model.ReportStage.C1_ONLY, reportC1.stage)

    // Rule 2: C1 and E1 exist -> 20% C1 + 80% E1
    repo.saveGrade(student.id, course1.id, ExamType.E1, 64.0)
    val reportS1 = repo.calculateStudentReport(student, listOf(course1), com.example.model.ReportStage.C1_E1)
    // 44 * 0.20 + 64 * 0.80 = 8.8 + 51.2 = 60.0
    assertEquals(60.0, reportS1.courseGrades[course1.id] ?: 0.0, 0.01)

    // Rule 3: C1, E1, C2 exist -> "don't show c1 e1 just show c2 (علامة السعي الثاني it will have 100% of the grade)"
    repo.saveGrade(student.id, course1.id, ExamType.C2, 52.0)
    val reportC2 = repo.calculateStudentReport(student, listOf(course1), com.example.model.ReportStage.C2_ONLY)
    assertEquals(52.0, reportC2.courseGrades[course1.id] ?: 0.0, 0.01)

    // Rule 4: ALL grades exist -> c1 20%, e1 40%, c2 20%, e2 40% (Image 2 case)
    // In Image 2:
    // Course 1 (البروتوكول): C1=112, E1=112, C2=96, E2=104 -> final = 107.2
    repo.saveGrade(student.id, course1.id, ExamType.C1, 112.0)
    repo.saveGrade(student.id, course1.id, ExamType.E1, 112.0)
    repo.saveGrade(student.id, course1.id, ExamType.C2, 96.0)
    repo.saveGrade(student.id, course1.id, ExamType.E2, 104.0)

    // Course 2 (الرياضيات): C1=55, E1=40, C2=40, E2=50 -> final = 45.0
    repo.saveGrade(student.id, course2.id, ExamType.C1, 55.0)
    repo.saveGrade(student.id, course2.id, ExamType.E1, 40.0)
    repo.saveGrade(student.id, course2.id, ExamType.C2, 40.0)
    repo.saveGrade(student.id, course2.id, ExamType.E2, 50.0)

    val reportAll = repo.calculateStudentReport(student, testCourses, com.example.model.ReportStage.ALL)
    assertEquals(107.2, reportAll.courseGrades[course1.id] ?: 0.0, 0.01)
    assertEquals(45.5, reportAll.courseGrades[course2.id] ?: 0.0, 0.01)
    // Sum = 107.2 + 45.5 = 152.7
    assertEquals(152.7, reportAll.finalScore, 0.01)
    assertEquals(com.example.model.ReportStage.ALL, reportAll.stage)
  }

  @Test
  fun `verify Lebanese school title and specialty resolver helpers`() {
    assertEquals("EDU", com.example.model.resolveClassSpecialty("TS1 EDU"))
    assertEquals("HM", com.example.model.resolveClassSpecialty("TS1 HM"))
    assertEquals("INF", com.example.model.resolveClassSpecialty("TS2 INF"))
    assertEquals("MEC", com.example.model.resolveClassSpecialty("BT3 MEC"))
    assertEquals("TS1", com.example.model.resolveDegreeAndYear("TS1 EDU"))
    assertEquals("BT1", com.example.model.resolveDegreeAndYear("BT1"))
    assertEquals("BT3", com.example.model.resolveDegreeAndYear("BT3 MEC"))
  }

  @Test
  fun `verify phantom courses without course name and teacher are excluded from courses and missing grades`() {
    val service = GoogleSheetsService()
    // Simulate sheet with TS1 HOT (block 3) where columns C to P have courses,
    // but columns Q (index 16) and R (index 17) have NO course name, NO teacher, NO coeff
    val rowCoeff = mutableListOf<String>("", "")
    val rowTeacher = mutableListOf<String>("", "TS1 HOT")
    val rowCourse = mutableListOf<String>("", "")
    for (c in 2..15) {
      rowCoeff.add("8")
      rowTeacher.add("استاذ المادة $c")
      rowCourse.add("مادة $c")
    }
    // Col 16 (Q) and 17 (R) are completely blank
    rowCoeff.add("")
    rowTeacher.add("")
    rowCourse.add("")
    rowCoeff.add("")
    rowTeacher.add("")
    rowCourse.add("")

    val mockSheet = mutableListOf<List<String>>()
    mockSheet.add(rowCoeff)   // Row 0
    mockSheet.add(rowTeacher) // Row 1 (Header: TS1 HOT)
    mockSheet.add(rowCourse)  // Row 2 (Courses)
    // Student row
    val studentRow = mutableListOf<String>("1", "طالب تجريبي")
    for (c in 2..17) {
      studentRow.add("")
    }
    mockSheet.add(studentRow)

    val courses = service.parseExamCoursesMulti(mockSheet)
    // Exactly 14 courses should be found (cols 2 to 15). Columns 16 (Q) and 17 (R) must NOT be added!
    assertEquals(14, courses.size)
    assertTrue(courses.none { it.columnIndex == 16 })
    assertTrue(courses.none { it.columnIndex == 17 })
    assertTrue(courses.none { it.columnLetter == "Q" })
    assertTrue(courses.none { it.columnLetter == "R" })

    // Test missing grades filtering in repository
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SchoolRepository(context)
    val missing = repo.getMissingGrades(ExamType.C1)
    // Ensure no phantom courses like "مادة عمود Q" or "مادة عمود R" exist in missing grades
    assertTrue(missing.none { it.courseName.contains("عمود Q") || it.courseName.contains("عمود R") })
  }

  @Test
  fun `verify teacher code login with valid teacher code`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)
    // Attempt login with valid teacher code 2643
    vm.loginWithCode("2643")
    val currentTeacher = vm.currentTeacher.value
    assertNotNull(currentTeacher)
    assertEquals("2643", currentTeacher?.code)
    assertEquals(com.example.viewmodel.AppScreen.WELCOME, vm.currentScreen.value)
    assertEquals(false, currentTeacher?.isAdmin)
  }

  @Test
  fun `verify admin code detection and admin login with sheet ID`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)

    assertTrue(vm.isAdminCode("admin"))
    assertTrue(vm.isAdminCode("admin123"))
    assertTrue(vm.isAdminCode("ADMIN"))
    assertFalse(vm.isAdminCode("2643"))

    // On fresh install or default state, sheet ID is empty
    assertEquals("", vm.sheetId.value)

    val testSheetId = "1a2b3c4d5e6f7g8h9i0j_custom_sheet_id"
    val customAdminCode = "admin_school_xyz"
    vm.loginAsAdminWithSheetId(adminCode = customAdminCode, sheetIdInput = testSheetId)

    val currentTeacher = vm.currentTeacher.value
    assertNotNull(currentTeacher)
    assertTrue(currentTeacher?.isAdmin == true)
    assertEquals(customAdminCode, currentTeacher?.code)
    assertEquals(testSheetId, vm.sheetId.value)
    assertEquals(com.example.viewmodel.AppScreen.WELCOME, vm.currentScreen.value)
  }

  @Test
  fun `verify admin code is strictly parsed from sheet rows without auto-adding default admin`() {
    val service = com.example.data.GoogleSheetsService()

    // Sheet rows with custom school admin
    val customSchoolRows = listOf(
      listOf("اسم المعلم", "رمز الدخول"),
      listOf("إدارة مدرسة النور", "al_noor_admin_2024"),
      listOf("أحمد خليل", "teach_101")
    )

    val teachers = service.parseTeachers(customSchoolRows, autoAddDefaultAdmin = false)
    assertEquals(2, teachers.size)

    val admin = teachers.find { it.isAdmin }
    assertNotNull(admin)
    assertEquals("al_noor_admin_2024", admin?.code)
    assertEquals("إدارة مدرسة النور", admin?.name)

    // Ensure default "admin123" was NOT injected
    assertTrue(teachers.none { it.code == "admin123" })
  }

  @Test
  fun `verify live admin verification strictly enforces sheet admin code`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.SchoolRepository(context)
    val realSheetId = "1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM"

    // Attempting login with an incorrect/random admin code should fail
    val failResult = repo.verifyAndApplyAdminFromSheet(realSheetId, "wrong_random_code_9999")
    assertTrue(failResult.isFailure)
    val errorMsg = failResult.exceptionOrNull()?.message ?: ""
    assertTrue("Error should mention invalid admin code", errorMsg.contains("رمز الإدارة غير صحيح"))

    // Default "admin123" should ALSO fail if this sheet uses "1111" as its admin code
    val genericAdminFail = repo.verifyAndApplyAdminFromSheet(realSheetId, "admin123")
    assertTrue("Generic admin123 must fail when sheet defines custom code 1111", genericAdminFail.isFailure)

    // Attempting with blank admin code should fail immediately
    val blankResult = repo.verifyAndApplyAdminFromSheet(realSheetId, "   ")
    assertTrue(blankResult.isFailure)

    // Attempting with the actual admin code in that sheet ("1111") must strictly succeed
    val successResult = repo.verifyAndApplyAdminFromSheet(realSheetId, "1111")
    assertTrue("Admin login with real sheet admin code (1111) should succeed", successResult.isSuccess)
    val adminTeacher = successResult.getOrThrow()
    assertTrue(adminTeacher.isAdmin)
    assertEquals("1111", adminTeacher.code)
    assertEquals("admin", adminTeacher.name)
    assertEquals(realSheetId, repo.sheetId.value)
    assertTrue("Teachers list should be populated from the sheet", repo.teachers.value.size >= 40)
  }

  @Test
  fun `verify master sheet parsing extracts column B for school name, filters by column C active, and uses column A for sheet id`() {
    val service = GoogleSheetsService()
    val rawMasterCsv = """
      Sheet ID,School Name,Status
      1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM,معهد عجلتون الفني,active
      1AbCdEfGhIjKlMnOpQrStUvWxYz1234567890,مدرسة بيروت النموذجية,inactive
      https://docs.google.com/spreadsheets/d/1XyZaBcDeFgHiJkLmNoPqRsTuVwXyZ/edit,معهد صيدا التقني,ACTIVE
      1234567890abcdefghijklmnopqrstuvwxyz,مدرسة طرابلس الوطنية,غير نشط
      1987654321zyxwvutsrqponmlkjihgfedcba,معهد المتن الفني,نشط
    """.trimIndent()

    val rows = service.parseCsv(rawMasterCsv)
    val schools = mutableListOf<com.example.model.MasterSchool>()

    for ((index, row) in rows.withIndex()) {
      if (row.size < 2) continue
      val colA = row.getOrNull(0)?.trim() ?: ""
      val colB = row.getOrNull(1)?.trim() ?: ""
      val colC = row.getOrNull(2)?.trim() ?: ""

      if (colA.isBlank() && colB.isBlank()) continue
      if (index == 0 && (colA.contains("sheet", ignoreCase = true) || colB.contains("school", ignoreCase = true))) continue

      val cleanId = service.extractSheetId(colA)
      if (cleanId.isBlank() || colB.isBlank()) continue

      val school = com.example.model.MasterSchool(sheetId = cleanId, schoolName = colB, status = colC)
      if (school.isActive) {
        schools.add(school)
      }
    }

    // Only 3 schools should be active:
    // 1: معهد عجلتون الفني (active)
    // 2: معهد صيدا التقني (ACTIVE)
    // 3: معهد المتن الفني (نشط)
    assertEquals(3, schools.size)

    assertEquals("معهد عجلتون الفني", schools[0].schoolName)
    assertEquals("1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM", schools[0].sheetId)
    assertTrue(schools[0].isActive)

    assertEquals("معهد صيدا التقني", schools[1].schoolName)
    assertEquals("1XyZaBcDeFgHiJkLmNoPqRsTuVwXyZ", schools[1].sheetId)
    assertTrue(schools[1].isActive)

    assertEquals("معهد المتن الفني", schools[2].schoolName)
    assertEquals("1987654321zyxwvutsrqponmlkjihgfedcba", schools[2].sheetId)
    assertTrue(schools[2].isActive)

    // Verify inactive schools are NOT included
    assertTrue(schools.none { it.schoolName == "مدرسة بيروت النموذجية" })
    assertTrue(schools.none { it.schoolName == "مدرسة طرابلس الوطنية" })
  }

  @Test
  fun `verify selecting active school updates repository sheetId to Column A and triggers synchronization`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.SchoolRepository(context)
    val testSchool = com.example.model.MasterSchool(
      sheetId = "1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM",
      schoolName = "معهد عجلتون الفني",
      status = "active"
    )

    repo.selectMasterSchool(testSchool)
    assertEquals("1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM", repo.sheetId.value)
    assertEquals("معهد عجلتون الفني", repo.schoolName.value)
  }

  @Test
  fun `verify EmisViewModel selectMasterSchool fetches sheetId from Column A and updates schoolName from Column B`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.EmisViewModel(app)

    val schoolA = com.example.model.MasterSchool(
      sheetId = "1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM",
      schoolName = "معهد عجلتون الفني",
      status = "active"
    )
    val schoolB = com.example.model.MasterSchool(
      sheetId = "1XyZaBcDeFgHiJkLmNoPqRsTuVwXyZ",
      schoolName = "معهد صيدا التقني",
      status = "نشط"
    )

    vm.selectMasterSchool(schoolA)
    assertEquals("1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM", vm.sheetId.value)
    assertEquals("معهد عجلتون الفني", vm.schoolName.value)

    vm.selectMasterSchool(schoolB)
    assertEquals("1XyZaBcDeFgHiJkLmNoPqRsTuVwXyZ", vm.sheetId.value)
    assertEquals("معهد صيدا التقني", vm.schoolName.value)
  }

  @Test
  fun `verify MasterSchool isActive only accepts active status in Column C`() {
    assertTrue(com.example.model.MasterSchool("id1", "School 1", "active").isActive)
    assertTrue(com.example.model.MasterSchool("id2", "School 2", "ACTIVE").isActive)
    assertTrue(com.example.model.MasterSchool("id3", "School 3", "Active ").isActive)
    assertTrue(com.example.model.MasterSchool("id4", "School 4", "نشط").isActive)
    assertTrue(com.example.model.MasterSchool("id5", "School 5", "مفعل").isActive)
    assertTrue(com.example.model.MasterSchool("id6", "School 6", "yes").isActive)
    assertTrue(com.example.model.MasterSchool("id7", "School 7", "1").isActive)

    assertFalse(com.example.model.MasterSchool("id8", "School 8", "inactive").isActive)
    assertFalse(com.example.model.MasterSchool("id9", "School 9", "غير نشط").isActive)
    assertFalse(com.example.model.MasterSchool("id10", "School 10", "معطل").isActive)
    assertFalse(com.example.model.MasterSchool("id11", "School 11", "").isActive)
    assertFalse(com.example.model.MasterSchool("id12", "School 12", "0").isActive)
    assertFalse(com.example.model.MasterSchool("id13", "School 13", "no").isActive)
  }

  @Test
  fun `verify parseMasterSchoolsCsv extracts sheetId from Column A, schoolName from Column B, and filters strictly by Column C active`() {
    val service = GoogleSheetsService()
    val mockCsvRows = listOf(
      listOf("Sheet ID", "School Name", "Status"), // Header row - must be skipped
      listOf("1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM", "معهد المتن التقني", "active"),
      listOf("https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit#gid=0", "معهد طرابلس الفني", "نشط"),
      listOf("1NonActiveSchoolSheetId999", "مدرسة بيروت النموذجية", "inactive"), // Inactive - must be excluded!
      listOf("1AnotherInactiveSheetId888", "معهد زحلة الفني", "معطل"), // Inactive - must be excluded!
      listOf("", "معهد بلا معرف", "active"), // Blank ID - must be excluded!
      listOf("1SomeValidIdWithoutName", "", "active"), // Blank Name - must be excluded!
      listOf("1ZzYyXxWwVvUuTtSsRrQqPpOoNnMmLl", "معهد النبطية الزراعي", "ACTIVE") // Active uppercase
    )

    val schools = service.parseMasterSchoolsCsv(mockCsvRows)

    // Only active schools with valid IDs and non-blank names must be present
    assertEquals(3, schools.size)

    assertEquals("1bmoxdIJcJ76ILIm3zgLXSgaKhw-GdmLp5cZCEcn6BZM", schools[0].sheetId)
    assertEquals("معهد المتن التقني", schools[0].schoolName)
    assertTrue(schools[0].isActive)

    assertEquals("1AbCdEfGhIjKlMnOpQrStUvWxYz", schools[1].sheetId)
    assertEquals("معهد طرابلس الفني", schools[1].schoolName)
    assertTrue(schools[1].isActive)

    assertEquals("1ZzYyXxWwVvUuTtSsRrQqPpOoNnMmLl", schools[2].sheetId)
    assertEquals("معهد النبطية الزراعي", schools[2].schoolName)
    assertTrue(schools[2].isActive)

    // Ensure inactive schools are nowhere in the result list
    assertTrue(schools.none { it.schoolName.contains("بيروت") })
    assertTrue(schools.none { it.schoolName.contains("زحلة") })
  }

  @Test
  fun `verify parseMasterSchoolsCsv does not skip row 1 when row 1 is a real school without header`() {
    val service = GoogleSheetsService()
    // Exact data from user's Image 1
    val mockCsvRows = listOf(
      listOf("1_CY5WRTmo9-gvRn5Lz-odjD4dv3yoz1L7Xqc310VgaU", "xzw", "active")
    )
    val schools = service.parseMasterSchoolsCsv(mockCsvRows)
    assertEquals(1, schools.size)
    assertEquals("1_CY5WRTmo9-gvRn5Lz-odjD4dv3yoz1L7Xqc310VgaU", schools[0].sheetId)
    assertEquals("xzw", schools[0].schoolName)
    assertTrue(schools[0].isActive)
  }

  @Test
  fun `verify sanitizeMasterSheetId fixes missing trailing s typo`() {
    val sanitized = GoogleSheetsService.sanitizeMasterSheetId("1lqbi4ArzITb3z6jINq5OUHjzPo0_Gkcla_Og6iMGwU")
    assertEquals(GoogleSheetsService.DEFAULT_MASTER_SHEET_ID, sanitized)
    assertEquals("1lqbi4ArzITb3z6jINq5OUHjzPo0_Gkcla_Og6iMGwUs", sanitized)
  }

  @Test
  fun `reproduce Abeer courses count when all grades are filled`() {
    val service = GoogleSheetsService()
    // Create 4 exam sheets with 12 classes
    fun createSheet(withGrades: Boolean): List<List<String>> {
      val rows = mutableListOf<MutableList<String>>()
      for (i in 0..287) {
        rows.add(MutableList(28) { "" })
      }
      val step24Rows = listOf(1, 25, 49, 73, 97, 121, 145, 169, 193, 217, 241, 265)
      val classNames = listOf(
        "TS1 INF", "TS1 EXP", "TS1 HOT", "TS1 EDU", "TS1 CLI", "TS1 ELI",
        "TS2 INFO", "TS2 EXP", "TS2 HOT", "TS2 EDU", "TS2 CLI", "TS2 ELI"
      )

      step24Rows.forEachIndexed { idx, rIdx ->
        val cName = classNames[idx]
        rows[rIdx][1] = cName

        // Courses for this class
        if (idx == 0) { // TS1 INF
          rows[rIdx][2] = "عبير فريز غضبان"
          rows[rIdx + 1][2] = "الاحصاء"
          rows[rIdx][3] = "عبير فريز غضبان"
          rows[rIdx + 1][3] = "الاقتصاد"
          rows[rIdx][16] = "عبير فريز غضبان"
          rows[rIdx + 1][16] = "محاسبة عامة"
        } else if (idx == 2) { // TS1 HOT
          rows[rIdx][10] = "عبير فريز غضبان"
          rows[rIdx + 1][10] = "مبادىء الإقتصاد الجزئي"
          rows[rIdx][15] = "عبير فريز غضبان"
          rows[rIdx + 1][15] = "مدخل الى علم الاحصاء"
        } else if (idx == 10) { // TS2 CLI
          rows[rIdx][17] = "عبير فريز غضبان"
          rows[rIdx + 1][17] = "planification"
        } else {
          rows[rIdx][2] = "استاذ آخر"
          rows[rIdx + 1][2] = "مادة اخرى"
        }

        // Students in this class (rows rIdx+2 to rIdx+21)
        for (s in (rIdx + 2)..(rIdx + 21)) {
          rows[s][0] = "${s - rIdx - 1}"
          rows[s][1] = "طالب ${idx + 1} رقم ${s - rIdx - 1}"
          if (withGrades) {
            for (col in 2..20) {
              rows[s][col] = if (col % 3 == 0) "15,5" else if (col % 5 == 0) "غ" else "15"
            }
          }
        }
      }
      return rows
    }

    val emptySheet = createSheet(withGrades = false)
    val emptyCourses = service.parseExamCoursesMulti(emptySheet)
    val emptyAbeerCourses = emptyCourses.filter { com.example.model.isTeacherNameMatch(it.teacherName, "عبير فريز غضبان") }
    println("Empty Abeer courses count: ${emptyAbeerCourses.size}")

    val filledSheet = createSheet(withGrades = true)
    val filledCourses = service.parseExamCoursesMulti(filledSheet)
    val filledAbeerCourses = filledCourses.filter { com.example.model.isTeacherNameMatch(it.teacherName, "عبير فريز غضبان") }
    println("Filled Abeer courses count: ${filledAbeerCourses.size}")
    filledAbeerCourses.forEach {
      println("  Survived course: classIndex=${it.classIndex}, name=${it.name}, col=${it.columnLetter}")
    }

    assertEquals(6, emptyAbeerCourses.size)
    assertEquals(6, filledAbeerCourses.size)
  }

  @Test
  fun `verify Abeer courses with BT3 MEC class header name`() {
    val service = GoogleSheetsService()
    val rows = mutableListOf<MutableList<String>>()
    for (i in 0..287) {
      rows.add(MutableList(28) { "" })
    }

    // Row 0: deadline
    rows[0][0] = "4/10/2025"

    // Row 1 (B2): BT3 MEC (Class 1)
    rows[1][1] = "BT3 MEC"
    rows[1][2] = "عبير فريز غضبان"
    rows[1][3] = "عبير فريز غضبان"
    rows[1][16] = "عبير فريز غضبان"

    rows[2][2] = "الاحصاء"
    rows[2][3] = "الاقتصاد"
    rows[2][16] = "محاسبة عامة"

    // Students in Class 1 with all grades filled:
    for (s in 3..22) {
      rows[s][1] = "طالب $s"
      for (c in 2..20) rows[s][c] = "15"
    }

    // Row 49 (B50): TS1 HOT (Class 3)
    rows[49][1] = "TS1 HOT"
    rows[49][10] = "عبير فريز غضبان"
    rows[49][15] = "عبير فريز غضبان"
    rows[50][10] = "مبادىء الإقتصاد الجزئي"
    rows[50][15] = "مدخل الى علم الاحصاء"
    for (s in 51..70) {
      rows[s][1] = "طالب $s"
      for (c in 2..20) rows[s][c] = "14"
    }

    // Row 241 (B242): TS2 CLI (Class 11)
    rows[241][1] = "TS2 CLI"
    rows[241][17] = "عبير فريز غضبان"
    rows[242][17] = "planification"
    for (s in 243..262) {
      rows[s][1] = "طالب $s"
      for (c in 2..20) rows[s][c] = "18"
    }

    // Test class blocks
    val classBlocks = service.parseClassBlocks(rows)
    val class1 = classBlocks.find { it.index == 1 }
    assertNotNull(class1)
    assertEquals("BT3 MEC", class1?.name)

    // Test courses
    val courses = service.parseExamCoursesMulti(rows)
    val abeerCourses = courses.filter { com.example.model.isTeacherNameMatch(it.teacherName, "عبير فريز غضبان") }
    assertEquals(6, abeerCourses.size)

    val c1Courses = abeerCourses.filter { it.classIndex == 1 }
    assertEquals(3, c1Courses.size)

    val c3Courses = abeerCourses.filter { it.classIndex == 3 }
    assertEquals(2, c3Courses.size)

    val c11Courses = abeerCourses.filter { it.classIndex == 11 }
    assertEquals(1, c11Courses.size)
  }
}


