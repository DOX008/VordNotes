package com.mhss.app.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.content.ContentUris
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import com.mhss.app.util.Constants
import com.mhss.app.domain.model.Priority
import com.mhss.app.domain.model.Task
import com.mhss.app.ui.R
import com.mhss.app.util.date.formatTime

private const val TASKS_GROUP_KEY = "TASKS_NOTIFICATIONS"
private const val TASKS_SUMMARY_ID = 99999

// --- TASK-NOTIS ---
fun NotificationManager.sendNotification(task: Task, context: Context, id: Int) {

    val completeIntent = Intent(context, TaskActionButtonBroadcastReceiver::class.java).apply {
        action = Constants.ACTION_COMPLETE
        putExtra(Constants.TASK_ID_EXTRA, task.id)
    }

    val completePendingIntent = PendingIntent.getBroadcast(
        context,
        task.id,
        completeIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val tasksIntent = Intent(
        Intent.ACTION_VIEW,
        Constants.TASKS_SCREEN_URI.toUri()
    )

    val taskDetailsPendingIntent =
        TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(tasksIntent)
            getPendingIntent(
                0,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

    val subTasksBlock = if (task.subTasks.isNotEmpty()) {
        val lines = task.subTasks.joinToString("\n") { "- ${it.title}" }
        "──────────\n$lines\n──────────"
    } else ""

    val bigText = buildString {
        if (subTasksBlock.isNotBlank()) {
            append(subTasksBlock)
            if (task.description.isNotBlank()) append("\n\n")
        }
        if (task.description.isNotBlank()) append(task.description)
    }

    val previewText =
        (task.subTasks.firstOrNull()?.let { "- ${it.title}" } ?: task.description).take(40)

    val timeToShow = task.dueDate.formatTime(context)

    val notification = NotificationCompat.Builder(context, Constants.REMINDERS_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("${task.title} – $timeToShow")
        .setContentText(previewText)
        .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        .setPriority(
            when (task.priority) {
                Priority.LOW -> NotificationCompat.PRIORITY_DEFAULT
                Priority.MEDIUM -> NotificationCompat.PRIORITY_HIGH
                Priority.HIGH -> NotificationCompat.PRIORITY_MAX
            }
        )
        .addAction(
            R.drawable.ic_check,
            context.getString(R.string.complete),
            completePendingIntent
        )
        .setContentIntent(taskDetailsPendingIntent)
        .setAutoCancel(true)
        .setGroup(TASKS_GROUP_KEY)
        .build()

    notify(task.id, notification)
}

// --- SAMMANFATTNING ---
fun NotificationManager.updateTaskSummary(context: Context, remainingTasks: Int) {
    if (remainingTasks > 1) {

        val tasksIntent = Intent(
            Intent.ACTION_VIEW,
            Constants.TASKS_SCREEN_URI.toUri()
        )

        val pendingToTasks =
            TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(tasksIntent)
                getPendingIntent(
                    TASKS_SUMMARY_ID,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        val summaryNotification =
            NotificationCompat.Builder(context, Constants.REMINDERS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_medicine)
                .setContentTitle(context.getString(R.string.tasksx))
                .setContentText(context.resources.getQuantityString(R.plurals.active_tasks, remainingTasks, remainingTasks))
                .setGroup(TASKS_GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(pendingToTasks)
                .build()

        notify(TASKS_SUMMARY_ID, summaryNotification)
    } else {
        cancel(TASKS_SUMMARY_ID)
    }
}

// --- MEDICIN / ALARM BADGE (ÅTERSTÄLLD) ---
fun NotificationManager.updateMedicineAlarmBadge(
    context: Context,
    activeCount: Int
) {
    if (activeCount <= 0) {
        cancel(Constants.MEDICINE_BADGE_NOTIFICATION_ID)
        return
    }

    val badgeNotification =
        NotificationCompat.Builder(context, Constants.MEDICINE_BADGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_medicine)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.resources.getQuantityString(R.plurals.medicine_active_alarms, activeCount, activeCount))
            .setNumber(activeCount)              // 🔴 BADGE-SIFFRAN
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    notify(Constants.MEDICINE_BADGE_NOTIFICATION_ID, badgeNotification)
}

// --- KALENDER-NOTIS ---
fun NotificationManager.sendCalendarNotification(
    title: String,
    content: String,
    context: Context,
    eventId: Long,
    id: Int
) {
    val eventUri = ContentUris.withAppendedId(
        CalendarContract.Events.CONTENT_URI,
        eventId
    )

    val detailsIntent = Intent(Intent.ACTION_VIEW).setData(eventUri)

    val pending =
        TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(detailsIntent)
            getPendingIntent(
                id,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

    val notification = NotificationCompat.Builder(context, Constants.REMINDERS_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_medicine)
        .setContentTitle(title)
        .setContentText(content.take(40))
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setContentIntent(pending)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

    notify(eventId.toInt(), notification)
}