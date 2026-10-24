@file:OptIn(ExperimentalLayoutApi::class)

package com.mhss.app.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mhss.app.ui.R
import com.mhss.app.preferences.domain.model.Order
import com.mhss.app.preferences.domain.model.OrderType
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.ui.navigation.Screen
import com.mhss.app.ui.titleRes
import com.mhss.app.util.date.formatTime
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiaryScreen(
    navController: NavHostController,
    viewModel: DiaryViewModel = koinViewModel()
) {
    val uiState = viewModel.uiState
    var orderSettingsVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var playingEntryId by remember { mutableStateOf<Int?>(null) }
    var showEmailDialog by remember { mutableStateOf(false) }
    val navBackStackEntry = navController.currentBackStackEntry
    LaunchedEffect(navBackStackEntry) {
        val updated = navBackStackEntry
            ?.savedStateHandle
            ?.get<Boolean>("diary_entry_updated") ?: false
        if (updated) {
            viewModel.onEvent(DiaryEvent.ForceReload)
            navBackStackEntry?.savedStateHandle?.set("diary_entry_updated", false)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
        contentColor   = MaterialTheme.colorScheme.onBackground,
        topBar = {
            MyBrainAppBar(
                title = stringResource(R.string.diary),
                actions = {
                    IconButton(
                        enabled = uiState.entries.isNotEmpty(),
                        onClick = { showEmailDialog = true }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_send),
                            contentDescription = stringResource(R.string.diary_send_email),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = {
                        navController.navigate(Screen.DiaryChartScreen)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chart),
                            contentDescription = stringResource(R.string.diary_chart),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate(Screen.DiaryDetailScreen())
                },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    modifier = Modifier.size(25.dp),
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.add_entry),
                    tint = Color.White
                )
            }
        }
    ) { paddingValues ->
        if (showEmailDialog) {
            DiaryEmailDialog(
                entries = uiState.entries.values.flatten(),
                onDismiss = { showEmailDialog = false }
            )
        }
        Column(Modifier.padding(paddingValues)) {
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
                IconButton(onClick = {
                    navController.navigate(Screen.DiarySearchScreen)
                }) {
                    Icon(
                        modifier = Modifier.size(25.dp),
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = stringResource(R.string.search)
                    )
                }
            }
            AnimatedVisibility(visible = orderSettingsVisible) {
                DiarySettingsSection(
                    order = uiState.entriesOrder,
                    onOrderChange = { viewModel.onEvent(DiaryEvent.UpdateOrder(it)) }
                )
            }
            if (uiState.entries.isEmpty()) {
                NoEntriesMessage()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    uiState.entries.forEach { (day, entries) ->
                        stickyHeader {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(bottom = 4.dp)
                                    .padding(horizontal = 12.dp)
                            )
                        }
                        items(entries) { entry ->
                            DiaryEntryItem(
                                entry = entry,
                                isPlaying = playingEntryId == entry.id,
                                onPlayPauseClick = {
                                    playingEntryId = if (playingEntryId == entry.id) null else entry.id
                                },
                                onClick = {
                                    navController.navigate(Screen.DiaryDetailScreen(entry.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun DiarySettingsSection(order: Order, onOrderChange: (Order) -> Unit) {
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
    Column(
        Modifier.background(color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
    ) {
        Text(
            text = stringResource(R.string.order_by),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
        FlowRow(
            modifier = Modifier.padding(end = 8.dp)
        ) {
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
                            if (order != it)
                                onOrderChange(
                                    order.copyOrder(it)
                                )
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(it.titleRes),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NoEntriesMessage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.no_entries_message),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Image(
            modifier = Modifier.size(125.dp),
            painter = painterResource(id = R.drawable.diary_img),
            contentDescription = stringResource(R.string.no_entries_message),
            alpha = 0.7f
        )
    }
}
