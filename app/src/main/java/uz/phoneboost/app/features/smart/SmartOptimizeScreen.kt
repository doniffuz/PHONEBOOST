package uz.phoneboost.app.features.smart

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.core.designsystem.PhoneBoostButton
import uz.phoneboost.app.core.designsystem.PhoneBoostProgressRing
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.ui.formatBytes
import uz.phoneboost.app.data.optimize.SmartOptimizer
import uz.phoneboost.app.data.optimize.SmartResult
import uz.phoneboost.app.domain.AppTarget
import uz.phoneboost.app.domain.DiagKind
import uz.phoneboost.app.features.home.RecommendationCard

@Composable
fun SmartOptimizeScreen(onBack: () -> Unit, onOpenTarget: (AppTarget) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SmartResult?>(null) }
    var failed by remember { mutableStateOf(false) }

    FeaturePage(R.string.smart_title, onBack) {
        NoteText(stringResource(R.string.smart_disclaimer))
        PhoneBoostButton(
            stringResource(if (running) R.string.smart_running else R.string.smart_run),
            enabled = !running,
            onClick = {
                running = true
                failed = false
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { SmartOptimizer(context).run() }.getOrNull() }
                    result = r
                    failed = r == null
                    running = false
                }
            }
        )
        if (failed) NoteText(stringResource(R.string.smart_error), color = BrandColors.Bad)

        val r = result
        if (r != null) {
            r.score?.let { s ->
                PhoneBoostProgressRing(s / 100f, size = 140.dp) {
                    Text("$s", color = BrandColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            SectionCard(R.string.smart_problems) {
                if (r.problems.isEmpty()) NoteText(stringResource(R.string.smart_none), color = BrandColors.Good)
                r.problems.forEach { Text("• " + stringResource(it.titleRes()), color = BrandColors.Warn) }
            }
            SectionCard(R.string.smart_performed) {
                r.performed.forEach { Text("✓ " + stringResource(it), color = BrandColors.Good, fontWeight = FontWeight.SemiBold) }
                if (r.freedBytes > 0) NoteText(formatBytes(context, r.freedBytes) ?: "")
            }
            SectionCard(R.string.smart_not_performed) {
                r.notPerformed.forEach { n ->
                    Text("✕ " + stringResource(n.title), color = BrandColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    NoteText(stringResource(n.reason))
                    Spacer(Modifier.size(6.dp))
                }
            }
            SectionCard(R.string.smart_reco) {
                if (r.recommendations.isEmpty()) NoteText(stringResource(R.string.smart_none_reco))
            }
            r.recommendations.forEach { RecommendationCard(it, onOpenTarget = onOpenTarget) }
        }
    }
}

private fun DiagKind.titleRes(): Int = when (this) {
    DiagKind.RAM -> R.string.stat_ram
    DiagKind.STORAGE -> R.string.stat_storage
    DiagKind.BATTERY -> R.string.stat_battery
    DiagKind.TEMPERATURE -> R.string.stat_temperature
    else -> R.string.unknown
}
