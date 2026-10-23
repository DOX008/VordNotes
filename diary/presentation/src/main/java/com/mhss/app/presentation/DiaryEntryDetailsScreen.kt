package com.mhss.app.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavHostController
import com.mhss.app.domain.model.DiaryEntry
import com.mhss.app.domain.model.Mood
import com.mhss.app.presentation.components.AiActionsRow
import com.mhss.app.presentation.components.AiResultOverlay
import com.mhss.app.presentation.components.rememberOcrLauncher
import com.mhss.app.ui.R
import com.mhss.app.ui.components.common.DateTimeDialog
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.ui.components.common.TTSSpeakerIcon
import com.mhss.app.util.DiaryImageStorage
import com.mhss.app.util.MAX_DIARY_ENTRY_IMAGE_COUNT
import com.mhss.app.util.date.fullDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.mhss.app.util.date.now
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEntryDetailsScreen(
    navController: NavHostController,
    entryId: Int,
    viewModel: DiaryDetailsViewModel = koinViewModel(parameters = { parametersOf(entryId) }),
    diaryViewModel: DiaryViewModel = koinViewModel()
) {
    val state = viewModel.uiState
    val diaryState = diaryViewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    var openDialog by rememberSaveable { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf(Mood.OKAY) }
    var date by remember { mutableLongStateOf(now()) }
    var images by remember { mutableStateOf(emptyList<String>()) }
    var showEmailDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val readingMode = state.readingMode
    var showDateDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var isTTSPlaying by remember { mutableStateOf(false) }
    var openDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var patientMenuOpen by remember { mutableStateOf(false) }
    val showPatientMenu = patientMenuOpen && diaryState.patientSuggestions.isNotEmpty()

    val ocrLauncher = rememberOcrLauncher(
        actions = viewModel.aiActions,
        content = content,
        onContentChange = { content = it }
    )

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        scope.launch {
            val room = (MAX_DIARY_ENTRY_IMAGE_COUNT - images.size).coerceAtLeast(0)
            val imported = uris.take(room).mapNotNull { uri ->
                try {
                    DiaryImageStorage.importImage(context, uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            images = images + imported
            if (uris.size > room) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.email_too_many_images, MAX_DIARY_ENTRY_IMAGE_COUNT)
                )
            }
        }
    }

    LaunchedEffect(state.entry) {
        if (state.entry != null) {
            title = state.entry.title
            content = state.entry.content
            date = state.entry.createdDate
            mood = state.entry.mood
            images = state.entry.images
        }
    }

    LaunchedEffect(state.navigateUp) {
        if (state.navigateUp) {
            isTTSPlaying = false
            openDeleteDialog = false
            diaryViewModel.onEvent(DiaryEvent.ClearPatientSuggestions)
            navController.navigateUp()
        }
    }

    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            viewModel.onEvent(
                DiaryDetailsEvent.ScreenOnStop(
                    DiaryEntry(
                        title = title,
                        content = content,
                        mood = mood,
                        images = images,
                        createdDate = date
                    )
                )
            )
            diaryViewModel.onEvent(DiaryEvent.ClearPatientSuggestions)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MyBrainAppBar(
                title = "",
                actions = {
                    TTSSpeakerIcon(
                        text = content,
                        isPlaying = isTTSPlaying,
                        onPlayPauseClick = { isTTSPlaying = !isTTSPlaying }
                    )
                    IconButton(onClick = {
                        viewModel.onEvent(DiaryDetailsEvent.ToggleReadingMode)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_font),
                            contentDescription = stringResource(R.string.reading_mode),
                            modifier = Modifier.size(24.dp),
                            tint = if (readingMode) Color.Green else Color.Gray
                        )
                    }
                    IconButton(
                        enabled = title.isNotBlank() || content.isNotBlank(),
                        onClick = { showEmailDialog = true }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_send),
                            contentDescription = stringResource(R.string.diary_send_email),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (state.entry != null) {
                        IconButton(onClick = { openDialog = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.delete_entry)
                            )
                        }
                    }
                    TextButton(onClick = { showDateDialog = true }) {
                        Text(
                            text = date.fullDate(context),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(paddingValues)
                .imePadding()
        ) {
            Text(
                text = stringResource(R.string.mood),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 10.dp)
            )
            Spacer(Modifier.height(8.dp))
            EntryMoodSection(
                currentMood = mood,
            ) { mood = it }
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = showPatientMenu,
                onExpandedChange = { patientMenuOpen = it && diaryState.patientSuggestions.isNotEmpty() },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        patientMenuOpen = it.isNotBlank()
                        diaryViewModel.onEvent(DiaryEvent.SearchPatients(it))
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
                    diaryState.patientSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                title = suggestion
                                patientMenuOpen = false
                                diaryViewModel.onEvent(DiaryEvent.ClearPatientSuggestions)
                            }
                        )
                    }
                }
            }

            AiActionsRow(
                onSummarize = { viewModel.onEvent(DiaryDetailsEvent.Summarize(content)) },
                onAutoFormat = { viewModel.onEvent(DiaryDetailsEvent.AutoFormat(content)) },
                onCorrectSpelling = { viewModel.onEvent(DiaryDetailsEvent.CorrectSpelling(content)) },
                onCameraClick = ocrLauncher.onCameraClick,
                onGalleryClick = ocrLauncher.onGalleryClick
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                images.forEach { path ->
                    DiaryImageThumbnail(
                        path = path,
                        onRemove = {
                            images = images - path
                            if (state.entry?.images?.contains(path) != true) {
                                scope.launch { DiaryImageStorage.delete(context, listOf(path)) }
                            }
                        }
                    )
                }
                if (images.size < MAX_DIARY_ENTRY_IMAGE_COUNT) {
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
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
                }
            }

            Spacer(Modifier.height(8.dp))

            if (readingMode) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 6.dp)
                        .padding(8.dp)
                )
            } else {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(text = stringResource(R.string.content)) },
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 8.dp)
                )
            }
        }

        AiResultOverlay(
            actions = viewModel.aiActions,
            content = content,
            onContentChange = { content = it }
        )

        if (showEmailDialog) {
            DiaryEmailDialog(
                entries = listOf(
                    DiaryEntry(
                        title = title,
                        content = content,
                        mood = mood,
                        images = images,
                        createdDate = date
                    )
                ),
                onDismiss = { showEmailDialog = false }
            )
        }

        if (showDateDialog) {
            DateTimeDialog(
                onDismissRequest = { showDateDialog = false },
                initialDate = date
            ) {
                date = it
                showDateDialog = false
            }
        }

        if (openDialog) {
            AlertDialog(
                shape = RoundedCornerShape(25.dp),
                onDismissRequest = { openDialog = false },
                title = { Text(stringResource(R.string.delete_diary_entry_confirmation_title)) },
                text = { Text(stringResource(R.string.delete_diary_entry_confirmation_message)) },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(25.dp),
                        onClick = {
                            viewModel.onEvent(DiaryDetailsEvent.DeleteEntry)
                        },
                    ) {
                        Text(
                            stringResource(R.string.delete_entry),
                            color = Color.White
                        )
                    }
                },
                dismissButton = {
                    Button(
                        shape = RoundedCornerShape(25.dp),
                        onClick = { openDialog = false }
                    ) {
                        Text(
                            stringResource(R.string.cancel),
                            color = Color.White
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun EntryMoodSection(
    currentMood: Mood,
    onMoodChange: (Mood) -> Unit
) {
    val moods = listOf(Mood.AWESOME, Mood.GOOD, Mood.OKAY, Mood.BAD, Mood.TERRIBLE)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        moods.forEach { mood ->
            MoodItem(
                mood = mood,
                chosen = mood == currentMood,
                onMoodChange = { onMoodChange(mood) }
            )
        }
    }
}

@Composable
private fun MoodItem(mood: Mood, chosen: Boolean, onMoodChange: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp))) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable { onMoodChange() }
                .padding(6.dp)
        ) {
            Icon(
                painter = painterResource(id = mood.iconRes),
                contentDescription = stringResource(mood.titleRes),
                tint = if (chosen) mood.color else Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(mood.titleRes),
                color = if (chosen) mood.color else Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}