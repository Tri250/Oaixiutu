package com.alcedo.studio.ui.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.i18n.stringRes

/**
 * 国内社交平台分享目标。
 */
enum class ShareTarget {
    WECHAT,
    WEIBO,
    REDNOTE,
    SYSTEM
}

/**
 * 导出完成后的分享选项面板：微信 / 微博 / 小红书 / 系统分享。
 *
 * 项目未集成微信/微博 SDK，因此微信与微博通过指定包名的 Intent 拉起对应 App；
 * 若目标 App 未安装，则回退到系统分享选择器。小红书暂无公开分享 API，直接使用系统分享。
 */
@Composable
fun ShareOptionsPanel(
    exportedUri: Uri?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (exportedUri == null) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringRes { shareImage },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShareButton(
                icon = Icons.Default.Chat,
                label = stringRes { shareToWechat },
                onClick = { shareImage(context, exportedUri, ShareTarget.WECHAT) }
            )
            ShareButton(
                icon = Icons.Default.Public,
                label = stringRes { shareToWeibo },
                onClick = { shareImage(context, exportedUri, ShareTarget.WEIBO) }
            )
            ShareButton(
                icon = Icons.Default.Favorite,
                label = stringRes { shareToRednote },
                onClick = { shareImage(context, exportedUri, ShareTarget.REDNOTE) }
            )
            ShareButton(
                icon = Icons.Default.Share,
                label = stringRes { shareToOther },
                onClick = { shareImage(context, exportedUri, ShareTarget.SYSTEM) }
            )
        }
    }
}

@Composable
private fun ShareButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 将导出的图片分享到指定平台。
 */
fun shareImage(context: Context, uri: Uri, target: ShareTarget) {
    val chooserTitle = Strings.current.shareImage
    when (target) {
        ShareTarget.SYSTEM -> shareToSystem(context, uri, chooserTitle)
        ShareTarget.WECHAT -> shareToPackage(
            context, uri, packageName = "com.tencent.mm",
            fallbackChooser = chooserTitle
        )
        ShareTarget.WEIBO -> shareToPackage(
            context, uri, packageName = "com.sina.weibo",
            fallbackChooser = chooserTitle
        )
        ShareTarget.REDNOTE -> shareToSystem(context, uri, chooserTitle)
    }
}

/**
 * 通过系统分享选择器分享图片。
 */
private fun shareToSystem(context: Context, uri: Uri, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        // 无可处理分享的应用，忽略
    }
}

/**
 * 通过指定包名拉起目标 App 分享；若目标 App 未安装则回退到系统分享选择器。
 */
private fun shareToPackage(
    context: Context,
    uri: Uri,
    packageName: String,
    fallbackChooser: String
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(packageName)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // 目标 App 未安装，回退到系统分享
        shareToSystem(context, uri, fallbackChooser)
    }
}
