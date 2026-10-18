package com.mhss.app.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mhss.app.domain.AiConstants
import com.mhss.app.domain.autoFormatNotePrompt
import com.mhss.app.domain.correctSpellingNotePrompt
import com.mhss.app.domain.summarizeNotePrompt
import com.mhss.app.domain.use_case.SendAiMessageUseCase
import com.mhss.app.domain.visionOcrPrompt
import com.mhss.app.network.NetworkResult
import com.mhss.app.preferences.PrefsConstants
import com.mhss.app.preferences.domain.model.AiProvider
import com.mhss.app.preferences.domain.model.stringPreferencesKey
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AiTextAction { SUMMARIZE, AUTO_FORMAT, CORRECT_SPELLING }

data class AiState(
    val loading: Boolean = false,
    val result: String? = null,
    val error: NetworkResult.Failure? = null,
    val showAiSheet: Boolean = false,
)

/**
 * Shared AI editor logic (summarize, auto format, spelling and vision OCR)
 * used by the note and follow-up/well-being editors.
 */
class AiEditorActions(
    private val scope: CoroutineScope,
    private val getPreference: GetPreferenceUseCase,
    private val sendAiMessage: SendAiMessageUseCase,
) {
    var state by mutableStateOf(AiState())
        private set
    private var actionJob: Job? = null

    private class Settings(val key: String, val model: String, val url: String)

    private suspend fun settings() = Settings(
        key = getPreference(stringPreferencesKey(PrefsConstants.OPENAI_KEY), "").first(),
        model = getPreference(
            stringPreferencesKey(PrefsConstants.OPENAI_MODEL_KEY),
            AiConstants.OPENAI_DEFAULT_MODEL
        ).first(),
        url = getPreference(
            stringPreferencesKey(PrefsConstants.OPENAI_URL_KEY),
            AiConstants.OPENAI_BASE_URL
        ).first()
    )

    fun run(action: AiTextAction, content: String) {
        val prompt = when (action) {
            AiTextAction.SUMMARIZE -> content.summarizeNotePrompt
            AiTextAction.AUTO_FORMAT -> content.autoFormatNotePrompt
            AiTextAction.CORRECT_SPELLING -> content.correctSpellingNotePrompt
        }
        actionJob?.cancel()
        actionJob = scope.launch {
            state = state.copy(loading = true, showAiSheet = true, result = null, error = null)
            val s = settings()
            val result = try {
                sendAiMessage(prompt, s.model, AiProvider.OpenAI, s.url, s.key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(loading = false, showAiSheet = false)
                return@launch
            }
            state = when (result) {
                is NetworkResult.Success -> state.copy(loading = false, result = result.data, error = null)
                is NetworkResult.Failure -> state.copy(error = result, loading = false)
            }
        }
    }

    fun dismiss() {
        actionJob?.cancel()
        actionJob = null
        state = state.copy(showAiSheet = false)
    }

    fun runVisionOcr(
        base64Image: String,
        onRecognized: (String) -> Unit,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        scope.launch {
            try {
                val s = settings()
                val result = sendAiMessage.sendVisionPrompt(
                    prompt = visionOcrPrompt,
                    imageBase64 = base64Image,
                    model = s.model,
                    baseURL = s.url,
                    apiKey = s.key
                )
                when (result) {
                    is NetworkResult.Success -> {
                        val recognized = result.data.trim()
                        if (recognized.isNotEmpty()) onRecognized(recognized)
                        onDone()
                    }
                    else -> onError()
                }
            } catch (_: Exception) {
                onError()
            }
        }
    }
}
