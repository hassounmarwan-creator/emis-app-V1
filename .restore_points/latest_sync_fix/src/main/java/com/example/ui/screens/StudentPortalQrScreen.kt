package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.components.EmisTopAppBar
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.util.QrCodeGenerator
import com.example.viewmodel.AppScreen
import com.example.viewmodel.EmisViewModel
import java.io.File
import java.io.FileOutputStream

@Composable
fun StudentPortalQrScreen(viewModel: EmisViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val webAppUrl by viewModel.webAppUrl.collectAsState()
    val sheetId by viewModel.sheetId.collectAsState()
    val schoolName by viewModel.schoolName.collectAsState()
    val isRefreshingQr by viewModel.isRefreshingQrSettings.collectAsState()

    val currentUrl = remember(webAppUrl, sheetId) {
        viewModel.getStudentPortalUrl()
    }

    var showHelpDialog by remember { mutableStateOf(false) }

    val qrBitmap = remember(currentUrl) {
        QrCodeGenerator.generateQrImageBitmapWithAppLogo(context, currentUrl, size = 600)
    }

    // Auto-fetch settings from sheet on screen entry to keep QR up-to-date with cell C3
    LaunchedEffect(Unit) {
        viewModel.refreshStudentPortalSettings()
    }

    Scaffold(
        topBar = {
            EmisTopAppBar(
                title = "بوابة نتائج الطلاب (QR Code)",
                subtitle = "استعلام مباشر مرتبط بـ Google Sheets",
                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // QR Code Card (Image 2)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qr_code_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "رمز QR لبوابة نتائج الطلاب",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmisBluePrimary
                            )
                            Text(
                                text = "فتح تصميم صفحة الويب المخصصة لنتائج الطلاب",
                                fontSize = 13.sp,
                                color = Color(0xFF0284C7),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable { viewModel.navigateTo(AppScreen.STUDENT_WEB_PORTAL) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    viewModel.refreshStudentPortalSettings { success ->
                                        if (success) {
                                            Toast.makeText(context, "تم تحديث رمز QR بنجاح من الخلية C3!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "تعذر التحديث من الخلية C3. يرجى التحقق من الاتصال بالإنترنت.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isRefreshingQr,
                                modifier = Modifier.testTag("refresh_qr_icon_button")
                            ) {
                                if (isRefreshingQr) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = EmisBluePrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "تحديث رمز QR",
                                        tint = EmisBluePrimary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showHelpDialog = true },
                                modifier = Modifier.testTag("help_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = "مساعدة",
                                    tint = EmisBluePrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons: Refresh QR from cell C3 & Print/Share QR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.refreshStudentPortalSettings { success ->
                                    if (success) {
                                        Toast.makeText(context, "تم تحديث رمز QR بنجاح من الخلية C3!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "تعذر التحديث من الخلية C3. يرجى التأكد من وضع الرابط في الخلية C3.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isRefreshingQr,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmisBluePrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("refresh_qr_button")
                        ) {
                            if (isRefreshingQr) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جاري التحديث...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تحديث رمز QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val logo = QrCodeGenerator.getAppLogoBitmap(context)
                                val bmp = QrCodeGenerator.generateQrBitmap(currentUrl, size = 1000, logo = logo)
                                if (bmp != null) {
                                    shareOrPrintQrBitmap(context, bmp, schoolName)
                                } else {
                                    Toast.makeText(context, "رمز QR غير متوفر حالياً", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("print_qr_button")
                        ) {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = EmisBluePrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "طباعة / مشاركة",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmisBluePrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // QR Code Display Box
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "QR Code لنتائج الطلاب",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("qr_code_image")
                            )
                        } else {
                            CircularProgressIndicator(color = EmisBluePrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Direct Access Banner (How to prevent Google Account prompt)
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF3B82F6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "كيف تتخلص من طلب حساب Google عند مسح الرمز QR؟",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E3A8A)
                                )
                            }
                            Text(
                                text = "إذا طلب الهاتف اختيار حساب Google عند مسح الرمز، فهذا يعني أن رابط النشر في Google Apps Script لم يُضبط على خيار «Anyone».\n\n" +
                                        "⚡ لفتحه مباشرة لكل الهواتف دون أي حساب:\n" +
                                        "1. في Google Sheets: ادخل على Extensions ➜ Apps Script.\n" +
                                        "2. اضغط Deploy (نشر) ➜ New deployment (نشر جديد).\n" +
                                        "3. اختر Web app واضبط الخيارين:\n" +
                                        "    • Execute as (تنفيذ كـ): Me (حسابي)\n" +
                                        "    • Who has access (من يمكنه الوصول): Anyone (الجميع)\n" +
                                        "4. انسخ الرابط وضعه في ورقة «الإعدادات» بالخلية C3.\n" +
                                        "✓ خيار Anyone يجعل الصفحة تفتح فوراً لكافة الطلاب دون طلب أي حساب Google إطلاقاً!",
                                fontSize = 12.sp,
                                color = Color(0xFF1E293B),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text("دليل بوابة نتائج الطلاب (QR Code)", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• يتم جلب رابط البوابة تلقائياً من الخلية C3 بورقة «الإعدادات».")
                    Text("• اضغط على زر 'تحديث رمز QR' في أي وقت لإعادة جلب الرابط بعد تعديل الخلية C3.")
                    Text("• يمكن للطلاب مسح رمز QR مباشرة بكاميرا أي هاتف محمول دون الحاجة لحساب Google.")
                    Text("• يطلب الموقع: رقم الهاتف وتاريخ الميلاد.")
                    Text("• يفحص الموقع تاريخ الخلية A1 لكل امتحان:")
                    Text("  - إذا مر التاريخ: يعتبر الامتحان مقفلاً وتعرض نتائج الطالب كاملة.")
                    Text("  - إذا لم يمر التاريخ: يعرض الموقع رسالة تفيد بأن الامتحان غير مقفل بعد وموعد إعلانه.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("حسناً")
                }
            }
        )
    }
}

private fun shareOrPrintQrBitmap(context: Context, bitmap: Bitmap, schoolName: String) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "school_portal_qr.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "رمز QR لبوابة نتائج الطلاب - $schoolName")
            putExtra(Intent.EXTRA_TEXT, "رمز QR المعتمد لبوابة نتائج واستعلام الطلاب ($schoolName)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة أو طباعة رمز QR"))
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر مشاركة الرمز: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
