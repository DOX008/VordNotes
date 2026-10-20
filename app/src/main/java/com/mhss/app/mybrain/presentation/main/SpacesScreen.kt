package com.mhss.app.mybrain.presentation.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.mhss.app.mybrain.presentation.main.components.SpaceCard
import com.mhss.app.ui.R
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.ui.navigation.Screen
import com.mhss.app.ui.theme.MyBrainTheme

@Composable
fun SpacesScreen(
    navController: NavHostController
) {
    // Ugyanaz a tiszta kártyaszín, mint a többi képernyőn
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            MyBrainAppBar(stringResource(R.string.spaces))
        }
    ) { paddingValues ->

        // BoxWithConstraints segítségével leolvassuk a képernyő valós magasságát
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val gap = 12.dp
            // Kiszámítjuk a kártyák magasságát: a teljes magasságból levonjuk a margókat,
            // majd elosztjuk a 6 gomb között, de minimum 84.dp méretet biztosítunk.
            val calculatedCardHeight = ((maxHeight - 56.dp - (gap * 5)) / 6).coerceAtLeast(84.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()) // Ha a kijelző extrém kicsi, görgethetővé válik
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                spaces.forEach { (title, subtitle, image, screen) ->
                    SpaceCard(
                        title = stringResource(title),
                        subtitleRes = subtitle,
                        image = image,
                        backgroundColor = cardColor,
                        isPulsing = title == R.string.diary,
                        onClick = { navController.navigate(screen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(calculatedCardHeight) // Átadjuk a dinamikus magasságot
                    )
                    Spacer(modifier = Modifier.height(gap))
                }

                SpaceCard(
                    title = stringResource(R.string.assistant),
                    subtitleRes = R.string.assistant_subtitle,
                    image = R.drawable.ai_chat_img,
                    backgroundColor = cardColor,
                    isPulsing = false,
                    onClick = { navController.navigate(Screen.AssistantScreen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(calculatedCardHeight) // Az asszisztens gomb is dinamikus magasságot kap
                )

                Spacer(modifier = Modifier.height(32.dp))
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
