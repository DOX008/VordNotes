package com.mhss.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mhss.app.database.dao.TaskDao
import com.mhss.app.database.entity.TaskEntity
import com.mhss.app.database.entity.toTask
import com.mhss.app.database.entity.toTaskEntity
import com.mhss.app.database.remote.await
import com.mhss.app.database.remote.taskDocToEntity
import com.mhss.app.database.remote.toTaskDoc
import com.mhss.app.domain.model.Task
import com.mhss.app.domain.repository.TaskRepository
import com.mhss.app.util.UserDataPaths
import com.mhss.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Single
class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val widgetUpdater: WidgetUpdater
) : TaskRepository {

    override fun getAllTasks(): Flow<List<Task>> =
        taskDao.getAllTasks().flowOn(ioDispatcher).map { tasks -> tasks.map { it.toTask() } }

    override suspend fun getTaskById(id: Int): Task =
        withContext(ioDispatcher) { taskDao.getTask(id).toTask() }

    override fun searchTasks(title: String): Flow<List<Task>> =
        taskDao.getTasksByTitle(title).flowOn(ioDispatcher).map { tasks -> tasks.map { it.toTask() } }

    override suspend fun insertTask(task: Task): Long {
        val uid = auth.currentUser?.uid ?: return 0L
        val newId = withContext(ioDispatcher) {
            val id = taskDao.insertTask(task.toTaskEntity())
            if (id != 0L) {
                val entity = task.toTaskEntity().copy(id = id.toInt())
                UserDataPaths.tasksCollection(firestore, uid).document(entity.id.toString())
                    .set(toTaskDoc(entity))
                    .await()
            }
            id
        }
        notifyTasksWidget()
        return newId
    }

    override suspend fun updateTask(task: Task) {
        val uid = auth.currentUser?.uid ?: return
        withContext(ioDispatcher) {
            taskDao.updateTask(task.toTaskEntity())
            if (task.id != 0) {
                val entity = task.toTaskEntity()
                UserDataPaths.tasksCollection(firestore, uid).document(entity.id.toString())
                    .set(toTaskDoc(entity))
                    .await()
            }
        }
        notifyTasksWidget()
    }

    override suspend fun completeTask(id: Int, completed: Boolean) {
        withContext(ioDispatcher) { taskDao.updateCompleted(id, completed) }
        notifyTasksWidget()
    }

    suspend fun downSyncTasksFromFirebase(): Boolean {
        return withContext(ioDispatcher) {
            try {
                val user = auth.currentUser ?: return@withContext false
                val snapshot = UserDataPaths.tasksCollection(firestore, user.uid).get().await()
                val entities = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    taskDocToEntity(data)
                }
                taskDao.insertTasks(entities)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun deleteTask(task: Task) {
        val uid = auth.currentUser?.uid ?: return
        withContext(ioDispatcher) {
            val entity = task.toTaskEntity()
            taskDao.deleteTask(entity)
            if (entity.id != 0) {
                UserDataPaths.tasksCollection(firestore, uid).document(entity.id.toString())
                    .delete()
                    .await()
            }
        }
        notifyTasksWidget()
    }

    private suspend fun notifyTasksWidget() {
        try {
            widgetUpdater.updateAll(WidgetUpdater.WidgetType.Tasks)
        } catch (_: Exception) {
        }
    }
}
