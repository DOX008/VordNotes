package com.mhss.app.widget.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.mhss.app.ui.R
import com.mhss.app.domain.model.Task
import com.mhss.app.widget.largeBackgroundBasedOnVersion
import com.mhss.app.widget.largeInnerBackgroundBasedOnVersion
import com.mhss.app.preferences.PrefsConstants
import android.content.Context


@Composable
fun TasksHomeScreenWidget(
    tasks: List<Task>,
    showCompletedTasks: Boolean
) {
    val context = LocalContext.current

    val visibleTasks = if (showCompletedTasks) {
        tasks
    } else {
        tasks.filter { !it.isCompleted }
    }

    val visibilityIcon =
        if (showCompletedTasks)
            R.drawable.ic_visibility
        else
            R.drawable.ic_visibility_off

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .largeBackgroundBasedOnVersion()
    ) {
        Column(
            modifier = GlanceModifier.padding(8.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = context.getString(R.string.tasks),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    modifier = GlanceModifier
                        .padding(horizontal = 8.dp)
                        .clickable(actionRunCallback<NavigateToTasksAction>())
                )

                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(visibilityIcon),
                        modifier = GlanceModifier
                            .size(22.dp)
                            .clickable(actionRunCallback<ToggleShowCompletedTasksAction>()),
                        contentDescription = context.getString(R.string.widget_toggle_completed_tasks),
                        colorFilter = ColorFilter.tint(
                            GlanceTheme.colors.onSecondaryContainer
                        )
                    )

                    Spacer(GlanceModifier.width(12.dp))

                    Image(
                        provider = ImageProvider(R.drawable.ic_add),
                        modifier = GlanceModifier
                            .size(22.dp)
                            .clickable(actionRunCallback<AddTaskAction>()),
                        contentDescription = context.getString(R.string.add_task),
                        colorFilter = ColorFilter.tint(
                            GlanceTheme.colors.onSecondaryContainer
                        )
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
                    .largeInnerBackgroundBasedOnVersion(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (visibleTasks.isEmpty()) {
                    item {
                        Text(
                            text = context.getString(R.string.no_tasks_message),
                            modifier = GlanceModifier.padding(16.dp),
                            style = TextStyle(
                                color = GlanceTheme.colors.secondary,
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                } else {
                    item { Spacer(GlanceModifier.height(6.dp)) }
                    items(visibleTasks) { task ->
                        TaskWidgetItem(task)
                    }
                }
            }
        }
    }
}