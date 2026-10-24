package com.mhss.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Synkar dagboksbilder mellan enheter via Firestore:
 * users/{uid}/diary/{entryId}/images/{filnamn} med bilddata som Blob.
 */
object DiaryImageSync {
    private const val TAG = "SYNC"
    private const val DIRECTORY = "diary_images"
    private const val MAX_BYTES = 700_000

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun imagesCollection(firestore: FirebaseFirestore, uid: String, entryId: Int) =
        UserDataPaths.diaryCollection(firestore, uid).document(entryId.toString()).collection("images")

    fun imageDirectory(context: Context): File = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun readBytes(file: File): ByteArray? {
        val raw = file.readBytes()
        if (raw.size <= MAX_BYTES) return raw
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var longSide = 1600
        while (longSide >= 400) {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longSide) sample *= 2
            val bitmap = BitmapFactory.decodeFile(
                file.path, BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            bitmap.recycle()
            if (out.size() <= MAX_BYTES) return out.toByteArray()
            longSide = (longSide * 0.75).toInt()
        }
        return null
    }

    /** Laddar upp bilder som inte redan finns i molnet. Fel loggas men kastas inte. */
    suspend fun upload(firestore: FirebaseFirestore, uid: String, entryId: Int, paths: Collection<String>) {
        if (entryId == 0) return
        withContext(Dispatchers.IO) {
            paths.map(::File).filter { it.isFile }.forEach { file ->
                try {
                    val doc = imagesCollection(firestore, uid, entryId).document(file.name)
                    val exists = try { doc.get().awaitResult().exists() } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                    if (exists) return@forEach
                    val bytes = readBytes(file) ?: return@forEach
                    doc.set(
                        hashMapOf(
                            "name" to file.name,
                            "data" to Blob.fromBytes(bytes),
                            "updatedDate" to System.currentTimeMillis()
                        )
                    ).awaitResult()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "diary image upload failed (${e.javaClass.simpleName})")
                }
            }
        }
    }

    suspend fun delete(firestore: FirebaseFirestore, uid: String, entryId: Int, names: Collection<String>) {
        if (entryId == 0) return
        withContext(Dispatchers.IO) {
            names.forEach { name ->
                try {
                    imagesCollection(firestore, uid, entryId).document(name).delete().awaitResult()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "diary image delete failed (${e.javaClass.simpleName})")
                }
            }
        }
    }

    /** Hämtar saknade bilder till enheten och returnerar lokala sökvägar för de som finns. */
    suspend fun download(
        context: Context,
        firestore: FirebaseFirestore,
        uid: String,
        entryId: Int,
        names: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        val directory = imageDirectory(context)
        names.mapNotNull { rawName ->
            val name = File(rawName).name
            val target = File(directory, name)
            if (target.isFile) return@mapNotNull target.absolutePath
            try {
                val data = imagesCollection(firestore, uid, entryId).document(name).get().awaitResult()
                    .getBlob("data")?.toBytes() ?: return@mapNotNull null
                target.writeBytes(data)
                target.absolutePath
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "diary image download failed (${e.javaClass.simpleName})")
                null
            }
        }
    }
}
