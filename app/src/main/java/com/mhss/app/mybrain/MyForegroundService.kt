package com.mhss.app.mybrain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mhss.app.mybrain.presentation.main.MainActivity
import com.mhss.app.ui.R as UiR
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyForegroundService : Service() {

    private var isForegroundStarted = false
    private var nextTitle: String? = null
    private var nextDescription: String? = null
    private var nextTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ensureForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must come first so startForegroundService() is always satisfied in time
        ensureForeground()
        if (!isForegroundStarted) return START_NOT_STICKY

        intent?.let {
            if (
                it.hasExtra("next_title") ||
                it.hasExtra("next_description") ||
                it.hasExtra("next_time")
            ) {
                nextTitle = it.getStringExtra("next_title")
                nextDescription = it.getStringExtra("next_description")
                nextTime = it.getLongExtra("next_time", 0L)
            }
        }

        try {
            getSystemService(NotificationManager::class.java)
                .notify(1000001, buildNotification())
        } catch (e: Exception) {
            Log.e("MyForegroundService", "Failed to update notification", e)
        }

        return START_NOT_STICKY
    }

    private fun ensureForeground() {
        if (isForegroundStarted) return
        val minimal = NotificationCompat.Builder(this, "my_service_channel")
            .setSmallIcon(UiR.drawable.ic_medicine)
            .setContentTitle(getString(UiR.string.foreground_service_default_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1000001, minimal, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1000001, minimal)
            }
            isForegroundStarted = true
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException (API 31+) when started from background.
            // Alarms are delivered by AlarmManager, so the service is optional: stop instead of crashing.
            Log.w("MyForegroundService", "Foreground start not allowed, stopping service", e)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "my_service_channel",
                "VordNotes",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false) // ⭐ viktigt: ingen badge från foreground-service
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title =
            nextTitle ?: getString(UiR.string.foreground_service_default_title)

        val timeFormatted =
            if (nextTime != 0L) formatTime(nextTime) else ""

        val collapsedText =
            if (timeFormatted.isNotEmpty()) "• $timeFormatted" else ""

        val expandedText = buildString {
            if (!nextDescription.isNullOrEmpty()) {
                append(nextDescription)
                append("\n")
            }
            if (timeFormatted.isNotEmpty()) {
                append("• $timeFormatted")
            }
        }.trim()

        return NotificationCompat.Builder(this, "my_service_channel")
            .setContentIntent(mainPendingIntent)
            .setContentTitle(title)
            .setContentText(collapsedText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedText)
            )
            .setSmallIcon(UiR.drawable.ic_medicine)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun formatTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}
