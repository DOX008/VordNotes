package com.mhss.app.presentation.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mhss.app.util.EmailAttachmentException
import com.mhss.app.util.EmailAttachmentLimit
import com.mhss.app.util.EmailPreferences
import com.mhss.app.util.MAX_EMAIL_IMAGE_COUNT
import com.mhss.app.util.MailSender
import com.mhss.app.util.getUriDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShareNoteAsPlainTextOption(
    title: String,
    content: String,
    onOptionSelected: () -> Unit
) {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Inmatningsdialog för e-post
    var showEmailDialog by rememberSaveable { mutableStateOf(false) }
    var emailInput by rememberSaveable { mutableStateOf(EmailPreferences.getRecipientEmail(context) ?: "") }
    var selectedImageUris by rememberSaveable(
        stateSaver = listSaver(
            save = { uris -> uris.map { it.toString() } },
            restore = { savedUris -> savedUris.map { Uri.parse(it) } }
        )
    ) { mutableStateOf(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var showImageLimitNotice by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { pickedImages ->
        val newImages = pickedImages.distinct().filterNot { it in selectedImageUris }
        val availableSlots = (MAX_EMAIL_IMAGE_COUNT - selectedImageUris.size).coerceAtLeast(0)
        val acceptedImages = newImages.take(availableSlots)
        acceptedImages.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // The current URI grant remains available for this send.
            } catch (_: UnsupportedOperationException) {
                // The provider does not support persisted URI grants.
            }
        }
        selectedImageUris = selectedImageUris + acceptedImages
        if (newImages.size > availableSlots) showImageLimitNotice = true
    }

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
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            ),
            onDismissRequest = {
                if (!sending) {
                    selectedImageUris.forEach { releaseImageReadPermission(context, it) }
                    selectedImageUris = emptyList()
                    showEmailDialog = false
                }
            },
            title = { Text(text = stringResource(R.string.enter_email_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { newValue -> emailInput = newValue },
                        label = { Text(stringResource(R.string.enter_email_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.share_images_title),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.share_images_hint, MAX_EMAIL_IMAGE_COUNT),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        enabled = !sending,
                        onClick = { imagePicker.launch(arrayOf("image/*")) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_attach),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.add_images),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    selectedImageUris.forEachIndexed { index, uri ->
                        key(uri) {
                            SelectedEmailImageRow(
                                uri = uri,
                                index = index,
                                enabled = !sending,
                                onRemove = {
                                    releaseImageReadPermission(context, uri)
                                    selectedImageUris = selectedImageUris - uri
                                }
                            )
                        }
                    }
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
                                    context = context,
                                    toEmail = emailInput.trim(),
                                    subject = title,
                                    messageBody = buildEmailBody(title, content),
                                    imageSectionLabel = context.getString(R.string.email_images_below),
                                    imageAltTextPrefix = context.getString(R.string.email_image_alt_prefix),
                                    imageUris = selectedImageUris
                                )
                                confirmMessage = if (result.isSuccess) {
                                    context.getString(R.string.notes_share_sent)
                                } else {
                                    val error = result.exceptionOrNull()
                                    val message = if (error is EmailAttachmentException) {
                                        when (error.reason) {
                                            EmailAttachmentLimit.TOO_MANY_IMAGES ->
                                                context.getString(R.string.email_too_many_images, MAX_EMAIL_IMAGE_COUNT)
                                            EmailAttachmentLimit.UNSUPPORTED_IMAGE ->
                                                context.getString(R.string.email_image_unsupported)
                                        }
                                    } else {
                                        val err = error?.localizedMessage ?: context.getString(R.string.notes_unknown_error)
                                        context.getString(R.string.notes_share_failed, err)
                                    }
                                    message
                                }
                                if (result.isSuccess) {
                                    selectedImageUris.forEach { releaseImageReadPermission(context, it) }
                                    selectedImageUris = emptyList()
                                    showEmailDialog = false
                                    callOnOptionWhenConfirmCloses = true
                                } else {
                                    callOnOptionWhenConfirmCloses = false
                                }
                                showConfirm = true
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
                    onClick = {
                        selectedImageUris.forEach { releaseImageReadPermission(context, it) }
                        selectedImageUris = emptyList()
                        showEmailDialog = false
                    }
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
    if (showImageLimitNotice) {
        ClickToDismissDialog(
            message = stringResource(R.string.email_too_many_images, MAX_EMAIL_IMAGE_COUNT),
            onDismiss = { showImageLimitNotice = false }
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

private fun releaseImageReadPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.releasePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        // Nothing to release if the provider only granted temporary access.
    } catch (_: UnsupportedOperationException) {
        // This provider does not support persistable URI permissions.
    }
}

@Composable
private fun SelectedEmailImageRow(
    uri: Uri,
    index: Int,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val fallbackName = stringResource(R.string.selected_image_fallback, index + 1)
    val displayName by produceState(
        initialValue = fallbackName,
        key1 = uri,
        key2 = fallbackName
    ) {
        value = withContext(Dispatchers.IO) {
            getUriDisplayName(context, uri) ?: fallbackName
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(enabled = enabled, onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.remove_image)
            )
        }
    }
}
