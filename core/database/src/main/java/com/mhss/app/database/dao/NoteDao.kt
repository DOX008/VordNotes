package com.mhss.app.database.dao

import androidx.room.*
import com.mhss.app.database.entity.NoteEntity
import com.mhss.app.database.entity.NoteFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // =========================
    // Notes (utan folder)
    // =========================
    @Query("""
        SELECT *
        FROM notes
        WHERE folder_id IS NULL
        ORDER BY order_index ASC
    """)
    fun getAllFolderlessNotes(): Flow<List<NoteEntity>>

    // =========================
    // Notes (alla)
    // =========================
    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: Int): NoteEntity

    // =========================
    // Sök notes
    // =========================
    @Query("""
        SELECT *
        FROM notes
        WHERE title LIKE '%' || :query || '%'
    """)
    suspend fun getNotesByTitle(query: String): List<NoteEntity>

    // =========================
    // Notes per folder (KRITISK)
    // =========================
    @Query("""
        SELECT *
        FROM notes
        WHERE folder_id = :folderId
        ORDER BY order_index ASC
    """)
    fun getNotesByFolder(folderId: Int): Flow<List<NoteEntity>>

    // =========================
    // CRUD Notes
    // =========================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    // =========================
    // Folders
    // =========================
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteFolder(folder: NoteFolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteFolders(
        folders: List<NoteFolderEntity>
    ): List<Long>

    @Update
    suspend fun updateNoteFolder(folder: NoteFolderEntity)

    @Delete
    suspend fun deleteNoteFolder(folder: NoteFolderEntity)

    @Query("SELECT * FROM note_folders")
    fun getAllNoteFolders(): Flow<List<NoteFolderEntity>>

    @Query("DELETE FROM note_folders")
    suspend fun deleteAllNoteFolders()

    @Query("SELECT * FROM note_folders WHERE id = :folderId")
    fun getNoteFolder(folderId: Int): NoteFolderEntity?
}
