package com.mhss.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mhss.app.database.dao.NoteDao
import com.mhss.app.database.entity.toNote
import com.mhss.app.database.entity.toNoteEntity
import com.mhss.app.database.entity.toNoteFolder
import com.mhss.app.database.entity.toNoteFolderEntity
import com.mhss.app.database.remote.toFolderDoc
import com.mhss.app.database.remote.toNoteDoc
import com.mhss.app.domain.model.Note
import com.mhss.app.domain.model.NoteFolder
import com.mhss.app.domain.repository.NoteRepository
import com.mhss.app.util.UserDataPaths
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Single
class NoteRepositoryImpl(
    private val noteDao: NoteDao,
    @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : NoteRepository {

    override fun getAllFolderlessNotes(): Flow<List<Note>> =
        noteDao.getAllFolderlessNotes()
            .flowOn(ioDispatcher)
            .map { notes -> notes.map { it.toNote() } }

    override suspend fun getNote(id: Int): Note =
        withContext(ioDispatcher) { noteDao.getNote(id).toNote() }

    override suspend fun searchNotes(query: String): List<Note> =
        withContext(ioDispatcher) { noteDao.getNotesByTitle(query).map { it.toNote() } }

    override fun getNotesByFolder(folderId: Int): Flow<List<Note>> =
        noteDao.getNotesByFolder(folderId)
            .flowOn(ioDispatcher)
            .map { notes -> notes.map { it.toNote() } }

    override suspend fun addNote(note: Note): Long {
        val uid = auth.currentUser?.uid
        return withContext(ioDispatcher) {
            val newId = noteDao.insertNote(note.toNoteEntity())
            if (uid != null && newId != 0L) {
                val entity = note.toNoteEntity().copy(id = newId.toInt())
                UserDataPaths.notesCollection(firestore, uid).document(entity.id.toString())
                    .set(toNoteDoc(entity))
            }
            newId
        }
    }

    override suspend fun updateNote(note: Note) {
        val uid = auth.currentUser?.uid
        withContext(ioDispatcher) {
            noteDao.updateNote(note.toNoteEntity())
            if (uid != null && note.id != 0) {
                val entity = note.toNoteEntity()
                UserDataPaths.notesCollection(firestore, uid).document(entity.id.toString())
                    .set(toNoteDoc(entity))
            }
        }
    }

    override suspend fun deleteNote(note: Note) {
        val uid = auth.currentUser?.uid
        withContext(ioDispatcher) {
            val entity = note.toNoteEntity()
            noteDao.deleteNote(entity)
            if (uid != null && entity.id != 0) {
                UserDataPaths.notesCollection(firestore, uid).document(entity.id.toString())
                    .delete()
            }
        }
    }

    override suspend fun insertNoteFolder(folder: NoteFolder) {
        val uid = auth.currentUser?.uid
        withContext(ioDispatcher) {
            val newId = noteDao.insertNoteFolder(folder.toNoteFolderEntity())
            if (uid != null && newId > 0L) {
                val entity = folder.toNoteFolderEntity().copy(id = newId.toInt())
                UserDataPaths.foldersCollection(firestore, uid).document(entity.id.toString())
                    .set(toFolderDoc(entity))
            }
        }
    }

    override suspend fun updateNoteFolder(folder: NoteFolder) {
        val uid = auth.currentUser?.uid
        withContext(ioDispatcher) {
            noteDao.updateNoteFolder(folder.toNoteFolderEntity())
            if (uid != null && folder.id != 0) {
                val entity = folder.toNoteFolderEntity()
                UserDataPaths.foldersCollection(firestore, uid).document(entity.id.toString())
                    .set(toFolderDoc(entity))
            }
        }
    }

    override suspend fun deleteNoteFolder(folder: NoteFolder) {
        val uid = auth.currentUser?.uid
        withContext(ioDispatcher) {
            val entity = folder.toNoteFolderEntity()
            noteDao.deleteNoteFolder(entity)
            if (uid != null && entity.id != 0) {
                UserDataPaths.foldersCollection(firestore, uid).document(entity.id.toString())
                    .delete()
            }
        }
    }

    override fun getAllNoteFolders(): Flow<List<NoteFolder>> =
        noteDao.getAllNoteFolders()
            .flowOn(ioDispatcher)
            .map { folders -> folders.map { it.toNoteFolder() } }

    override suspend fun getNoteFolder(folderId: Int): NoteFolder? =
        withContext(ioDispatcher) { noteDao.getNoteFolder(folderId)?.toNoteFolder() }
}
