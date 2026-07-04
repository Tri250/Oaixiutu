package com.alcedo.studio.ui.ai

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun SemanticGenerationDialog(
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("语义生成") },
        text = { Text("AI 语义生成功能暂未实现") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
