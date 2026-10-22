package com.mhss.app.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UserScopedDaoTest {

    interface FakeDao {
        suspend fun owner(): String
        fun ownerFlow(): Flow<String>
    }

    private class OwnerDao(private val owner: String) : FakeDao {
        override suspend fun owner() = owner
        override fun ownerFlow(): Flow<String> = flowOf(owner)
    }

    @Test
    fun `dao follows the current user instead of the first one`() = runBlocking {
        var uid = "a"
        val key = MutableStateFlow(uid)
        val dao = userScopedDao(FakeDao::class.java, key) { OwnerDao(uid) }

        assertEquals("a", dao.owner())
        assertEquals("a", dao.ownerFlow().first())

        uid = "guest"
        key.value = uid
        assertEquals("guest", dao.owner())
        assertEquals("guest", dao.ownerFlow().first())

        uid = "b"
        key.value = uid
        assertEquals("b", dao.owner())
        assertEquals("b", dao.ownerFlow().first())
    }
}
