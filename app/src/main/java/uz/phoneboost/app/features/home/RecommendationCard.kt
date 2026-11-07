package uz.phoneboost.app.features.home

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import uz.phoneboost.app.core.designsystem.PhoneBoostButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.phoneboost.app.R
import uz.phoneboost.app.core.util.openSettings
import uz.phoneboost.app.domain.AppTarget
import uz.phoneboost.app.domain.Recommendation
import uz.phoneboost.app.domain.RecommendationType
import uz.phoneboost.app.domain.SettingsAction

private data class RecTexts(
    @StringRes val title: Int,
    @StringRes val reason: Int,
    @StringRes val todo: Int
)

private fun RecommendationType.texts(): RecTexts = when (this) {
    RecommendationType.STORAGE_LOW -> RecTexts(
        R.string.rec_storage_title, R.string.rec_storage_reason, R.string.rec_storage_todo
    )
    RecommendationType.MEMORY_HIGH -> RecTexts(
        R.string.rec_memory_title, R.string.rec_memory_reason, R.string.rec_memory_todo
    )
    RecommendationType.TEMPERATURE_HIGH -> RecTexts(
        R.string.rec_temp_title, R.string.rec_temp_reason, R.string.rec_temp_todo
    )
    RecommendationType.BATTERY_LOW -> RecTexts(
        R.string.rec_battery_title, R.string.rec_battery_reason, R.string.rec_battery_todo
    )
    RecommendationType.LARGE_FILES -> RecTexts(
        R.string.rec_large_title, R.string.rec_large_reason, R.string.rec_large_todo
    )
    RecommendationType.BATTERY_HEALTH -> RecTexts(
        R.string.rec_bhealth_title, R.string.rec_bhealth_reason, R.string.rec_bhealth_todo
    )
}

/** Muammo, sabab, nima qilish kerak va (kerak bo‘lsa) Sozlamalarni ochish tugmasi. */
@Composable
fun RecommendationCard(
    recommendation: Recommendation,
    modifier: Modifier = Modifier,
    onOpenTarget: (AppTarget) -> Unit = {}
) {
    val context = LocalContext.current
    val type = recommendation.type
    val texts = type.texts()
    val unavailable = stringResource(R.string.settings_unavailable)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        shadowElevation = 2.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "⚠ " + stringResource(texts.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.label_reason) + ": " + stringResource(texts.reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.label_todo) + ": " + stringResource(texts.todo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (type.target != AppTarget.NONE) {
                PhoneBoostButton(
                    text = stringResource(
                        if (type.target == AppTarget.STORAGE) R.string.action_open_storage else R.string.action_open_battery
                    ),
                    onClick = { onOpenTarget(type.target) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (type.action != SettingsAction.NONE) {
                Text(
                    stringResource(R.string.settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PhoneBoostButton(
                    text = stringResource(R.string.action_open_settings),
                    onClick = {
                        if (!context.openSettings(type.action)) {
                            Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
