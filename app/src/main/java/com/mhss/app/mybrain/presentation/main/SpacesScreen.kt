package com.mhss.app.mybrain.presentation.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.mhss.app.mybrain.presentation.main.components.SpaceCard
import com.mhss.app.ui.R
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.ui.navigation.Screen
import com.mhss.app.ui.theme.MyBrainTheme

@Composable
fun SpacesScreen(
    navController: NavHostController
) {
    // Samma kortfärg som NoteCard, TaskCard och NotesScreen
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            MyBrainAppBar(stringResource(R.string.spaces))
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(spaces) { (title, subtitle, image, screen) ->
                SpaceCard(
                    title = stringResource(title),
                    subtitleRes = subtitle,
                    image = image,
                    backgroundColor = cardColor,
                    isPulsing = title == R.string.diary,
                    onClick = { navController.navigate(screen) }
                )
            }

            item {
                SpaceCard(
                    title = stringResource(R.string.assistant),
                    subtitleRes = R.string.assistant_subtitle,
                    image = R.drawable.ai_chat_img,
                    backgroundColor = cardColor,
                    isPulsing = false,
                    onClick = { navController.navigate(Screen.AssistantScreen) }
                )
            }
        }
    }
}

private val spaces = listOf(
    Space(R.string.notes, R.string.notes_subtitle, R.drawable.notes_img, Screen.NotesScreen()),
    Space(R.string.tasks, R.string.tasks_subtitle, R.drawable.tasks_img, Screen.TasksScreen()),
    Space(R.string.diary, R.string.diary_subtitle, R.drawable.diary_img, Screen.DiaryScreen),
    Space(R.string.bookmarks, R.string.bookmarks_subtitle, R.drawable.bookmarks_img, Screen.BookmarksScreen),
    Space(R.string.calendar, R.string.calendar_subtitle, R.drawable.calendar_img, Screen.CalendarScreen),
)

private data class Space(
    val title: Int,
    val subtitle: Int,
    val image: Int,
    val route: Screen
)

@Preview(widthDp = 360, heightDp = 680)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SpacesScreenPreview() {
    MyBrainTheme {
        SpacesScreen(
            navController = rememberNavController()
        )
    }
}