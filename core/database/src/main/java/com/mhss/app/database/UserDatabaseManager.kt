package com.mhss.app.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.mhss.app.database.dao.AlarmDao
import com.mhss.app.database.dao.BookmarkDao
import com.mhss.app.database.dao.DiaryDao
import com.mhss.app.database.dao.NoteDao
import com.mhss.app.database.dao.TaskDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

class UserDatabaseManager(
    private val context: Context
) {
    private val databases = ConcurrentHashMap<String, MyBrainDatabase>()

    private val _currentUid = MutableStateFlow(currentUid())
    val currentUidFlow: StateFlow<String> = _currentUid.asStateFlow()

    init {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            _currentUid.value = auth.currentUser?.uid ?: GUEST_UID
        }
    }

    private fun currentUid(): String =
        FirebaseAuth.getInstance().currentUser?.uid ?: GUEST_UID

    fun databaseForCurrentUser(): MyBrainDatabase = getOrCreate(currentUid())

    val noteDao: NoteDao by lazy {
        userScopedDao(NoteDao::class.java, currentUidFlow) { databaseForCurrentUser().noteDao() }
    }
    val taskDao: TaskDao by lazy {
        userScopedDao(TaskDao::class.java, currentUidFlow) { databaseForCurrentUser().taskDao() }
    }
    val diaryDao: DiaryDao by lazy {
        userScopedDao(DiaryDao::class.java, currentUidFlow) { databaseForCurrentUser().diaryDao() }
    }
    val bookmarkDao: BookmarkDao by lazy {
        userScopedDao(BookmarkDao::class.java, currentUidFlow) { databaseForCurrentUser().bookmarkDao() }
    }
    val alarmDao: AlarmDao by lazy {
        userScopedDao(AlarmDao::class.java, currentUidFlow) { databaseForCurrentUser().alarmDao() }
    }

    fun getOrCreate(uid: String): MyBrainDatabase {
        return databases.getOrPut(uid) {
            Room.databaseBuilder(
                context.applicationContext,
                MyBrainDatabase::class.java,
                MyBrainDatabase.databaseName(uid)
            )
                .addMigrations(
                    com.mhss.app.database.migrations.MIGRATION_1_2,
                    com.mhss.app.database.migrations.MIGRATION_2_3,
                    com.mhss.app.database.migrations.MIGRATION_3_4,
                    com.mhss.app.database.migrations.MIGRATION_4_5,
                    com.mhss.app.database.migrations.MIGRATION_5_6,
                    com.mhss.app.database.migrations.MIGRATION_6_7,
                    com.mhss.app.database.migrations.MIGRATION_7_8
                )
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    suspend fun mergeGuestDataInto(uid: String) {
        if (uid == GUEST_UID) return

        val guestDatabase = getOrCreate(GUEST_UID)
        val userDatabase = getOrCreate(uid)
        val guestFolders = guestDatabase.noteDao().getAllNoteFolders().first()
        val guestNotes = guestDatabase.noteDao().getAllNotes()
        val guestTasks = guestDatabase.taskDao().getAllTasks().first()
        val guestDiaryEntries = guestDatabase.diaryDao().getAllFullEntries()
        val guestBookmarks = guestDatabase.bookmarkDao().getAllBookmarks().first()

        userDatabase.withTransaction {
            val folderIds = mutableMapOf<Int, Int>()
            guestFolders.forEach { folder ->
                val newId = userDatabase.noteDao().insertNoteFolder(
                    folder.copy(id = 0, parentId = null)
                )
                if (newId > 0L) folderIds[folder.id] = newId.toInt()
            }
            guestFolders.forEach { folder ->
                val newId = folderIds[folder.id] ?: return@forEach
                userDatabase.noteDao().updateNoteFolder(
                    folder.copy(id = newId, parentId = folder.parentId?.let(folderIds::get))
                )
            }
            guestNotes.forEach { note ->
                userDatabase.noteDao().insertNote(
                    note.copy(id = 0, folderId = note.folderId?.let(folderIds::get))
                )
            }
            guestTasks.forEach { task ->
                userDatabase.taskDao().insertTask(task.copy(id = 0))
            }
            guestDiaryEntries.forEach { entry ->
                userDatabase.diaryDao().insertEntry(entry.copy(id = 0))
            }
            guestBookmarks.forEach { bookmark ->
                userDatabase.bookmarkDao().insertBookmark(bookmark.copy(id = 0))
            }
        }

        guestDatabase.withTransaction {
            guestDatabase.noteDao().deleteAllNotes()
            guestDatabase.noteDao().deleteAllNoteFolders()
            guestDatabase.taskDao().deleteAllTasks()
            guestDatabase.diaryDao().deleteAllEntries()
            guestDatabase.bookmarkDao().deleteAllBookmarks()
        }
    }

    private companion object {
        const val GUEST_UID = "guest"
    }
}
