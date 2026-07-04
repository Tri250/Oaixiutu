package com.alcedo.studio.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun rememberAlcedoSnackbarState(): AlcedoSnackbarState {
    return remember { AlcedoSnackbarState() }
}

class AlcedoSnackbarState {
    var hostState by mutableStateOf(SnackbarHostState())
        internal set

    suspend fun showMessage(message: String, actionLabel: String? = null) {
        hostState.showSnackbar(message, actionLabel, duration = SnackbarDuration.Short)
    }

    suspend fun showError(message: String) {
        hostState.showSnackbar(message, "OK", duration = SnackbarDuration.Long)
    }

    suspend fun showSuccess(message: String) {
        hostState.showSnackbar(message, duration = SnackbarDuration.Short)
    }
}

@Composable
fun AlcedoSnackbarHost(
    state: AlcedoSnackbarState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = state.hostState,
        modifier = modifier.padding(16.dp),
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                shape = MaterialTheme.shapes.small,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.inversePrimary
            )
        }
    )
}
