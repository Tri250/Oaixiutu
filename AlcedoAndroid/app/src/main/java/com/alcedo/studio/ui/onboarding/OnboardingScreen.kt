package com.alcedo.studio.ui.onboarding

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alcedo.studio.permission.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val activity = (LocalView.current.context as? Activity) ?: (context as? Activity)

    var isFinishing by rememberSaveable { mutableStateOf(false) }
    var permissionLaunched by rememberSaveable { mutableStateOf(false) }
    var clickCount by rememberSaveable { mutableIntStateOf(0) }
    var watchdogArmed by rememberSaveable { mutableStateOf(false) }

    val finishAction: (String) -> Unit = { reason ->
        if (isFinishing) {
            Log.w("Onboarding", "finishAction[$reason] skipped: already finishing")
            return
        }
        isFinishing = true
        Log.i("Onboarding", "[$reason] finish -> entering main screen")
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        runCatching { onFinish() }.onFailure { e ->
            Log.wtf("Onboarding", "onFinish callback threw, forcing recomposition to unblock", e)
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            watchdogArmed = false
            val allGranted = runCatching { results.isNotEmpty() && results.all { it.value } }.getOrDefault(false)
            val anyGranted = runCatching { results.any { it.value } }.getOrDefault(false)
            Log.i("Onboarding",
                "Permission callback: total=${results.size} all=$allGranted any=$anyGranted")
            finishAction("permissionResultCallback")
        }
    )

    LaunchedEffect(watchdogArmed, permissionLaunched) {
        if (watchdogArmed && permissionLaunched) {
            Log.i("Onboarding", "Watchdog armed: will force-finish in 8s if no callback")
            delay(8000L)
            if (watchdogArmed && !isFinishing) {
                Log.e("Onboarding", "Watchdog TRIGGERED: permission callback never arrived — force unblocking")
                finishAction("watchdogTimeout")
            }
        }
    }

    val pages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Default.PhotoCamera,
                title = "专业 RAW 修图",
                description = "支持 500+ 相机 RAW 格式，GPU 加速实时预览，\n从曝光到色彩全链路精准控制"
            ),
            OnboardingPage(
                icon = Icons.Default.AutoAwesome,
                title = "AI 语义搜索",
                description = "用自然语言描述图片内容，\nAI 理解含义并智能匹配相似图片"
            ),
            OnboardingPage(
                icon = Icons.Default.Star,
                title = "智能美学评分",
                description = "AI 从构图、曝光、色彩、锐度等维度\n评估图片质量，帮你快速精选"
            ),
            OnboardingPage(
                icon = Icons.Default.CheckCircle,
                title = "开始使用",
                description = "导入照片，开始你的专业修图之旅"
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    if (isFinishing) return@TextButton
                    runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    Log.i("Onboarding", "Skip clicked @ page=$currentPage")
                    permissionLaunched = true
                    watchdogArmed = true
                    requestMediaPermission(
                        context = context,
                        activity = activity,
                        launcher = mediaPermissionLauncher,
                        onFinish = { finishAction("skipButton") }
                    )
                }
            ) {
                Text("跳过")
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentPage,
                label = "OnboardingPageTransition",
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                    }
                }
            ) { page ->
                OnboardingPageContent(pages[page])
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            pages.indices.forEach { index ->
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == currentPage) 8.dp else 6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = if (index == currentPage)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                ) {}
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage > 0) {
                OutlinedButton(
                    onClick = {
                        if (isFinishing) return@OutlinedButton
                        runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        currentPage--
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("上一步")
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Button(
                onClick = {
                    if (isFinishing) return@Button
                    runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    if (currentPage < pages.size - 1) {
                        currentPage++
                    } else {
                        clickCount++
                        Log.i("Onboarding", "Get-started clicked @ page=$currentPage (clickCount=$clickCount)")
                        if (clickCount >= 5) {
                            Log.w("Onboarding", "Emergency bypass: 5x rapid clicks — force finish without permission")
                            finishAction("emergencyBypass5x")
                            return@Button
                        }
                        permissionLaunched = true
                        watchdogArmed = true
                        scope.launch {
                            delay(400)
                            if (!isFinishing && watchdogArmed && !permissionLaunched) {
                                Log.w("Onboarding", "Slow launch fallback: force finish after 400ms")
                                finishAction("slowLaunchFallback")
                            }
                        }
                        requestMediaPermission(
                            context = context,
                            activity = activity,
                            launcher = mediaPermissionLauncher,
                            onFinish = { finishAction("getStartedButton") }
                        )
                    }
                },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (currentPage < pages.size - 1) "下一步" else "开始使用",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (currentPage < pages.size - 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun requestMediaPermission(
    context: Context,
    activity: Activity?,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onFinish: () -> Unit
) {
    runCatching {
        if (PermissionHelper.hasReadMediaAccess(context)) {
            Log.i("Onboarding", "hasReadMediaAccess=true → skip permission → onFinish")
            onFinish()
            return@runCatching
        }
        val permissions = PermissionHelper.getReadMediaPermissions()
        if (permissions.isEmpty()) {
            Log.w("Onboarding", "permission list is empty → onFinish as fallback")
            onFinish()
            return@runCatching
        }
        Log.i("Onboarding", "launching permissions=$permissions (activity=${activity != null})")
        runCatching { launcher.launch(permissions.toTypedArray()) }.onFailure { e ->
            Log.e("Onboarding", "launcher.launch failed → fallback onFinish", e)
            onFinish()
        }
    }.onFailure { e ->
        Log.e("Onboarding", "requestMediaPermission top-level failure → fallback onFinish", e)
        runCatching { onFinish() }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(128.dp),
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        ProvideTextStyle(value = MaterialTheme.typography.headlineMedium) {
            Text(
                text = page.title,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.45f
        )
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)
