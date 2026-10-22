package com.mhss.app.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Debug logger mirroring Logcat output to VordNotes_auth_log.txt in Downloads.
 * Always logs to Logcat; file writing fails safely (never throws).
 */
object FileLogger {
    const val FILE_NAME = "VordNotes_auth_log.txt"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var fileEnabled = false

    private val executor = Executors.newSingleThreadExecutor()

    fun init(context: Context, enableFileLogging: Boolean) {
        appContext = context.applicationContext
        fileEnabled = enableFileLogging
    }

    fun d(tag: String, msg: String) = log('D', tag, msg)
    fun w(tag: String, msg: String) = log('W', tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) = log('E', tag, msg, t)

    fun log(level: Char, tag: String, msg: String, t: Throwable? = null) {
        when (level) {
            'W' -> Log.w(tag, msg, t)
            'E' -> Log.e(tag, msg, t)
            else -> Log.d(tag, msg)
        }
        if (!fileEnabled) return
        val context = appContext ?: return
        val line = buildString {
            append(timestamp()).append(' ').append(level).append('/').append(tag).append(' ').append(msg)
            if (t != null) append(" | ").append(t.javaClass.name).append(": ").append(t.localizedMessage)
            append('\n')
        }
        try {
            executor.execute {
                try {
                    append(context, line)
                } catch (ex: Throwable) {
                    Log.w(tag, "FileLogger: write failed: ${ex.javaClass.simpleName}")
                }
            }
        } catch (_: Throwable) {
        }
    }

    /** Returns a shareable content URI for the log file, or null if it does not exist. */
    fun getShareUri(context: Context): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findMediaStoreUri(context)
        } else {
            val file = legacyFile()
            if (file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else null
        }
    } catch (_: Throwable) {
        null
    }

    private fun timestamp(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    private fun append(context: Context, line: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val uri = findMediaStoreUri(context) ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            ) ?: return
            resolver.openOutputStream(uri, "wa")?.use { it.write(line.toByteArray()) }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            File(dir, FILE_NAME).appendText(line)
        }
    }

    private fun legacyFile() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        FILE_NAME
    )

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun findMediaStoreUri(context: Context): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf(FILE_NAME, "${Environment.DIRECTORY_DOWNLOADS}%"),
            null
        )?.use { c ->
            if (c.moveToFirst()) return ContentUris.withAppendedId(collection, c.getLong(0))
        }
        return null
    }
}
