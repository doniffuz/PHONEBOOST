package uz.phoneboost.app.features.apps

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.phoneboost.app.R
import uz.phoneboost.app.core.ui.OnResume
import uz.phoneboost.app.core.ui.PageHeader
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.formatBytes
import uz.phoneboost.app.core.util.openAppDetails
import uz.phoneboost.app.core.util.requestUninstall
import uz.phoneboost.app.domain.model.AppEntry
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class AppFilter { USER, SYSTEM, ALL }

@Composable
fun AppsScreen(onBack: () -> Unit, viewModel: AppsViewModel = viewModel()) {
    // Uninstall/Settings dan qaytganda ro‘yxat yangilanadi.
    var firstResume by remember { mutableStateOf(true) }
    OnResume {
        if (firstResume) firstResume = false else viewModel.reload()
    }

    val ui by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.USER.name) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val activeFilter = runCatching { AppFilter.valueOf(filter) }.getOrDefault(AppFilter.USER)

    val visible = remember(ui.apps, query, activeFilter) {
        ui.apps.filter { app ->
            when (activeFilter) {
                AppFilter.USER -> !app.isSystem
                AppFilter.SYSTEM -> app.isSystem
                AppFilter.ALL -> true
            } && (query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            PageHeader(R.string.feature_apps, onBack)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.apps_search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeFilter == AppFilter.USER,
                    onClick = { filter = AppFilter.USER.name },
                    label = { Text(stringResource(R.string.apps_filter_user), maxLines = 1) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = activeFilter == AppFilter.SYSTEM,
                    onClick = { filter = AppFilter.SYSTEM.name },
                    label = { Text(stringResource(R.string.apps_filter_system), maxLines = 1) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = activeFilter == AppFilter.ALL,
                    onClick = { filter = AppFilter.ALL.name },
                    label = { Text(stringResource(R.string.apps_filter_all), maxLines = 1) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }

            when {
                ui.loading -> NoteText(stringResource(R.string.loading), Modifier.padding(8.dp))
                ui.apps.isEmpty() -> NoteText(stringResource(R.string.apps_none), Modifier.padding(8.dp))
                else -> {
                    NoteText(
                        stringResource(R.string.apps_count, visible.size),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                    ) {
                        items(visible, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                expanded = expanded == app.packageName,
                                onToggle = { expanded = if (expanded == app.packageName) null else app.packageName }
                            )
                        }
                        item { NoteText(stringResource(R.string.apps_size_note), Modifier.padding(vertical = 8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, expanded: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.settings_unavailable)
    val noData = stringResource(R.string.no_data)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(app.packageName)
                Column(Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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
                Text(
                    formatBytes(context, app.apkSizeBytes) ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                val installed = app.installedAtMillis?.let {
                    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(it))
                } ?: noData
                Column(Modifier.padding(top = 8.dp)) {
                    NoteText(stringResource(R.string.apps_version, app.versionName ?: noData))
                    NoteText(stringResource(R.string.apps_installed, installed))
                    NoteText(stringResource(R.string.apps_perm_count, app.grantedPermissions.size))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                if (!context.openAppDetails(app.packageName)) {
                                    Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.apps_details)) }
                        if (!app.isSystem) {
                            TextButton(
                                onClick = {
                                    if (!context.requestUninstall(app.packageName)) {
                                        Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.apps_uninstall), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(44.dp))
    } else {
        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
    }
}
