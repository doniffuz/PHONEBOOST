package uz.phoneboost.app.features.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import uz.phoneboost.app.core.ui.findActivity
import uz.phoneboost.app.ads.Ads
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.phoneboost.app.R
import uz.phoneboost.app.core.AppConfig
import uz.phoneboost.app.core.util.openEmail
import uz.phoneboost.app.core.util.openOwnNotificationSettings
import uz.phoneboost.app.core.util.openRateApp
import uz.phoneboost.app.core.util.openUrl
import uz.phoneboost.app.data.settings.ThemeMode

private val languages = listOf(
    "uz" to R.string.language_uz,
    "ru" to R.string.language_ru,
    "en" to R.string.language_en
)

private val themes = listOf(
    ThemeMode.SYSTEM to R.string.theme_system,
    ThemeMode.LIGHT to R.string.theme_light,
    ThemeMode.DARK to R.string.theme_dark
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenAutomation: () -> Unit = {}) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lang = LocalConfiguration.current.locales.get(0).language
    val currentLang = if (lang == "ru" || lang == "en") lang else "uz"
    var showPrivacy by remember { mutableStateOf(false) }
    val unavailable = stringResource(R.string.settings_unavailable)
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            SectionTitle(R.string.settings_language)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                languages.forEach { (tag, label) ->
                    FilterChip(
                        selected = currentLang == tag,
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(tag)
                            )
                        },
                        label = {
                            Text(
                                stringResource(label),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                }
            }

            SectionTitle(R.string.settings_theme)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                themes.forEach { (value, label) ->
                    FilterChip(
                        selected = (mode ?: ThemeMode.SYSTEM) == value,
                        onClick = { viewModel.setThemeMode(value) },
                        label = {
                            Text(
                                stringResource(label),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                }
            }

            SectionTitle(R.string.settings_general)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shadowElevation = 2.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    SettingsRow(R.string.auto_title, R.string.settings_automation_hint) { onOpenAutomation() }
                    SettingsRow(R.string.settings_notifications, R.string.settings_notifications_hint) {
                        if (!context.openOwnNotificationSettings()) {
                            Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                        }
                    }
                    SettingsRow(R.string.settings_privacy, R.string.settings_privacy_hint) { showPrivacy = true }
                    if (Ads.privacyOptionsRequired.value) {
                        SettingsRow(R.string.settings_ads_privacy, R.string.settings_ads_privacy_hint) {
                            context.findActivity()?.let { Ads.showPrivacyOptions(it) }
                        }
                    }
                    SettingsRow(R.string.settings_rate, R.string.settings_rate_hint) {
                        if (!context.openRateApp()) {
                            Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                        }
                    }
                    SettingsRow(
                        R.string.settings_contact,
                        if (AppConfig.CONTACT_EMAIL.isBlank()) R.string.settings_contact_not_set else R.string.settings_contact_hint,
                        enabled = AppConfig.CONTACT_EMAIL.isNotBlank()
                    ) {
                        if (!context.openEmail(AppConfig.CONTACT_EMAIL)) {
                            Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            SectionTitle(R.string.settings_about)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shadowElevation = 2.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    InfoRow(stringResource(R.string.about_version), version)
                    InfoRow(stringResource(R.string.about_developer), stringResource(R.string.dev_name))
                    InfoRow(stringResource(R.string.about_owner), stringResource(R.string.owner_name))
                }
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(stringResource(R.string.settings_privacy)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.privacy_policy_text))
                }
            },
            confirmButton = {
                if (AppConfig.PRIVACY_POLICY_URL.isNotBlank()) {
                    TextButton(onClick = {
                        if (!context.openUrl(AppConfig.PRIVACY_POLICY_URL)) {
                            Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.privacy_full_policy)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

@Composable
private fun SettingsRow(
    @StringRes title: Int,
    @StringRes hint: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
