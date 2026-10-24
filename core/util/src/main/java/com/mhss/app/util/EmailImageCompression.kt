package com.mhss.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

private const val MAX_EMAIL_IMAGE_DIMENSION = 1600
private const val EMAIL_JPEG_QUALITY = 82

internal class CleanImage(val file: File, val contentType: String) {
    val extension: String get() = if (contentType == "image/png") "png" else "jpg"
}

/**
 * Avkodar bilden och kodar om den till en ny fil. Ingen EXIF, GPS, kameramodell, tidsstämplar eller
 * andra metadata följer med eftersom bara pixlarna skrivs. Rotation appliceras på pixlarna först.
 * Kastar [EmailAttachmentException] om bilden inte kan avkodas, så originalet skickas aldrig.
 */
internal fun stripImageMetadata(openInput: () -> InputStream?, cacheDir: File): CleanImage {
    var outputFile: File? = null
    var decoded: Bitmap? = null
    var oriented: Bitmap? = null
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInput()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw EmailAttachmentException(EmailAttachmentLimit.UNSUPPORTED_IMAGE)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        }
        decoded = openInput()?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw EmailAttachmentException(EmailAttachmentLimit.UNSUPPORTED_IMAGE)

        val orientation = try {
            openInput()?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = orientationMatrix(orientation)
        val bitmap = if (matrix == null) decoded!! else {
            Bitmap.createBitmap(decoded!!, 0, 0, decoded!!.width, decoded!!.height, matrix, true)
                .also { if (it !== decoded) oriented = it }
        }
        // PNG behåller transparens; allt annat blir JPEG
        val usePng = bounds.outMimeType == "image/png" && bitmap.hasAlpha()
        val format = if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val clean = CleanImage(
            File.createTempFile("clean-image-", if (usePng) ".png" else ".jpg", cacheDir)
                .also { outputFile = it },
            if (usePng) "image/png" else "image/jpeg"
        )
        val ok = clean.file.outputStream().buffered().use { bitmap.compress(format, EMAIL_JPEG_QUALITY, it) }
        if (!ok) throw EmailAttachmentException(EmailAttachmentLimit.UNSUPPORTED_IMAGE)
        return clean
    } catch (e: Exception) {
        outputFile?.delete()
        throw e
    } finally {
        oriented?.recycle()
        decoded?.recycle()
    }
}

private fun calculateSampleSize(width: Int, height: Int): Int {
    var sampleSize = 1
    while (maxOf(width, height) / sampleSize > MAX_EMAIL_IMAGE_DIMENSION) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun orientationMatrix(orientation: Int): Matrix? {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return null
    }
    return matrix
}
