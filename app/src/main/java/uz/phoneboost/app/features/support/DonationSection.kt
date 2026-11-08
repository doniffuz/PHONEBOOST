package uz.phoneboost.app.features.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import uz.phoneboost.app.core.AppConfig
import uz.phoneboost.app.core.util.openUrl
import uz.phoneboost.app.data.remote.BackendRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uz.phoneboost.app.BuildConfig
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.PrimaryButton
import uz.phoneboost.app.core.ui.SecondaryButton
import uz.phoneboost.app.core.ui.SectionCard
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

private const val MIN_AMOUNT = 1_000L
private val PRESETS = listOf(1_000L, 5_000L, 10_000L, 20_000L, 50_000L, 100_000L)
private val KNOWN_PAY_APPS = listOf("uz.dida.payme", "air.com.ssdsoftwaresolutions.clickuz")

/** Karta ma'lumoti APK ichida yo'q: faqat HTTPS orqali server config'dan olinadi va xotirada turadi (log/saqlash yo'q). */
private data class PayInfo(
    val bank: String, val holder: String, val card: String, val qr: String = "",
    val enabled: Boolean = true, val minAmount: Long = MIN_AMOUNT,
    val presets: List<Long> = PRESETS, val allowCustom: Boolean = true, val instructions: String = ""
)

private suspend fun loadPayInfo(): PayInfo? {
    val url = BuildConfig.DONATION_CONFIG_URL.ifBlank { AppConfig.API_BASE.let { if (it.isEmpty()) "" else "$it/donation-config" } }
    if (url.isBlank() || !url.startsWith("https://")) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            try {
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                if (j.has("enabled") && !j.optBoolean("enabled", false)) return@runCatching null
                val card = j.optString("card").filter { it.isDigit() || it == ' ' }
                val pr = j.optJSONArray("presets")?.let { a -> (0 until a.length()).map { a.optLong(it) }.filter { it >= MIN_AMOUNT } }
                val qr = j.optString("qrPayload").trim()
                // Server karta/QR'ni faqat usul YOQILGAN va SOZLANGAN bo'lsa beradi; ikkalasi ham bo'sh = "sozlanmagan".
                if (card.isBlank() && qr.isBlank()) null else PayInfo(
                    j.optString("bank"), j.optString("holder"), card, qr,
                    minAmount = maxOf(MIN_AMOUNT, j.optLong("minAmount", MIN_AMOUNT)),
                    presets = if (pr.isNullOrEmpty()) PRESETS else pr,
                    allowCustom = j.optBoolean("allowCustom", true),
                    instructions = j.optString("instructions")
                )
            } finally { c.disconnect() }
        }.getOrNull()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DonationSection() {
    val context = LocalContext.current
    val nf = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    var selected by remember { mutableStateOf<Long?>(10_000L) }
    var custom by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<PayInfo?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { info = loadPayInfo(); loaded = true }

    val amount: Long? = if (selected != null) selected else custom.toLongOrNull()
    val minAmt = info?.minAmount ?: MIN_AMOUNT
    val valid = amount != null && amount >= minAmt
    val unavailable = stringResource(R.string.settings_unavailable)
    val copied = stringResource(R.string.donate_copied)

    SectionCard(R.string.donate_title) {
        NoteText(stringResource(R.string.donate_intro), color = BrandColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            (info?.presets ?: PRESETS).forEach { v ->
                FilterChip(selected = selected == v, onClick = { selected = v; custom = "" },
                    label = { Text(nf.format(v)) })
            }
            if (info?.allowCustom != false) FilterChip(selected = selected == null, onClick = { selected = null },
                label = { Text(stringResource(R.string.donate_custom)) })
        }
        if (selected == null) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.filter(Char::isDigit).take(12) },
                singleLine = true,
                label = { Text(stringResource(R.string.donate_amount_hint, nf.format(minAmt))) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!valid) NoteText(stringResource(R.string.donate_min, nf.format(minAmt)), color = BrandColors.Warn)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(stringResource(R.string.donate_open_app), enabled = valid, onClick = {
                if (!openPaymentApp(context)) Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
            })
            val pi = info
            if (pi != null) {
                if (pi.card.isNotBlank()) NoteText(listOf(pi.bank, pi.holder, pi.card).filter { it.isNotBlank() }.joinToString("\n"),
                    color = BrandColors.TextPrimary)
                if (pi.instructions.isNotBlank()) NoteText(pi.instructions)
                if (pi.card.isNotBlank()) SecondaryButton(stringResource(R.string.donate_copy_card), onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("card", pi.card.filter { it.isDigit() }))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                })
                if (pi.qr.isNotBlank()) {
                    if (pi.qr.startsWith("https://")) SecondaryButton(stringResource(R.string.donate_open_qr), onClick = {
                        if (!context.openUrl(pi.qr)) Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
                    }) else NoteText(pi.qr, color = BrandColors.TextPrimary)
                }
                DonationRegistration(amount = amount, valid = valid, provider = if (pi.card.isNotBlank()) "manual_card" else "qr")
            } else if (loaded) {
                NoteText(stringResource(R.string.donate_card_unavailable))
            }
            NoteText(stringResource(R.string.donate_gateways_not_configured))
            NoteText(stringResource(R.string.donate_no_auto_success))
        }
    }
}

