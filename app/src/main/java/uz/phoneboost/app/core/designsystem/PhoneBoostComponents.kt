package uz.phoneboost.app.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Butun ilova foni: qorong'i premium gradient. */
@Composable
fun PhoneBoostBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(BrandColors.ScreenBrush), content = content)
}

@Composable
fun PhoneBoostCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(PbRadius.Card)
    val source = remember2()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "cardPress")
    var m = modifier.fillMaxWidth().scale(scale).clip(shape)
        .background(BrandColors.CardBrush)
        .pbBorder(shape)
    if (onClick != null) m = m.clickable(interactionSource = source, indication = null, onClick = onClick)
    Column(m.padding(16.dp), content = content)
}

@Composable
private fun remember2(): MutableInteractionSource = remember { MutableInteractionSource() }

private fun Modifier.pbBorder(shape: RoundedCornerShape): Modifier =
    border(BorderStroke(1.dp, BrandColors.BorderBrush), shape)

@Composable
fun PhoneBoostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val source = remember2()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(120), label = "btnPress")
    val shape = RoundedCornerShape(PbRadius.Button)
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).scale(scale)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape).background(BrandColors.ButtonBrush)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun PhoneBoostSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val source = remember2()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(120), label = "btn2Press")
    val shape = RoundedCornerShape(PbRadius.Button)
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).scale(scale)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape).background(BrandColors.CardTop)
            .pbBorder(shape)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = BrandColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun PhoneBoostTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = BrandColors.TextPrimary)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = BrandColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (onBack == null) 8.dp else 0.dp)
        )
    }
}

data class PbNavItem(val route: String, val label: String, val icon: ImageVector, val selected: Boolean)

@Composable
fun PhoneBoostBottomBar(items: List<PbNavItem>, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1A44), Color(0xFF040A1E))))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { item ->
            val tint = if (item.selected) BrandColors.NeonBlue else BrandColors.TextSecondary
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (item.selected) BrandColors.CardTop else Color.Transparent)
                    .clickable { onSelect(item.route) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Text(item.label, color = tint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/** Haqiqiy foiz (0..1) bo'yicha halqa. Qiymat uydirilmaydi: chaqiruvchi beradi. */
@Composable
fun PhoneBoostProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    color: Color = BrandColors.Good,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val p by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.09f
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val tl = Offset(stroke / 2f, stroke / 2f)
            drawArc(Color(0xFF13214A), 135f, 270f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            if (p > 0f) drawArc(
                Brush.linearGradient(listOf(color, BrandColors.Cyan)), 135f, 270f * p, false, tl, arc,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun PhoneBoostMetricCard(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null) {
    PhoneBoostCard(modifier) {
        Text(label, color = BrandColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = BrandColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        if (hint != null) Text(hint, color = BrandColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun PhoneBoostStatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f))
            .colorBorder(color, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)
    ) { Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) }
}

private fun Modifier.colorBorder(color: Color, shape: RoundedCornerShape): Modifier =
    border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), shape)

@Composable
fun PhoneBoostSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked, onCheckedChange = onCheckedChange, modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = BrandColors.Good,
            uncheckedThumbColor = BrandColors.TextSecondary, uncheckedTrackColor = Color(0xFF13214A),
            uncheckedBorderColor = Color(0xFF2F4A93)
        )
    )
}

@Composable
fun PhoneBoostSlider(
    value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Slider(
        value = value, onValueChange = onValueChange, modifier = modifier, valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = BrandColors.Cyan, activeTrackColor = BrandColors.NeonBlue,
            inactiveTrackColor = Color(0xFF13214A)
        )
    )
}

@Composable
fun PhoneBoostDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = null
) {
    val dismiss: (@Composable () -> Unit)? = if (dismissText != null) {
        { TextButton(onClick = onDismiss) { Text(dismissText, color = BrandColors.TextSecondary) } }
    } else null
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = BrandColors.CardTop,
        titleContentColor = BrandColors.TextPrimary,
        textContentColor = BrandColors.TextSecondary,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { Text(text) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = BrandColors.NeonBlue, fontWeight = FontWeight.Bold) } },
        dismissButton = dismiss
    )
}

@Composable
fun PhoneBoostLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandColors.Cyan, trackColor = Color(0xFF13214A))
    }
}
