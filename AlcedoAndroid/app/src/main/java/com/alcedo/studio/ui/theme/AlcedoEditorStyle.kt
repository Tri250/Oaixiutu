package com.alcedo.studio.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════
// 2026 Alcedo Editor Style System – 参照 AlcedoStudio app_theme.hpp
// ═══════════════════════════════════════════════════════════════════
// 编辑器专用样式令牌和 Composable 样式函数
// 对应参考项目的 Editor*Style 方法集
// 提供 Compose 原生实现,替代 Qt StyleSheet 字符串方式
// ═══════════════════════════════════════════════════════════════════

// ── Editor Panel Radius ──────────────────────────────────────────
object EditorPanelRadius {
    val toggle = AlcedoRadius.sm          // 面板切换标签圆角
    val card = AlcedoRadius.md            // 方法卡片圆角
    val button = AlcedoRadius.sm          // 按钮圆角
    val input = AlcedoRadius.xs           // 输入框圆角
    val comboBox = AlcedoRadius.sm        // 下拉框圆角
    val spinBox = AlcedoRadius.xs         // 数值微调框圆角
    val scrollArea = AlcedoRadius.md      // 滚动区域圆角
    val historyCard = AlcedoRadius.md     // 历史卡片圆角
}

// ── Editor Panel Padding ─────────────────────────────────────────
object EditorPanelPadding {
    val toggleHorizontal = 12.dp          // 面板切换标签水平内边距
    val toggleVertical = 6.dp             // 面板切换标签垂直内边距
    val cardInner = AlcedoSpacing.md      // 卡片内部间距
    val buttonHorizontal = 16.dp          // 按钮水平内边距
    val buttonVertical = 8.dp             // 按钮垂直内边距
    val sectionGap = AlcedoSpacing.sm     // 小节间距
    val panelContent = AlcedoSpacing.lg   // 面板内容内边距
}

// ── Editor Panel Dimensions ──────────────────────────────────────
object EditorPanelDimensions {
    val toggleHeight = 32.dp              // 面板切换标签高度
    val buttonHeight = 36.dp              // 按钮高度
    val cardMinHeight = 40.dp             // 方法卡片最小高度
    val comboBoxHeight = 32.dp            // 下拉框高度
    val spinBoxWidth = 72.dp              // 数值微调框宽度
    val spinBoxHeight = 28.dp             // 数值微调框高度
    val historyCardHeight = 48.dp         // 历史卡片高度
    val scrollBarWidth = 6.dp             // 滚动条宽度
    val scrollBarRadius = 3.dp            // 滚动条圆角
}

// ── Editor Slider Colors ─────────────────────────────────────────
// 参照 app_theme.hpp 的 EditorSlider*Color 方法
object EditorSliderColors {
    @Composable
    fun trackColor(): Color = LocalAlcedoColorScheme().divider

    @Composable
    fun accentColor(positive: Boolean): Color {
        val colors = LocalAlcedoColorScheme()
        return if (positive) colors.accent else colors.danger
    }

    @Composable
    fun borderColor(positive: Boolean): Color {
        val colors = LocalAlcedoColorScheme()
        return if (positive) colors.accent.copy(alpha = 0.4f) else colors.danger.copy(alpha = 0.4f)
    }

    @Composable
    fun handleColor(): Color = LocalAlcedoColorScheme().bgPanel

    @Composable
    fun handleBorderColor(): Color = LocalAlcedoColorScheme().accent
}

// ── Editor Label Style ───────────────────────────────────────────
// 参照 app_theme.hpp 的 EditorLabelStyle(color)
object EditorLabelStyle {
    @Composable
    fun textStyle(): TextStyle = AlcedoFontRoles.uiCaption

    @Composable
    fun textColor(): Color = LocalAlcedoColorScheme().textMuted

    @Composable
    fun numericStyle(): TextStyle = AlcedoFontRoles.dataCaption

    @Composable
    fun numericColor(): Color = LocalAlcedoColorScheme().text
}

