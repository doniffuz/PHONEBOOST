package uz.phoneboost.app.features.privacy

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.phoneboost.app.R
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.OnResume
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.util.openAppDetails
import uz.phoneboost.app.domain.model.AppEntry
import uz.phoneboost.app.features.apps.AppsViewModel

private const val MAX_LISTED = 30

private data class PrivacyCategory(
    @StringRes val title: Int,
    @StringRes val explain: Int,
    val emoji: String,
    val permissions: Set<String>,
    /** Android shu ruxsatni faqat ma’lum versiyadan beradi. */
    val minSdk: Int = 0
)

private val categories = listOf(
    PrivacyCategory(
        R.string.perm_camera, R.string.perm_camera_explain, "📷",
        setOf(Manifest.permission.CAMERA)
    ),
    PrivacyCategory(
        R.string.perm_mic, R.string.perm_mic_explain, "🎙",
        setOf(Manifest.permission.RECORD_AUDIO)
    ),
    PrivacyCategory(
        R.string.perm_location, R.string.perm_location_explain, "📍",
        setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    ),
    PrivacyCategory(
        R.string.perm_contacts, R.string.perm_contacts_explain, "👤",
        setOf(Manifest.permission.READ_CONTACTS)
    ),
    PrivacyCategory(
        R.string.perm_media, R.string.perm_media_explain, "🖼",
        setOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        )
    ),
    PrivacyCategory(
        R.string.perm_notifications, R.string.perm_notifications_explain, "🔔",
        setOf("android.permission.POST_NOTIFICATIONS"), minSdk = 33
    )
)

/** Faqat Android bergan real ruxsat holati. “Xavfli” deb baho berilmaydi. */
@Composable
fun PrivacyScreen(onBack: () -> Unit, viewModel: AppsViewModel = viewModel()) {
    var firstResume by remember { mutableStateOf(true) }
    OnResume { if (firstResume) firstResume = false else viewModel.reload() }

    val ui by viewModel.state.collectAsStateWithLifecycle()
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var openCategory by rememberSaveable { mutableStateOf<Int?>(null) }

    val pool = remember(ui.apps, showSystem) { ui.apps.filter { showSystem || !it.isSystem } }

    FeaturePage(R.string.feature_privacy, onBack) {
        NoteText(stringResource(R.string.privacy_intro))

        FilterChip(
            selected = showSystem,
            onClick = { showSystem = !showSystem },
            label = { Text(stringResource(R.string.privacy_include_system), maxLines = 1) },
            modifier = Modifier.heightIn(min = 48.dp)
        )

        when {
            ui.loading -> NoteText(stringResource(R.string.loading))
            ui.apps.isEmpty() -> NoteText(stringResource(R.string.apps_none))
            else -> categories.forEach { cat ->
                CategoryCard(
                    cat = cat,
                    pool = pool,
                    open = openCategory == cat.title,
                    onToggle = { openCategory = if (openCategory == cat.title) null else cat.title }
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(cat: PrivacyCategory, pool: List<AppEntry>, open: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.settings_unavailable)
    val supported = Build.VERSION.SDK_INT >= cat.minSdk
    val granted = remember(pool, cat) {
        if (!supported) emptyList()
        else pool.filter { app -> app.grantedPermissions.any { it in cat.permissions } }
    }

    SectionCard(cat.title) {
        Text(
            cat.emoji + "  " + stringResource(cat.explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!supported) {
            NoteText(stringResource(R.string.perm_not_tracked))
        } else {
            Text(
                stringResource(R.string.perm_granted_count, granted.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (granted.isNotEmpty()) {
                TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (open) R.string.perm_hide_list else R.string.perm_show_list))
                }
            }
            if (open) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    granted.take(MAX_LISTED).forEach { app ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (!context.openAppDetails(app.packageName)) {
                                        Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text(stringResource(R.string.perm_change)) }
                        }
                    }
                    if (granted.size > MAX_LISTED) {
                        NoteText(stringResource(R.string.perm_more, granted.size - MAX_LISTED))
                    }
                }
            }
        }
    }
}
