package com.alcedo.studio.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Simple no-op wrapper. PullToRefreshBox is not available in the
 * current Compose Material3 version; this placeholder just renders
 * the content so the rest of the UI compiles without pull-to-refresh.
 */
@Composable
fun AlcedoPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Pull-to-refresh is not available in this Material3 version.
    // Just render content directly.
    content()
}
