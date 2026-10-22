package com.mhss.app.mybrain.presentation.main

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.mhss.app.preferences.PrefsConstants
import com.mhss.app.ui.R
import com.mhss.app.mybrain.presentation.app_lock.AppLockManager
import com.mhss.app.preferences.domain.model.*
import com.mhss.app.presentation.SettingsBasicLinkItem
import com.mhss.app.presentation.SettingsItemCard
import com.mhss.app.presentation.SettingsSwitchCard
import com.mhss.app.presentation.SettingsViewModel
import com.mhss.app.ui.FontSizeSettings
import com.mhss.app.ui.StartUpScreenSettings
import com.mhss.app.ui.ThemeSettings
import com.mhss.app.ui.components.common.MyBrainAppBar
import com.mhss.app.ui.getName
import com.mhss.app.ui.navigation.Screen
import com.mhss.app.ui.theme.Rubik
import com.mhss.app.ui.getFontSizeName
import com.mhss.app.ui.toFontFamily
import com.mhss.app.ui.toInt
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.mhss.app.util.RegistrationValidator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mhss.app.mybrain.presentation.auth.AuthViewModel
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.PasswordVisualTransformation


@Composable
fun SettingsScreen(
    navController: NavHostController,
    appLockManager: AppLockManager,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showAppInfo by rememberSaveable { mutableStateOf(false) }
    var showLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showRegisterDialog by rememberSaveable { mutableStateOf(false) }

    val showMaterialYouOption = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authViewModel: AuthViewModel = koinViewModel()
    val currentUser by authViewModel.user.collectAsStateWithLifecycle()
    val syncInProgress by authViewModel.syncInProgress.collectAsStateWithLifecycle()
    val syncResult by authViewModel.syncResult.collectAsStateWithLifecycle()
    val canSync = currentUser != null && !syncInProgress

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { MyBrainAppBar(stringResource(R.string.settings)) }
    ) { paddingValues ->

        // Dialogen MÅSTE ligga inne i composablen
        if (showAppInfo) {
            AlertDialog(
                onDismissRequest = { showAppInfo = false },
                confirmButton = {
                    TextButton(onClick = { showAppInfo = false }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                title = { Text(stringResource(R.string.app_info)) },
                text = { Text(stringResource(R.string.app_info_text)) }
            )
        }

        if (showLoginDialog) {
            LoginDialog(
                onDismiss = { showLoginDialog = false },
                onConfirm = { email, password ->
                    authViewModel.signIn(email, password) { /* valfritt: hantera fel */ }
                    showLoginDialog = false
                },
                onForgotPassword = { email, onFinished, onError ->
                    authViewModel.sendPasswordReset(
                        email = email,
                        onSuccess = {
                            onFinished()
                            Toast.makeText(
                                context,
                                context.getString(R.string.forgot_password_sent_toast, email),
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onError = { msg ->
                            onFinished()
                            onError(msg)
                        }
                    )
                }
            )
        }

        if (showRegisterDialog) {
            RegisterDialog(
                onDismiss = { showRegisterDialog = false },
                onRegister = { email, password, onFinished, onError ->
                    authViewModel.registerAccount(
                        email = email,
                        password = password,
                        onSuccess = {
                            onFinished()
                            showRegisterDialog = false
                            Toast.makeText(
                                context,
                                context.getString(R.string.register_success_logged_in_toast),
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onError = { msg ->
                            onFinished()
                            onError(msg)
                        }
                    )
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val theme by viewModel
                    .getSettings(
                        intPreferencesKey(PrefsConstants.SETTINGS_THEME_KEY),
                        ThemeSettings.AUTO.value
                    ).collectAsStateWithLifecycle(ThemeSettings.AUTO.value)
                ThemeSettingsItem(theme) {
                    when (theme) {
                        ThemeSettings.AUTO.value -> viewModel.saveSettings(
                            intPreferencesKey(PrefsConstants.SETTINGS_THEME_KEY),
                            ThemeSettings.LIGHT.value
                        )

                        ThemeSettings.LIGHT.value -> viewModel.saveSettings(
                            intPreferencesKey(PrefsConstants.SETTINGS_THEME_KEY),
                            ThemeSettings.DARK.value
                        )

                        ThemeSettings.DARK.value -> viewModel.saveSettings(
                            intPreferencesKey(PrefsConstants.SETTINGS_THEME_KEY),
                            ThemeSettings.AUTO.value
                        )
                    }
                }
            }

            item {
                val keywordsFlow = viewModel.getSettings(
                    stringPreferencesKey(PrefsConstants.SCHEDULE_KEYWORDS_KEY),
                    ""
                ).collectAsStateWithLifecycle("")
                var showKeywordsDialog by rememberSaveable { mutableStateOf(false) }
                if (showKeywordsDialog) {
                    var text by remember { mutableStateOf(keywordsFlow.value) }
                    AlertDialog(
                        onDismissRequest = { showKeywordsDialog = false },
                        title = { Text(stringResource(R.string.app_edit_schedule_keywords)) },
                        text = {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text(stringResource(R.string.app_schedule_keywords_label)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.saveSettings(
                                    stringPreferencesKey(PrefsConstants.SCHEDULE_KEYWORDS_KEY),
                                    text.trim()
                                )
                                showKeywordsDialog = false
                            }) { Text(stringResource(android.R.string.ok)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showKeywordsDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                        }
                    )
                }

                SettingsItemCard(
                    cornerRadius = 16.dp,
                    onClick = { showKeywordsDialog = true }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(R.drawable.ic_schedule), contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(text = stringResource(R.string.app_edit_schedule_keywords), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            item {
                val menuStyle by viewModel
                    .getSettings(
                        intPreferencesKey(PrefsConstants.MENU_STYLE_KEY),
                        0
                    ).collectAsStateWithLifecycle(0)

                MenuStyleSettingsItem(
                    menuStyle
                ) { styleValue ->
                    viewModel.saveSettings(
                        intPreferencesKey(PrefsConstants.MENU_STYLE_KEY),
                        styleValue
                    )
                }
            }
            item {
                val screen by viewModel
                    .getSettings(
                        intPreferencesKey(PrefsConstants.DEFAULT_START_UP_SCREEN_KEY),
                        StartUpScreenSettings.SPACES.value
                    ).collectAsStateWithLifecycle(StartUpScreenSettings.SPACES.value)
                StartUpScreenSettingsItem(
                    screen
                ) { screenValue ->
                    viewModel.saveSettings(
                        intPreferencesKey(PrefsConstants.DEFAULT_START_UP_SCREEN_KEY),
                        screenValue
                    )
                }
            }
            item {
                val screen = viewModel
                    .getSettings(
                        intPreferencesKey(PrefsConstants.APP_FONT_KEY),
                        Rubik.toInt()
                    ).collectAsStateWithLifecycle(Rubik.toInt())
                AppFontSettingsItem(
                    screen.value,
                ) { font ->
                    viewModel.saveSettings(
                        intPreferencesKey(PrefsConstants.APP_FONT_KEY),
                        font
                    )
                }
            }
            item {
                val fontSize = viewModel
                    .getSettings(
                        intPreferencesKey(PrefsConstants.FONT_SIZE_KEY),
                        FontSizeSettings.NORMAL.value
                    ).collectAsStateWithLifecycle(FontSizeSettings.NORMAL.value)
                FontSizeSettingsItem(
                    fontSize.value,
                ) { fontSizeValue ->
                    viewModel.saveSettings(
                        intPreferencesKey(PrefsConstants.FONT_SIZE_KEY),
                        fontSizeValue
                    )
                }
            }
            item {
                val block = viewModel
                    .getSettings(
                        booleanPreferencesKey(PrefsConstants.BLOCK_SCREENSHOTS_KEY),
                        false
                    ).collectAsStateWithLifecycle(false)
                SettingsSwitchCard(
                    text = stringResource(R.string.block_screenshots),
                    checked = block.value,
                    painterResource(R.drawable.ic_block_screenshot)
                ) {
                    viewModel.saveSettings(
                        booleanPreferencesKey(PrefsConstants.BLOCK_SCREENSHOTS_KEY),
                        it
                    )
                }
            }

            item {
                val block = viewModel
                    .getSettings(
                        booleanPreferencesKey(PrefsConstants.LOCK_APP_KEY),
                        false
                    ).collectAsStateWithLifecycle(false)
                SettingsSwitchCard(
                    text = stringResource(R.string.lock_app),
                    checked = block.value,
                    iconPainter = painterResource(R.drawable.ic_lock)
                ) {
                    if (appLockManager.canUseFeature()) {
                        viewModel.saveSettings(
                            booleanPreferencesKey(PrefsConstants.LOCK_APP_KEY),
                            it
                        )
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.no_auth_method)
                            )
                        }
                    }
                }
            }


            if (showMaterialYouOption) {
                item {
                    val block = viewModel
                        .getSettings(
                            booleanPreferencesKey(PrefsConstants.SETTINGS_MATERIAL_YOU),
                            false
                        ).collectAsStateWithLifecycle(false)
                    SettingsSwitchCard(
                        text = stringResource(R.string.material_you),
                        checked = block.value,
                        iconPainter = painterResource(R.drawable.ic_palette)
                    ) {
                        viewModel.saveSettings(
                            booleanPreferencesKey(PrefsConstants.SETTINGS_MATERIAL_YOU),
                            it
                        )
                    }
                }
            }
            item {
                SettingsItemCard(
                    cornerRadius = 16.dp,
                    onClick = {
                        if (currentUser == null) {
                            showLoginDialog = true
                        } else {
                            authViewModel.signOut()
                        }
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (currentUser == null) stringResource(R.string.login) else stringResource(R.string.logout),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            item {
                SettingsBasicLinkItem(
                    title = R.string.export_import,
                    icon = R.drawable.ic_import_export,
                    onClick = {
                        navController.navigate(Screen.ImportExportScreen)
                    }
                )
            }

            item {
                SettingsItemCard(
                    cornerRadius = 16.dp,
                    onClick = {
                        if (canSync) {
                            authViewModel.syncNow()
                        }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(if (canSync) 1f else 0.5f)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_import_export),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.sync_now),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (syncInProgress) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            item {
                SettingsItemCard(
                    cornerRadius = 16.dp,
                    onClick = {
                        authViewModel.resyncAllFromCloud()
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_replace),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.app_resync_all),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            item {
                SettingsBasicLinkItem(
                    title = R.string.grant_permission, // återanvänds som rubrik
                    icon = R.drawable.ic_privacy,      // befintlig ikon i projektet
                    onClick = {
                        navController.navigate(Screen.PermissionsScreen)
                    }
                )
            }
            item {
                SettingsBasicLinkItem(
                    title = R.string.integrations,
                    icon = R.drawable.ic_integrations, // byt till ikon som finns i projektet
                    onClick = {
                        navController.navigate(Screen.IntegrationsScreen)
                    }
                )
            }
            item {
                Spacer(Modifier.height(24.dp))
                SettingsItemCard(
                    cornerRadius = 16.dp,
                    onClick = { showAppInfo = true }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.app_info),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.app_info),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            item {
                SettingsItemCard(
                    cornerRadius = 16.dp,
                    onClick = {
                        if (currentUser != null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.register_already_logged_in),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            showRegisterDialog = true
                        }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(if (currentUser == null) 1f else 0.5f)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person_add),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.register_account),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MenuStyleSettingsItem(
    selectedStyle: Int,
    onStyleChange: (Int) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsItemCard(
        cornerRadius = 16.dp,
        onClick = { expanded = true }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_bullet_list),
                contentDescription = stringResource(R.string.menu_style),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.menu_style),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (selectedStyle) {
                        1 -> stringResource(R.string.menu_style_grid)
                        else -> stringResource(R.string.menu_style_list)
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    onClick = {
                        onStyleChange(0)
                        expanded = false
                    },
                    text = {
                        Text(stringResource(R.string.menu_style_list))
                    }
                )
                DropdownMenuItem(
                    onClick = {
                        onStyleChange(1)
                        expanded = false
                    },
                    text = {
                        Text(stringResource(R.string.menu_style_grid))
                    }
                )
            }
        }
    }
}


@Composable
fun ThemeSettingsItem(theme: Int = 0, onClick: () -> Unit = {}) {
    SettingsItemCard(
        onClick = onClick,
        cornerRadius = 18.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_paint_roller),
                contentDescription = stringResource(R.string.app_theme),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.app_theme),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        val themeTextId = remember(theme) {
            when (theme) {
                ThemeSettings.LIGHT.value -> R.string.light_theme
                ThemeSettings.DARK.value -> R.string.dark_theme
                else -> R.string.auto_theme
            }
        }
        val themePainterId = remember(theme) {
            when (theme) {
                ThemeSettings.LIGHT.value -> R.drawable.ic_sun
                ThemeSettings.DARK.value -> R.drawable.ic_dark
                else -> R.drawable.ic_auto
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(themeTextId, label = "themeTex") { id ->
                Text(
                    text = stringResource(id),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            AnimatedContent(themePainterId, label = "themePainter") { id ->
                Icon(
                    painter = painterResource(id),
                    contentDescription = stringResource(themeTextId),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun StartUpScreenSettingsItem(
    screen: Int,
    onScreenChange: (Int) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsItemCard(
        cornerRadius = 16.dp,
        onClick = {
            expanded = true
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_home),
                contentDescription = stringResource(R.string.start_up_screen),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.start_up_screen),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (screen) {
                        StartUpScreenSettings.SPACES.value -> stringResource(R.string.spaces)
                        StartUpScreenSettings.DASHBOARD.value -> stringResource(R.string.dashboard)
                        StartUpScreenSettings.NOTES.value -> stringResource(R.string.notes)
                        StartUpScreenSettings.TASKS.value -> stringResource(R.string.tasks)
                        StartUpScreenSettings.DIARY.value -> stringResource(R.string.diary)
                        StartUpScreenSettings.BOOKMARKS.value -> stringResource(R.string.bookmarks)
                        StartUpScreenSettings.CALENDAR.value -> stringResource(R.string.calendar)
                        StartUpScreenSettings.ASSISTANT.value -> stringResource(R.string.assistant)
                        else -> stringResource(R.string.spaces)
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                val options = listOf(
                    StartUpScreenSettings.SPACES to R.string.spaces,
                    StartUpScreenSettings.DASHBOARD to R.string.dashboard,
                    StartUpScreenSettings.NOTES to R.string.notes,
                    StartUpScreenSettings.TASKS to R.string.tasks,
                    StartUpScreenSettings.DIARY to R.string.diary,
                    StartUpScreenSettings.BOOKMARKS to R.string.bookmarks,
                    StartUpScreenSettings.CALENDAR to R.string.calendar,
                    StartUpScreenSettings.ASSISTANT to R.string.assistant
                )

                options.forEach { (screenOption, stringRes) ->
                    DropdownMenuItem(
                        onClick = {
                            onScreenChange(screenOption.value)
                            expanded = false
                        },
                        text = {
                            Text(
                                text = stringResource(id = stringRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppFontSettingsItem(
    selectedFont: Int,
    onFontChange: (Int) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    // Alla 6 tillgängliga typsnitt baserat på bildens exakta filnamn
    val fonts = listOf(
        FontFamily.Default,
        com.mhss.app.ui.theme.Rubik,
        com.mhss.app.ui.theme.CroissantOne,
        com.mhss.app.ui.theme.NimbusSanL,
        com.mhss.app.ui.theme.Thyssen,
        com.mhss.app.ui.theme.KuraleRegular,
        FontFamily.Monospace,
        FontFamily.SansSerif
    )

    SettingsItemCard(
        cornerRadius = 16.dp,
        onClick = {
            expanded = true
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_font),
                contentDescription = stringResource(R.string.app_font),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.app_font),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentFontFamily = selectedFont.toFontFamily()
                val currentFontName = when (currentFontFamily) {
                    com.mhss.app.ui.theme.Rubik -> "Rubik"
                    com.mhss.app.ui.theme.CroissantOne -> stringResource(R.string.font_croissantone)
                    com.mhss.app.ui.theme.NimbusSanL -> stringResource(R.string.font_nimbussanl)
                    com.mhss.app.ui.theme.Thyssen -> stringResource(R.string.font_thyssen)
                    com.mhss.app.ui.theme.KuraleRegular -> stringResource(R.string.font_kuraleregular)
                    else -> currentFontFamily.getName()
                }

                Text(
                    text = currentFontName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                fonts.forEach { font ->
                    val displayName = when (font) {
                        com.mhss.app.ui.theme.Rubik -> "Rubik"
                        com.mhss.app.ui.theme.CroissantOne -> stringResource(R.string.font_croissantone)
                        com.mhss.app.ui.theme.NimbusSanL -> stringResource(R.string.font_nimbussanl)
                        com.mhss.app.ui.theme.Thyssen -> stringResource(R.string.font_thyssen)
                        com.mhss.app.ui.theme.KuraleRegular -> stringResource(R.string.font_kuraleregular)
                        else -> font.getName()
                    }

                    DropdownMenuItem(
                        onClick = {
                            onFontChange(font.toInt())
                            expanded = false
                        },
                        text = {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    )
                }
            }
        }
    }
}



@Composable
fun FontSizeSettingsItem(
    selectedFontSize: Int,
    onFontSizeChange: (Int) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val fontSizes = FontSizeSettings.entries
    SettingsItemCard(
        cornerRadius = 16.dp,
        onClick = {
            expanded = true
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_font_size),
                contentDescription = stringResource(R.string.font_size),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.font_size),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedFontSize.getFontSizeName(),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                fontSizes.forEach { fontSizeItem ->
                    DropdownMenuItem(
                        onClick = {
                            onFontSizeChange(fontSizeItem.value)
                            expanded = false
                        },
                        text = {
                            Text(
                                text = stringResource(fontSizeItem.title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    )
                }
            }
        }
    }
}

fun Context.getPackageInfo(): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }

}
@Composable
private fun LoginDialog(
    onDismiss: () -> Unit,
    onConfirm: (email: String, password: String) -> Unit,
    onForgotPassword: (
        email: String,
        onFinished: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resetLoading by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf<String?>(null) }
    val confirmEnabled = email.isNotBlank() && password.isNotBlank()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.login)) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; resetError = null },
                    label = { Text(stringResource(R.string.email)) },
                    singleLine = true,
                    isError = resetError != null,
                    supportingText = { resetError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        val trimmed = email.trim()
                        if (!RegistrationValidator.isValidEmail(trimmed)) {
                            resetError = context.getString(R.string.forgot_password_enter_email)
                        } else {
                            resetError = null
                            resetLoading = true
                            onForgotPassword(
                                trimmed,
                                { resetLoading = false },
                                { resetError = it }
                            )
                        }
                    },
                    enabled = !resetLoading
                ) {
                    Text(stringResource(R.string.forgot_password))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(email.trim(), password) },
                enabled = confirmEnabled
            ) {
                Text(stringResource(R.string.sign_in))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun RegisterDialog(
    onDismiss: () -> Unit,
    onRegister: (
        email: String,
        password: String,
        onFinished: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val emailValid = RegistrationValidator.isValidEmail(email)
    val passwordValid = RegistrationValidator.isValidPassword(password)
    val matches = RegistrationValidator.passwordsMatch(password, confirm)
    val passwordTransformation =
        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(text = stringResource(R.string.register_account)) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; serverError = null },
                    label = { Text(stringResource(R.string.email)) },
                    singleLine = true,
                    enabled = !loading,
                    isError = submitted && !emailValid,
                    supportingText = {
                        if (submitted && !emailValid) {
                            Text(stringResource(R.string.register_invalid_email))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; serverError = null },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    enabled = !loading,
                    isError = submitted && !passwordValid,
                    supportingText = {
                        if (submitted && !passwordValid) {
                            Text(stringResource(R.string.register_password_too_short))
                        }
                    },
                    visualTransformation = passwordTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.hide_password else R.string.show_password
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; serverError = null },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    singleLine = true,
                    enabled = !loading,
                    isError = submitted && !matches,
                    supportingText = {
                        if (submitted && !matches) {
                            Text(stringResource(R.string.register_passwords_mismatch))
                        }
                    },
                    visualTransformation = passwordTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                serverError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    submitted = true
                    if (emailValid && passwordValid && matches) {
                        loading = true
                        serverError = null
                        onRegister(
                            email.trim(),
                            password,
                            { loading = false },
                            { serverError = it }
                        )
                    }
                }
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.register))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
