package com.alcedo.studio.ui.ai

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*
import com.alcedo.studio.di.AppModule
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRatingScreen(navController: NavController) {
    val aiRatingService = AppModule.aiRatingService
    val aiCredentialService = AppModule.aiCredentialService
    val scope = rememberCoroutineScope()

    var selectedMood by remember { mutableStateOf(RatingMood.NORMAL) }
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var providerCredentials by remember { mutableStateOf<List<AiCredential>>(emptyList()) }
    var activeCredential by remember { mutableStateOf<AiCredential?>(null) }

    // Rating state
    var isRating by remember { mutableStateOf(false) }
    var currentRating by remember { mutableStateOf<AiRating?>(null) }
    var ratingHistory by remember { mutableStateOf<List<AiRating>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load credentials
    LaunchedEffect(Unit) {
        providerCredentials = aiCredentialService.getAllCredentials()
        activeCredential = aiCredentialService.getActiveCredential()
        selectedProviderId = activeCredential?.providerId
    }

    // Mood selector dialog
    var showMoodSelector by remember { mutableStateOf(false) }

    // Credential dialog
    var showCredentialDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var selectedProviderType by remember { mutableStateOf(AiProviderType.OPENAI) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 美学评分") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Provider selection
            item {
                ProviderSelectionCard(
                    credentials = providerCredentials,
                    activeCredential = activeCredential,
                    selectedProviderId = selectedProviderId,
                    onSelectProvider = { selectedProviderId = it },
                    onAddCredential = { showCredentialDialog = true }
                )
            }

            // Mood selector
            item {
                MoodSelectorCard(
                    selectedMood = selectedMood,
                    onSelectMood = {
                        selectedMood = it
                        showMoodSelector = false
                    },
                    onShowSelector = { showMoodSelector = true }
                )
            }

            // Rate button
            item {
                Button(
                    onClick = {
                        scope.launch {
                            isRating = true
                            errorMessage = null
                            try {
                                // Use a placeholder imageId for demo
                                // In production, user selects images from the album
                                val rating = aiRatingService.rateImage(
                                    imageId = 0u,
                                    bitmap = null, // Demo: no actual bitmap
                                    mood = selectedMood,
                                    providerId = selectedProviderId
                                )
                                if (rating != null) {
                                    currentRating = rating
                                    ratingHistory = listOf(rating) + ratingHistory
                                } else {
                                    errorMessage = "评分失败，请检查 API 密钥和网络连接"
                                }
                            } catch (e: Exception) {
                                errorMessage = "错误: ${e.message}"
                            } finally {
                                isRating = false
                            }
                        }
                    },
                    enabled = !isRating && selectedProviderId != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isRating) "评分中..." else "开始评分")
                }
            }

            // Error message
            if (errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Current rating result
            if (currentRating != null) {
                item {
                    Text(
                        "评分结果",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    RatingResultCard(currentRating!!)
                }
            }

            // Rating history
            if (ratingHistory.isNotEmpty()) {
                item {
                    Text(
                        "评分历史",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(ratingHistory) { rating ->
                    RatingHistoryCard(rating)
                }
            }

            // Empty state
            if (!isRating && currentRating == null && ratingHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Star,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "选择评分风格和 AI 提供商",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "从相册中选择照片开始评分",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "评分说明",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "AI 美学评分使用远程大语言模型对照片进行多维度评价，包括构图、光影、色彩、技术和内容等方面。评分结果可写入 EXIF 元数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Mood selector dialog
    if (showMoodSelector) {
        AlertDialog(
            onDismissRequest = { showMoodSelector = false },
            title = { Text("选择评分风格") },
            text = {
                Column {
                    RatingMood.entries.forEach { mood ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMood = mood
                                    showMoodSelector = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMood == mood,
                                onClick = {
                                    selectedMood = mood
                                    showMoodSelector = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "${mood.displayName} - ${mood.name}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    mood.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoodSelector = false }) {
                    Text("确定")
                }
            }
        )
    }

    // Credential dialog
    if (showCredentialDialog) {
        var providerType by remember { mutableStateOf(AiProviderType.OPENAI) }
        var apiKey by remember { mutableStateOf("") }
        var showApiKey by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCredentialDialog = false },
            title = { Text("添加 API 密钥") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Provider type selector
                    Text("提供商", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiProviderType.entries.forEach { type ->
                            FilterChip(
                                selected = providerType == type,
                                onClick = { providerType = type },
                                label = {
                                    Text(
                                        when (type) {
                                            AiProviderType.OPENAI -> "OpenAI"
                                            AiProviderType.ANTHROPIC -> "Anthropic"
                                            AiProviderType.DOUBAO -> "火山方舟"
                                            AiProviderType.CUSTOM -> "自定义"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    // API Key input
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (!showApiKey) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "隐藏" else "显示"
                                )
                            }
                        }
                    )

                    Text(
                        "密钥将被加密存储，不会上传到任何服务器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val profile = aiCredentialService.defaultProfiles.find {
                                it.providerType == providerType
                            }
                            val credential = AiCredential(
                                providerId = profile?.providerId ?: providerType.name.lowercase(),
                                providerName = profile?.providerName ?: providerType.name,
                                apiKey = apiKey,
                                apiBaseUrl = profile?.defaultBaseUrl ?: "",
                                modelName = profile?.defaultModel ?: ""
                            )
                            aiCredentialService.saveCredential(credential)
                            aiCredentialService.setActiveCredential(credential.providerId)
                            providerCredentials = aiCredentialService.getAllCredentials()
                            activeCredential = aiCredentialService.getActiveCredential()
                            selectedProviderId = credential.providerId
                        }
                        showCredentialDialog = false
                    },
                    enabled = apiKey.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCredentialDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ProviderSelectionCard(
    credentials: List<AiCredential>,
    activeCredential: AiCredential?,
    selectedProviderId: String?,
    onSelectProvider: (String) -> Unit,
    onAddCredential: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI 提供商", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onAddCredential, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "添加", modifier = Modifier.size(18.dp))
                }
            }

            if (credentials.isEmpty()) {
                Text(
                    "尚未配置 API 密钥。点击 + 添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                credentials.forEach { cred ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProvider(cred.providerId) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProviderId == cred.providerId,
                            onClick = { onSelectProvider(cred.providerId) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                cred.providerName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Key: ${AiCredentialService.maskApiKey(cred.apiKey)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (cred.isActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "活跃",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodSelectorCard(
    selectedMood: RatingMood,
    onSelectMood: (RatingMood) -> Unit,
    onShowSelector: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowSelector() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("评分风格", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${selectedMood.displayName} · ${selectedMood.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    selectedMood.displayName,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RatingResultCard(rating: AiRating) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Stars and mood
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { index ->
                        Icon(
                            if (index < rating.stars) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            tint = if (index < rating.stars) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${rating.stars}/5",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        rating.mood.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Caption
            if (rating.caption.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    rating.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            // Tags
            if (rating.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    rating.tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Reason
            if (rating.reason.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    rating.reason,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Meta info
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Provider: ${rating.providerId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (rating.isWrittenToExif) "已写入EXIF" else "未写入EXIF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rating.isWrittenToExif) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun RatingHistoryCard(rating: AiRating) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { index ->
                        Icon(
                            if (index < rating.stars) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            tint = if (index < rating.stars) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        rating.mood.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (rating.caption.isNotEmpty()) {
                    Text(
                        rating.caption,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                rating.providerId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PasswordVisualTransformation(): VisualTransformation {
    return VisualTransformation { text ->
        TransformedText(
            text = androidx.compose.ui.text.AnnotatedString("*".repeat(text.text.length)),
            offsetMapping = androidx.compose.ui.text.input.OffsetMapping.Identity
        )
    }
}