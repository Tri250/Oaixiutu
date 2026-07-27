package com.alcedo.studio.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alcedo.studio.permission.PermissionHelper

/**
 * First-launch onboarding screen with 4 pages:
 * 1. Welcome / RAW editing
 * 2. AI semantic search
 * 3. Smart rating
 * 4. Get started (with permission request)
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    val hasMediaPermission = remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 防抖/防重复点击：一旦进入 onFinish 流程就锁死所有按钮，避免用户狂点导致 launcher 多次调用或状态错乱
    var isFinishing by rememberSaveable { mutableStateOf(false) }

    val onFinishUpdated by rememberUpdatedState(newValue = onFinish)
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // 🚨 修复：以前只有「全部授权」或「至少部分授权」才 onFinish，全拒时卡死！
        //   现在无论授予还是拒绝，只要回调来了都 onFinish，继续进入主界面，
        //   后续具体功能（AlbumScreen）会再做二次权限请求，不让用户卡在引导页。
        val allGranted = runCatching { results.isNotEmpty() && results.all { it.value } }.getOrDefault(false)
        val anyGranted = runCatching { results.any { it.value } }.getOrDefault(false)
        hasMediaPermission.value = allGranted
        Log.i("Onboarding",
            "Media permission result: total=${results.size}, allGranted=$allGranted, anyGranted=$anyGranted -> proceeding to main")
        isFinishing = true
        onFinishUpdated()
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
            .systemBarsPadding()
    ) {
        // Skip button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                enabled = !isFinishing,
                onClick = {
                    if (isFinishing) return@TextButton
                    isFinishing = true
                    Log.i("Onboarding", "Skip clicked at page=$currentPage")
                    requestMediaPermission(context, mediaPermissionLauncher, onFinish)
                }
            ) {
                Text("跳过")
            }
        }

        // Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentPage,
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

        // Page indicator
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

        // Navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage > 0) {
                OutlinedButton(
                    enabled = !isFinishing,
                    onClick = {
                        if (isFinishing) return@OutlinedButton
                        currentPage--
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("上一步")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                enabled = !isFinishing,
                onClick = {
                    if (isFinishing) return@Button
                    if (currentPage < pages.size - 1) {
                        currentPage++
                    } else {
                        isFinishing = true
                        Log.i("Onboarding", "Get-started clicked at page=$currentPage")
                        requestMediaPermission(context, mediaPermissionLauncher, onFinish)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (currentPage < pages.size - 1) "下一步" else "开始使用")
                if (currentPage < pages.size - 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun requestMediaPermission(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onFinish: () -> Unit
) {
    runCatching {
        if (PermissionHelper.hasReadMediaAccess(context)) {
            Log.i("Onboarding", "hasReadMediaAccess=true, skipping permission request -> onFinish")
            onFinish()
            return@runCatching
        }
        val permissions = PermissionHelper.getReadMediaPermissions()
        // 🚨 修复：如果 permission 列表为空（未知版本或系统异常），直接放行，
        //   launcher.launch(emptyArray) 在部分机型上会静默不回调 → 卡死！
        if (permissions.isEmpty()) {
            Log.w("Onboarding", "getReadMediaPermissions() returned empty list, skipping -> onFinish")
            onFinish()
            return@runCatching
        }
        Log.i("Onboarding", "requesting ${permissions.size} media permissions: $permissions")
        runCatching { launcher.launch(permissions.toTypedArray()) }.onFailure { e ->
            Log.e("Onboarding", "launcher.launch failed -> proceed anyway", e)
            onFinish()
        }
    }.onFailure { e ->
        Log.e("Onboarding", "requestMediaPermission unexpected error -> proceed anyway", e)
        onFinish()
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
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4f
        )
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)
