package com.mhss.app.presentation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mhss.app.domain.model.DiaryEntry
import com.mhss.app.ui.R
import com.mhss.app.util.DiaryMailEntry
import com.mhss.app.util.EmailAttachmentException
import com.mhss.app.util.EmailAttachmentLimit
import com.mhss.app.util.EmailPreferences
import com.mhss.app.util.MAX_DIARY_EMAIL_IMAGE_COUNT
import com.mhss.app.util.MailSender
import com.mhss.app.util.date.fullDate
import kotlinx.coroutines.launch

private fun DiaryEntry.toMailEntry(context: Context) = DiaryMailEntry(
    title = title,
    content = content,
    dateTimeText = createdDate.fullDate(context),
    moodLabel = context.getString(mood.titleRes),
    moodColorHex = String.format("#%06X", 0xFFFFFF and mood.color.toArgb()),
    imagePaths = images
)

/** Skickar angivna inlägg i ett mejl, ett block per inlägg med text och egna bilder. */
@Composable
fun DiaryEmailDialog(
    entries: List<DiaryEntry>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf(EmailPreferences.getRecipientEmail(context) ?: "") }
    var sending by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.enter_email_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.enter_email_label)) },
                    singleLine = true,
                    enabled = !sending && !sent,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.diary_email_count, entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                resultMessage?.let {
                    Text(text = it, modifier = Modifier.padding(top = 12.dp))
                }
                if (sending) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_ok)) }
            } else {
                TextButton(
                    enabled = !sending && email.isNotBlank() && entries.isNotEmpty(),
                    onClick = {
                        EmailPreferences.setRecipientEmail(context, email.trim())
                        sending = true
                        resultMessage = null
                        scope.launch {
                            val result = MailSender.sendDiaryMail(
                                toEmail = email.trim(),
                                subject = context.getString(R.string.diary_email_subject),
                                entries = entries.map { it.toMailEntry(context) },
                                imageAltTextPrefix = context.getString(R.string.email_image_alt_prefix)
                            )
                            sending = false
                            sent = result.isSuccess
                            resultMessage = if (result.isSuccess) {
                                context.getString(R.string.notes_share_sent)
                            } else {
                                val error = result.exceptionOrNull()
                                if (error is EmailAttachmentException) {
                                    when (error.reason) {
                                        EmailAttachmentLimit.TOO_MANY_IMAGES ->
                                            context.getString(R.string.email_too_many_images, MAX_DIARY_EMAIL_IMAGE_COUNT)
                                        EmailAttachmentLimit.UNSUPPORTED_IMAGE ->
                                            context.getString(R.string.email_image_unsupported)
                                    }
                                } else {
                                    context.getString(
                                        R.string.notes_share_failed,
                                        error?.localizedMessage ?: context.getString(R.string.notes_unknown_error)
                                    )
                                }
                            }
                        }
                    }
                ) { Text(stringResource(R.string.send)) }
            }
        },
        dismissButton = {
            if (!sent) {
                TextButton(enabled = !sending, onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
