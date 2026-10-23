package com.mhss.app.presentation

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mhss.app.ui.R
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.util.permissions.Permission
import com.mhss.app.util.permissions.rememberPermissionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen() {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Permissions enligt din util
    val notifPermission        = rememberPermissionState(Permission.NOTIFICATIONS)
    val readCalPermission      = rememberPermissionState(Permission.READ_CALENDAR)
    val writeCalPermission     = rememberPermissionState(Permission.WRITE_CALENDAR)
    val exactAlarmsPermission  = rememberPermissionState(Permission.SCHEDULE_ALARMS)
    val cameraPermission = rememberPermissionState(Permission.CAMERA)


    // Komponentnamnet för din service i app-modulen
    val serviceClassName = "com.mhss.app.mybrain.MyForegroundService"

    // Initiera state via faktisk processinfo
    var isServiceRunning by remember {
        mutableStateOf(isServiceRunning(context, serviceClassName))
    }

    Scaffold(
        topBar = { MyBrainAppBar(title = stringResource(R.string.grant_permission)) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // POST_NOTIFICATIONS (Android 13+)
            item {
                SettingsSwitchCard(
                    text = stringResource(R.string.notifications),
                    checked = notifPermission.isGranted,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { checked ->
                    if (!notifPermission.isGranted && checked) {
                        notifPermission.launchRequest()
                    } else if (notifPermission.isGranted && !checked) {
                        // Kan inte “av-bevilja” via runtime – öppna appens inställningar
                        notifPermission.openAppSettings()
                    }
                }
            }
            item {
                SettingsSwitchCard(
                    text = stringResource(R.string.settings_permission_camera),
                    checked = cameraPermission.isGranted,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { checked ->
                    if (!cameraPermission.isGranted && checked) {
                        cameraPermission.launchRequest()
                    } else if (cameraPermission.isGranted && !checked) {
                        cameraPermission.openAppSettings()
                    }
                }
            }
            // Starta/stoppa foreground-service (utan direkt beroende till :app)
            item {
                SettingsSwitchCard(
                    text = stringResource(R.string.always_keep_app_alive),
                    checked = isServiceRunning,
                ) { checked ->
                    if (checked) {
                        val intent = Intent().setClassName(context, serviceClassName)
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            isServiceRunning = true
                        } catch (e: Exception) {
                            // e.g. ForegroundServiceStartNotAllowedException (API 31+)
                            isServiceRunning = false
                        }
                    } else {
                        context.stopService(Intent().setClassName(context, serviceClassName))
                        isServiceRunning = false
                    }
                }
            }

            // READ_CALENDAR
            item {
                SettingsSwitchCard(
                    text = stringResource(R.string.calendar) + " (READ)",
                    checked = readCalPermission.isGranted
                ) { checked ->
                    if (!readCalPermission.isGranted && checked) {
                        readCalPermission.launchRequest()
                    } else if (readCalPermission.isGranted && !checked) {
                        readCalPermission.openAppSettings()
                    }
                }
            }

            // WRITE_CALENDAR
            item {
                SettingsSwitchCard(
                    text = stringResource(R.string.calendar) + " (WRITE)",
                    checked = writeCalPermission.isGranted
                ) { checked ->
                    if (!writeCalPermission.isGranted && checked) {
                        writeCalPermission.launchRequest()
                    } else if (writeCalPermission.isGranted && !checked) {
                        writeCalPermission.openAppSettings()
                    }
                }
            }

            // SCHEDULE_EXACT_ALARM – särskild systemskärm via din PermissionState
            item {
                SettingsItemCard(
                    onClick = { exactAlarmsPermission.launchRequest() },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    SettingsBasicLinkItem(
                        title = R.string.grant_permission,
                        icon = com.mhss.app.ui.R.drawable.ic_alarm,
                        onClick = { exactAlarmsPermission.launchRequest() }
                    )
                }
            }
        }
    }
}

/**
 * Checkar om en service (via fullt klassnamn) kör i den egna processen.
 * ActivityManager#getRunningServices är deprecierad men fungerar för egen process.
 */
private fun isServiceRunning(context: Context, serviceClassName: String): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    val running = am.getRunningServices(Int.MAX_VALUE)
    return running.any { it.service.className == serviceClassName }
}
