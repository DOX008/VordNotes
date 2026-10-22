package com.mhss.app.presentation.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mhss.app.ui.R
import com.mhss.app.util.EmailPreferences
import com.mhss.app.util.MailSender
import kotlinx.coroutines.launch

@Composable
fun ShareNoteAsPlainTextOption(
    title: String,
    content: String,
    onOptionSelected: () -> Unit
) {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Inmatningsdialog för e-post
    var showEmailDialog by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf(EmailPreferences.getRecipientEmail(context) ?: "") }
    var sending by remember { mutableStateOf(false) }

    // Manuell bekräftelsedialog (stannar kvar tills användaren stänger)
    var showConfirm by remember { mutableStateOf(false) }
    var confirmMessage by remember { mutableStateOf("") }

    // Viktigt: anropa onOptionSelected FÖRST när man stänger bekräftelsen
    var callOnOptionWhenConfirmCloses by remember { mutableStateOf(false) }

    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.share_note)) },
        onClick = {
            emailInput = EmailPreferences.getRecipientEmail(context) ?: ""
            showEmailDialog = true
        },
        leadingIcon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_plain_text),
                contentDescription = stringResource(R.string.plain_text)
            )
        }
    )

    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = { if (!sending) showEmailDialog = false },
            title = { Text(text = stringResource(R.string.enter_email_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { newValue -> emailInput = newValue },
                        label = { Text(stringResource(R.string.enter_email_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (sending) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator()
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !sending && emailInput.isNotBlank(),
                    onClick = {
                        EmailPreferences.setRecipientEmail(context, emailInput.trim())
                        sending = true
                        scope.launch {
                            try {
                                val result = MailSender.sendMail(
                                    toEmail = emailInput.trim(),
                                    subject = title,
                                    messageBody = buildEmailBody(title, content)
                                )
                                confirmMessage = if (result.isSuccess) {
                                    context.getString(R.string.notes_share_sent)
                                } else {
                                    val err = result.exceptionOrNull()?.localizedMessage ?: context.getString(R.string.notes_unknown_error)
                                    context.getString(R.string.notes_share_failed, err)
                                }
                                // Visa bekräftelsedialog som användaren själv stänger
                                showConfirm = true
                                // Stäng inmatningsdialogen
                                showEmailDialog = false
                                // VÄNTA med onOptionSelected tills bekräftelsen stängs
                                callOnOptionWhenConfirmCloses = true
                            } finally {
                                sending = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.send)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !sending,
                    onClick = { showEmailDialog = false }
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showConfirm) {
        ClickToDismissDialog(
            message = confirmMessage,
            onDismiss = {
                showConfirm = false
                if (callOnOptionWhenConfirmCloses) {
                    callOnOptionWhenConfirmCloses = false
                    onOptionSelected() // <-- nu först
                }
            }
        )
    }
}

/**
 * Ramlös, centrerad dialog som ligger kvar tills användaren klickar bort den.
 * - Klick på rutan (kortet) eller OK-knappen stänger.
 * - Klick utanför stänger INTE (för att undvika “omedelbar stängning”).
 */
@Composable
private fun ClickToDismissDialog(
    message: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* blockera implicit stängning */ },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,   // viktigt: undvik att den stängs direkt
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .shadow(0.dp, RoundedCornerShape(16.dp))
                    .clickable { onDismiss() }   // klick på rutan stänger
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(text = message)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_ok)) }
                }
            }
        }
    }
}

private fun buildEmailBody(title: String, content: String): String {
    return """
$title

$content

______________________________________
Anteckningar från: Vård & Omsorgs app
""".trimIndent()
}
