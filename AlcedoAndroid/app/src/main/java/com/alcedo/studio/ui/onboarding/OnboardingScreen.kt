package com.alcedo.studio.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

/**
 * First-run onboarding. A swipeable pager introducing the app's core
 * capabilities (album, editor, AI, privacy) with a "Get started" action that
 * completes onboarding via [onDone].
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    val scope = rememberCoroutineScope()
    val pages = listOf(
        OnboardingPage(Icons.Outlined.Collections, s.welcome, s.welcomeSubtitle),
        OnboardingPage(Icons.Outlined.Edit, s.tabEditor, s.openInEditor),
        OnboardingPage(Icons.Outlined.AutoAwesome, s.aiTitle, s.aiSearchHint),
        OnboardingPage(Icons.Outlined.Lock, s.privacyPolicy, s.cloudLlmDesc),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AlcedoColors.SurfaceBase)
            .padding(DesignTokens.spacingXxl),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            OnboardingPageContent(pages[page])
        }

        // Page indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = DesignTokens.spacingXxs)
                        .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                        .background(
                            if (i == pagerState.currentPage) AlcedoColors.AccentBlue else AlcedoColors.TextDisabled,
                            CircleShape,
                        ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.spacingLg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDone) {
                Text(s.close, color = AlcedoColors.TextTertiary)
            }
            if (pagerState.currentPage == pages.lastIndex) {
                Button(onClick = onDone) { Text(s.done) }
            } else {
                Button(onClick = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }) { Text("→") }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            tint = AlcedoColors.AccentBlue,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            color = AlcedoColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = DesignTokens.spacingLg),
        )
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyMedium,
            color = AlcedoColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = DesignTokens.spacingSm),
        )
    }
}
