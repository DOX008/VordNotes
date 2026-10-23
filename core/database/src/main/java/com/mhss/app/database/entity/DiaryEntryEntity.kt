package com.mhss.app.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mhss.app.domain.model.Mood
import com.mhss.app.domain.model.DiaryEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val IMAGE_SEPARATOR = "\n"

@Entity(tableName = "diary")
@Serializable
data class DiaryEntryEntity(
    @SerialName("title")
    val title: String = "",
    @SerialName("content")
    val content: String = "",
    @SerialName("createdDate")
    @ColumnInfo(name = "created_date")
    val createdDate: Long = 0L,
    @SerialName("updatedDate")
    @ColumnInfo(name = "updated_date")
    val updatedDate: Long = 0L,
    @SerialName("mood")
    val mood: Mood,
    @SerialName("images")
    @ColumnInfo(name = "images", defaultValue = "''")
    val images: String = "",
    @SerialName("id")
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
)

fun DiaryEntryEntity.toDiaryEntry() = DiaryEntry(
    title = title,
    content = content,
    createdDate = createdDate,
    updatedDate = updatedDate,
    mood = mood,
    images = images.split(IMAGE_SEPARATOR).filter { it.isNotBlank() },
    id = id
)

fun DiaryEntry.toDiaryEntryEntity() = DiaryEntryEntity(
    title = title,
    content = content,
    createdDate = createdDate,
    updatedDate = updatedDate,
    mood = mood,
    images = images.joinToString(IMAGE_SEPARATOR),
    id = id
)

fun List<DiaryEntryEntity>.withoutIds() = map { it.copy(id = 0) }