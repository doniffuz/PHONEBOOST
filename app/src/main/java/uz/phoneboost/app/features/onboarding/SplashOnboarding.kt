package uz.phoneboost.app.features.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.core.designsystem.PhoneBoostBackground
import uz.phoneboost.app.core.designsystem.PhoneBoostButton

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) { delay(1500); done() }
    val t = rememberInfiniteTransition(label = "splash")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "a")
    PhoneBoostBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Text("🚀", fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            Text("PHONEBOOST", color = BrandColors.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("MAX", color = BrandColors.Cyan, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.splash_tagline), color = BrandColors.TextSecondary,
                fontSize = 16.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Canvas(Modifier.size(44.dp)) {
                val w = size.minDimension * 0.12f
                drawArc(
                    Brush.linearGradient(listOf(BrandColors.Blue, BrandColors.Cyan)), angle, 100f, false,
                    Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.splash_developer), color = BrandColors.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(R.string.onb_1_title, R.string.onb_2_title, R.string.onb_3_title)
    val texts = listOf(R.string.onb_1_text, R.string.onb_2_text, R.string.onb_3_text)
    val icons = listOf("📊", "🎮", "🔒")
    val last = page == titles.lastIndex
    PhoneBoostBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.onb_skip), color = BrandColors.TextSecondary) }
            }
            Spacer(Modifier.weight(1f))
            Text(icons[page], fontSize = 80.sp)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(titles[page]), color = BrandColors.TextPrimary, fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(texts[page]), color = BrandColors.TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            PhoneBoostButton(
                stringResource(if (last) R.string.onb_start else R.string.onb_next),
                onClick = { if (last) onFinish() else page += 1 }
            )
        }
    }
}

