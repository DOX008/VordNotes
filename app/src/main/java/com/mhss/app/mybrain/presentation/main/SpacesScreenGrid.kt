package com.mhss.app.mybrain.presentation.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.mhss.app.mybrain.presentation.main.components.SpaceCardGrid
import com.mhss.app.mybrain.presentation.main.components.SpaceCardWide
import com.mhss.app.ui.R
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.ui.navigation.Screen
import com.mhss.app.ui.theme.MyBrainTheme

private val Big = 32.dp
private val Inner = 0.dp
private val WideCardHeight = 120.dp
private val GridIconSize = 92.dp
private val WideIconSize = 96.dp

@Composable
fun SpacesScreenGrid(
    navController: NavHostController
) {
    // Samma kortfärg som NoteCard, TaskCard och NotesScreen
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = { MyBrainAppBar(stringResource(R.string.spaces)) }
    ) { paddingValues ->

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val gap = 6.dp
            val availableTop = (maxHeight - 14.dp - WideCardHeight - 10.dp - WideCardHeight - 24.dp)
                .coerceAtLeast(220.dp)

            val rowHeight = ((availableTop - gap) / 2).coerceAtLeast(110.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    SpaceCardGrid(
                        title = stringResource(R.string.notes),
                        subtitle = stringResource(R.string.notes_subtitle),
                        image = R.drawable.notes_img,
                        backgroundColor = cardColor,
                        iconSize = GridIconSize,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        cardShape = RoundedCornerShape(Big, Big, Inner, Big),
                        onClick = { navController.navigate(Screen.NotesScreen()) }
                    )

                    SpaceCardGrid(
                        title = stringResource(R.string.tasks),
                        subtitle = stringResource(R.string.tasks_subtitle),
                        image = R.drawable.tasks_img,
                        backgroundColor = cardColor,
                        iconSize = GridIconSize,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        cardShape = RoundedCornerShape(Big, Big, Big, Inner),
                        onClick = { navController.navigate(Screen.TasksScreen()) }
                    )
                }

                Spacer(Modifier.height(gap))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    SpaceCardGrid(
                        title = stringResource(R.string.diary),
                        subtitle = stringResource(R.string.diary_subtitle),
                        image = R.drawable.diary_img,
                        backgroundColor = cardColor,
                        isPulsing = true,
                        iconSize = GridIconSize,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        cardShape = RoundedCornerShape(Big, Inner, Big, Big),
                        onClick = { navController.navigate(Screen.DiaryScreen) }
                    )

                    SpaceCardGrid(
                        title = stringResource(R.string.calendar),
                        subtitle = stringResource(R.string.calendar_subtitle),
                        image = R.drawable.calendar_img,
                        backgroundColor = cardColor,
                        iconSize = GridIconSize,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        cardShape = RoundedCornerShape(Inner, Big, Big, Big),
                        onClick = { navController.navigate(Screen.CalendarScreen) }
                    )
                }

                Spacer(Modifier.height(14.dp))

                SpaceCardWide(
                    title = stringResource(R.string.bookmarks),
                    subtitle = stringResource(R.string.bookmarks_subtitle),
                    image = R.drawable.bookmarks_img,
                    backgroundColor = cardColor,
                    modifier = Modifier.fillMaxWidth().height(WideCardHeight),
                    cardShape = RoundedCornerShape(Big),
                    iconSize = WideIconSize,
                    onClick = { navController.navigate(Screen.BookmarksScreen) }
                )

                Spacer(Modifier.height(10.dp))

                SpaceCardWide(
                    title = stringResource(R.string.assistant),
                    subtitle = stringResource(R.string.assistant_subtitle),
                    image = R.drawable.ai_chat_img,
                    backgroundColor = cardColor,
                    modifier = Modifier.fillMaxWidth().height(WideCardHeight),
                    cardShape = RoundedCornerShape(Big),
                    iconSize = WideIconSize,
                    onClick = { navController.navigate(Screen.AssistantScreen) }
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 680)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SpacesScreenGridPreview() {
    MyBrainTheme {
        SpacesScreenGrid(
            navController = rememberNavController()
        )
    }
}