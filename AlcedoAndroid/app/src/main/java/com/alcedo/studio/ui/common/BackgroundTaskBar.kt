package com.alcedo.studio.ui.common

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.domain.service.TaskSnapshot
import com.alcedo.studio.domain.service.TaskStatus
import com.alcedo.studio.domain.service.TaskType
import com.alcedo.studio.i18n.stringRes
import kotlinx.coroutines.delay

@Composable
fun BackgroundTaskBar(
    activeTasks: List<TaskSnapshot>,
    onCancelTask: (String) -> Unit,
    onTaskClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val runningTasks = activeTasks.filter {
        it.status == TaskStatus.RUNNING || it.status == TaskStatus.PENDING || it.status == TaskStatus.RETRYING
    }
    val completedTasks = activeTasks.filter {
        it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED || it.status == TaskStatus.CANCELLED
    }

    // Only return when BOTH running and completed tasks are empty
    if (runningTasks.isEmpty() && completedTasks.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(true) }

    // Auto-dismiss completed tasks after 5 seconds (but keep bar visible until then)
    LaunchedEffect(runningTasks.isEmpty(), completedTasks.isNotEmpty()) {
        if (runningTasks.isEmpty() && completedTasks.isNotEmpty()) {
            delay(5000)
            isVisible = false
        }
    }

    // Reset visibility when new tasks start
    LaunchedEffect(runningTasks.isNotEmpty()) {
        if (runningTasks.isNotEmpty()) {
            isVisible = true
        }
    }

    if (!isVisible) return

    Column(modifier = modifier) {
        // Expandable popover
        AnimatedVisibility(visible = isExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringRes { backgroundTasks },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { isExpanded = false }) {
                            // When expanded, show ExpandLess (up arrow) to collapse
                            Icon(Icons.Default.ExpandLess, contentDescription = stringRes { collapse })
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp)
                    ) {
                        items(runningTasks, key = { it.taskId }) { task ->
                            TaskDetailItem(
                                task = task,
                                onCancel = { onCancelTask(task.taskId) },
                                onClick = { onTaskClick?.invoke(task.taskId) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (completedTasks.isNotEmpty()) {
                            item {
                                Text(
                                    stringRes { completed },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(completedTasks.take(3), key = { it.taskId }) { task ->
                                CompletedTaskItem(
                                    task = task,
                                    onClick = { onTaskClick?.invoke(task.taskId) }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        // Bottom bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = if (isExpanded) 0.dp else 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (runningTasks.isNotEmpty()) {
                    // Running tasks mode
                    Icon(
                        taskTypeIcon(runningTasks.firstOrNull()?.type),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (runningTasks.size == 1) runningTasks.first().title
                            else stringRes { this.activeTasks }.format(runningTasks.size),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (runningTasks.size == 1 && runningTasks.first().progress > 0f) {
                            LinearProgressIndicator(
                                progress = { runningTasks.first().progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val avgProgress = runningTasks.map { it.progress }.average().toFloat()
                    Text(
                        "${(avgProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    // All tasks completed – show completion summary
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        // UX 修复: 使用主题色而非硬编码绿色
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringRes { tasksCompleted }.format(completedTasks.size),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        // When expanded show ExpandLess (up), when collapsed show ExpandMore (down)
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) stringRes { collapse } else stringRes { expand }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskDetailItem(
    task: TaskSnapshot,
    onCancel: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                taskTypeIcon(task.type),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    )
                }
            }
            Text(
                "${(task.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp)
            )
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringRes { cancel },
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompletedTaskItem(
    task: TaskSnapshot,
    onClick: () -> Unit
) {
    val isSuccess = task.status == TaskStatus.COMPLETED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            // UX 修复: 使用主题色而非硬编码绿色
            tint = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            task.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun taskTypeIcon(type: TaskType?) = when (type) {
    TaskType.IMPORT -> Icons.Default.FileDownload
    TaskType.EXPORT -> Icons.Default.FileUpload
    TaskType.AI_TAGGING -> Icons.Default.AutoAwesome
    TaskType.MODEL_DOWNLOAD -> Icons.Default.ModelTraining
    TaskType.THUMBNAIL_GEN -> Icons.Default.Image
    TaskType.SEMANTIC_INDEXING -> Icons.Default.Search
    null -> Icons.Default.Pending
}
