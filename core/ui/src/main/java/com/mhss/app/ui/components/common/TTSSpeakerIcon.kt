package com.mhss.app.ui.components.common

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mhss.app.ui.R
import java.util.Locale

private val SpeakerIconSize = 40.dp * 0.7f  // ≈17.dp

@Composable
fun TTSSpeakerIcon(
    text: String,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tts = remember {
        var instance: TextToSpeech? = null
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale("sv", "SE")
            }
        }.also { instance = it }
    }

    DisposableEffect(tts) {
        onDispose { tts.shutdown() }
    }

    IconButton(
        onClick = {
            onPlayPauseClick()
            if (isPlaying) {
                tts.stop()
            } else {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        },
        modifier = modifier.size(SpeakerIconSize)
    ) {
        Icon(
            painter = painterResource(
                id = if (isPlaying) R.drawable.ic_pause else R.drawable.ttspeakericon_gron
            ),
            contentDescription = stringResource(if (isPlaying) R.string.app_tts_pause else R.string.app_tts_read_aloud),
            modifier = Modifier.fillMaxSize()
        )
    }
}