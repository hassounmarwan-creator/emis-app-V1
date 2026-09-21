package com.example.ui.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.model.Course
import com.example.model.ReportStage
import com.example.model.StudentReportRow
import java.io.File
import java.io.FileOutputStream

object PdfReportGenerator {

    /**
     * Generates an official Lebanese Technical School report card PDF matching the Ministry format,
     * saves it to device downloads, and triggers the Android share sheet.
     */
    fun generateAndSharePdf(
        context: Context,
        row: StudentReportRow,
        courses: List<Course>,
        className: String,
        schoolName: String,
        schoolYear: String
    ) {
        try {
            val pdfFile = generatePdfFile(context, row, courses, className, schoolName, schoolYear)
            if (pdfFile == null || !pdfFile.exists()) {
                Toast.makeText(context, "تعذر إنشاء ملف PDF، يرجى المحاولة مجدداً", Toast.LENGTH_SHORT).show()
                return
            }

            // Also copy / save to public Downloads folder so it is readily available in Downloads
            saveToPublicDownloads(context, pdfFile, row.student.name)

            // Share / Open Intent via FileProvider
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "بطاقة علامات رسمية - ${row.student.name}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "بطاقة علامات رسمية للطالب: ${row.student.name}\nالمدرسة: $schoolName\nالصف: $className"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "مشاركة أو فتح كشف العلامات (PDF)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            Toast.makeText(context, "تم حفظ ومشاركة كشف العلامات (PDF) بنجاح ✓", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "حدث خطأ أثناء إعداد ملف PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generatePdfFile(
        context: Context,
        row: StudentReportRow,
        courses: List<Course>,
        className: String,
        schoolName: String,
        schoolYear: String
    ): File? {
        val document = PdfDocument()

        // Standard A4 dimensions in points (72 DPI): 595 x 842 points
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // Background
        canvas.drawColor(Color.WHITE)

        val margin = 24f
        val contentWidth = pageWidth - (margin * 2)

        // Outer Border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        val outerRect = RectF(margin, margin, pageWidth - margin, pageHeight - margin)
        canvas.drawRoundRect(outerRect, 6f, 6f, borderPaint)

        var currentY = margin + 14f

        // ================= 1. HEADER =================
        // Right Side: Ministry & School Header (Arabic RTL)
        val headerRightX = margin + 16f
        val headerRightWidth = (contentWidth * 0.60f).toInt()

        drawArabicText(
            canvas = canvas,
            text = "الجمهورية اللبنانية",
            x = headerRightX,
            y = currentY,
            width = headerRightWidth,
            textSize = 10.5f,
            textColor = Color.parseColor("#0F172A"),
            isBold = true,
            alignment = Layout.Alignment.ALIGN_OPPOSITE
        )
        currentY += 13f

        drawArabicText(
            canvas = canvas,
            text = "وزارة التربية و التعليم العالي",
            x = headerRightX,
            y = currentY,
            width = headerRightWidth,
            textSize = 10.5f,
            textColor = Color.parseColor("#0F172A"),
            isBold = true,
            alignment = Layout.Alignment.ALIGN_OPPOSITE
        )
        currentY += 13f

        drawArabicText(
            canvas = canvas,
            text = "المديرية العامة للتعليم المهني والتقني",
            x = headerRightX,
            y = currentY,
            width = headerRightWidth,
            textSize = 10.5f,
            textColor = Color.parseColor("#0F172A"),
            isBold = true,
            alignment = Layout.Alignment.ALIGN_OPPOSITE
        )
        currentY += 14f

        val effectiveSchoolName = schoolName.ifBlank { "المديرية العامة للتعليم المهني والتقني" }
        drawArabicText(
            canvas = canvas,
            text = effectiveSchoolName,
            x = headerRightX,
            y = currentY,
            width = headerRightWidth,
            textSize = 12f,
            textColor = Color.parseColor("#1E3A8A"),
            isBold = true,
            alignment = Layout.Alignment.ALIGN_OPPOSITE
        )

        // Left Side: Badge Box with School Year & Stage Title
        val badgeWidth = 140f
        val badgeHeight = 52f
        val badgeX = margin + 16f
        val badgeY = margin + 14f

        val badgeRect = RectF(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight)
        canvas.drawRoundRect(badgeRect, 4f, 4f, borderPaint)

        // Divider in badge box
        val badgeDividerX = badgeX + 60f
        canvas.drawLine(badgeDividerX, badgeY, badgeDividerX, badgeY + badgeHeight, borderPaint)

        // Year in left sub-box
        val effectiveYear = schoolYear.ifBlank { "2025-2026" }
        drawArabicText(
            canvas = canvas,
            text = effectiveYear,
            x = badgeX + 4f,
            y = badgeY + 18f,
            width = 52,
            textSize = 10f,
            textColor = Color.parseColor("#0F172A"),
            isBold = true,
            alignment = Layout.Alignment.ALIGN_CENTER
        )

        // Stage Title in right sub-box
        val stageLines = when (row.stage) {
            ReportStage.C1_ONLY -> listOf("بطاقة", "علامات", "السعي", "الاول")
            ReportStage.C1_E1 -> listOf("بطاقة", "علامات", "الفصل", "الاول")
            ReportStage.C2_ONLY -> listOf("بطاقة", "علامات", "السعي", "الثاني")
            ReportStage.ALL -> listOf("بطاقة", "العلامات", "السنوية", "")
            else -> listOf("بطاقة", "علامات", "الطالب", "")
        }
        val stageText = stageLines.filter { it.isNotBlank() }.joinToString("\n")
        drawArabicText(
            canvas = canvas,
            text = stageText,
            x = badgeDividerX + 4f,
            y = badgeY + 6f,
            width = 72,
            textSize = 9.5f,
            textColor = Color.parseColor("#0F172A"),
            isBold = true,
            alignment = Layout.Alignment.ALIGN_CENTER
        )

        currentY = margin + 74f

        // ================= 2. STUDENT METADATA TABLE =================
        val metaTableX = margin + 16f
        val metaTableWidth = contentWidth - 32f
        val metaRow1Height = 22f
        val metaRow2Height = 24f

        val metaRect = RectF(metaTableX, currentY, metaTableX + metaTableWidth, currentY + metaRow1Height + metaRow2Height)
        canvas.drawRect(metaRect, borderPaint)

        // Row 1 Divider
        canvas.drawLine(metaTableX, currentY + metaRow1Height, metaTableX + metaTableWidth, currentY + metaRow1Height, borderPaint)

        // Row 1: الشهادة و السنة و الاختصاص
        drawArabicText(
            canvas = canvas,
            text = "الشهادة و السنة و الاختصاص: $className",
            x = metaTableX + 8f,
            y = currentY + 5f,
            width = (metaTableWidth - 16f).toInt(),
            textSize = 9.5f,
            textColor = Color.parseColor("#0F172A"),
            isBold = true,
            alignment = Layout.Alignment.ALIGN_OPPOSITE
        )

        // Row 2: الصف | الشعبة | اسم الطالب
        val r2Y = currentY + metaRow1Height
        val col1Width = metaTableWidth * 0.25f
        val col2Width = metaTableWidth * 0.20f
        val col3Width = metaTableWidth * 0.55f

        val div1X = metaTableX + col1Width
        val div2X = div1X + col2Width

        canvas.drawLine(div1X, r2Y, div1X, r2Y + metaRow2Height, borderPaint)
        canvas.drawLine(div2X, r2Y, div2X, r2Y + metaRow2Height, borderPaint)

        // Col 1: الصف
        val shortClass = className.takeWhile { it != ' ' }.ifBlank { className }
        drawArabicText(
            canvas = canvas,
            text = "الصف: $shortClass",
            x = metaTableX + 6f,
            y = r2Y + 6f,
            width = (col1Width - 12f).toInt(),
            textSize = 9.5f,
            textColor = Color.parseColor("#0F172A"),
            isBold = true,
            alignment = Layout.Alignment.ALIGN_OPPOSITE
        )

        // Col 2: الشعبة
        drawArabicText(
            canvas = canvas,
            text = "الشعبة: F1",
            x = div1X + 6f,
            y = r2Y + 6f,
            width = (col2Width - 12f).toInt(),
            textSize = 9.5f,
            textColor = Color.parseColor("#0F172A"),
            isBold = true,
            alignment = Layout.Alignment.ALIGN_OPPOSITE
        )

        // Col 3: اسم الطالب
        drawArabicText(
            canvas = canvas,
            text = "اسم الطالب: ${row.student.name}",
            x = div2X + 6f,
            y = r2Y + 6f,
            width = (col3Width - 12f).toInt(),
            textSize = 10f,
            textColor = Color.parseColor("#1E3A8A"),
            isBold = true,
            alignment = Layout.Alignment.ALIGN_OPPOSITE
        )

        currentY += metaRow1Height + metaRow2Height + 10f

        // ================= 3. GRADES TABLE =================
        val tableX = metaTableX
        val tableWidth = metaTableWidth
        val stage = row.stage
        val isAll = stage == ReportStage.ALL
        val isC1Only = stage == ReportStage.C1_ONLY
        val isC2Only = stage == ReportStage.C2_ONLY

        // Define column widths
        val colCourseW = if (isAll) tableWidth * 0.32f else tableWidth * 0.40f
        val colMaxW = if (isAll) tableWidth * 0.12f else tableWidth * 0.14f
        val remainingW = tableWidth - colCourseW - colMaxW
        val numSubCols = if (isAll) 5 else 3
        val subColW = remainingW / numSubCols

        val headerHeight = 24f
        val rowHeight = 18f

        // Table Header Background
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.FILL
        }
        canvas.drawRect(tableX, currentY, tableX + tableWidth, currentY + headerHeight, headerBgPaint)

        // Table Header Border
        val thinBorderPaint = Paint().apply {
            color = Color.parseColor("#334155")
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }
        canvas.drawRect(tableX, currentY, tableX + tableWidth, currentY + headerHeight, thinBorderPaint)

        // Header Titles (in RTL: Course name is furthest right)
        var curColRight = tableX + tableWidth
        // 1. Course Name (Far right)
        drawTableCellText(canvas, "اسم المادة", curColRight - colCourseW, currentY + 5f, colCourseW, isBold = true, align = Layout.Alignment.ALIGN_OPPOSITE)
        canvas.drawLine(curColRight - colCourseW, currentY, curColRight - colCourseW, currentY + headerHeight, thinBorderPaint)
        curColRight -= colCourseW

        // 2. Max Grade
        drawTableCellText(canvas, "العلامة القصوى", curColRight - colMaxW, currentY + 5f, colMaxW, isBold = true)
        canvas.drawLine(curColRight - colMaxW, currentY, curColRight - colMaxW, currentY + headerHeight, thinBorderPaint)
        curColRight -= colMaxW

        // 3. Middle columns depending on stage
        if (isAll) {
            val hTitles = listOf("سعي ١", "فصل ١", "سعي ٢", "فصل ٢", "المعدل النهائي")
            hTitles.forEach { title ->
                drawTableCellText(canvas, title, curColRight - subColW, currentY + 5f, subColW, isBold = true)
                canvas.drawLine(curColRight - subColW, currentY, curColRight - subColW, currentY + headerHeight, thinBorderPaint)
                curColRight -= subColW
            }
        } else if (isC1Only) {
            listOf("علامة السعي", "علامة الفصل الاول", "المعدل النهائي").forEach { title ->
                drawTableCellText(canvas, title, curColRight - subColW, currentY + 5f, subColW, isBold = true)
                canvas.drawLine(curColRight - subColW, currentY, curColRight - subColW, currentY + headerHeight, thinBorderPaint)
                curColRight -= subColW
            }
        } else if (isC2Only) {
            listOf("علامة السعي", "علامة الفصل الثاني", "المعدل النهائي").forEach { title ->
                drawTableCellText(canvas, title, curColRight - subColW, currentY + 5f, subColW, isBold = true)
                canvas.drawLine(curColRight - subColW, currentY, curColRight - subColW, currentY + headerHeight, thinBorderPaint)
                curColRight -= subColW
            }
        } else {
            listOf("علامة السعي", "علامة الفصل الاول", "المعدل النهائي").forEach { title ->
                drawTableCellText(canvas, title, curColRight - subColW, currentY + 5f, subColW, isBold = true)
                canvas.drawLine(curColRight - subColW, currentY, curColRight - subColW, currentY + headerHeight, thinBorderPaint)
                curColRight -= subColW
            }
        }

        currentY += headerHeight

        // Course Rows
        val rowBgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }

        courses.forEachIndexed { index, course ->
            if (index % 2 == 1) {
                canvas.drawRect(tableX, currentY, tableX + tableWidth, currentY + rowHeight, rowBgPaint)
            }
            canvas.drawRect(tableX, currentY, tableX + tableWidth, currentY + rowHeight, thinBorderPaint)

            val courseMax = (course.coefficient * 20.0).coerceAtLeast(20.0)
            val c1 = row.c1RawGrades[course.id]
            val e1 = row.e1RawGrades[course.id]
            val c2 = row.c2RawGrades[course.id]
            val e2 = row.e2RawGrades[course.id]
            val finalGrade = if (isC1Only) c1 else if (isC2Only) c2 else row.courseGrades[course.id]

            fun fmt(v: Double?): String {
                if (v == null) return ""
                return if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v)
            }

            var colR = tableX + tableWidth
            // Name
            drawTableCellText(canvas, course.name, colR - colCourseW, currentY + 4f, colCourseW, align = Layout.Alignment.ALIGN_OPPOSITE)
            canvas.drawLine(colR - colCourseW, currentY, colR - colCourseW, currentY + rowHeight, thinBorderPaint)
            colR -= colCourseW

            // Max
            drawTableCellText(canvas, courseMax.toInt().toString(), colR - colMaxW, currentY + 4f, colMaxW)
            canvas.drawLine(colR - colMaxW, currentY, colR - colMaxW, currentY + rowHeight, thinBorderPaint)
            colR -= colMaxW

            // Sub columns
            if (isAll) {
                val vals = listOf(fmt(c1), fmt(e1), fmt(c2), fmt(e2), fmt(finalGrade))
                vals.forEachIndexed { vIdx, v ->
                    val isFinal = vIdx == vals.lastIndex
                    drawTableCellText(
                        canvas, v, colR - subColW, currentY + 4f, subColW,
                        isBold = isFinal,
                        textColor = if (isFinal) Color.parseColor("#1E3A8A") else Color.BLACK
                    )
                    canvas.drawLine(colR - subColW, currentY, colR - subColW, currentY + rowHeight, thinBorderPaint)
                    colR -= subColW
                }
            } else if (isC1Only) {
                val vals = listOf(fmt(c1), "", fmt(finalGrade))
                vals.forEachIndexed { vIdx, v ->
                    val isFinal = vIdx == vals.lastIndex
                    drawTableCellText(
                        canvas, v, colR - subColW, currentY + 4f, subColW,
                        isBold = isFinal,
                        textColor = if (isFinal) Color.parseColor("#1E3A8A") else Color.BLACK
                    )
                    canvas.drawLine(colR - subColW, currentY, colR - subColW, currentY + rowHeight, thinBorderPaint)
                    colR -= subColW
                }
            } else if (isC2Only) {
                val vals = listOf(fmt(c2), "", fmt(finalGrade))
                vals.forEachIndexed { vIdx, v ->
                    val isFinal = vIdx == vals.lastIndex
                    drawTableCellText(
                        canvas, v, colR - subColW, currentY + 4f, subColW,
                        isBold = isFinal,
                        textColor = if (isFinal) Color.parseColor("#1E3A8A") else Color.BLACK
                    )
                    canvas.drawLine(colR - subColW, currentY, colR - subColW, currentY + rowHeight, thinBorderPaint)
                    colR -= subColW
                }
            } else {
                val vals = listOf(fmt(c1), fmt(e1), fmt(finalGrade))
                vals.forEachIndexed { vIdx, v ->
                    val isFinal = vIdx == vals.lastIndex
                    drawTableCellText(
                        canvas, v, colR - subColW, currentY + 4f, subColW,
                        isBold = isFinal,
                        textColor = if (isFinal) Color.parseColor("#1E3A8A") else Color.BLACK
                    )
                    canvas.drawLine(colR - subColW, currentY, colR - subColW, currentY + rowHeight, thinBorderPaint)
                    colR -= subColW
                }
            }

            currentY += rowHeight
        }

