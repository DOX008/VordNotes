package com.mhss.app.data

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.mhss.app.database.MyBrainDatabase
import com.mhss.app.database.UserDatabaseManager
import com.mhss.app.database.entity.BookmarkEntity
import com.mhss.app.database.entity.DiaryEntryEntity
import com.mhss.app.database.entity.NoteEntity
import com.mhss.app.database.entity.NoteFolderEntity
import com.mhss.app.database.entity.TaskEntity
import com.mhss.app.database.entity.withoutIds
import com.mhss.app.domain.repository.BackupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import com.mhss.app.util.CryptoManager

@Single
class BackupRepositoryImpl(
    private val context: Context,
    private val databaseManager: UserDatabaseManager,
    @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher
) : BackupRepository {

    private val database: MyBrainDatabase
        get() = databaseManager.databaseForCurrentUser()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun exportDatabase(
        directoryUri: String,
        exportNotes: Boolean,
        exportTasks: Boolean,
        exportDiary: Boolean,
        exportBookmarks: Boolean,
        encrypted: Boolean, // To be added in a future version
        password: String // To be added in a future version
    ): Boolean {
        return withContext(ioDispatcher) {
            try {
                val fileName = "Vord_Backup_${System.currentTimeMillis()}.json"
                val pickedDir = DocumentFile.fromTreeUri(context, directoryUri.toUri())
                    ?: return@withContext false

                val destination = pickedDir.createFile("application/json", fileName)
                    ?: return@withContext false

                val notes = if (exportNotes) database.noteDao().getAllNotes() else emptyList()
                val noteFolders = if (exportNotes) database.noteDao().getAllNoteFolders().first() else emptyList()
                val tasks = if (exportTasks) database.taskDao().getAllTasks().first() else emptyList()
                val diary = if (exportDiary) database.diaryDao().getAllEntries().first() else emptyList()
                val bookmarks =
                    if (exportBookmarks) database.bookmarkDao().getAllBookmarks().first() else emptyList()

                val backupData = BackupData(
                    notes = notes,
                    noteFolders = noteFolders,
                    tasks = tasks,
                    diary = diary,
                    bookmarks = bookmarks
                )

                val jsonBytes = Json.encodeToString(
                    BackupData.serializer(),
                    backupData
                ).toByteArray(Charsets.UTF_8)

                val finalBytes = if (encrypted && password.isNotBlank()) {
                    CryptoManager.encrypt(jsonBytes, password)
                } else {
                    jsonBytes
                }

                destination.uri?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(finalBytes)
                    } ?: return@withContext false
                } ?: return@withContext false

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun importDatabase(
        fileUri: String,
        encrypted: Boolean,
        password: String
    ): Boolean {
        return withContext(ioDispatcher) {
            try {
                val json = Json {
                    ignoreUnknownKeys = true
                }

                val bytes = context.contentResolver
                    .openInputStream(fileUri.toUri())
                    ?.readBytes()
                    ?: return@withContext false

                val jsonBytes = if (encrypted && password.isNotBlank()) {
                    CryptoManager.decrypt(bytes, password)
                } else {
                    bytes
                }

                val backupData = json.decodeFromString<BackupData>(
                    jsonBytes.toString(Charsets.UTF_8)
                )

                database.withTransaction {
                    val noteDao = database.noteDao()
                    val folders = backupData.noteFolders
                    // Pass 1: insert all folders as roots, collect old id -> new id
                    val newIds = noteDao.insertNoteFolders(
                        folders.map { it.copy(id = 0, parentId = null) }
                    )
                    val oldToNewFolderId = HashMap<Int, Int>()
                    folders.forEachIndexed { index, folder ->
                        val newId = newIds[index]
                        if (newId > 0L) oldToNewFolderId[folder.id] = newId.toInt()
                    }

                    // Pass 2: restore parent links (any depth) using the new ids
                    val oldParents = folders.associate { it.id to it.parentId }
                    folders.forEach { folder ->
                        val parentOld = folder.parentId ?: return@forEach
                        val newId = oldToNewFolderId[folder.id] ?: return@forEach
                        val newParent = oldToNewFolderId[parentOld]
                        when {
                            newParent == null -> Log.w(
                                TAG,
                                "Orphan parentId=$parentOld for folder '${folder.name}' (old id=${folder.id}); importing as root"
                            )
                            hasCycle(folder.id, oldParents) -> Log.w(
                                TAG,
                                "Cyclic parentId=$parentOld for folder '${folder.name}' (old id=${folder.id}); importing as root"
                            )
                            else -> noteDao.updateNoteFolder(
                                folder.copy(id = newId, parentId = newParent)
                            )
                        }
                    }

                    val notes = backupData.notes.map { note ->
                        val oldFolderId = note.folderId
                        val newFolderId = oldFolderId?.let { oldToNewFolderId[it] }
                        if (oldFolderId != null && newFolderId == null) {
                            Log.w(TAG, "Note '${note.title}' references missing folderId=$oldFolderId; importing without folder")
                        }
                        note.copy(folderId = newFolderId, id = 0)
                    }
                    database.noteDao().insertNotes(notes)
                    database.taskDao().insertTasks(backupData.tasks.withoutIds())
                    database.diaryDao().insertEntries(backupData.diary.withoutIds())
                    database.bookmarkDao().insertBookmarks(backupData.bookmarks.withoutIds())
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "importDatabase failed", e)
                false
            }
        }
    }

    private fun hasCycle(startId: Int, parents: Map<Int, Int?>): Boolean {
        val visited = HashSet<Int>()
        var current: Int? = startId
        while (current != null) {
            if (!visited.add(current)) return true
            current = parents[current]
        }
        return false
    }

    private companion object {
        const val TAG = "BACKUP"
    }

    @Serializable
    private data class BackupData(
        @SerialName("notes") val notes: List<NoteEntity> = emptyList(),
        @SerialName("noteFolders") val noteFolders: List<NoteFolderEntity> = emptyList(),
        @SerialName("tasks") val tasks: List<TaskEntity> = emptyList(),
        @SerialName("diary") val diary: List<DiaryEntryEntity> = emptyList(),
        @SerialName("bookmarks") val bookmarks: List<BookmarkEntity> = emptyList()
    )
}