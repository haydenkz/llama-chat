package com.llamacpp.mobile.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.llamacpp.mobile.data.remote.LocalNetwork

/**
 * Returns `gate(baseUrl) { action }`: runs [action] right away, or first asks for
 * the Android 17 local network permission when [baseUrl] is on the LAN. The
 * action runs whatever the answer; a denial surfaces as a connection error.
 */
@Composable
fun rememberLocalNetworkGate(): (baseUrl: String, action: () -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending[0]?.invoke()
        pending[0] = null
    }
    return remember(context, launcher) {
        { baseUrl, action ->
            if (LocalNetwork.needsPermission(context, baseUrl)) {
                pending[0] = action
                launcher.launch(LocalNetwork.permission)
            } else {
                action()
            }
        }
    }
}

/** Asks for the local network permission when the active server is on the LAN. */
@Composable
fun LocalNetworkPermissionEffect(baseUrl: String?, onGranted: () -> Unit) {
    val context = LocalContext.current
    val latestOnGranted by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) latestOnGranted()
    }
    LaunchedEffect(baseUrl) {
        if (baseUrl != null && LocalNetwork.needsPermission(context, baseUrl)) {
            launcher.launch(LocalNetwork.permission)
        }
    }
}