/** Avval o'rnatilgan bank/to'lov ilovasi, bo'lmasa donation havolasi. To'lov natijasi hech qachon taxmin qilinmaydi. */
private fun openPaymentApp(context: Context): Boolean {
    val pm = context.packageManager
    for (pkg in KNOWN_PAY_APPS) {
        val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
    }
    val url = BuildConfig.DONATION_URL
    if (url.isBlank()) return false
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

/**
 * To'lov niyatini backend'ga 'pending' deb yozadi va holatini so'raydi. Ilova hech qachon "success" deb o'zi hisoblamaydi:
 * holat FAQAT serverdan keladi (admin to'lov tushganini tekshirib tasdiqlaydi).
 */
@Composable
private fun DonationRegistration(amount: Long?, valid: Boolean, provider: String) {
    val scope = rememberCoroutineScope()
    var showName by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var reference by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = showName, onCheckedChange = { showName = it })
        NoteText(stringResource(R.string.donate_show_name))
    }
    if (showName) OutlinedTextField(
        value = name, onValueChange = { name = it.take(60) }, singleLine = true,
        label = { Text(stringResource(R.string.donate_name_hint)) }, modifier = Modifier.fillMaxWidth()
    )
    PrimaryButton(stringResource(R.string.donate_register),
        enabled = valid && !busy && reference == null && (!showName || name.isNotBlank()), onClick = {
            val a = amount ?: return@PrimaryButton
            busy = true; failed = false
            scope.launch {
                val r = BackendRepository.registerDonation(a, provider, name, showName)
                if (r == null) failed = true else { reference = r.reference; status = r.status }
                busy = false
            }
        })
    if (failed) NoteText(stringResource(R.string.donate_register_failed), color = BrandColors.Warn)
    val ref = reference
    if (ref != null) {
        NoteText(stringResource(R.string.donate_reference, ref), color = BrandColors.TextPrimary)
        NoteText(stringResource(R.string.donate_status, statusLabel(status)))
        SecondaryButton(stringResource(R.string.donate_check_status), enabled = !busy, onClick = {
            busy = true
            scope.launch { status = BackendRepository.donationStatus(ref) ?: status; busy = false }
        })
    }
}

@Composable
private fun statusLabel(status: String?): String = stringResource(
    when (status) {
        "pending" -> R.string.donate_status_pending
        "success" -> R.string.donate_status_success
        "failed" -> R.string.donate_status_failed
        "cancelled" -> R.string.donate_status_cancelled
        else -> R.string.donate_status_unknown
    }
)
