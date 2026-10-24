package com.mhss.app.database.remote

import android.util.Log
import com.mhss.app.database.entity.BookmarkEntity
import com.mhss.app.database.entity.NoteEntity
import com.mhss.app.database.entity.NoteFolderEntity
import com.mhss.app.database.entity.TaskEntity
import com.mhss.app.database.entity.DiaryEntryEntity
import com.mhss.app.database.entity.imageNames
import com.benasher44.uuid.Uuid
import com.mhss.app.domain.model.SubTask

private const val TAG = "SYNC"

private fun NoteEntity.toNoteMap(): HashMap<String, Any?> = hashMapOf(
    "title" to title,
    "content" to content,
    "createdDate" to createdDate,
    "updatedDate" to updatedDate,
    "pinned" to pinned,          // kan saknas i äldre data -> hanteras i DAO/Entity default
    "pins" to pins,              // kommaseparerade vård-pins, kan saknas i äldre data
    "folderId" to folderId,
    "orderIndex" to orderIndex,  // drag & drop-ordning
    "id" to id
)

private fun NoteFolderEntity.toFolderMap(): HashMap<String, Any?> = hashMapOf(
    "id" to id,
    "name" to name,
    "parentId" to parentId       // kan vara null / saknas för rotmappar
)

private fun BookmarkEntity.toBookmarkMap(): HashMap<String, Any?> = hashMapOf(
    "url" to url,
    "title" to title,
    "description" to description,
    "createdDate" to createdDate,
    "updatedDate" to updatedDate,
    "id" to id
)

private fun TaskEntity.toTaskMap(): HashMap<String, Any?> = hashMapOf(
    "title" to title,
    "description" to description,
    "isCompleted" to isCompleted,
    "priority" to priority,
    "createdDate" to createdDate,
    "updatedDate" to updatedDate,
    "lastFiredDate" to lastFiredDate,
    "subTasks" to subTasks.map { subTask ->
        hashMapOf(
            "id" to subTask.id.toString(),          // UUID som sträng – matchar hur vi läser
            "title" to subTask.title,
            "isCompleted" to subTask.isCompleted
        )
    },
    "dueDate" to dueDate,
    "recurring" to recurring,
    "frequency" to frequency,
    "frequencyAmount" to frequencyAmount,
    "id" to id
)

fun taskDocToEntity(data: Map<String, Any?>): TaskEntity {
    return TaskEntity(
        id = (data["id"] as? Number)?.toInt() ?: 0,
        title = data["title"] as? String ?: "",
        description = data["description"] as? String ?: "",
        isCompleted = data["isCompleted"] as? Boolean ?: false,
        priority = (data["priority"] as? Number)?.toInt() ?: 0,
        createdDate = (data["createdDate"] as? Number)?.toLong() ?: 0L,
        updatedDate = (data["updatedDate"] as? Number)?.toLong() ?: 0L,
        lastFiredDate = (data["lastFiredDate"] as? Number)?.toLong() ?: 0L,
        subTasks = (data["subTasks"] as? List<*>)?.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val idValue = map["id"] ?: return@mapNotNull null
            val uuid = when (idValue) {
                is String -> try { Uuid.fromString(idValue) } catch (e: Exception) {
                    Log.w(TAG, "taskDocToEntity: ogiltigt subtask-UUID: ${e.message}")
                    return@mapNotNull null
                }
                is Number -> Uuid(0L, idValue.toLong()) // fallback om någon gammal version har int-id
                is Map<*, *> -> {
                    val msb = (idValue["mostSigBits"] as? Number)?.toLong() ?: return@mapNotNull null
                    val lsb = (idValue["leastSigBits"] as? Number)?.toLong() ?: return@mapNotNull null
                    Uuid(msb, lsb)
                }
                else -> {
                    Log.w(TAG, "taskDocToEntity: okänd subtask-id-typ: ${idValue::class.simpleName}")
                    return@mapNotNull null
                }
            }
            SubTask(
                id = uuid,
                title = map["title"] as? String ?: "",
                isCompleted = map["isCompleted"] as? Boolean ?: false
            )
        } ?: emptyList(),
        dueDate = (data["dueDate"] as? Number)?.toLong() ?: 0L,
        recurring = data["recurring"] as? Boolean ?: false,
        frequency = (data["frequency"] as? Number)?.toInt() ?: 0,
        frequencyAmount = (data["frequencyAmount"] as? Number)?.toInt() ?: 0
    )
}

private fun DiaryEntryEntity.toDiaryMap(): HashMap<String, Any?> = hashMapOf(
    "title" to title,
    "content" to content,
    "createdDate" to createdDate,
    "updatedDate" to updatedDate,
    "mood" to mood.ordinal,
    "images" to imageNames(),
    "id" to id
)

// Publika wrappers så repositories kan använda dem utan att känna till Entity-typen internt.
// Loggar bara id (aldrig innehåll) för att inte läcka patientdata i logcat.
fun toNoteDoc(data: NoteEntity): HashMap<String, Any?> {
    Log.d(TAG, "toNoteDoc: id=${data.id}, folderId=${data.folderId}, orderIndex=${data.orderIndex}")
    return data.toNoteMap()
}

fun toFolderDoc(data: NoteFolderEntity): HashMap<String, Any?> {
    Log.d(TAG, "toFolderDoc: id=${data.id}, parentId=${data.parentId}")
    return data.toFolderMap()
}

fun toBookmarkDoc(data: BookmarkEntity): HashMap<String, Any?> {
    Log.d(TAG, "toBookmarkDoc: id=${data.id}")
    return data.toBookmarkMap()
}

fun toTaskDoc(data: TaskEntity): HashMap<String, Any?> {
    Log.d(TAG, "toTaskDoc: id=${data.id}")
    return data.toTaskMap()
}

fun toDiaryDoc(data: DiaryEntryEntity): HashMap<String, Any?> {
    Log.d(TAG, "toDiaryDoc: id=${data.id}")
    return data.toDiaryMap()
}