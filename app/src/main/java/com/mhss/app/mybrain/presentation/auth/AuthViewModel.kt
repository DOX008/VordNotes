package com.mhss.app.mybrain.presentation.auth

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benasher44.uuid.Uuid
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mhss.app.database.UserDatabaseManager
import com.mhss.app.database.entity.BookmarkEntity
import com.mhss.app.database.entity.DiaryEntryEntity
import com.mhss.app.database.entity.imageNames
import com.mhss.app.util.DiaryImageSync
import com.mhss.app.database.entity.NoteEntity
import com.mhss.app.database.entity.NoteFolderEntity
import com.mhss.app.database.entity.TaskEntity
import com.mhss.app.mybrain.InternetDetector
import com.mhss.app.ui.R
import com.mhss.app.util.RegistrationValidator
import com.mhss.app.util.UserDataPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import kotlin.coroutines.resumeWithException

private const val TAG = "SYNC"

@KoinViewModel
class AuthViewModel(
    private val app: Application,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val databaseManager: UserDatabaseManager
) : ViewModel() {

    // Viktigt: initiera dessa tidigt så de alltid finns innan init/startSyncForUser körs
    private val _syncInProgress = MutableStateFlow(false)
    val syncInProgress: StateFlow<Boolean> = _syncInProgress.asStateFlow()

    private val _syncResult = MutableStateFlow<Boolean?>(null)
    val syncResult: StateFlow<Boolean?> = _syncResult.asStateFlow()

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

    private var syncPaused: Boolean = false

    private var downloadJob: Job? = null
    private var syncJob: Job? = null
    private var resyncJob: Job? = null
    private val itemSyncJobs = java.util.concurrent.CopyOnWriteArrayList<Job>()

    private fun launchItemSync(block: suspend () -> Unit) {
        val job = viewModelScope.launch(Dispatchers.IO) { block() }
        itemSyncJobs.add(job)
        job.invokeOnCompletion { itemSyncJobs.remove(job) }
    }

    private val pendingSyncQueue = ArrayDeque<suspend () -> Unit>()

    private val queueFileName = "pending_sync_queue.flag"

    private fun persistQueueFlag() {
        val file = java.io.File(app.filesDir, queueFileName)
        file.writeText("pending")
        Log.d(TAG, "persistQueueFlag: kö-flagga sparad (pending=${pendingSyncQueue.size})")
    }

    private fun clearQueueFlag() {
        val file = java.io.File(app.filesDir, queueFileName)
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "clearQueueFlag: kö-flagga borttagen")
        }
    }

    private fun hasPersistedQueue(): Boolean {
        val file = java.io.File(app.filesDir, queueFileName)
        return file.exists()
    }

    private var previousUid: String? = auth.currentUser?.uid
    private var registrationInProgress = false

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val currentUser = firebaseAuth.currentUser
        val currentUid = currentUser?.uid
        val oldUid = previousUid

        Log.d(TAG, "authListener: uid=$currentUid, previousUid=$oldUid")
        _user.value = currentUser

        if (registrationInProgress) {
            Log.d(TAG, "authListener: registrering pågår, hoppar över meddelande och sync")
            previousUid = currentUid
            return@AuthStateListener
        }

        if (currentUid != oldUid) {
            if (currentUser != null) {
                if (oldUid != null) resetSessionState()
                _authMessage.value = app.getString(R.string.auth_logged_in)
                startSyncForUser(currentUser.uid)
            } else {
                resetSessionState()
                _authMessage.value = app.getString(R.string.auth_logged_out)
                Log.d(TAG, "authListener: utloggad")
            }
        }

        previousUid = currentUid
    }

    init {
        _user.value = auth.currentUser
        Log.d(TAG, "init: inloggad=${auth.currentUser != null}, uid=${auth.currentUser?.uid}")

        auth.addAuthStateListener(authListener)
        if (auth.currentUser == null) {
            clearQueueFlag()
        } else if (hasPersistedQueue()) {
            syncPaused = true
            Log.w(TAG, "init: hittade sparad kö-flagga -> syncPaused=true")
        }
        auth.currentUser?.uid?.let(::startSyncForUser)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }

    fun signIn(email: String, password: String, onError: (String) -> Unit) {
        val normalizedEmail = email.trim()

        Log.d(TAG, "signIn: försöker logga in för e-post=${RegistrationValidator.maskEmail(normalizedEmail)}")
        auth.signInWithEmailAndPassword(normalizedEmail, password)
            .addOnSuccessListener { result ->
                Log.d(TAG, "signIn: OK uid=${result.user?.uid}")
                _user.value = result.user
                _authMessage.value = app.getString(R.string.auth_logged_in)
            }
            .addOnFailureListener { signInError ->
                Log.w(TAG, "signIn: inloggning misslyckades: ${signInError.message}")
                _authMessage.value = app.getString(R.string.auth_login_failed)
                onError(app.getString(R.string.auth_login_failed_detail, signInError.localizedMessage ?: ""))
            }
    }

    fun registerAccount(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val normalizedEmail = email.trim()
        val maskedEmail = RegistrationValidator.maskEmail(normalizedEmail)
        if (auth.currentUser != null) {
            Log.w(TAG, "registerAccount: användare redan inloggad, avbryter")
            onError(app.getString(R.string.auth_already_logged_in))
            return
        }
        if (!RegistrationValidator.isValidEmail(normalizedEmail) ||
            !RegistrationValidator.isValidPassword(password)
        ) {
            onError(app.getString(R.string.auth_invalid_input))
            return
        }

        Log.d(TAG, "registerAccount: skapar konto för $maskedEmail")
        registrationInProgress = true
        auth.createUserWithEmailAndPassword(normalizedEmail, password)
            .addOnSuccessListener { result ->
                Log.d(TAG, "registerAccount: OK för $maskedEmail, behåller användaren inloggad")
                registrationInProgress = false
                _user.value = result.user
                _authMessage.value = app.getString(R.string.auth_logged_in)
                result.user?.uid?.let(::startSyncForUser)
                onSuccess()
            }
            .addOnFailureListener { e ->
                registrationInProgress = false
                Log.w(TAG, "registerAccount: misslyckades för $maskedEmail: ${e.javaClass.simpleName}")
                onError(
                    app.getString(
                        when (e) {
                            is FirebaseAuthUserCollisionException -> R.string.auth_email_in_use
                            is FirebaseAuthWeakPasswordException -> R.string.auth_weak_password
                            is FirebaseAuthInvalidCredentialsException -> R.string.auth_invalid_email
                            is FirebaseNetworkException -> R.string.auth_network_error
                            else -> R.string.auth_register_failed
                        }
                    )
                )
            }
    }

    fun sendPasswordReset(
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val normalizedEmail = email.trim()
        val maskedEmail = RegistrationValidator.maskEmail(normalizedEmail)
        if (!RegistrationValidator.isValidEmail(normalizedEmail)) {
            onError(app.getString(R.string.auth_enter_email_first))
            return
        }
        Log.d(TAG, "sendPasswordReset: skickar återställningsmail till $maskedEmail")
        auth.sendPasswordResetEmail(normalizedEmail)
            .addOnSuccessListener {
                Log.d(TAG, "sendPasswordReset: OK för $maskedEmail")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "sendPasswordReset: misslyckades för $maskedEmail: ${e.javaClass.simpleName}")
                when (e) {
                    is FirebaseAuthInvalidUserException -> onSuccess()
                    is FirebaseAuthInvalidCredentialsException -> onError(app.getString(R.string.auth_invalid_email))
                    is FirebaseNetworkException -> onError(app.getString(R.string.auth_no_internet))
                    else -> onError(app.getString(R.string.auth_reset_failed))
                }
            }
    }

    fun signOut() {
        Log.d(TAG, "signOut()")
        resetSessionState()
        auth.signOut()
    }

    /**
     * Cancels running sync work and clears all auth/sync related state so that the next sign-in
     * (same or another account) starts from a clean slate.
     */
    private fun resetSessionState() {
        downloadJob?.cancel()
        syncJob?.cancel()
        resyncJob?.cancel()
        downloadJob = null
        syncJob = null
        resyncJob = null
        itemSyncJobs.forEach { it.cancel() }
        itemSyncJobs.clear()
        pendingSyncQueue.clear()
        clearQueueFlag()
        syncPaused = false
        _syncInProgress.value = false
        _syncResult.value = null
        _authMessage.value = null
    }

    private fun startSyncForUser(uid: String) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            if (auth.currentUser?.uid != uid) return@launch
            if (InternetDetector.hasInternet(app)) downloadAllFromCloud()
            if (auth.currentUser?.uid != uid) return@launch
            databaseManager.mergeGuestDataInto(uid)
            if (auth.currentUser?.uid == uid) syncNow()
        }
    }

    fun resyncAllFromCloud() {
        val user = auth.currentUser ?: run {
            Log.w(TAG, "resyncAllFromCloud: ingen inloggad användare, avbryter")
            return
        }

        resyncJob?.cancel()
        resyncJob = viewModelScope.launch {
            Log.d(TAG, "resyncAllFromCloud: START uid=${user.uid}")
            _syncInProgress.value = true
            try {
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "resyncAllFromCloud: rensar alla lokala tabeller")
                    databaseManager.getOrCreate(user.uid).clearAllTables()
                    downloadAllFromCloud()
                }
            } finally {
                _syncInProgress.value = false
                Log.d(TAG, "resyncAllFromCloud: KLAR")
            }
        }
    }

    fun syncNow(onDone: () -> Unit = {}) {
        val user = auth.currentUser ?: run {
            Log.w(TAG, "syncNow: ingen inloggad användare")
            _syncResult.value = false
            onDone()
            return
        }

        _syncInProgress.value = true
        Log.d(TAG, "syncNow() starting for uid=${user.uid}")

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            syncPaused = false
            resumePendingSyncIfAny()
            try {
                withContext(Dispatchers.IO) {
                    val userDatabase = databaseManager.getOrCreate(user.uid)
                    val userNotes = userDatabase.noteDao()
                    val notes = userNotes.getAllNotes()
                    val noteFolders = userNotes.getAllNoteFolders().first()
                    val tasks = userDatabase.taskDao().getAllTasks().first()
                    val diary = userDatabase.diaryDao().getAllFullEntries()
                    val bookmarks = userDatabase.bookmarkDao().getAllBookmarks().first()

                    val userRef = UserDataPaths.userRoot(firestore, user.uid)
                    val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()

                    notes.filter { it.id != 0 }.forEach { n ->
                        writes += userRef.collection("notes").document(n.id.toString()) to n.toNoteMap()
                    }
                    noteFolders.filter { it.id != 0 }.forEach { f ->
                        writes += userRef.collection("folders").document(f.id.toString()) to f.toFolderMap()
                    }
                    tasks.filter { it.id != 0 }.forEach { t ->
                        writes += userRef.collection("tasks").document(t.id.toString()) to t.toTaskMap()
                    }
                    diary.filter { it.id != 0 }.forEach { d ->
                        writes += userRef.collection("diary").document(d.id.toString()) to d.toDiaryMap()
                    }
                    bookmarks.filter { it.id != 0 }.forEach { b ->
                        writes += userRef.collection("bookmarks").document(b.id.toString()) to b.toBookmarkMap()
                    }

                    writes.chunked(450).forEach { chunk ->
                        val batch = firestore.batch()
                        chunk.forEach { (document, data) -> batch.set(document, data) }
                        batch.commit().await()
                    }

                    diary.filter { it.id != 0 }.forEach { d ->
                        DiaryImageSync.upload(
                            firestore, user.uid, d.id,
                            d.images.split("\n").filter { it.isNotBlank() }
                        )
                    }

                    val meta = hashMapOf(
                        "device" to Build.MODEL,
                        "lastSync" to FieldValue.serverTimestamp()
                    )
                    firestore.batch()
                        .set(userRef.collection("meta").document("sync"), meta)
                        .commit()
                        .await()
                }

                _syncResult.value = true
                Log.d(TAG, "syncNow() completed OK")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _syncResult.value = false
                Log.e(TAG, "syncNow() failed: ${e.message}", e)
            } finally {
                _syncInProgress.value = false
                onDone()
            }
        }
    }

    private suspend fun resumePendingSyncIfAny() {
        if (syncPaused) {
            Log.d(TAG, "resumePendingSyncIfAny: syncPaused=true, hoppar över")
            return
        }
        if (pendingSyncQueue.isEmpty()) return

        Log.d(TAG, "resumePendingSyncIfAny: kör ${pendingSyncQueue.size} köade jobb")
        while (pendingSyncQueue.isNotEmpty()) {
            val block = pendingSyncQueue.first()
            try {
                block()
                pendingSyncQueue.removeFirst()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "resumePendingSyncIfAny: jobb misslyckades: ${e.message}", e)
                syncPaused = true
                persistQueueFlag()
                return
            }
        }

        clearQueueFlag()
    }

    private suspend fun runLogged(name: String, block: suspend () -> Unit): Boolean {
        return try {
            Log.d(TAG, "[$name] START")
            block()
            Log.d(TAG, "[$name] OK")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[$name] MISSLYCKADES: ${e.javaClass.simpleName}: ${e.message}", e)
            false
        }
    }

    private suspend fun downloadAllFromCloud() {
        val user = auth.currentUser ?: run {
            Log.w(TAG, "downloadAllFromCloud: no currentUser, aborting")
            return
        }

        val base = UserDataPaths.userRoot(firestore, user.uid)
        val userDatabase = databaseManager.getOrCreate(user.uid)
        val userNoteDao = userDatabase.noteDao()
        val userTaskDao = userDatabase.taskDao()
        val userDiaryDao = userDatabase.diaryDao()
        val userBookmarkDao = userDatabase.bookmarkDao()
        Log.d(TAG, "downloadAllFromCloud: ENTER for uid=${user.uid}")
        _syncInProgress.value = true

        val failedSteps = mutableListOf<String>()

        try {
            withContext(Dispatchers.IO) {
                if (!runLogged("Folders") {
                        val snap = base.collection("folders").get().await()
                        val folders = snap.documents.mapNotNull { d ->
                            val data = d.data ?: return@mapNotNull null
                            NoteFolderEntity(
                                id = (data["id"] as? Number)?.toInt() ?: 0,
                                name = data["name"] as? String ?: "",
                                parentId = (data["parentId"] as? Number)?.toInt()
                            )
                        }
                        if (folders.isNotEmpty()) {
                            userNoteDao.insertNoteFolders(folders)
                        }
                    }) failedSteps.add("Folders")

                if (!runLogged("Notes") {
                        val snap = base.collection("notes").get().await()
                        val validFolderIds = userNoteDao.getAllNoteFolders().first().map { it.id }.toSet()
                        val notes = snap.documents.mapNotNull { d ->
                            val data = d.data ?: return@mapNotNull null
                            val rawFolderId = (data["folderId"] as? Number)?.toInt()
                            val folderId = rawFolderId?.takeIf { it in validFolderIds }

                            NoteEntity(
                                title = data["title"] as? String ?: "",
                                content = data["content"] as? String ?: "",
                                createdDate = (data["createdDate"] as? Number)?.toLong() ?: 0L,
                                updatedDate = (data["updatedDate"] as? Number)?.toLong() ?: 0L,
                                pinned = data["pinned"] as? Boolean ?: false,
                                folderId = folderId,
                                orderIndex = (data["orderIndex"] as? Number)?.toInt() ?: 0,
                                pins = data["pins"] as? String ?: "",
                                id = (data["id"] as? Number)?.toInt() ?: 0
                            )
                        }
                        if (notes.isNotEmpty()) {
                            userNoteDao.insertNotes(notes)
                        }
                    }) failedSteps.add("Notes")

                if (!runLogged("Diary") {
                        val snap = base.collection("diary").get().await()
                        val entries = snap.documents.mapNotNull { d ->
                            val data = d.data ?: return@mapNotNull null
                            val mood = when (val mv = data["mood"]) {
                                is Number -> com.mhss.app.domain.model.Mood.values().getOrNull(mv.toInt())
                                is String -> runCatching { com.mhss.app.domain.model.Mood.valueOf(mv) }.getOrNull()
                                else -> null
                            } ?: com.mhss.app.domain.model.Mood.values().first()
                            val entryId = (data["id"] as? Number)?.toInt() ?: 0
                            val imageNames = (data["images"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                            val imagePaths = if (entryId != 0 && imageNames.isNotEmpty()) {
                                DiaryImageSync.download(app, firestore, user.uid, entryId, imageNames)
                            } else emptyList()
                            DiaryEntryEntity(
                                title = data["title"] as? String ?: "",
                                content = data["content"] as? String ?: "",
                                createdDate = (data["createdDate"] as? Number)?.toLong() ?: 0L,
                                updatedDate = (data["updatedDate"] as? Number)?.toLong() ?: 0L,
                                mood = mood,
                                images = imagePaths.joinToString("\n"),
                                id = entryId
                            )
                        }
                        if (entries.isNotEmpty()) userDiaryDao.insertEntries(entries)
                    }) failedSteps.add("Diary")

                if (!runLogged("Tasks") {
                        val snap = base.collection("tasks").get().await()
                        val tasks = snap.documents.mapNotNull { d ->
                            val data = d.data ?: return@mapNotNull null
                            TaskEntity(
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
                                        is String -> try {
                                            Uuid.fromString(idValue)
                                        } catch (_: Exception) {
                                            return@mapNotNull null
                                        }
                                        else -> return@mapNotNull null
                                    }
                                    com.mhss.app.domain.model.SubTask(
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
                        if (tasks.isNotEmpty()) userTaskDao.insertTasks(tasks)
                    }) failedSteps.add("Tasks")

                if (!runLogged("Bookmarks") {
                        val snap = base.collection("bookmarks").get().await()
                        val bookmarks = snap.documents.mapNotNull { d ->
                            val data = d.data ?: return@mapNotNull null
                            BookmarkEntity(
                                url = data["url"] as? String ?: "",
                                title = data["title"] as? String ?: "",
                                description = data["description"] as? String ?: "",
                                createdDate = (data["createdDate"] as? Number)?.toLong() ?: 0L,
                                updatedDate = (data["updatedDate"] as? Number)?.toLong() ?: 0L,
                                id = (data["id"] as? Number)?.toInt() ?: 0
                            )
                        }
                        if (bookmarks.isNotEmpty()) userBookmarkDao.insertBookmarks(bookmarks)
                    }) failedSteps.add("Bookmarks")

                runLogged("Meta") { touchMeta() }
            }

            if (failedSteps.isEmpty()) {
                _syncResult.value = true
                Log.d(TAG, "downloadAllFromCloud: EXIT success (alla steg OK)")
            } else {
                _syncResult.value = false
                Log.e(TAG, "downloadAllFromCloud: EXIT med fel i steg: $failedSteps")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _syncResult.value = false
            Log.e(TAG, "downloadAllFromCloud: ERROR -> ${e.message}", e)
        } finally {
            _syncInProgress.value = false
        }
    }

    fun syncNote(note: NoteEntity) {
        val user = auth.currentUser ?: return
        if (note.id == 0) {
            Log.w(TAG, "syncNote: id=0, hoppar över")
            return
        }

        launchItemSync {
            enqueueOrRun {
                Log.d(TAG, "syncNote: skickar anteckning id=${note.id}")
                userRef(user.uid).collection("notes").document(note.id.toString()).set(note.toNoteMap()).await()
                touchMeta()
                Log.d(TAG, "syncNote: OK id=${note.id}")
            }
        }
    }

    fun syncBookmark(bookmark: BookmarkEntity) {
        val user = auth.currentUser ?: return
        if (bookmark.id == 0) {
            Log.w(TAG, "syncBookmark: id=0, hoppar över")
            return
        }

        launchItemSync {
            enqueueOrRun {
                Log.d(TAG, "syncBookmark: skickar bokmärke id=${bookmark.id}")
                userRef(user.uid).collection("bookmarks").document(bookmark.id.toString()).set(bookmark.toBookmarkMap()).await()
                touchMeta()
                Log.d(TAG, "syncBookmark: OK id=${bookmark.id}")
            }
        }
    }

    private suspend fun enqueueOrRun(block: suspend () -> Unit) {
        if (syncPaused) {
            Log.w(TAG, "enqueueOrRun: syncPaused=true, köar jobb (kö=${pendingSyncQueue.size + 1})")
            pendingSyncQueue.addLast(block)
            persistQueueFlag()
            return
        }

        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "enqueueOrRun: jobb misslyckades, pausar sync: ${e.message}", e)
            syncPaused = true
            pendingSyncQueue.addLast(block)
            persistQueueFlag()
        }
    }

    private fun userRef(uid: String): DocumentReference =
        UserDataPaths.userRoot(firestore, uid)

    private suspend fun touchMeta() {
        val uid = auth.currentUser?.uid ?: return
        val metaDoc = UserDataPaths.metaCollection(firestore, uid).document("sync")
        val meta = hashMapOf(
            "device" to Build.MODEL,
            "lastSync" to FieldValue.serverTimestamp()
        )
        metaDoc.set(meta).await()
    }

    private fun NoteEntity.toNoteMap(): HashMap<String, Any?> = hashMapOf(
        "title" to title,
        "content" to content,
        "createdDate" to createdDate,
        "updatedDate" to updatedDate,
        "pinned" to pinned,
        "folderId" to folderId,
        "orderIndex" to orderIndex,
        "pins" to pins,
        "id" to id
    )

    private fun NoteFolderEntity.toFolderMap(): HashMap<String, Any?> = hashMapOf(
        "id" to id,
        "name" to name,
        "parentId" to parentId
    )

    private fun TaskEntity.toTaskMap(): HashMap<String, Any?> {
        val subTasksList = subTasks.map {
            hashMapOf(
                "title" to it.title,
                "isCompleted" to it.isCompleted,
                "id" to it.id.toString()
            )
        }
        return hashMapOf(
            "title" to title,
            "description" to description,
            "isCompleted" to isCompleted,
            "priority" to priority,
            "createdDate" to createdDate,
            "updatedDate" to updatedDate,
            "lastFiredDate" to lastFiredDate,
            "subTasks" to subTasksList,
            "dueDate" to dueDate,
            "recurring" to recurring,
            "frequency" to frequency,
            "frequencyAmount" to frequencyAmount,
            "id" to id
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

    private fun BookmarkEntity.toBookmarkMap(): HashMap<String, Any?> = hashMapOf(
        "url" to url,
        "title" to title,
        "description" to description,
        "createdDate" to createdDate,
        "updatedDate" to updatedDate,
        "id" to id
    )

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> cont.resume(res) {} }
        addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    fun clearAuthMessage() {
        _authMessage.value = null
    }

    fun clearSyncResult() {
        _syncResult.value = null
    }
}