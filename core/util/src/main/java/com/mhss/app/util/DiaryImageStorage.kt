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
        requireImageContentType(context.contentResolver.getType(uri), displayName)
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val clean = stripImageMetadata({ context.contentResolver.openInputStream(uri) }, context.cacheDir)
        val target = File(directory, "${UUID.randomUUID()}.${clean.extension}")
        try {
            clean.file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            clean.file.delete()
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
