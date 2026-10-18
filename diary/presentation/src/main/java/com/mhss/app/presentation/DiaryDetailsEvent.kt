package com.mhss.app.presentation

import com.mhss.app.domain.model.DiaryEntry

sealed class DiaryDetailsEvent {
    data object DeleteEntry : DiaryDetailsEvent()
    data object ToggleReadingMode : DiaryDetailsEvent()
    data class ScreenOnStop(val currentEntry: DiaryEntry) : DiaryDetailsEvent()

    data class Summarize(val text: String) : DiaryDetailsEvent()
    data class AutoFormat(val text: String) : DiaryDetailsEvent()
    data class CorrectSpelling(val text: String) : DiaryDetailsEvent()
    data object AiResultHandled : DiaryDetailsEvent()
}