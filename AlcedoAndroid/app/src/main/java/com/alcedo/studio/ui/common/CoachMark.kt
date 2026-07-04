package com.alcedo.studio.ui.common

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

data class CoachMarkInfo(
    val key: String,
    val title: String,
    val message: String,
    val dismissText: String = "Got it"
)

@Composable
fun CoachMark(
    info: CoachMarkInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Popup(
        alignment = Alignment.BottomCenter,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            Column(
                modifier = modifier
                    .padding(16.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = info.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss) {
                    Text(
                        text = info.dismissText,
                        color = MaterialTheme.colorScheme.inversePrimary
                    )
                }
            }
        }
    }
}

object CoachMarkManager {
    private const val PREFS_NAME = "alcedo_coach_marks"
    private val shownKeys = mutableSetOf<String>()

    fun hasBeenShown(key: String): Boolean = key in shownKeys

    fun markShown(key: String) {
        shownKeys.add(key)
    }

    fun resetAll() {
        shownKeys.clear()
    }
}
