package uz.phoneboost.app.features.speed

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uz.phoneboost.app.R
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.InfoRow
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
    ScaleOption.OFF -> R.string.scale_opt_off
    ScaleOption.HALF -> R.string.scale_opt_half
    ScaleOption.NORMAL -> R.string.scale_opt_1x
    ScaleOption.SLOW -> R.string.scale_opt_slow
    ScaleOption.SLOWER -> R.string.scale_opt_slower
}

/**
 * Animatsiya tezligi: faqat REAL joriy qiymatlar va Developer options bo‘yicha yo‘riqnoma.
 * Ilova hech narsani o‘zgartirmaydi va “tezlashtirdim” demaydi.
 */
@Composable
fun SpeedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var values by remember { mutableStateOf(readAll(context)) }
    var devEnabled by remember { mutableStateOf(AnimationScales.developerOptionsEnabled(context)) }
    val unavailable = stringResource(R.string.settings_unavailable)
    val noData = stringResource(R.string.no_data)

    OnResume {
        values = readAll(context)
        devEnabled = AnimationScales.developerOptionsEnabled(context)
    }

    FeaturePage(R.string.feature_speed, onBack) {
        NoteText(stringResource(R.string.speed_note))

        SectionCard(R.string.speed_current_title) {
            ScaleKind.entries.forEach { kind ->
                val v = values[kind]
                val option = AnimationScales.matchOption(v)
                val text = when {
                    v == null -> noData
                    option != null -> stringResource(option.label())
                    else -> String.format(Locale.getDefault(), "%.2fx", v)
                }
                InfoRow(stringResource(kind.title()), text)
            }
        }

        SectionCard(R.string.speed_howto_title) {
            NoteText(stringResource(R.string.speed_guide))
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

        NoteText(stringResource(R.string.speed_effect_note))
    }
}

private fun readAll(context: android.content.Context): Map<ScaleKind, Float?> =
    ScaleKind.entries.associateWith { AnimationScales.read(context, it) }
