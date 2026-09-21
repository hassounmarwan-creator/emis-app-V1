package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.example.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeGenerator {

    /**
     * Retrieves the application icon as a Bitmap to embed in the center of QR codes.
     */
    fun getAppLogoBitmap(context: Context, targetSize: Int = 140): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                ?: context.packageManager.getApplicationIcon(context.packageName)
            val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, targetSize, targetSize)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates an authentic, standard QR Code Android Bitmap using ZXing with optional logo in the center.
     * Can be scanned by any smartphone camera or QR scanner.
     */
    fun generateQrBitmap(content: String, size: Int = 512, logo: Bitmap? = null): Bitmap? {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(trimmed, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            // If a logo is provided, draw it in the center over a rounded badge with full color
            if (logo != null) {
                val canvas = Canvas(bitmap)
                val badgeSize = (size * 0.28f).toInt()
                val left = (size - badgeSize) / 2f
                val top = (size - badgeSize) / 2f
                val badgeRect = RectF(left, top, left + badgeSize, top + badgeSize)
                val cornerRadius = badgeSize * 0.22f

                // Outer crisp white rim for contrast against dark QR modules
                val rimWidth = (size * 0.012f).coerceAtLeast(3f)
                val rimRect = RectF(left - rimWidth, top - rimWidth, left + badgeSize + rimWidth, top + badgeSize + rimWidth)
                val rimCorner = cornerRadius + rimWidth
                val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(rimRect, rimCorner, rimCorner, rimPaint)

                // Draw full-color logo into the badge with rounded corners matching the uploaded design
                val scaledLogo = Bitmap.createScaledBitmap(logo, badgeSize, badgeSize, true)
                val roundedLogoBitmap = Bitmap.createBitmap(badgeSize, badgeSize, Bitmap.Config.ARGB_8888)
                val roundCanvas = Canvas(roundedLogoBitmap)
                val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.BLACK
                }
                roundCanvas.drawRoundRect(RectF(0f, 0f, badgeSize.toFloat(), badgeSize.toFloat()), cornerRadius, cornerRadius, clipPaint)
                clipPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
                roundCanvas.drawBitmap(scaledLogo, 0f, 0f, clipPaint)

                canvas.drawBitmap(roundedLogoBitmap, left, top, null)

                // Subtle outer dark border around badge
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#014F86")
                    style = Paint.Style.STROKE
                    strokeWidth = (size * 0.005f).coerceAtLeast(1.5f)
                }
                canvas.drawRoundRect(badgeRect, cornerRadius, cornerRadius, borderPaint)
            }

            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a Compose ImageBitmap for direct rendering in Canvas or Image composables.
     */
    fun generateQrImageBitmap(content: String, size: Int = 512, logo: Bitmap? = null): ImageBitmap? {
        return generateQrBitmap(content, size, logo)?.asImageBitmap()
    }

    /**
     * Convenience method to generate QR with app logo directly given a Context.
     */
    fun generateQrImageBitmapWithAppLogo(context: Context, content: String, size: Int = 512): ImageBitmap? {
        val logo = getAppLogoBitmap(context)
        return generateQrImageBitmap(content, size, logo)
    }
}
