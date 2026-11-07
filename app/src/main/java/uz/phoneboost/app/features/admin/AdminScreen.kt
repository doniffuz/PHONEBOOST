package uz.phoneboost.app.features.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.core.designsystem.PhoneBoostButton
import uz.phoneboost.app.core.designsystem.PhoneBoostLoading
import uz.phoneboost.app.core.designsystem.PhoneBoostSecondaryButton
import uz.phoneboost.app.core.designsystem.PhoneBoostSwitch
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.SectionCard

/** Admin Panel: autentifikatsiya SERVERDA. Backend ulanmagan bo‘lsa, haqiqiy holat ko‘rsatiladi. */
@Composable
fun AdminScreen(onBack: () -> Unit, vm: AdminViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var announcement by remember { mutableStateOf("") }
    val na = stringResource(R.string.unknown)

    FeaturePage(R.string.admin_title, onBack) {
        if (!vm.configured) {
            SectionCard(R.string.admin_backend_required) {
                NoteText(stringResource(R.string.admin_backend_required_text))
            }
            return@FeaturePage
        }
        if (ui.session == null) {
            SectionCard(R.string.admin_login) {
                OutlinedTextField(email, { email = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.admin_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(password, { password = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.admin_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                PhoneBoostButton(stringResource(R.string.admin_signin), enabled = !ui.busy && email.isNotBlank() && password.isNotEmpty(),
                    onClick = { vm.login(email.trim(), password); password = "" })
                if (ui.error) NoteText(stringResource(R.string.admin_error), color = BrandColors.Bad)
            }
            if (ui.busy) PhoneBoostLoading()
            return@FeaturePage
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { d ->
                FilterChip(selected = ui.days == d, onClick = { vm.setDays(d) }, label = { Text("$d") })
            }
        }
        if (ui.busy) PhoneBoostLoading()
        if (ui.error) NoteText(stringResource(R.string.admin_error), color = BrandColors.Bad)
        val s = ui.stats
        SectionCard(R.string.admin_stats) {
            fun f(v: Long?) = v?.toString() ?: na
            InfoRow(stringResource(R.string.admin_users), f(s?.users))
            InfoRow(stringResource(R.string.admin_devices), f(s?.devices))
            InfoRow(stringResource(R.string.admin_optimizations), f(s?.optimizations))
            InfoRow(stringResource(R.string.admin_gameboosts), f(s?.gameBoosts))
            InfoRow(stringResource(R.string.admin_diagnostics), f(s?.diagnostics))
            InfoRow(stringResource(R.string.admin_donations), f(s?.donations))
            InfoRow(stringResource(R.string.admin_errors), f(s?.errors))
            InfoRow(stringResource(R.string.admin_app_version), s?.appVersion ?: na)
        }
        SectionCard(R.string.admin_maintenance) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.admin_maintenance), color = BrandColors.TextPrimary)
                PhoneBoostSwitch(s?.maintenance == true, { vm.setMaintenance(it) })
            }
        }
        SectionCard(R.string.admin_announcements) {
            OutlinedTextField(announcement, { announcement = it.take(500) }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.admin_announce_hint)) })
            PhoneBoostButton(stringResource(R.string.admin_send), enabled = announcement.isNotBlank() && !ui.busy,
                onClick = { vm.announce(announcement.trim()); announcement = "" })
        }
        SectionCard(R.string.admin_logs) {
            if (ui.logs.isEmpty()) NoteText(stringResource(R.string.admin_logs_empty))
            ui.logs.take(50).forEach { NoteText(it, color = BrandColors.TextPrimary) }
        }
        PhoneBoostSecondaryButton(stringResource(R.string.admin_signout), onClick = vm::logout)
    }
}