// ── Editor Primary Button Style ──────────────────────────────────
// 参照 app_theme.hpp 的 EditorPrimaryButtonStyle(include_disabled)
object EditorPrimaryButtonStyle {
    @Composable
    fun containerColor(): Color = LocalAlcedoColorScheme().accent

    @Composable
    fun contentColor(): Color = LocalAlcedoColorScheme().onAccent

    @Composable
    fun disabledContainerColor(): Color = LocalAlcedoColorScheme().accent.copy(alpha = AlcedoOpacity.disabled)

    @Composable
    fun disabledContentColor(): Color = LocalAlcedoColorScheme().onAccent.copy(alpha = AlcedoOpacity.disabled)

    @Composable
    fun shape() = RoundedCornerShape(EditorPanelRadius.button)

    @Composable
    fun padding() = PaddingValues(
        horizontal = EditorPanelPadding.buttonHorizontal,
        vertical = EditorPanelPadding.buttonVertical
    )

    @Composable
    fun elevation() = ButtonDefaults.buttonElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp
    )
}

// ── Editor Secondary Button Style ────────────────────────────────
// 参照 app_theme.hpp 的 EditorSecondaryButtonStyle()
object EditorSecondaryButtonStyle {
    @Composable
    fun containerColor(): Color = Color.Transparent

    @Composable
    fun contentColor(): Color = LocalAlcedoColorScheme().text

    @Composable
    fun borderColor(): Color = LocalAlcedoColorScheme().outlineVariant

    @Composable
    fun border(): BorderStroke = BorderStroke(AlcedoStroke.normal, borderColor())

    @Composable
    fun shape() = RoundedCornerShape(EditorPanelRadius.button)

    @Composable
    fun padding() = PaddingValues(
        horizontal = EditorPanelPadding.buttonHorizontal,
        vertical = EditorPanelPadding.buttonVertical
    )
}

// ── Editor Panel Toggle Style ────────────────────────────────────
// 参照 app_theme.hpp 的 EditorPanelToggleStyle(active, is_first, is_last)
object EditorPanelToggleStyle {
    @Composable
    fun activeColor(): Color = LocalAlcedoColorScheme().selectedTint

    @Composable
    fun inactiveColor(): Color = Color.Transparent

    @Composable
    fun activeTextColor(): Color = LocalAlcedoColorScheme().accent

    @Composable
    fun inactiveTextColor(): Color = LocalAlcedoColorScheme().textMuted

    @Composable
    fun shape(isFirst: Boolean = false, isLast: Boolean = false): RoundedCornerShape {
        return when {
            isFirst && isLast -> RoundedCornerShape(EditorPanelRadius.toggle)
            isFirst -> RoundedCornerShape(
                topStart = EditorPanelRadius.toggle,
                bottomStart = EditorPanelRadius.toggle
            )
            isLast -> RoundedCornerShape(
                topEnd = EditorPanelRadius.toggle,
                bottomEnd = EditorPanelRadius.toggle
            )
            else -> RoundedCornerShape(0.dp)
        }
    }

    @Composable
    fun textStyle(): TextStyle = AlcedoFontRoles.uiCaptionStrong
}

// ── Editor Method Card Style ─────────────────────────────────────
// 参照 app_theme.hpp 的 EditorMethodCardStyle(active)
object EditorMethodCardStyle {
    @Composable
    fun activeColor(): Color = LocalAlcedoColorScheme().selectedTint

    @Composable
    fun inactiveColor(): Color = Color.Transparent

    @Composable
    fun activeBorderColor(): Color = LocalAlcedoColorScheme().accent.copy(alpha = 0.3f)

    @Composable
    fun inactiveBorderColor(): Color = Color.Transparent

    @Composable
    fun activeTextColor(): Color = LocalAlcedoColorScheme().text

    @Composable
    fun inactiveTextColor(): Color = LocalAlcedoColorScheme().textMuted

    @Composable
    fun shape() = RoundedCornerShape(EditorPanelRadius.card)

