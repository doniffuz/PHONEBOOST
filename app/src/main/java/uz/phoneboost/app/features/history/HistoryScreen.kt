package uz.phoneboost.app.features.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.phoneboost.app.R
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.SecondaryButton
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.data.history.HistoryEntry
import uz.phoneboost.app.data.history.HistoryRepository
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { HistoryRepository(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<HistoryEntry>?>(null) }
    var askClear by remember { mutableStateOf(false) }
    val unknown = stringResource(R.string.unknown)

    LaunchedEffect(Unit) { entries = withContext(Dispatchers.IO) { repo.load() } }

    FeaturePage(R.string.hist_title, onBack) {
        NoteText(stringResource(R.string.hist_note))
        val list = entries
        if (list != null && list.isEmpty()) {
            NoteText(stringResource(R.string.hist_empty))
        }
        list.orEmpty().forEach { e ->
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                .format(Date(e.timeMillis))
            SectionCard(date) {
                InfoRow(
                    stringResource(R.string.hist_score),
                    e.score?.let { "$it/100" } ?: unknown
                )
                InfoRow(stringResource(R.string.stat_ram), e.ramBusyPercent?.let { "$it%" } ?: unknown)
                InfoRow(stringResource(R.string.stat_storage), e.storageFreePercent?.let { "$it%" } ?: unknown)
                InfoRow(stringResource(R.string.stat_battery), e.batteryPercent?.let { "$it%" } ?: unknown)
                InfoRow(
                    stringResource(R.string.stat_temperature),
                    e.batteryTempC?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: unknown
                )
                InfoRow(
                    stringResource(R.string.hist_source),
                    stringResource(
                        if (e.source == HistoryEntry.SOURCE_AUTO) R.string.hist_source_auto else R.string.hist_source_manual
                    )
                )
            }
        }
        if (!list.isNullOrEmpty()) {
            SecondaryButton(stringResource(R.string.hist_clear), onClick = { askClear = true })
        }
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(stringResource(R.string.hist_clear_title)) },
            text = { Text(stringResource(R.string.hist_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.clear() }
                        entries = emptyList()
                    }
                }) { Text(stringResource(R.string.hist_clear_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) { Text(stringResource(R.string.hist_cancel)) }
            }
        )
    }
}
