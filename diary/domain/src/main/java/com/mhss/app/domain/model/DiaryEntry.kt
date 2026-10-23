package com.mhss.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DiaryEntry(
    val title: String = "",
    val content: String = "",
    val createdDate: Long = 0L,
    val updatedDate: Long = 0L,
    val mood: Mood,
    val images: List<String> = emptyList(),
    val id: Int = 0
)
