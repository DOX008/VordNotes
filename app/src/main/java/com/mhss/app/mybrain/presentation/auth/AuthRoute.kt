package com.mhss.app.mybrain.presentation.auth

import com.mhss.app.ui.R
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.mhss.app.mybrain.presentation.app_lock.AuthScreen
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthRoute(
    viewModel: AuthViewModel = koinViewModel()
) {
    val context = LocalContext.current

    val authMessage by viewModel.authMessage.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()

    LaunchedEffect(authMessage) {
        authMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearAuthMessage()
        }
    }

    LaunchedEffect(syncResult) {
        when (syncResult) {
            true -> Toast.makeText(context, context.getString(R.string.app_sync_done), Toast.LENGTH_SHORT).show()
            false -> Toast.makeText(context, context.getString(R.string.app_sync_failed), Toast.LENGTH_SHORT).show()
            null -> {}
        }
    }
}