        // Summary Rows
        val sumHeight = 20f

        // 1. المجموع
        val sumBgPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }
        canvas.drawRect(tableX, currentY, tableX + tableWidth, currentY + sumHeight, sumBgPaint)
        canvas.drawRect(tableX, currentY, tableX + tableWidth, currentY + sumHeight, thinBorderPaint)

        var sumColR = tableX + tableWidth
        drawTableCellText(canvas, "المجموع", sumColR - colCourseW, currentY + 4f, colCourseW, isBold = true)
        canvas.drawLine(sumColR - colCourseW, currentY, sumColR - colCourseW, currentY + sumHeight, thinBorderPaint)
        sumColR -= colCourseW

        drawTableCellText(canvas, row.maxPointsTotal.toInt().toString(), sumColR - colMaxW, currentY + 4f, colMaxW, isBold = true)
        canvas.drawLine(sumColR - colMaxW, currentY, sumColR - colMaxW, currentY + sumHeight, thinBorderPaint)
        sumColR -= colMaxW

        fun fmtSum(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v)
        val displayedFinalScore = if (isC1Only) row.c1WeightedTotal else if (isC2Only) row.c2WeightedTotal else row.finalScore

        if (isAll) {
            val sumVals = listOf(
                fmtSum(row.c1WeightedTotal),
                fmtSum(row.e1WeightedTotal),
                fmtSum(row.c2WeightedTotal),
                fmtSum(row.e2WeightedTotal),
                String.format("%.2f", displayedFinalScore)
            )
            sumVals.forEachIndexed { vIdx, v ->
                val isFinal = vIdx == sumVals.lastIndex
                drawTableCellText(
                    canvas, v, sumColR - subColW, currentY + 4f, subColW,
                    isBold = true,
                    textColor = if (isFinal) Color.parseColor("#DC2626") else Color.BLACK
                )
                canvas.drawLine(sumColR - subColW, currentY, sumColR - subColW, currentY + sumHeight, thinBorderPaint)
                sumColR -= subColW
            }
        } else {
            val sumVals = listOf(
                fmtSum(row.c1WeightedTotal),
                if (isC1Only) "" else fmtSum(row.e1WeightedTotal),
                String.format("%.2f", displayedFinalScore)
            )
            sumVals.forEachIndexed { vIdx, v ->
                val isFinal = vIdx == sumVals.lastIndex
                drawTableCellText(
                    canvas, v, sumColR - subColW, currentY + 4f, subColW,
                    isBold = true,
                    textColor = if (isFinal) Color.parseColor("#DC2626") else Color.BLACK
                )
                canvas.drawLine(sumColR - subColW, currentY, sumColR - subColW, currentY + sumHeight, thinBorderPaint)
                sumColR -= subColW
            }
        }

        currentY += sumHeight

        // 2. المعدل العام 20/
        canvas.drawRect(tableX, currentY, tableX + tableWidth, currentY + sumHeight, thinBorderPaint)

        var avgColR = tableX + tableWidth
        drawTableCellText(canvas, "المعدل العام 20/", avgColR - colCourseW, currentY + 4f, colCourseW, isBold = true)
        canvas.drawLine(avgColR - colCourseW, currentY, avgColR - colCourseW, currentY + sumHeight, thinBorderPaint)
        avgColR -= colCourseW

        drawTableCellText(canvas, "معدل النجاح: 10 \\ 20", avgColR - colMaxW, currentY + 5f, colMaxW, textSize = 7.5f)
        canvas.drawLine(avgColR - colMaxW, currentY, avgColR - colMaxW, currentY + sumHeight, thinBorderPaint)
        avgColR -= colMaxW

        fun fmtAvg(v: Double): String = String.format("%.2f", (v / row.maxPointsTotal.coerceAtLeast(1.0)) * 20.0)
        val finalAvg = if (isC1Only) {
            if (row.maxPointsTotal > 0) (row.c1WeightedTotal / row.maxPointsTotal) * 20.0 else 0.0
        } else if (isC2Only) {
            if (row.maxPointsTotal > 0) (row.c2WeightedTotal / row.maxPointsTotal) * 20.0 else 0.0
        } else {
            row.generalAverage20
        }
        val isCardPassed = finalAvg >= 10.0

        if (isAll) {
            val avgVals = listOf(
                fmtAvg(row.c1WeightedTotal),
                fmtAvg(row.e1WeightedTotal),
                fmtAvg(row.c2WeightedTotal),
                fmtAvg(row.e2WeightedTotal),
                String.format("%.2f", finalAvg)
            )
            avgVals.forEachIndexed { vIdx, v ->
                val isFinal = vIdx == avgVals.lastIndex
                drawTableCellText(
                    canvas, v, avgColR - subColW, currentY + 4f, subColW,
                    isBold = isFinal,
                    textColor = if (isFinal) (if (isCardPassed) Color.parseColor("#059669") else Color.parseColor("#DC2626")) else Color.BLACK
                )
                canvas.drawLine(avgColR - subColW, currentY, avgColR - subColW, currentY + sumHeight, thinBorderPaint)
                avgColR -= subColW
            }
        } else {
            val avgVals = listOf(
                fmtAvg(row.c1WeightedTotal),
                if (isC1Only) "" else fmtAvg(row.e1WeightedTotal),
                String.format("%.2f", finalAvg)
            )
            avgVals.forEachIndexed { vIdx, v ->
                val isFinal = vIdx == avgVals.lastIndex
                drawTableCellText(
                    canvas, v, avgColR - subColW, currentY + 4f, subColW,
                    isBold = isFinal,
                    textColor = if (isFinal) (if (isCardPassed) Color.parseColor("#059669") else Color.parseColor("#DC2626")) else Color.BLACK
                )
                canvas.drawLine(avgColR - subColW, currentY, avgColR - subColW, currentY + sumHeight, thinBorderPaint)
                avgColR -= subColW
            }
        }

        currentY += sumHeight

        // 3. النتيجة (ناجح / راسب)
        val passBgColor = if (isCardPassed) Color.parseColor("#F0FDF4") else Color.parseColor("#FEF2F2")
        val passBgPaint = Paint().apply {
            color = passBgColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(tableX, currentY, tableX + tableWidth, currentY + sumHeight, passBgPaint)
        canvas.drawRect(tableX, currentY, tableX + tableWidth, currentY + sumHeight, thinBorderPaint)

        var resColR = tableX + tableWidth
        drawTableCellText(canvas, "النتيجة", resColR - colCourseW, currentY + 4f, colCourseW, isBold = true)
        canvas.drawLine(resColR - colCourseW, currentY, resColR - colCourseW, currentY + sumHeight, thinBorderPaint)
        resColR -= colCourseW

        canvas.drawLine(resColR - colMaxW, currentY, resColR - colMaxW, currentY + sumHeight, thinBorderPaint)
        resColR -= colMaxW

        val passText = if (isCardPassed) "ناجح" else "راسب"
        val passColor = if (isCardPassed) Color.parseColor("#059669") else Color.parseColor("#DC2626")

        if (isAll) {
            val resVals = listOf("", "", "", "", passText)
            resVals.forEachIndexed { _, v ->
                drawTableCellText(canvas, v, resColR - subColW, currentY + 4f, subColW, isBold = true, textColor = passColor)
                canvas.drawLine(resColR - subColW, currentY, resColR - subColW, currentY + sumHeight, thinBorderPaint)
                resColR -= subColW
            }
        } else {
            val resVals = listOf("", "", passText)
            resVals.forEachIndexed { _, v ->
                drawTableCellText(canvas, v, resColR - subColW, currentY + 4f, subColW, isBold = true, textColor = passColor)
                canvas.drawLine(resColR - subColW, currentY, resColR - subColW, currentY + sumHeight, thinBorderPaint)
                resColR -= subColW
            }
        }

        currentY += sumHeight

        // 4. عدد الطلاب والمرتبة
        canvas.drawRect(tableX, currentY, tableX + tableWidth, currentY + sumHeight, thinBorderPaint)
        val rankColCourseW = tableWidth * 0.50f
        val rankColRemainW = tableWidth * 0.50f

        var rnkR = tableX + tableWidth
        drawTableCellText(canvas, "عدد الطلاب: ${row.classStudentCount}", rnkR - rankColCourseW, currentY + 4f, rankColCourseW, isBold = true)
        canvas.drawLine(rnkR - rankColCourseW, currentY, rnkR - rankColCourseW, currentY + sumHeight, thinBorderPaint)
        rnkR -= rankColCourseW

        drawTableCellText(
            canvas, "المرتبة: ${row.rank}", rnkR - rankColRemainW, currentY + 4f, rankColRemainW,
            isBold = true,
            textColor = Color.parseColor("#1E3A8A")
        )

        currentY += sumHeight + 24f

        // ================= 4. SIGNATURES FOOTER =================
        val sigColW = (contentWidth - 32f) / 3f
        val sigY = currentY

        // 1. رئيس الدروس النظرية
        drawArabicText(
            canvas = canvas,
            text = "رئيس الدروس النظرية",
            x = margin + 16f,
            y = sigY,
            width = sigColW.toInt(),
            textSize = 9.5f,
            textColor = Color.BLACK,
            isBold = true,
            alignment = Layout.Alignment.ALIGN_CENTER
        )

        // 2. رئيس الدروس التطبيقية
        drawArabicText(
            canvas = canvas,
            text = "رئيس الدروس التطبيقية",
            x = margin + 16f + sigColW,
            y = sigY,
            width = sigColW.toInt(),
            textSize = 9.5f,
            textColor = Color.BLACK,
            isBold = true,
            alignment = Layout.Alignment.ALIGN_CENTER
        )

        // 3. اسم المدير
        drawArabicText(
            canvas = canvas,
            text = "اسم المدير:",
            x = margin + 16f + (sigColW * 2),
            y = sigY,
            width = sigColW.toInt(),
            textSize = 9.5f,
            textColor = Color.BLACK,
            isBold = true,
            alignment = Layout.Alignment.ALIGN_CENTER
        )
        drawArabicText(
            canvas = canvas,
            text = "التوقيع والختم الرسمي",
            x = margin + 16f + (sigColW * 2),
            y = sigY + 14f,
            width = sigColW.toInt(),
            textSize = 8.5f,
            textColor = Color.parseColor("#64748B"),
            alignment = Layout.Alignment.ALIGN_CENTER
        )

        // Bottom Watermark / Verification notice
        val footerY = pageHeight - margin - 18f
        drawArabicText(
            canvas = canvas,
            text = "نظام الإدارة المدرسية الموحد EMIS — وثيقة بطاقة علامات رسمية صادرة إلكترونياً",
            x = margin + 16f,
            y = footerY,
            width = (contentWidth - 32f).toInt(),
            textSize = 8f,
            textColor = Color.parseColor("#94A3B8"),
            alignment = Layout.Alignment.ALIGN_CENTER
        )

        document.finishPage(page)

        // Save PDF to cache dir
        val cleanName = row.student.name.replace(" ", "_").replace(Regex("[^a-zA-Z0-9_\\u0600-\\u06FF]"), "")
        val fileName = "Report_${cleanName}_${System.currentTimeMillis()}.pdf"
        val outputFile = File(context.cacheDir, fileName)

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return outputFile
    }

    private fun saveToPublicDownloads(context: Context, pdfFile: File, studentName: String) {
        try {
            val cleanName = studentName.replace(" ", "_").replace(Regex("[^a-zA-Z0-9_\\u0600-\\u06FF]"), "")
            val fileName = "بطاقة_علامات_${cleanName}.pdf"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        pdfFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val destFile = File(downloadsDir, fileName)
                    pdfFile.copyTo(destFile, overwrite = true)
                }
            }
        } catch (_: Exception) {}
    }

    private fun drawTableCellText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        textSize: Float = 8.5f,
        textColor: Int = Color.BLACK,
        isBold: Boolean = false,
        align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER
    ) {
        val safeW = width.toInt().coerceAtLeast(8)
        val pad = if (align == Layout.Alignment.ALIGN_OPPOSITE) 4f else 2f
        drawArabicText(
            canvas = canvas,
            text = text,
            x = x + pad,
            y = y,
            width = (safeW - (pad * 2)).toInt().coerceAtLeast(4),
            textSize = textSize,
            textColor = textColor,
            isBold = isBold,
            alignment = align
        )
    }

    private fun drawArabicText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Int,
        textSize: Float,
        textColor: Int = Color.BLACK,
        isBold: Boolean = false,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Int {
        val textPaint = TextPaint().apply {
            isAntiAlias = true
            color = textColor
            this.textSize = textSize
            typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        val safeWidth = width.coerceAtLeast(8)
        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, safeWidth)
                .setAlignment(alignment)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, textPaint, safeWidth, alignment, 1.0f, 0.0f, false)
        }
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }
}
