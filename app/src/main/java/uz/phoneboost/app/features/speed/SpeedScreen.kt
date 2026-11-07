package uz.phoneboost.app.features.speed

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
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

private const val ADB_COMMAND = "adb shell pm grant uz.phoneboost.app android.permission.WRITE_SECURE_SETTINGS"

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
 * Animatsiya tezligi: 0x / 0.5x / 1x / 1.5x / 2x. Uchala qiymat birga o‘zgaradi.
 * Yozish faqat WRITE_SECURE_SETTINGS (foydalanuvchi ADB orqali beradi) bo‘lganda va tasdiqdan keyin ishlaydi.
 * Ruxsat yo‘q bo‘lsa: tugmalar o‘chiq, foydalanuvchiga tushuntirish va Developer options yo‘li ko‘rsatiladi.
 */
@Composable
fun SpeedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var values by remember { mutableStateOf(readAll(context)) }
    var devEnabled by remember { mutableStateOf(AnimationScales.developerOptionsEnabled(context)) }
    var canWrite by remember { mutableStateOf(AnimationScales.canWrite(context)) }
    var hasBackup by remember { mutableStateOf(AnimationScales.hasBackup(context)) }
    var confirm by remember { mutableStateOf<ScaleOption?>(null) }
    val unavailable = stringResource(R.string.settings_unavailable)
    val appliedMsg = stringResource(R.string.speed_applied)
    val restoredMsg = stringResource(R.string.speed_restored)
    val failedMsg = stringResource(R.string.speed_failed)
    val noData = stringResource(R.string.no_data)

    val reload = {
        values = readAll(context)
        devEnabled = AnimationScales.developerOptionsEnabled(context)
        canWrite = AnimationScales.canWrite(context)
        hasBackup = AnimationScales.hasBackup(context)
    }
    OnResume { reload() }

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

        SectionCard(R.string.speed_mode_title) {
            val current = AnimationScales.commonOption(values)
            ScaleOption.entries.chunked(3).forEach { rowOptions ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        FilterChip(
                            selected = current == option,
                            enabled = canWrite,
                            onClick = { if (current != option) confirm = option },
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
                    repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (canWrite) {
                NoteText(stringResource(R.string.speed_perm_ok), color = BrandColors.Good)
            }
            SecondaryButton(
                stringResource(R.string.speed_restore),
                enabled = canWrite && hasBackup,
                onClick = {
                    val ok = AnimationScales.restore(context)
                    reload()
                    Toast.makeText(context, if (ok) restoredMsg else failedMsg, Toast.LENGTH_SHORT).show()
                }
            )
            if (canWrite && !hasBackup) NoteText(stringResource(R.string.speed_restore_none))
        }

        if (!canWrite) {
            SectionCard(R.string.speed_perm_title) {
                NoteText(stringResource(R.string.speed_perm_missing))
                SelectionContainer {
                    Text(
                        ADB_COMMAND,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                NoteText(stringResource(R.string.speed_perm_after))
            }
            SectionCard(R.string.speed_howto_title) {
                NoteText(stringResource(R.string.speed_manual_note))
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

    val pending = confirm
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.speed_confirm_title)) },
            text = { Text(stringResource(R.string.speed_confirm_text, stringResource(pending.label()))) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    val ok = AnimationScales.apply(context, pending)
                    reload()
                    Toast.makeText(context, if (ok) appliedMsg else failedMsg, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.speed_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.speed_cancel)) }
            }
        )
    }
}

private fun readAll(context: android.content.Context): Map<ScaleKind, Float?> =
    ScaleKind.entries.associateWith { AnimationScales.read(context, it) }
