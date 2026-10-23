package com.mhss.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mhss.app.database.dao.BookmarkDao
import com.mhss.app.database.entity.toBookmark
import com.mhss.app.database.entity.toBookmarkEntity
import com.mhss.app.database.remote.toBookmarkDoc
import com.mhss.app.domain.model.Bookmark
import com.mhss.app.domain.repository.BookmarkRepository
import com.mhss.app.util.UserDataPaths
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Single
class BookmarkRepositoryImpl(
    private val bookmarkDao: BookmarkDao,
    @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : BookmarkRepository {

    override fun getAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkDao.getAllBookmarks().flowOn(ioDispatcher).map { bookmarks -> bookmarks.map { it.toBookmark() } }

    override suspend fun getBookmark(id: Int): Bookmark =
        withContext(ioDispatcher) { bookmarkDao.getBookmark(id).toBookmark() }

    override suspend fun searchBookmarks(query: String): List<Bookmark> =
        withContext(ioDispatcher) { bookmarkDao.getBookmark(query).map { it.toBookmark() } }

    override suspend fun addBookmark(bookmark: Bookmark): Long {
        val uid = auth.currentUser?.uid
        return withContext(ioDispatcher) {
            val newId = bookmarkDao.insertBookmark(bookmark.toBookmarkEntity())
            if (uid != null && newId != 0L) {
                val entity = bookmark.toBookmarkEntity().copy(id = newId.toInt())
                UserDataPaths.bookmarksCollection(firestore, uid).document(entity.id.toString())
                    .set(toBookmarkDoc(entity))
            }
            newId
        }
    }

    override suspend fun deleteBookmark(bookmark: Bookmark) {
        val uid = auth.currentUser?.uid
        withContext(ioDispatcher) {
            val entity = bookmark.toBookmarkEntity()
            bookmarkDao.deleteBookmark(entity)
            if (uid != null && entity.id != 0) {
                UserDataPaths.bookmarksCollection(firestore, uid).document(entity.id.toString())
                    .delete()
            }
        }
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        val uid = auth.currentUser?.uid
        withContext(ioDispatcher) {
            bookmarkDao.updateBookmark(bookmark.toBookmarkEntity())
            if (uid != null && bookmark.id != 0) {
                val entity = bookmark.toBookmarkEntity()
                UserDataPaths.bookmarksCollection(firestore, uid).document(entity.id.toString())
                    .set(toBookmarkDoc(entity))
            }
        }
    }
}
