package com.alcedo.studio.ui.ai

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
import androidx.compose.ui.text.font.FontWeight
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 评分") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Mood selector
            MoodSelector(
                selectedMood = selectedMood,
                onSelectMood = { selectedMood = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Provider selector
            Text("AI 提供商", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            // Placeholder for credential selection
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("未配置 AI 提供商", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MoodSelector(
    selectedMood: RatingMood,
    onSelectMood: (RatingMood) -> Unit
) {
    Column {
        Text("评分风格", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RatingMood.entries.forEach { mood ->
                FilterChip(
                    selected = mood == selectedMood,
                    onClick = { onSelectMood(mood) },
                    label = { Text(mood.name) }
                )
            }
        }
    }
}
