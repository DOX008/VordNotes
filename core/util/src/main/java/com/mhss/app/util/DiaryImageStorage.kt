package com.mhss.app.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

const val MAX_DIARY_ENTRY_IMAGE_COUNT = 10

/** Lagrar dagboksbilder i appens privata filområde så de överlever att källan försvinner. */
object DiaryImageStorage {
    private const val DIRECTORY = "diary_images"

    /**
     * Kopierar (och komprimerar när möjligt) en vald bild och returnerar filens sökväg.
     * Kastar [EmailAttachmentException] om filen inte är en bild.
     */
    suspend fun importImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val displayName = getUriDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()
        val contentType = requireImageContentType(context.contentResolver.getType(uri), displayName)
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val compressed = prepareCompressedEmailImage(context, uri, contentType)
        val extension = when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> contentType.substringAfter('/').filter { it.isLetterOrDigit() }.ifEmpty { "img" }
        }
        val target = File(directory, "${UUID.randomUUID()}.$extension")
        if (compressed != null) {
            try {
                compressed.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            } finally {
                compressed.delete()
            }
        } else {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw java.io.FileNotFoundException("Unable to open image: $displayName")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
        }
        target.absolutePath
    }

    suspend fun delete(context: Context, paths: Collection<String>) = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, DIRECTORY).canonicalFile
        paths.forEach { path ->
            val file = File(path).canonicalFile
            if (file.parentFile == directory) file.delete()
        }
    }
}
