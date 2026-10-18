package com.mhss.app.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mhss.app.domain.model.Note
import com.mhss.app.domain.model.NoteFolder
import com.mhss.app.domain.use_case.AddNoteUseCase
import com.mhss.app.domain.use_case.DeleteNoteUseCase
import com.mhss.app.domain.use_case.GetAllNoteFoldersUseCase
import com.mhss.app.domain.use_case.GetNoteFolderUseCase
import com.mhss.app.domain.use_case.GetNoteUseCase
import com.mhss.app.domain.use_case.SendAiMessageUseCase
import com.mhss.app.domain.use_case.UpdateNoteUseCase
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import com.mhss.app.util.date.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.Named
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow

@KoinViewModel
class NoteDetailsViewModel(
    private val getNote: GetNoteUseCase,
    private val updateNote: UpdateNoteUseCase,
    private val addNote: AddNoteUseCase,
    private val deleteNote: DeleteNoteUseCase,
    private val getPreference: GetPreferenceUseCase,
    private val getAllFolders: GetAllNoteFoldersUseCase,
    private val getNoteFolder: GetNoteFolderUseCase,
    private val sendAiPrompt: SendAiMessageUseCase,
    @Named("applicationScope") private val applicationScope: CoroutineScope,
    id: Int,
    folderId: Int,
) : ViewModel() {

    var noteUiState by mutableStateOf(UiState())
        private set

    var title by mutableStateOf("")
        private set
    var content by mutableStateOf("")
        private set

    private var autoSaveJob: Job? = null
    private val debounceTime = 2000L

    private val _aiEnabled = MutableStateFlow(true)
    val aiEnabled: StateFlow<Boolean> = _aiEnabled
    val aiActions = AiEditorActions(viewModelScope, getPreference, sendAiPrompt)

    private val saveMutex = Mutex()
    private var creatingNote = false

    init {
        viewModelScope.launch {
            val note: Note? = if (id != -1) getNote(id) else null
            val folder = getNoteFolder(note?.folderId ?: folderId)
            val folders = getAllFolders().first()

            if (note != null) {
                title = note.title
                content = note.content
            }

            noteUiState = noteUiState.copy(
                note = note,
                folder = folder,
                folders = folders,
                readingMode = note != null,
                pinned = note?.pinned ?: false,
                pins = note?.pins.orEmpty().toSet()
            )
        }
    }

    fun onEvent(event: NoteDetailsEvent) {
        when (event) {
            is NoteDetailsEvent.ScreenOnStop -> applicationScope.launch {
                if (!noteUiState.navigateUp) {
                    autoSaveJob?.cancel()
                    saveNote()
                }
            }

            is NoteDetailsEvent.DeleteNote -> viewModelScope.launch {
                deleteNote(event.note)
                noteUiState = noteUiState.copy(navigateUp = true)
            }

            NoteDetailsEvent.ToggleReadingMode -> noteUiState =
                noteUiState.copy(readingMode = !noteUiState.readingMode)

            is NoteDetailsEvent.UpdateTitle -> {
                title = event.title
                saveNoteWithDebounce()
            }

            is NoteDetailsEvent.UpdateContent -> {
                content = event.content
                saveNoteWithDebounce()
            }

            is NoteDetailsEvent.UpdateFolder -> {
                noteUiState = noteUiState.copy(folder = event.folder)
                saveNoteWithDebounce()
            }

            is NoteDetailsEvent.UpdatePinned -> {
                noteUiState = noteUiState.copy(pinned = event.pinned)
                saveNoteWithDebounce()
            }

            is NoteDetailsEvent.TogglePin -> {
                val updatedPins = noteUiState.pins.toMutableSet().apply {
                    if (!add(event.pinId)) remove(event.pinId)
                }.toSet()
                noteUiState = noteUiState.copy(pins = updatedPins)
                saveNoteWithDebounce()
            }

            is NoteDetailsEvent.Summarize -> aiActions.run(AiTextAction.SUMMARIZE, event.content)
            is NoteDetailsEvent.AutoFormat -> aiActions.run(AiTextAction.AUTO_FORMAT, event.content)
            is NoteDetailsEvent.CorrectSpelling ->
                aiActions.run(AiTextAction.CORRECT_SPELLING, event.content)

            NoteDetailsEvent.AiResultHandled -> aiActions.dismiss()
        }
    }

    private fun saveNoteWithDebounce() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(debounceTime)
            saveNote()
        }
    }

    private suspend fun saveNote() = saveMutex.withLock {
        if (noteUiState.navigateUp) return@withLock

        val folderId = noteUiState.folder?.id
        val pinned = noteUiState.pinned
        val pins = noteUiState.pins.toList()

        if (noteUiState.note == null) {
            if (title.isNotBlank() || content.isNotBlank()) {
                if (creatingNote) return@withLock
                creatingNote = true
                try {
                    val note = Note(
                        title = title,
                        content = content,
                        folderId = folderId,
                        pinned = pinned,
                        createdDate = now(),
                        updatedDate = now(),
                        pins = pins
                    )
                    val id = addNote(note)
                    noteUiState = noteUiState.copy(note = note.copy(id = id.toInt()))
                } finally {
                    creatingNote = false
                }
            }
        } else {
            val currentNote = noteUiState.note!!
            if (
                currentNote.title != title ||
                currentNote.content != content ||
                currentNote.folderId != folderId ||
                currentNote.pinned != pinned ||
                currentNote.pins != pins
            ) {
                val newNote = currentNote.copy(
                    title = title,
                    content = content,
                    folderId = folderId,
                    pinned = pinned,
                    updatedDate = now(),
                    pins = pins
                )
                updateNote(newNote)
                noteUiState = noteUiState.copy(note = newNote)
            }
        }
    }

    data class UiState(
        val note: Note? = null,
        val navigateUp: Boolean = false,
        val readingMode: Boolean = false,
        val folders: List<NoteFolder> = emptyList(),
        val folder: NoteFolder? = null,
        val pinned: Boolean = false,
        val pins: Set<String> = emptySet()
    )
}
