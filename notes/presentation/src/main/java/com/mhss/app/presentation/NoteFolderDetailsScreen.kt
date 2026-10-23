package com.mhss.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mhss.app.domain.model.NoteFolder
import com.mhss.app.ui.ItemView
import com.mhss.app.ui.R
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.ui.components.notes.NoteCard
import com.mhss.app.ui.navigation.Screen
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.mhss.app.preferences.domain.model.Order
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

@Composable
fun NoteFolderDetailsScreen(
    navController: NavHostController,
    id: Int,
    viewModel: NotesViewModel = koinViewModel()
) {
    val uiState = viewModel.notesUiState
    val folder = uiState.folder

    var openDeleteDialog by remember { mutableStateOf(false) }
    var openEditDialog by remember { mutableStateOf(false) }
    var openCreateSubfolderDialog by remember { mutableStateOf(false) }

    // Valda avdelningar (underavdelningar) som ska visas i Tidsschema
    var selectedSubfolderIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }


    val listState = rememberLazyListState()

    LaunchedEffect(id) {
        viewModel.onEvent(NoteEvent.GetFolderNotes(id))
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it))
            viewModel.onEvent(NoteEvent.ErrorDisplayed)
        }
    }

    LaunchedEffect(uiState.navigateUp) {
        if (uiState.navigateUp) navController.navigateUp()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MyBrainAppBar(
                title = folder?.name.orEmpty(),
                actions = {
                    IconButton(onClick = { openCreateSubfolderDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_home_filled),
                            contentDescription = stringResource(R.string.create_folder)
                        )
                    }
                    // Tidsschema: valda avdelningar, annars den här mappen
                    IconButton(onClick = {
                        val ids = if (selectedSubfolderIds.isNotEmpty()) {
                            selectedSubfolderIds
                        } else {
                            listOf(id)
                        }
                        navController.navigate(Screen.ScheduleScreen(folderIds = ids))
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_time),
                            contentDescription = stringResource(R.string.schedule)
                        )
                    }
                    IconButton(onClick = { openDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_folder)
                        )
                    }
                    IconButton(onClick = { openEditDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_folder)
                        )
                    }
                    IconButton(onClick = {
                        navController.navigate(Screen.Main) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.home_start),
                            contentDescription = stringResource(R.string.home)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.NoteDetailsScreen(folderId = id)) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.add_note),
                    tint = Color.White,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    ) { contentPadding ->

        val subfolders = uiState.folders.filter { it.parentId == id }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Subfolders (avdelningar)
            if (subfolders.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.subfolders),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                items(subfolders, key = { "folder-${it.id}" }) { subfolder ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate(
                                    Screen.NoteFolderDetailsScreen(folderId = subfolder.id)
                                )
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                start = 8.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            )
                        ) {
                            Checkbox(
                                checked = subfolder.id in selectedSubfolderIds,
                                onCheckedChange = { checked ->
                                    selectedSubfolderIds =
                                        if (checked) {
                                            (selectedSubfolderIds + subfolder.id).distinct()
                                        } else {
                                            selectedSubfolderIds - subfolder.id
                                        }
                                }
                            )
                            Text(
                                text = subfolder.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp)
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_medicine),
                                contentDescription = stringResource(R.string.notes_subfolder),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 12.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
            }

            // Notes header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ant_text),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    if (uiState.notesOrder is Order.Manual) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = stringResource(R.string.notes_manual_sort_active),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Notes – reorderable i listläge
            if (uiState.noteView == ItemView.LIST) {
                itemsIndexed(
                    items = uiState.folderNotes,
                    key = { _, note -> "note-${note.id}" }
                ) { index, note ->

                    var isDragging by remember { mutableStateOf(false) }
                    var offsetY by remember { mutableFloatStateOf(0f) }

                    NoteCard(
                        note = note,
                        onClick = {
                            if (!isDragging) {
                                navController.navigate(
                                    Screen.NoteDetailsScreen(noteId = note.id)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = offsetY
                                scaleX = if (isDragging) 1.04f else 1f
                                scaleY = if (isDragging) 1.04f else 1f

                                shadowElevation = 0f
                                shape = RoundedCornerShape(20.dp)
                                clip = false

                            }
                            .pointerInput(note.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        isDragging = true
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        offsetY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        isDragging = false

                                        val itemHeightApprox = 120f
                                        val deltaIndex =
                                            (offsetY / itemHeightApprox).roundToInt()

                                        val newIndex =
                                            (index + deltaIndex)
                                                .coerceIn(0, uiState.folderNotes.size - 1)

                                        if (newIndex != index) {
                                            val newList =
                                                uiState.folderNotes.toMutableList().apply {
                                                    val movedNote = removeAt(index)
                                                    add(newIndex, movedNote)
                                                }

                                            viewModel.onEvent(
                                                NoteEvent.ReorderNotes(newList)
                                            )
                                        }

                                        offsetY = 0f
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        offsetY = 0f
                                    }
                                )
                            }
                    )
                }
            } else {
                // Staggered grid – ingen drag & drop ännu
                item {
                    Text(
                        text = stringResource(R.string.notes_drag_drop_list_only),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(uiState.folderNotes, key = { "note-${it.id}" }) { note ->
                    NoteCard(
                        note = note,
                        onClick = {
                            navController.navigate(
                                Screen.NoteDetailsScreen(noteId = note.id)
                            )
                        }
                    )
                }
            }
        }
    }

    if (openCreateSubfolderDialog) {
        CreateFolderDialog(
            onCreate = { name ->
                viewModel.onEvent(
                    NoteEvent.CreateFolder(
                        NoteFolder(name = name.trim(), parentId = id)
                    )
                )
                openCreateSubfolderDialog = false
            },
            onDismiss = { openCreateSubfolderDialog = false }
        )
    }

    if (openDeleteDialog) {
        AlertDialog(
            shape = RoundedCornerShape(25.dp),
            onDismissRequest = { openDeleteDialog = false },
            title = { Text(text = stringResource(R.string.delete_note_confirmation_title)) },
            text = { Text(text = stringResource(R.string.delete_folder_confirmation_message)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(25.dp),
                    onClick = {
                        viewModel.onEvent(NoteEvent.DeleteFolder(folder!!))
                        openDeleteDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.delete_folder), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { openDeleteDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (openEditDialog) {
        var folderName by remember { mutableStateOf(folder?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { openEditDialog = false },
            title = { Text(text = stringResource(R.string.edit_folder)) },
            text = {
                TextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(text = stringResource(R.string.name)) }
                )
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(25.dp),
                    onClick = {
                        viewModel.onEvent(
                            NoteEvent.UpdateFolder(folder!!.copy(name = folderName))
                        )
                        openEditDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.save), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { openEditDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}