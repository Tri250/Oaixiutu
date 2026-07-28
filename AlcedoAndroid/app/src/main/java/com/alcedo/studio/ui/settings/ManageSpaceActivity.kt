package com.alcedo.studio.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoTheme

/**
 * 管理空间 Activity：让用户在不卸载应用的情况下清理缓存和临时数据。
 * 作为 android:manageSpaceActivity 在 AndroidManifest 中注册。
 */
class ManageSpaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlcedoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ManageSpaceScreen(
                        onClearCache = { clearCache() },
                        onClearDatabases = { clearDatabases() },
                        onFinish = { finish() }
                    )
                }
            }
        }
    }

    private fun clearCache() {
        runCatching {
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
            Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "清除缓存失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearDatabases() {
        runCatching {
            // 仅清除可重建的缓存数据库，保留用户设置和核心数据
            val dbDir = getDatabasePath("alcedo_sleeve.db").parentFile
            dbDir?.listFiles { _, name ->
                name.endsWith("-wal") || name.endsWith("-shm")
            }?.forEach { it.delete() }
            Toast.makeText(this, "数据库缓存已清除", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "清除数据库缓存失败", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun ManageSpaceScreen(
    onClearCache: () -> Unit,
    onClearDatabases: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "管理存储空间",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
        )
        Text(
            text = "清理缓存和临时数据以释放空间，不会影响您的照片和编辑记录。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        ManageSpaceCard(
            title = "清除图片缓存",
            description = "删除缩略图和预览缓存，应用会自动重新生成。",
            onClick = onClearCache
        )

        Spacer(modifier = Modifier.height(16.dp))

        ManageSpaceCard(
            title = "清除数据库缓存",
            description = "删除数据库临时文件（WAL/SHM），保留核心数据。",
            onClick = onClearDatabases
        )

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("完成")
        }
    }
}

@Composable
private fun ManageSpaceCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
