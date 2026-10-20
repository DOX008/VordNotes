@file:OptIn(ExperimentalLayoutApi::class)

package com.mhss.app.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mhss.app.ui.R
import com.mhss.app.domain.model.*
import com.mhss.app.preferences.domain.model.Order
import com.mhss.app.preferences.domain.model.OrderType
import com.mhss.app.ui.ItemView
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.ui.navigation.Screen
import com.mhss.app.ui.titleRes
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun NotesScreen(
    navController: NavHostController,
    viewModel: NotesViewModel = koinViewModel(),
    addNote: Boolean = false,
    prefillNoteTitle: String = ""
) {
    var playingNoteId by remember { mutableStateOf<Int?>(null) }
    val uiState = viewModel.notesUiState
    var orderSettingsVisible by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var openCreateFolderDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var addNoteOnce by rememberSaveable { mutableStateOf(addNote) }
    var combineUrl by rememberSaveable { mutableStateOf("https://vannas.se") }
    var combineSavedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Skapa WebView en gång – överlever tabb-byte
    val combineWebView = remember {
        android.webkit.WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = android.webkit.WebViewClient()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(context.getString(it))
            viewModel.onEvent(NoteEvent.ErrorDisplayed)
        }
    }

    fun Long.formatTime(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())
        return sdf.format(this)
    }

    LaunchedEffect(addNoteOnce, prefillNoteTitle) {
        if (addNoteOnce) {
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set("prefillNoteTitle", prefillNoteTitle)

            addNoteOnce = false
            navController.navigate(Screen.NoteDetailsScreen())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MyBrainAppBar(
                when (selectedTab) {
                    0 -> stringResource(R.string.notes)
                    1 -> stringResource(R.string.folders)
                    2 -> stringResource(R.string.combine)
                    else -> ""
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> navController.navigate(Screen.NoteDetailsScreen())
                            1 -> openCreateFolderDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        modifier = Modifier.size(25.dp),
                        painter = if (selectedTab == 0)
                            painterResource(R.drawable.ic_add)
                        else
                            painterResource(R.drawable.ic_integrations),
                        contentDescription = stringResource(R.string.add_note),
                        tint = Color.White
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    text = { Text(stringResource(R.string.notes), style = MaterialTheme.typography.bodyLarge) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )

                Tab(
                    text = { Text(stringResource(R.string.folders), style = MaterialTheme.typography.bodyLarge) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )

                Tab(
                    text = { Text(stringResource(R.string.combine), style = MaterialTheme.typography.bodyLarge) },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            when (selectedTab) {
                0 -> {

                    if (uiState.notes.isEmpty()) NoNotesMessage()

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        IconButton(onClick = { orderSettingsVisible = !orderSettingsVisible }) {
                            Icon(
                                modifier = Modifier.size(25.dp),
                                painter = painterResource(R.drawable.ic_settings_sliders),
                                contentDescription = stringResource(R.string.order_by)
                            )
                        }

                        IconButton(onClick = { navController.navigate(Screen.NoteSearchScreen) }) {
                            Icon(
                                modifier = Modifier.size(25.dp),
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                    }

                    AnimatedVisibility(visible = orderSettingsVisible) {
                        NotesSettingsSection(
                            uiState.notesOrder,
                            uiState.noteView,
                            onOrderChange = { viewModel.onEvent(NoteEvent.UpdateOrder(it)) },
                            onViewChange = { viewModel.onEvent(NoteEvent.UpdateView(it)) }
                        )
                    }

                    if (uiState.noteView == ItemView.LIST) {

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                top = 12.dp,
                                bottom = 24.dp,
                                start = 12.dp,
                                end = 12.dp
                            )
                        ) {

                            items(uiState.notes, key = { it.id }) { note ->

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            navController.navigate(
                                                Screen.NoteDetailsScreen(noteId = note.id)
                                            )
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(0.dp),
                                    // ÄNDRAD: Använder surfaceVariant istället för surface för att få rätt mörka grundfärg
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    )
                                ) {

                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {

                                        Text(
                                            text = note.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(Modifier.height(4.dp))

                                        Text(
                                            text = note.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(Modifier.height(8.dp))

                                        Text(
                                            text = note.updatedDate.formatTime(),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.End
                                        )

                                    }
                                }
                            }
                        }

                    } else {

                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(150.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(12.dp)
                        ) {

                            items(uiState.notes) { note ->

                                Card(
                                    modifier = Modifier
                                        .padding(bottom = 12.dp)
                                        .clickable {
                                            navController.navigate(
                                                Screen.NoteDetailsScreen(noteId = note.id)
                                            )
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {

                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {

                                        Text(
                                            text = note.title,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(Modifier.height(4.dp))

                                        Text(
                                            text = note.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    FoldersTab(uiState.folders) {
                        navController.navigate(
                            Screen.NoteFolderDetailsScreen(folderId = it.id)
                        )
                    }

                    if (openCreateFolderDialog) {
                        CreateFolderDialog(
                            onCreate = {
                                viewModel.onEvent(
                                    NoteEvent.CreateFolder(NoteFolder(it.trim()))
                                )
                                openCreateFolderDialog = false
                            },
                            onDismiss = { openCreateFolderDialog = false }
                        )
                    }
                }

                2 -> {
                    CombineTab(
                        url = combineUrl,
                        savedUrl = combineSavedUrl,
                        webView = combineWebView,
                        onUrlChange = { combineUrl = it },
                        onLoad = { newUrl ->
                            combineSavedUrl = newUrl
                            combineWebView.loadUrl(newUrl)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FoldersTab(
    folders: List<NoteFolder>,
    onItemClick: (NoteFolder) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        items(folders.filter { it.parentId == null }) { folder ->
            Card(
                modifier = Modifier
                    .height(120.dp) // MINSKAD: Ändrat från 180.dp till 120.dp
                    .graphicsLayer(alpha = 0.55f)
                    .clickable { onItemClick(folder) },
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.elevatedCardElevation(8.dp)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_home),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp) // MINSKAD: Ändrat från 100.dp till 48.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp)) // Tillagt för lite luft mellan ikon och text
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), // Ändrat till titleMedium för att passa bättre
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}



@Composable
fun CombineTab(
    url: String,
    savedUrl: String?,
    webView: android.webkit.WebView,
    onUrlChange: (String) -> Unit,
    onLoad: (String) -> Unit
) {
    val context = LocalContext.current
    var showAppNotInstalledDialog by remember { mutableStateOf(false) }

    // Hämta strängarna här (i Composable-kontexten)
    val combineAppUrl = stringResource(R.string.combine_app_url)
    val nexusSmartIdUrl = stringResource(R.string.nexus_smart_id_url)
    val combineTitle = stringResource(R.string.combine_title)
    val combineDescription = stringResource(R.string.combine_description)
    val combineApp = stringResource(R.string.combine_app)
    val combineAnd = stringResource(R.string.combine_and)
    val nexusSmartId = stringResource(R.string.nexus_smart_id)
    val combineInfo = stringResource(R.string.combine_info)

    if (savedUrl == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    val packageName = "com.pulsenomsorg.act"
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)

                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    } else {
                        showAppNotInstalledDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Combine",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Dialog när appen inte är installerad
            if (showAppNotInstalledDialog) {
                AppNotInstalledDialog(
                    onDismiss = { showAppNotInstalledDialog = false },
                    onDownload = {
                        val downloadIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse(combineAppUrl)
                        }
                        context.startActivity(downloadIntent)
                        showAppNotInstalledDialog = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = combineTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = combineDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = combineApp,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                ),
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse(combineAppUrl)
                                    }
                                    context.startActivity(intent)
                                }
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = combineAnd,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = nexusSmartId,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                ),
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse(nexusSmartIdUrl)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = combineInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun NoNotesMessage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.no_notes_message),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Image(
            modifier = Modifier.size(125.dp),
            painter = painterResource(id = R.drawable.notes_img),
            contentDescription = stringResource(R.string.no_notes_message),
            alpha = 0.7f
        )
    }
}

@Composable
fun CreateFolderDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.create_folder),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) }
            )

        },
        confirmButton = {
            Button(onClick = { onCreate(name) }) {
                Text(stringResource(R.string.create_folder), color = Color.White)
            }

        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
        }
    )
}

@Composable
fun AppNotInstalledDialog(
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "App is Not Installed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = "The required app 'Combine' is not installed on your device. " +
                        "Would you like to download it?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onDownload) {
                Text("Download", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesSettingsSection(
    order: Order,
    view: ItemView,
    onOrderChange: (Order) -> Unit,
    onViewChange: (ItemView) -> Unit
) {
    val orders = remember {
        listOf(
            Order.DateModified(),
            Order.DateCreated(),
            Order.Alphabetical()
        )
    }

    val orderTypes = remember {
        listOf(
            OrderType.ASC,
            OrderType.DESC
        )
    }

    val noteViews = remember {
        listOf(
            ItemView.LIST,
            ItemView.GRID
        )
    }

    Column(
        Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
    ) {

        Text(
            text = stringResource(R.string.order_by),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )

        FlowRow {
            orders.forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = order::class == it::class,
                        onClick = {
                            if (order != it)
                                onOrderChange(
                                    it.copyOrder(orderType = order.orderType)
                                )
                        }
                    )
                    Text(
                        text = stringResource(it.titleRes),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        HorizontalDivider()

        FlowRow {
            orderTypes.forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = order.orderType == it,
                        onClick = {
                            onOrderChange(order.copyOrder(it))
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(it.titleRes),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.view_as),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        )

        FlowRow {
            noteViews.forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = view == it,
                        onClick = {
                            onViewChange(it)
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(it.title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}