package com.mhss.app.ui.components.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowRow
import com.mhss.app.domain.model.Priority
import com.mhss.app.domain.model.SubTask
import com.mhss.app.domain.model.Task
import com.mhss.app.ui.R
import com.mhss.app.ui.color
import com.mhss.app.ui.components.common.TTSSpeakerIcon
import com.mhss.app.util.date.formatTime

private fun Modifier.redStrikeThrough(enabled: Boolean, strokeWidthDp: Float = 2f) =
    if (!enabled) this else this.then(
        Modifier.drawWithContent {
            drawContent()
            val y = size.height / 2f
            drawLine(
                color = Color.Red,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidthDp.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    )

@Composable
fun LazyItemScope.TaskCard(
    modifier: Modifier = Modifier,
    task: Task,
    onComplete: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isTTSPlaying by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .animateItem()
            .clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .clickable { onClick() }
                    .padding(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(25.dp)
                            .padding(end = 8.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        drawCircle(
                            color = task.priority.color,
                            radius = size.minDimension / 2
                        )
                    }

                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            textDecoration = TextDecoration.None
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.redStrikeThrough(task.isCompleted)
                    )

                    if (task.dueDate != 0L) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            modifier = Modifier.size(16.dp),
                            painter = painterResource(android.R.drawable.ic_popup_reminder),
                            contentDescription = stringResource(R.string.due_date),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = (task.lastFiredDate.takeIf { it > 0 } ?: task.dueDate)
                                .formatTime(context),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(15.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubTasksProgressBar(
                        modifier = Modifier.padding(end = 8.dp),
                        subTasks = task.subTasks
                    )

                    FlowRow(
                        modifier = Modifier.weight(1f),
                        mainAxisSpacing = 8.dp,
                        crossAxisSpacing = 8.dp
                    ) {
                        task.subTasks.forEach { subTask ->
                            Text(
                                text = subTask.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDecoration = TextDecoration.None
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .redStrikeThrough(subTask.isCompleted)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = TextDecoration.None
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 20,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
fun TaskCheckBox(
    isComplete: Boolean,
    borderColor: Color,
    onComplete: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .border(2.dp, borderColor, CircleShape)
            .clickable { onComplete() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = isComplete) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = null
            )
        }
    }
}

@Composable
fun SubTasksProgressBar(modifier: Modifier = Modifier, subTasks: List<SubTask>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        val completed = remember { subTasks.count { it.isCompleted } }
        val total = subTasks.size
        val progress by remember {
            derivedStateOf { if (total == 0) 0f else completed.toFloat() / total.toFloat() }
        }
        val circleColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        val progressColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)

        Canvas(
            modifier = Modifier.size(16.dp)
        ) {
            drawCircle(
                color = circleColor,
                radius = size.width / 2,
                style = Stroke(width = 8f)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360 * progress,
                style = Stroke(width = 8f, cap = StrokeCap.Round),
                useCenter = false
            )
        }

        Spacer(Modifier.width(8.dp))
        Text(
            text = "$completed/$total",
            style = MaterialTheme.typography.bodyMedium,
            color = progressColor,
        )
    }
}

@Preview
@Composable
fun TaskItemPreview() {
    LazyColumn {
        item {
            TaskCard(
                task = Task(
                    title = "Task 1",
                    description = "Task 1 description",
                    dueDate = 1666999999999L,
                    priority = Priority.MEDIUM,
                    isCompleted = false
                ),
                onComplete = {},
                onClick = {}
            )
        }
    }
}