package uz.phoneboost.app.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.domain.AppTarget
import uz.phoneboost.app.domain.Recommendation

private val scanSteps = listOf(
    R.string.step_storage,
    R.string.step_ram,
    R.string.step_battery,
    R.string.step_temperature,
    R.string.step_charging,
    R.string.step_system,
    R.string.step_large_files
)

/** Tekshiruv davomida: bajarilgan bosqichlar ro‘yxati. */
@Composable
internal fun ScanSteps(done: Int) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        scanSteps.forEachIndexed { index, res ->
            AnimatedVisibility(visible = index < done) {
                Text(
                    "✓ " + stringResource(res),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = BrandColors.Good
                )
            }
        }
    }
}

/** Tekshiruv yakunida: natija va tavsiyalar. */
@Composable
internal fun ScanResults(recommendations: List<Recommendation>, onOpenTarget: (AppTarget) -> Unit = {}) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.result_done),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        if (recommendations.isEmpty()) {
            Text(
                stringResource(R.string.result_ok),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = BrandColors.Good
            )
        } else {
            Text(
                stringResource(R.string.result_count, recommendations.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            recommendations.forEach { RecommendationCard(it, onOpenTarget = onOpenTarget) }
        }
    }
}