    @Composable
    fun textStyle(): TextStyle = AlcedoFontRoles.uiBody
}

// ── Editor Combo Box Style ───────────────────────────────────────
// 参照 app_theme.hpp 的 EditorComboBoxStyle()
object EditorComboBoxStyle {
    @Composable
    fun backgroundColor(): Color = LocalAlcedoColorScheme().bgDeep

    @Composable
    fun borderColor(): Color = LocalAlcedoColorScheme().outlineVariant

    @Composable
    fun textColor(): Color = LocalAlcedoColorScheme().text

    @Composable
    fun indicatorColor(): Color = LocalAlcedoColorScheme().icon

    @Composable
    fun shape() = RoundedCornerShape(EditorPanelRadius.comboBox)

    @Composable
    fun textStyle(): TextStyle = AlcedoFontRoles.uiBody
}

// ── Editor Spin Box Style ────────────────────────────────────────
// 参照 app_theme.hpp 的 EditorSpinBoxStyle()
object EditorSpinBoxStyle {
    @Composable
    fun backgroundColor(): Color = LocalAlcedoColorScheme().bgDeep

    @Composable
    fun borderColor(): Color = LocalAlcedoColorScheme().outlineVariant

    @Composable
    fun textColor(): Color = LocalAlcedoColorScheme().text

    @Composable
    fun shape() = RoundedCornerShape(EditorPanelRadius.spinbox)

    @Composable
    fun textStyle(): TextStyle = AlcedoFontRoles.dataBody
}

// ── Editor Check Box Style ───────────────────────────────────────
// 参照 app_theme.hpp 的 EditorCheckBoxStyle()
object EditorCheckBoxStyle {
    @Composable
    fun checkedColor(): Color = LocalAlcedoColorScheme().accent

    @Composable
    fun uncheckedColor(): Color = LocalAlcedoColorScheme().outlineVariant

    @Composable
    fun checkmarkColor(): Color = LocalAlcedoColorScheme().onAccent

    @Composable
    fun shape() = RoundedCornerShape(AlcedoRadius.xs)
}

// ── Editor Scroll Area Style ─────────────────────────────────────
// 参照 app_theme.hpp 的 EditorScrollAreaStyle()
object EditorScrollAreaStyle {
    @Composable
    fun backgroundColor(): Color = Color.Transparent

    @Composable
    fun scrollBarColor(): Color = LocalAlcedoColorScheme().icon.copy(alpha = 0.3f)

    @Composable
    fun scrollBarHoverColor(): Color = LocalAlcedoColorScheme().icon.copy(alpha = 0.5f)

    val scrollBarWidth = EditorPanelDimensions.scrollBarWidth
    val scrollBarRadius = EditorPanelDimensions.scrollBarRadius
}

// ── Editor History Card Style ────────────────────────────────────
// 参照 app_theme.hpp 的 EditorHistoryCardStyle()
object EditorHistoryCardStyle {
    @Composable
    fun backgroundColor(): Color = LocalAlcedoColorScheme().surfaceContainerLow

    @Composable
    fun activeBackgroundColor(): Color = LocalAlcedoColorScheme().selectedTint

    @Composable
    fun textColor(): Color = LocalAlcedoColorScheme().text

    @Composable
    fun activeTextColor(): Color = LocalAlcedoColorScheme().accent

    @Composable
    fun shape() = RoundedCornerShape(EditorPanelRadius.historyCard)

    @Composable
    fun textStyle(): TextStyle = AlcedoFontRoles.uiBody

    @Composable
    fun captionStyle(): TextStyle = AlcedoFontRoles.uiCaption
}

// ── Editor Transparent Frame Style ───────────────────────────────
// 参照 app_theme.hpp 的 EditorTransparentFrameStyle()
object EditorTransparentFrameStyle {
    @Composable
    fun backgroundColor(): Color = Color.Transparent

    @Composable
    fun borderColor(): Color = Color.Transparent
}
