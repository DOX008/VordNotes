package com.mhss.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mhss.app.database.dao.DiaryDao
import com.mhss.app.database.entity.*
import com.mhss.app.database.remote.toDiaryDoc
import com.mhss.app.domain.model.DiaryEntry
import com.mhss.app.domain.repository.DiaryRepository
import com.mhss.app.util.UserDataPaths
import java.io.File
import com.mhss.app.util.DiaryImageSync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Single
class DiaryRepositoryImpl(
    private val diaryDao: DiaryDao,
    @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher,
    @Named("applicationScope") private val applicationScope: CoroutineScope,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : DiaryRepository {

    override fun getAllEntries(): Flow<List<DiaryEntry>> =
        diaryDao.getAllEntries().flowOn(ioDispatcher).map { entries -> entries.map { it.toDiaryEntry() } }

    override suspend fun getEntry(id: Int): DiaryEntry =
        withContext(ioDispatcher) { diaryDao.getEntry(id).toDiaryEntry() }

    override suspend fun searchEntries(title: String): List<DiaryEntry> =
        withContext(ioDispatcher) { diaryDao.getEntriesByTitle(title).map { it.toDiaryEntry() } }

    override suspend fun addEntry(diary: DiaryEntry): Long {
        val uid = auth.currentUser?.uid
        return withContext(ioDispatcher) {
            val newId = diaryDao.insertEntry(diary.toDiaryEntryEntity())
            if (uid != null && newId != 0L) {
                val entity = diary.toDiaryEntryEntity().copy(id = newId.toInt())
                UserDataPaths.diaryCollection(firestore, uid).document(entity.id.toString())
                    .set(toDiaryDoc(entity))
                applicationScope.launch(ioDispatcher) {
                    DiaryImageSync.upload(firestore, uid, entity.id, diary.images)
                }
            }
            newId
        }
    }

    override suspend fun updateEntry(diary: DiaryEntry) {
        val uid = auth.currentUser?.uid
        withContext(ioDispatcher) {
            val removed = runCatching { diaryDao.getEntry(diary.id).toDiaryEntry().images }
                .getOrDefault(emptyList()) - diary.images.toSet()
            deleteImageFiles(removed)
            if (uid != null) {
                applicationScope.launch(ioDispatcher) {
                    DiaryImageSync.delete(firestore, uid, diary.id, removed.map { File(it).name })
                }
            }
            diaryDao.updateEntry(diary.toDiaryEntryEntity())
            if (uid != null && diary.id != 0) {
                val entity = diary.toDiaryEntryEntity()
                UserDataPaths.diaryCollection(firestore, uid).document(entity.id.toString())
                    .set(toDiaryDoc(entity))
                applicationScope.launch(ioDispatcher) {
                    DiaryImageSync.upload(firestore, uid, entity.id, diary.images)
                }
            }
        }
    }

    override suspend fun deleteEntry(diary: DiaryEntry) {
        val uid = auth.currentUser?.uid
        withContext(ioDispatcher) {
            val entity = diary.toDiaryEntryEntity()
            diaryDao.deleteEntry(entity)
            deleteImageFiles(diary.images)
            if (uid != null && entity.id != 0) {
                UserDataPaths.diaryCollection(firestore, uid).document(entity.id.toString())
                    .delete()
                applicationScope.launch(ioDispatcher) {
                    DiaryImageSync.delete(firestore, uid, entity.id, diary.images.map { File(it).name })
                }
            }
        }
    }

    // Rör bara filer i appens egen dagboksbildmapp
    private fun deleteImageFiles(paths: List<String>) {
        paths.map(::File).filter { it.parentFile?.name == "diary_images" }.forEach { it.delete() }
    }
}
