package uz.phoneboost.app.features.automation

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.phoneboost.app.R
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.OnResume
import uz.phoneboost.app.core.ui.PrimaryButton
import uz.phoneboost.app.core.ui.SecondaryButton
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.util.openOwnNotificationSettings
import uz.phoneboost.app.data.automation.AutomationNotifier
import uz.phoneboost.app.data.automation.AutomationPrefs
import uz.phoneboost.app.data.automation.AutomationWorker
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutomationScreen(onBack: () -> Unit, onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AutomationPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var alerts by remember { mutableStateOf(prefs.alerts) }
    var lastRun by remember { mutableStateOf(prefs.lastRunMillis) }
    val deniedMsg = stringResource(R.string.auto_perm_denied)
    val startedMsg = stringResource(R.string.auto_run_started)
    val unavailable = stringResource(R.string.settings_unavailable)

    OnResume { lastRun = prefs.lastRunMillis }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            alerts = true; prefs.alerts = true
        } else {
            alerts = false; prefs.alerts = false
            Toast.makeText(context, deniedMsg, Toast.LENGTH_LONG).show()
        }
    }

    FeaturePage(R.string.auto_title, onBack) {
        NoteText(stringResource(R.string.auto_note))

        SectionCard(R.string.auto_card_settings) {
            ToggleRow(R.string.auto_enable, R.string.auto_enable_hint, enabled) { on ->
                enabled = on
                prefs.enabled = on
                AutomationWorker.schedule(context, on)
            }
            ToggleRow(R.string.auto_alerts, R.string.auto_alerts_hint, alerts) { on ->
                if (!on) {
                    alerts = false; prefs.alerts = false
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!AutomationNotifier.canNotify(context)) {
                    Toast.makeText(context, deniedMsg, Toast.LENGTH_LONG).show()
                } else {
                    alerts = true; prefs.alerts = true
                }
            }
            InfoRow(
                stringResource(R.string.auto_last_run),
                if (lastRun == 0L) stringResource(R.string.auto_never)
                else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                    .format(Date(lastRun))
            )
        }

        PrimaryButton(stringResource(R.string.auto_run_now), onClick = {
            AutomationWorker.runNow(context)
            Toast.makeText(context, startedMsg, Toast.LENGTH_SHORT).show()
        })
        SecondaryButton(stringResource(R.string.hist_open), onClick = onOpenHistory)
        SecondaryButton(stringResource(R.string.settings_notifications), onClick = {
            if (!context.openOwnNotificationSettings()) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
        })
        NoteText(stringResource(R.string.auto_limits))
    }
}

@Composable
private fun ToggleRow(title: Int, hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
