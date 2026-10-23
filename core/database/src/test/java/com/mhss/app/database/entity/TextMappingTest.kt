package com.mhss.app.database.entity

import com.mhss.app.domain.model.DiaryEntry
import com.mhss.app.domain.model.Mood
import com.mhss.app.domain.model.Note
import com.mhss.app.domain.model.Task
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TextMappingTest {
    private val formattedText = "  leading\n\n\r\nmixed\rline with  spaces  \n trailing  "

    @Test
    fun `task description survives entity mapping and serialization`() {
        val task = Task(title = "task", description = formattedText)

        val entity = task.toTaskEntity()
        val serializedEntity = Json.encodeToString(entity)
        val restoredEntity = Json.decodeFromString<TaskEntity>(serializedEntity)

        assertEquals(formattedText, restoredEntity.toTask().description)
    }

    @Test
    fun `note content survives entity mapping and serialization`() {
        val note = Note(title = "note", content = formattedText)

        val entity = note.toNoteEntity()
        val serializedEntity = Json.encodeToString(entity)
        val restoredEntity = Json.decodeFromString<NoteEntity>(serializedEntity)

        assertEquals(formattedText, restoredEntity.toNote().content)
    }

    @Test
    fun `diary content survives entity mapping`() {
        val entry = DiaryEntry(title = "diary", content = formattedText, mood = Mood.OKAY)

        val entity = entry.toDiaryEntryEntity()

        assertEquals(formattedText, entity.toDiaryEntry().content)
    }
}
