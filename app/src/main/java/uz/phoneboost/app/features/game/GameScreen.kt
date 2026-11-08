package uz.phoneboost.app.features.game

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.core.designsystem.PhoneBoostButton
import uz.phoneboost.app.core.designsystem.PhoneBoostCard
import uz.phoneboost.app.core.designsystem.PhoneBoostLoading
import uz.phoneboost.app.core.designsystem.PhoneBoostSecondaryButton
import uz.phoneboost.app.core.designsystem.PhoneBoostStatusBadge
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.OnResume
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.ui.formatBytes
import uz.phoneboost.app.core.util.launchApp
import uz.phoneboost.app.data.game.BoostAction
import uz.phoneboost.app.data.game.GameBoostManager
import uz.phoneboost.app.data.game.GamesRepository
import uz.phoneboost.app.data.game.InstalledGame
import uz.phoneboost.app.data.game.RestoreOutcome
import uz.phoneboost.app.data.history.HistoryEntry
import uz.phoneboost.app.data.history.HistoryRepository
import uz.phoneboost.app.features.home.HomeViewModel
import java.util.Locale

/**
 * Universal Game Boost: o‘rnatilgan barcha o‘yinlar Android kategoriyasi bo‘yicha aniqlanadi.
 * Faqat real amallar; FPS/ping soxta ko‘rsatilmaydi. Restore asl animatsiya qiymatlarini qaytaradi.
 */
@Composable
fun GameScreen(homeViewModel: HomeViewModel, onBack: () -> Unit, onOpenApps: () -> Unit) {
    OnResume { homeViewModel.refresh() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = homeViewModel.state.collectAsStateWithLifecycle().value.status
    val unknown = stringResource(R.string.unknown)

    var games by remember { mutableStateOf<List<InstalledGame>?>(null) }
    var active by remember { mutableStateOf(GameBoostManager.isActive(context)) }
    var actions by remember { mutableStateOf<List<BoostAction>>(emptyList()) }
    var restoreMsg by remember { mutableStateOf<Int?>(null) }
    val notOpen = stringResource(R.string.settings_unavailable)

    LaunchedEffect(Unit) { games = withContext(Dispatchers.IO) { GamesRepository(context).load() } }

    FeaturePage(R.string.feature_game, onBack) {
        NoteText(stringResource(R.string.gb_intro))

        SectionCard(R.string.game_device_state) {
            InfoRow(stringResource(R.string.stat_ram), status?.busyRamPercent?.let { "$it%" } ?: unknown)
            InfoRow(stringResource(R.string.stat_battery), status?.batteryPercent?.let { "$it%" } ?: unknown)
            InfoRow(
                stringResource(R.string.stat_temperature),
                status?.batteryTempC?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: unknown
            )
            InfoRow(stringResource(R.string.stat_storage), formatBytes(context, status?.storageFreeBytes) ?: unknown)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gb_status), color = BrandColors.TextSecondary, modifier = Modifier.weight(1f))
                PhoneBoostStatusBadge(
                    stringResource(if (active) R.string.gb_active else R.string.gb_inactive),
                    if (active) BrandColors.Good else BrandColors.TextSecondary
                )
            }
        }

        if (actions.isNotEmpty()) {
            SectionCard(R.string.smart_performed) {
                actions.forEach { a ->
                    Text(
                        (if (a.done) "✓ " else "✕ ") + stringResource(a.title),
                        color = if (a.done) BrandColors.Good else BrandColors.Warn,
                        fontWeight = FontWeight.SemiBold
                    )
                    a.reason?.let { NoteText(stringResource(it)) }
                }
            }
        }

        SectionCard(R.string.gb_restore_title) {
            NoteText(stringResource(R.string.gb_restore_note))
            PhoneBoostSecondaryButton(stringResource(R.string.gb_restore), onClick = {
                val outcome = GameBoostManager.restore(context)
                active = GameBoostManager.isActive(context)
                restoreMsg = when (outcome) {
                    RestoreOutcome.RESTORED -> R.string.gb_restore_ok
                    RestoreOutcome.NOTHING_TO_RESTORE -> R.string.gb_restore_nothing
                    RestoreOutcome.NO_PERMISSION -> R.string.gb_restore_noperm
                    RestoreOutcome.FAILED -> R.string.gb_restore_fail
                }
            })
            restoreMsg?.let { NoteText(stringResource(it), color = BrandColors.Cyan) }
        }

        SectionCard(R.string.gb_detected) {
            val list = games
            when {
                list == null -> PhoneBoostLoading(Modifier.size(48.dp))
                list.isEmpty() -> {
                    NoteText(stringResource(R.string.gb_none))
                    PhoneBoostSecondaryButton(stringResource(R.string.feature_apps), onClick = onOpenApps)
                }
                else -> Unit
            }
        }

        games.orEmpty().forEach { g ->
            PhoneBoostCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    g.icon?.let {
                        Image(
                            bitmap = it.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(g.label, color = BrandColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(g.packageName, color = BrandColors.TextSecondary, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        g.apkSizeBytes?.let {
                            Text(stringResource(R.string.gb_size) + ": " + (formatBytes(context, it) ?: ""),
                                color = BrandColors.TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhoneBoostButton(stringResource(R.string.gb_boost), onClick = {
                        actions = GameBoostManager.boost(context)
                        active = GameBoostManager.isActive(context)
                        val s = status
                        if (s != null) scope.launch(Dispatchers.IO) {
                            runCatching {
                                HistoryRepository(context).add(
                                    HistoryEntry.from(
                                        s, null, HistoryEntry.SOURCE_MANUAL, HistoryEntry.TYPE_GAME_BOOST,
                                        "game=${g.packageName}; active=$active"
                                    )
                                )
                            }
                        }
                    }, modifier = Modifier.weight(1f))
                    PhoneBoostSecondaryButton(stringResource(R.string.gb_open), onClick = {
                        if (!context.launchApp(g.packageName)) Toast.makeText(context, notOpen, Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
