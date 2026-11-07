package uz.phoneboost.app.features.storage

import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.phoneboost.app.R
import uz.phoneboost.app.ads.BannerAd
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.OnResume
import uz.phoneboost.app.core.ui.PrimaryButton
import uz.phoneboost.app.core.ui.SecondaryButton
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.ui.UsageBar
import uz.phoneboost.app.core.ui.formatBytes
import uz.phoneboost.app.core.util.openAppDetails
import uz.phoneboost.app.core.util.openSettings
import uz.phoneboost.app.core.util.startSafely
import uz.phoneboost.app.data.storage.LargeFile
import uz.phoneboost.app.data.storage.LargeFileKind
import uz.phoneboost.app.data.storage.StoragePermissions
import uz.phoneboost.app.domain.SettingsAction
import uz.phoneboost.app.features.home.HomeViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StorageScreen(
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
    storageViewModel: StorageViewModel = viewModel()
) {
    OnResume {
        homeViewModel.refresh()
        storageViewModel.refresh()
    }

    val context = LocalContext.current
    val status = homeViewModel.state.collectAsStateWithLifecycle().value.status
    val ui by storageViewModel.state.collectAsStateWithLifecycle()
    val noData = stringResource(R.string.no_data)
    val unavailable = stringResource(R.string.settings_unavailable)
    var confirmDelete by remember { mutableStateOf<LargeFile?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> storageViewModel.onPermissionResult(StoragePermissions.granted(context)) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { _ ->
        // Natija Android tizimining o‘z oynasida hal bo‘ldi; ro‘yxatni qayta o‘qiymiz.
        storageViewModel.refresh()
    }

    fun requestSystemDelete(file: LargeFile) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val sender = MediaStore.createDeleteRequest(context.contentResolver, listOf(file.uri)).intentSender
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }.onFailure {
            Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    fun openFile(file: LargeFile) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(file.uri, file.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!context.startSafely(intent)) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
    }

    FeaturePage(R.string.feature_memory, onBack) {
        // Umumiy
        SectionCard(R.string.storage_overview) {
            InfoRow(stringResource(R.string.row_total), formatBytes(context, status?.storageTotalBytes) ?: noData)
            InfoRow(stringResource(R.string.row_used), formatBytes(context, status?.storageUsedBytes) ?: noData)
            InfoRow(stringResource(R.string.row_free), formatBytes(context, status?.storageFreeBytes) ?: noData)
            val total = status?.storageTotalBytes
            val used = status?.storageUsedBytes
            if (total != null && used != null && total > 0) {
                Spacer(Modifier.height(8.dp))
                UsageBar(used.toFloat() / total)
            }
        }

        // Kategoriyalar
        SectionCard(R.string.storage_categories) {
            val media = ui.media
            InfoRow(
                stringResource(R.string.cat_apps),
                formatBytes(context, ui.appsApkBytes) ?: noData
            )
            InfoRow(
                stringResource(R.string.cat_media),
                if (media != null) formatBytes(context, media.photosBytes + media.videosBytes) ?: noData
                else stringResource(R.string.need_permission)
            )
            InfoRow(
                stringResource(R.string.cat_documents),
                when {
                    media == null -> stringResource(R.string.need_permission)
                    media.documentsBytes != null -> formatBytes(context, media.documentsBytes) ?: noData
                    else -> stringResource(R.string.android_hides)
                }
            )
            val used = status?.storageUsedBytes
            val known = if (media != null && ui.appsApkBytes != null) {
                ui.appsApkBytes!! + media.photosBytes + media.videosBytes + (media.documentsBytes ?: 0L)
            } else null
            InfoRow(
                stringResource(R.string.cat_other),
                if (used != null && known != null) formatBytes(context, (used - known).coerceAtLeast(0L)) ?: noData
                else noData
            )
            NoteText(stringResource(R.string.cat_note))
        }

        // Ruxsat / katta fayllar
        if (!ui.permissionGranted) {
            SectionCard(R.string.large_files) {
                NoteText(stringResource(R.string.large_files_need_permission))
                PrimaryButton(stringResource(R.string.find_large_files), onClick = {
                    permissionLauncher.launch(StoragePermissions.required())
                })
                if (ui.permissionDenied) {
                    NoteText(stringResource(R.string.permission_denied_hint), color = MaterialTheme.colorScheme.error)
                    SecondaryButton(stringResource(R.string.open_app_settings), onClick = {
                        if (!context.openAppDetails(context.packageName)) {
                            Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        } else {
            SectionCard(R.string.large_files) {
                val files = ui.media?.largeFiles.orEmpty()
                when {
                    ui.loading && ui.media == null -> NoteText(stringResource(R.string.loading))
                    files.isEmpty() -> NoteText(stringResource(R.string.large_files_none))
                    else -> {
                        NoteText(stringResource(R.string.large_files_hint))
                        files.forEach { file ->
                            LargeFileRow(
                                file = file,
                                canDelete = Build.VERSION.SDK_INT >= 30,
                                onOpen = { openFile(file) },
                                onDelete = { confirmDelete = file }
                            )
                        }
                        if (Build.VERSION.SDK_INT < 30) NoteText(stringResource(R.string.delete_old_android))
                    }
                }
            }
        }

        SecondaryButton(stringResource(R.string.open_storage_settings), onClick = {
            if (!context.openSettings(SettingsAction.STORAGE)) {
                Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
            }
        })
        NoteText(stringResource(R.string.never_delete_without_confirm))
        BannerAd()
    }

    val target = confirmDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_confirm_text,
                        target.name,
                        formatBytes(context, target.sizeBytes) ?: ""
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    requestSystemDelete(target)
                }) { Text(stringResource(R.string.delete_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun LargeFileRow(file: LargeFile, canDelete: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val kind = stringResource(
        when (file.kind) {
            LargeFileKind.IMAGE -> R.string.kind_image
            LargeFileKind.VIDEO -> R.string.kind_video
            LargeFileKind.DOCUMENT -> R.string.kind_document
            LargeFileKind.OTHER -> R.string.kind_other
        }
    )
    val date = file.modifiedMillis?.let {
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(it))
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            file.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            listOfNotNull(formatBytes(context, file.sizeBytes), kind, date).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpen, modifier = Modifier.height(48.dp)) {
                Text(stringResource(R.string.action_open))
            }
            if (canDelete) {
                TextButton(onClick = onDelete, modifier = Modifier.height(48.dp)) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
