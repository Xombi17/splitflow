package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.DrawableRes
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Zero-permission app-private storage for receipt photo thumbnails and full-res captures.
 * Stores compressed JPEGs inside `context.filesDir/receipts/` so no broad media permissions
 * are ever required (100% Google Play Policy compliant).
 */
object ReceiptPhotoStore {

    private const val DIR_NAME = "receipts"

    private fun receiptsDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun resolveFile(context: Context, relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null
        val file = File(context.applicationContext.filesDir, relativePath)
        return if (file.exists() && file.isFile) file else null
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, preferredFileName: String? = null): String? {
        return runCatching {
            val scaled = scaleDown(bitmap, maxDim = 1280)
            val fileName = preferredFileName?.takeIf { it.isNotBlank() }
                ?: "rcpt_${UUID.randomUUID().toString().take(10)}.jpg"
            val target = File(receiptsDir(context), fileName)
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            "$DIR_NAME/$fileName"
        }.getOrNull()
    }

    fun saveFromUri(context: Context, uri: Uri): String? {
        val bitmap = decodeUri(context, uri) ?: return null
        return saveBitmap(context, bitmap)
    }

    fun ensureSampleDrawableSaved(
        context: Context,
        @DrawableRes drawableResId: Int,
        fileName: String = "rcpt_sample_thalassa.jpg"
    ): String? {
        val existing = File(receiptsDir(context), fileName)
        if (existing.exists() && existing.length() > 0L) {
            return "$DIR_NAME/$fileName"
        }
        val bmp = BitmapFactory.decodeResource(context.resources, drawableResId) ?: return null
        return saveBitmap(context, bmp, preferredFileName = fileName)
    }

    fun decodeUri(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val src = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        }.getOrNull()
    }

    private fun scaleDown(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(
            source,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}

/**
 * Persists the user's personal uploaded UPI QR code image (e.g. GPay / PhonePe / Paytm QR)
 * inside app-private storage with zero storage permissions required.
 * Works identically in both Guest Mode and Google-signed-in sessions.
 */
object UpiQrStore {
    private const val PREFS_NAME = "splitflow_upi_qr_prefs"
    private const val KEY_CUSTOM_UPI_QR_PATH = "custom_upi_qr_path"

    fun getCustomUpiQrPath(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_UPI_QR_PATH, null) ?: return null
        return if (ReceiptPhotoStore.resolveFile(context, saved) != null) saved else null
    }

    fun saveCustomUpiQr(context: Context, uri: Uri): String? {
        val appContext = context.applicationContext
        val previousPath = getCustomUpiQrPath(appContext)
        val bitmap = ReceiptPhotoStore.decodeUri(appContext, uri) ?: return null
        val uniqueName = "upi_qr_${UUID.randomUUID().toString().take(8)}.jpg"
        val savedPath = ReceiptPhotoStore.saveBitmap(
            context = appContext,
            bitmap = bitmap,
            preferredFileName = uniqueName
        ) ?: return null

        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_UPI_QR_PATH, savedPath)
            .apply()

        // Clean up previous QR image file so app-private storage never accumulates stale copies
        if (previousPath != null && previousPath != savedPath) {
            runCatching { ReceiptPhotoStore.resolveFile(appContext, previousPath)?.delete() }
        }
        return savedPath
    }

    fun clearCustomUpiQr(context: Context) {
        val appContext = context.applicationContext
        val previousPath = getCustomUpiQrPath(appContext)
        if (previousPath != null) {
            runCatching { ReceiptPhotoStore.resolveFile(appContext, previousPath)?.delete() }
        }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CUSTOM_UPI_QR_PATH)
            .apply()
    }
}

