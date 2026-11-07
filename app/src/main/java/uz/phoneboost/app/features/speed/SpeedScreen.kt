package uz.phoneboost.app.features.speed

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.OnResume
import uz.phoneboost.app.core.ui.PrimaryButton
import uz.phoneboost.app.core.ui.SecondaryButton
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.util.openAboutPhone
import uz.phoneboost.app.core.util.openDeveloperOptions
import uz.phoneboost.app.data.device.AnimationScales
import uz.phoneboost.app.data.device.ScaleKind
import uz.phoneboost.app.data.device.ScaleOption
import java.util.Locale

@StringRes
private fun ScaleKind.title(): Int = when (this) {
    ScaleKind.WINDOW -> R.string.scale_window
    ScaleKind.TRANSITION -> R.string.scale_transition
    ScaleKind.ANIMATOR -> R.string.scale_animator
}

@StringRes
private fun ScaleOption.label(): Int = when (this) {
    ScaleOption.NORMAL -> R.string.scale_opt_1x
    ScaleOption.HALF -> R.string.scale_opt_half
    ScaleOption.OFF -> R.string.scale_opt_off
}

/**
 * Android animatsiya sozlamalarini oddiy ilova o‘zgartira olmaydi (WRITE_SECURE_SETTINGS / ADB / root kerak).
 * Shuning uchun: joriy qiymat HAQIQIY o‘qiladi, o‘zgartirish uchun foydalanuvchi Developer options’ga olib boriladi.
 */
@Composable
fun SpeedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var values by remember { mutableStateOf(readAll(context)) }
    var devEnabled by remember { mutableStateOf(AnimationScales.developerOptionsEnabled(context)) }
    var pending by remember { mutableStateOf<Pair<ScaleKind, ScaleOption>?>(null) }
    val unavailable = stringResource(R.string.settings_unavailable)

    OnResume {
        values = readAll(context)
        devEnabled = AnimationScales.developerOptionsEnabled(context)
    }

    FeaturePage(R.string.feature_speed, onBack) {
        NoteText(stringResource(R.string.speed_note))

        ScaleKind.entries.forEach { kind ->
            val current = values[kind]
            val currentOption = AnimationScales.matchOption(current)
            SectionCard(kind.title()) {
                val currentText = when {
                    current == null -> stringResource(R.string.no_data)
                    currentOption != null -> stringResource(currentOption.label())
                    else -> String.format(Locale.getDefault(), "%.2fx", current)
                }
                Text(
                    stringResource(R.string.scale_current, currentText),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScaleOption.entries.forEach { option ->
                        FilterChip(
                            selected = currentOption == option,
                            onClick = { pending = if (currentOption == option) null else kind to option },
                            label = {
                                Text(
                                    stringResource(option.label()),
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        )
                    }
                }
                if (currentOption != null && pending?.first != kind) {
                    NoteText(stringResource(R.string.scale_is_set), color = BrandColors.Good)
                }
            }
        }

        val p = pending
        if (p != null) {
            SectionCard(R.string.speed_howto_title) {
                Text(
                    stringResource(
                        R.string.speed_steps,
                        stringResource(p.first.title()),
                        stringResource(p.second.label())
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!devEnabled) {
                    NoteText(stringResource(R.string.speed_dev_hidden))
                    SecondaryButton(stringResource(R.string.speed_open_about), onClick = {
                        if (!context.openAboutPhone()) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                    })
                }
                PrimaryButton(stringResource(R.string.speed_open_dev), onClick = {
                    if (!context.openDeveloperOptions()) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                })
            }
        }

        NoteText(stringResource(R.string.speed_effect_note))
    }
}

private fun readAll(context: android.content.Context): Map<ScaleKind, Float?> =
    ScaleKind.entries.associateWith { AnimationScales.read(context, it) }
