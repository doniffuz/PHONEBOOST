package uz.phoneboost.app.features.home

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.phoneboost.app.R
import uz.phoneboost.app.core.ui.OnResume
import uz.phoneboost.app.core.ui.findActivity
import uz.phoneboost.app.domain.AppTarget
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.core.designsystem.PhoneBoostCard
import uz.phoneboost.app.domain.HealthLevel
import uz.phoneboost.app.domain.model.DeviceStatus
import java.util.Locale

enum class FeatureKey { SMART, SPEED, GAME, BATTERY, STORAGE, DIAGNOSTICS, PHONE, PRIVACY, APPS }

private data class Feature(val key: FeatureKey, val emoji: String, @StringRes val title: Int)

private val features = listOf(
    Feature(FeatureKey.SMART, "✨", R.string.smart_title),
    Feature(FeatureKey.SPEED, "⚡", R.string.feature_speed),
    Feature(FeatureKey.GAME, "🎮", R.string.feature_game),
    Feature(FeatureKey.BATTERY, "🔋", R.string.feature_battery),
    Feature(FeatureKey.STORAGE, "💾", R.string.feature_memory),
    Feature(FeatureKey.DIAGNOSTICS, "🛡", R.string.feature_diagnostics),
    Feature(FeatureKey.PHONE, "📱", R.string.feature_phone),
    Feature(FeatureKey.PRIVACY, "🔒", R.string.feature_privacy),
    Feature(FeatureKey.APPS, "📦", R.string.feature_apps)
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onFeatureClick: (FeatureKey) -> Unit,
    onOpenTarget: (AppTarget) -> Unit = {}
) {
    OnResume { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(colors.primary.copy(alpha = 0.16f), Color.Transparent),
                    endY = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Header()
            Spacer(Modifier.height(20.dp))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val ringSize = minOf(maxWidth * 0.72f, 260.dp)
                HealthRing(
                    score = state.result?.score,
                    level = state.result?.level,
                    scanning = state.scanning,
                    ringSize = ringSize
                )
            }
            Spacer(Modifier.height(24.dp))
            OptimizeButton(scanning = state.scanning, onClick = viewModel::scan)
            Spacer(Modifier.height(20.dp))
            if (state.scanning) {
                ScanSteps(state.scanStep)
                Spacer(Modifier.height(20.dp))
            } else if (state.showResults) {
                ScanResults(state.recommendations, onOpenTarget)
                Spacer(Modifier.height(20.dp))
            }
            AnnouncementsCard()
            StatGrid(state.status)
            Spacer(Modifier.height(20.dp))
            FeatureGrid(onFeatureClick)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@StringRes
private fun HealthLevel.labelRes(): Int = when (this) {
    HealthLevel.EXCELLENT -> R.string.health_excellent
    HealthLevel.GOOD -> R.string.health_good
    HealthLevel.ATTENTION -> R.string.health_attention
    HealthLevel.CHECK -> R.string.health_check
}

private fun HealthLevel?.color(): Color = when (this) {
    HealthLevel.EXCELLENT, HealthLevel.GOOD -> BrandColors.Good
    HealthLevel.ATTENTION -> BrandColors.Warn
    HealthLevel.CHECK -> BrandColors.Bad
    null -> Color.Gray
}

@Composable
private fun HealthRing(score: Int?, level: HealthLevel?, scanning: Boolean, ringSize: Dp) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue = (score ?: 0) / 100f,
        animationSpec = tween(900),
        label = "score"
    )
    val track = Color(0xFF13214A)
    val brush = Brush.linearGradient(listOf(level.color(), BrandColors.Cyan))

    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = ringSize.toPx() * 0.08f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            if (!scanning && progress > 0f) {
                drawArc(
                    brush = brush,
                    startAngle = 135f,
                    sweepAngle = 270f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            }
        }

        if (scanning) {
            ScanningArc(ringSize)
            Text(
                stringResource(R.string.scanning_message),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = colors.onBackground,
                modifier = Modifier.padding(horizontal = 36.dp)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = score?.toString() ?: "—",
                    fontSize = (ringSize.value * 0.30f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onBackground
                )
                Text(
                    stringResource(R.string.score_out_of),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(level?.labelRes() ?: R.string.unknown),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = level.color()
                )
            }
        }
    }
}

@Composable
private fun ScanningArc(ringSize: Dp) {
    val transition = rememberInfiniteTransition(label = "scan")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rotation"
    )
    val brush = Brush.linearGradient(listOf(BrandColors.Blue, BrandColors.Cyan))
    Canvas(Modifier.fillMaxSize().rotate(rotation)) {
        val strokePx = ringSize.toPx() * 0.08f
        drawArc(
            brush = brush,
            startAngle = 0f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(strokePx / 2f, strokePx / 2f),
            size = Size(size.width - strokePx, size.height - strokePx),
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun OptimizeButton(scanning: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !scanning,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        shadowElevation = 6.dp
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .alpha(if (scanning) 0.7f else 1f)
                .background(BrandColors.ButtonBrush)
                .heightIn(min = 68.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(if (scanning) R.string.optimize_scanning else R.string.optimize_now),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatGrid(status: DeviceStatus?) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.unknown)
    val storage = status?.storageFreeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: unknown
    val ramBusy = status?.busyRamPercent?.let { "$it%" } ?: unknown
    val battery = status?.batteryPercent?.let {
        if (status.isCharging == true) "$it% ⚡" else "$it%"
    } ?: unknown
    val temp = status?.batteryTempC?.let { String.format(Locale.getDefault(), "%.0f°C", it) } ?: unknown

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(R.string.stat_storage, storage, Modifier.weight(1f).fillMaxHeight())
            StatCard(R.string.stat_ram, ramBusy, Modifier.weight(1f).fillMaxHeight())
        }
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(R.string.stat_battery, battery, Modifier.weight(1f).fillMaxHeight())
            StatCard(R.string.stat_temperature, temp, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun StatCard(@StringRes label: Int, value: String, modifier: Modifier = Modifier) {
    PhoneBoostCard(modifier) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BrandColors.TextPrimary
            )
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextSecondary
            )
    }
}

@Composable
private fun FeatureGrid(onClick: (FeatureKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        features.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { feature ->
                    FeatureCard(feature, { onClick(feature.key) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: Feature, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PhoneBoostCard(modifier, onClick = onClick) {
        Column(Modifier.heightIn(min = 80.dp), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BrandColors.NeonBlue.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(feature.emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(feature.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.TextPrimary
            )
        }
    }
}
