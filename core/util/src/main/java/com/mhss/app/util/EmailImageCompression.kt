package com.mhss.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

private const val MAX_EMAIL_IMAGE_DIMENSION = 1600
private const val EMAIL_JPEG_QUALITY = 82

internal fun prepareCompressedEmailImage(
    context: Context,
    uri: Uri,
    contentType: String
): File? {
    val outputFormat = when (contentType) {
        "image/jpeg" -> Bitmap.CompressFormat.JPEG
        "image/png" -> Bitmap.CompressFormat.PNG
        else -> return null
    }
    val extension = if (outputFormat == Bitmap.CompressFormat.JPEG) ".jpg" else ".png"
    var outputFile: File? = null
    var decoded: Bitmap? = null
    var oriented: Bitmap? = null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = orientationMatrix(orientation)
        val bitmap = if (matrix == null) decoded!! else {
            Bitmap.createBitmap(decoded!!, 0, 0, decoded!!.width, decoded!!.height, matrix, true)
                .also { if (it !== decoded) oriented = it }
        }
        outputFile = File.createTempFile("email-image-", extension, context.cacheDir)
        val compressed = outputFile!!.outputStream().buffered().use { output ->
            bitmap.compress(outputFormat, EMAIL_JPEG_QUALITY, output)
        }
        val sourceSize = context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) {
                    cursor.getLong(index).takeIf { it > 0L }
                } else {
                    null
                }
            } else {
                null
            }
        }
        if (compressed && (sourceSize == null || outputFile!!.length() < sourceSize)) {
            outputFile
        } else {
            outputFile!!.delete()
            null
        }
    } catch (_: Exception) {
        outputFile?.delete()
        null
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
