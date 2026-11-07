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
    val bank: String, val holder: String, val card: String,
    val enabled: Boolean = true, val minAmount: Long = MIN_AMOUNT,
    val presets: List<Long> = PRESETS, val allowCustom: Boolean = true, val instructions: String = ""
)

private suspend fun loadPayInfo(): PayInfo? {
    val url = BuildConfig.DONATION_CONFIG_URL
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
                if (card.isBlank()) null else PayInfo(
                    j.optString("bank"), j.optString("holder"), card,
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
                NoteText(listOf(pi.bank, pi.holder, pi.card).filter { it.isNotBlank() }.joinToString("\n"),
                    color = BrandColors.TextPrimary)
                if (pi.instructions.isNotBlank()) NoteText(pi.instructions)
                SecondaryButton(stringResource(R.string.donate_copy_card), onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("card", pi.card.filter { it.isDigit() }))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                })
            } else if (loaded) {
                NoteText(stringResource(R.string.donate_card_unavailable))
            }
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
