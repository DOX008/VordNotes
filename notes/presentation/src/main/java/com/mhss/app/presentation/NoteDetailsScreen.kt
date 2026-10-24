@file:OptIn(ExperimentalLayoutApi::class)

package com.mhss.app.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.mhss.app.ui.R
import com.mhss.app.presentation.components.AiActionsRow
import com.mhss.app.presentation.components.AiResultOverlay
import com.mhss.app.presentation.components.rememberOcrLauncher
import com.mhss.app.presentation.components.ShareNoteAsPlainTextOption
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.ui.theme.Orange
import com.mhss.app.ui.toUserMessage
import com.mhss.app.util.date.formatDateDependingOnDay
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.mhss.app.ui.components.common.TTSSpeakerIcon
import com.mhss.app.ui.navigation.Screen
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.window.Dialog
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.foundation.shape.CircleShape
import android.graphics.*
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.camera.core.Preview
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import com.mhss.app.core.util.toBase64
import com.mhss.app.ui.components.notes.NotePinsRow




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailsScreen(
    navController: NavHostController,
    noteId: Int,
    folderId: Int,
    viewModel: NoteDetailsViewModel = koinViewModel(
        parameters = { parametersOf(noteId, folderId) }
    ),
) {
    val state = viewModel.noteUiState
    val incomingPrefillTitle =
        navController.previousBackStackEntry?.savedStateHandle?.get<String>("prefillNoteTitle")

    // Om vi öppnar en helt ny note och titeln är tom, sätt den en gång
    LaunchedEffect(incomingPrefillTitle) {
        if (!incomingPrefillTitle.isNullOrBlank() && state.note == null && viewModel.title.isBlank()) {
            viewModel.onEvent(NoteDetailsEvent.UpdateTitle(incomingPrefillTitle))
            // Ta bort värdet så det inte återanvänds av misstag
            navController.previousBackStackEntry?.savedStateHandle?.remove<String>("prefillNoteTitle")
        }
    }
    var openDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var openFolderDialog by rememberSaveable { mutableStateOf(false) }
    var showShareMenu by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val selectedPins = state.pins.toList()
    val title = viewModel.title
    val content = viewModel.content
    val pinned = state.pinned
    val readingMode = state.readingMode
    val folder = state.folder
    var lastModified by remember { mutableStateOf("") }
    var wordCountString by remember { mutableStateOf("") }
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()

    var showPinsDialog by rememberSaveable { mutableStateOf(false) }

    val ocrLauncher = rememberOcrLauncher(
        actions = viewModel.aiActions,
        content = content,
        onContentChange = { viewModel.onEvent(NoteDetailsEvent.UpdateContent(it)) }
    )



    var isTTSPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(content) {
        delay(500)
        wordCountString = content.countWords().toString()
    }
    LaunchedEffect(state.note) {
        if (state.note != null) {
            lastModified = state.note.updatedDate.formatDateDependingOnDay(context)
        }
    }
    LaunchedEffect(state.navigateUp) {
        if (state.navigateUp) {
            isTTSPlaying = false    // ← Lägg till denna rad!
            openDeleteDialog = false
            navController.navigateUp()
        }
    }
    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            viewModel.onEvent(
                NoteDetailsEvent.ScreenOnStop
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            MyBrainAppBar(
                title = "",
                actions = {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TTSSpeakerIcon(
                            text = content,
                            isPlaying = isTTSPlaying,
                            onPlayPauseClick = { isTTSPlaying = !isTTSPlaying }
                        )

                        IconButton(onClick = {
                            navController.navigate(
                                Screen.NotesScreen(
                                    addNote = true,
                                    prefillNoteTitle = title
                                )
                            )
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_plain_text),
                                contentDescription = stringResource(R.string.add_note)
                            )
                        }

                        IconButton(onClick = {
                            navController.navigate(
                                Screen.TasksScreen(
                                    addTask = true,
                                    prefillTitle = title
                                )
                            )
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_medicine),
                                contentDescription = stringResource(R.string.add_task)
                            )
                        }

                        if (folder != null) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(25.dp))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(25.dp))
                                    .clickable { openFolderDialog = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_folder),
                                    stringResource(R.string.folders),
                                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = folder.name,
                                    modifier = Modifier.padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            IconButton(onClick = { openFolderDialog = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_home_filled),
                                    stringResource(R.string.folders),
                                )
                            }
                        }

                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(
                                painterResource(android.R.drawable.ic_dialog_email),
                                stringResource(R.string.share_note),
                            )
                        }

                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            ShareNoteAsPlainTextOption(
                                title = title,
                                content = content,
                                onOptionSelected = { showShareMenu = false }
                            )
                        }

                        if (state.note != null) {
                            IconButton(onClick = { openDeleteDialog = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = stringResource(R.string.delete_task)
                                )
                            }
                        }


                        IconButton(onClick = { showPinsDialog = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pin_filled),
                                contentDescription = stringResource(R.string.notes_pins),
                                modifier = Modifier.size(24.dp),
                                tint = Color.Red
                            )
                        }

                        IconButton(onClick = {
                            viewModel.onEvent(NoteDetailsEvent.ToggleReadingMode)
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_font),
                                contentDescription = stringResource(R.string.reading_mode),
                                modifier = Modifier.size(24.dp),
                                tint = if (readingMode) Color.Green else Color.Gray
                            )
                        }
                    }
                }
            )
        },
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            var patientMenuOpen by remember { mutableStateOf(false) }
            val showPatientMenu = patientMenuOpen && viewModel.patientSuggestions.isNotEmpty()
            ExposedDropdownMenuBox(
                expanded = showPatientMenu,
                onExpandedChange = {
                    patientMenuOpen = it && viewModel.patientSuggestions.isNotEmpty()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        viewModel.onEvent(NoteDetailsEvent.UpdateTitle(it))
                        patientMenuOpen = it.isNotBlank()
                        viewModel.searchPatients(it)
                    },
                    label = { Text(text = stringResource(R.string.title)) },
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = showPatientMenu,
                    onDismissRequest = { patientMenuOpen = false }
                ) {
                    viewModel.patientSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                viewModel.onEvent(NoteDetailsEvent.UpdateTitle(suggestion))
                                patientMenuOpen = false
                                viewModel.clearPatientSuggestions()
                            }
                        )
                    }
                }
            }
            if (selectedPins.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                NotePinsRow(
                    pinIds = selectedPins,
                    modifier = Modifier.fillMaxWidth(),
                    iconSize = 24.dp
                )
            }


            AnimatedVisibility(aiEnabled) {
                AiActionsRow(
                    onSummarize = { viewModel.onEvent(NoteDetailsEvent.Summarize(content)) },
                    onAutoFormat = { viewModel.onEvent(NoteDetailsEvent.AutoFormat(content)) },
                    onCorrectSpelling = { viewModel.onEvent(NoteDetailsEvent.CorrectSpelling(content)) },
                    onCameraClick = ocrLauncher.onCameraClick,
                    onGalleryClick = ocrLauncher.onGalleryClick
                )
            }

            if (readingMode)
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .padding(8.dp)
                )
            else
                OutlinedTextField(
                    value = content,
                    onValueChange = { viewModel.onEvent(NoteDetailsEvent.UpdateContent(it)) },
                    label = {
                        Text(text = stringResource(R.string.note_content))
                    },
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 8.dp)
                )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = lastModified,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
                Text(
                    text = wordCountString,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }
        }
        AiResultOverlay(
            actions = viewModel.aiActions,
            content = content,
            onContentChange = { viewModel.onEvent(NoteDetailsEvent.UpdateContent(it)) }
        )
        if (openDeleteDialog)
            AlertDialog(
                shape = RoundedCornerShape(25.dp),
                onDismissRequest = { openDeleteDialog = false },
                title = { Text(stringResource(R.string.delete_note_confirmation_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.delete_note_confirmation_message,
                            state.note?.title!!
                        )
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(25.dp),
                        onClick = {
                            viewModel.onEvent(NoteDetailsEvent.DeleteNote(state.note!!))
                        },
                    ) {
                        Text(stringResource(R.string.delete_note), color = Color.White)
                    }
                },
                dismissButton = {
                    Button(
                        shape = RoundedCornerShape(25.dp),
                        onClick = {
                            openDeleteDialog = false
                        }) {
                        Text(stringResource(R.string.cancel), color = Color.White)
                    }
                }
            )
        if (openFolderDialog) {
            AlertDialog(
                onDismissRequest = { openFolderDialog = false },
                confirmButton = {},
                text = {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(stringResource(R.string.change_folder))
                        FlowRow {
                            Row(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(25.dp))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(25.dp))
                                    .clickable {
                                        viewModel.onEvent(NoteDetailsEvent.UpdateFolder(null))
                                        openFolderDialog = false
                                    }
                                    .background(
                                        if (folder == null) MaterialTheme.colorScheme.onBackground else Color.Transparent
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.none),
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (folder == null) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
                                )
                            }

                            state.folders.forEach {
                                Row(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(25.dp))
                                        .border(1.dp, Color.Gray, RoundedCornerShape(25.dp))
                                        .clickable {
                                            viewModel.onEvent(NoteDetailsEvent.UpdateFolder(it))
                                            openFolderDialog = false
                                        }
                                        .background(
                                            if (folder?.id == it.id) MaterialTheme.colorScheme.onBackground else Color.Transparent
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_folder),
                                        stringResource(R.string.folders),
                                        modifier = Modifier.padding(
                                            start = 8.dp,
                                            top = 8.dp,
                                            bottom = 8.dp
                                        ),
                                        tint = if (folder?.id == it.id) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = it.name,
                                        modifier = Modifier.padding(
                                            end = 8.dp,
                                            top = 8.dp,
                                            bottom = 8.dp
                                        ),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (folder?.id == it.id) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }

        if (showPinsDialog) {
            NotePinsDialog(
                selectedPins = selectedPins,
                important = pinned,
                onToggleImportant = { viewModel.onEvent(NoteDetailsEvent.UpdatePinned(!pinned)) },
                onTogglePin = { pin -> viewModel.onEvent(NoteDetailsEvent.TogglePin(pin.id)) },
                onDismiss = { showPinsDialog = false }
            )
        }
    }
}

private fun String.countWords(): Int {
    var count = 0
    var inWord = false

    forEach { char ->
        if (char == ' ' || char == '\n') {
            inWord = false
        } else if (!inWord) {
            count++
            inWord = true
        }
    }

    return count

}