package com.mhss.app.mybrain.presentation.main

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.mhss.app.mybrain.MyForegroundService
import com.mhss.app.mybrain.presentation.app_lock.AppLockManager
import com.mhss.app.ui.ThemeSettings
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.androidx.compose.koinViewModel
import com.mhss.app.mybrain.presentation.auth.AuthViewModel
import com.mhss.app.mybrain.R

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModel()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.all { it.value }) {
            Toast.makeText(
                this,
                "Vissa behörigheter behövs för kalender, notifikationer och alarm",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startMyForegroundService()
        askForBatteryWhitelist()
        requestRequiredPermissions()

        val appLockManager = AppLockManager(this)

        setContent {

            /* =======================
               🔐 AUTH VIEWMODEL
               ======================= */

            val authVm: AuthViewModel = koinViewModel()

            val authMessage by authVm.authMessage.collectAsState()
            val syncResult by authVm.syncResult.collectAsState()
            val context = this@MainActivity

            LaunchedEffect(authMessage) {
                authMessage?.let { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()

                    // ⏱ Vänta innan vi nollställer
                    kotlinx.coroutines.delay(3500)

                    authVm.clearAuthMessage()
                }
            }

            LaunchedEffect(syncResult) {
                when (syncResult) {
                    true -> {
                        kotlinx.coroutines.delay(3800) // vänta tills auth-Toasten är klar
                        Toast.makeText(context, "Synkning klar", Toast.LENGTH_LONG).show()
                    }
                    false -> {
                        kotlinx.coroutines.delay(3800)
                        Toast.makeText(context, "Synkning misslyckades", Toast.LENGTH_LONG).show()
                    }
                    null -> {}
                }
            }

            val blockScreenshots by viewModel.blockScreenshots.collectAsState(initial = false)
            val isSystemDarkMode = isSystemInDarkTheme()
            val isDarkMode by viewModel.themeMode
                .map {
                    it == ThemeSettings.DARK.value ||
                            (it == ThemeSettings.AUTO.value && isSystemDarkMode)
                }
                .collectAsState(true)

            LaunchedEffect(blockScreenshots) {
                if (blockScreenshots) {
                    window.setFlags(
                        LayoutParams.FLAG_SECURE,
                        LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(isDarkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.Transparent.toArgb(),
                        Color.Transparent.toArgb(),
                        detectDarkMode = { isDarkMode }
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.Transparent.toArgb(),
                        Color.Transparent.toArgb(),
                        detectDarkMode = { isDarkMode }
                    )
                )
            }

            /* =======================
               🚀 APP ROOT
               ======================= */

            MyBrainApp(
                viewModel = viewModel,
                isDarkMode = isDarkMode,
                appLockManager = appLockManager
            )
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.USE_FULL_SCREEN_INTENT)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }

    private fun startMyForegroundService() {
        val intent = Intent(this, MyForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException (API 31+)
            android.util.Log.e("MainActivity", "Could not start foreground service", e)
        }
    }

    private fun askForBatteryWhitelist() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val pkg = packageName

        if (pm.isIgnoringBatteryOptimizations(pkg)) {
            // Redan whitelisted → hoppa över
            return
        }

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$pkg")
        }

        // Kolla om det finns en aktivitet som kan hantera intenten
        val resolveInfo = packageManager.queryIntentActivities(
            intent,
            0  // eller PackageManager.MATCH_DEFAULT_ONLY i äldre kod
        )

        if (resolveInfo.isNotEmpty()) {
            // Säker → starta
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Fånga ändå ifall något annat går fel
                e.printStackTrace()
                // Visa toast eller logga
                Toast.makeText(this, "Kunde inte öppna batteriinställningar automatiskt", Toast.LENGTH_LONG).show()
            }
        } else {
            val appName = getString(R.string.app_name)

            Toast.makeText(
                this,
                "Gå till Inställningar → Appar → $appName",
                Toast.LENGTH_LONG
            ).show()

            // Eller öppna generella batterioptimeringssidan om möjligt
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            if (packageManager.queryIntentActivities(fallback, 0).isNotEmpty()) {
                try {
                    startActivity(fallback)
                } catch (ignore: Exception) { }
            }
        }
    }
}