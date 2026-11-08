package uz.phoneboost.app.data.report

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.format.Formatter
import uz.phoneboost.app.R
import uz.phoneboost.app.core.ui.healthLabelRes
import uz.phoneboost.app.domain.DiagKind
import uz.phoneboost.app.domain.DiagStatus
import uz.phoneboost.app.domain.DiagnosticsEngine
import uz.phoneboost.app.domain.RecommendationType
import uz.phoneboost.app.features.home.HomeUiState
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Hisobotni PDF qiladi. Matnlar faqat telefonning haqiqiy o‘lchovlaridan olinadi; ma’lumot yo‘q bo‘lsa “noma’lum”. */
object PdfReportBuilder {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private data class Line(val text: String, val size: Float, val bold: Boolean = false, val gap: Float = 0f)

    fun build(context: Context, state: HomeUiState): ByteArray {
        val lines = compose(context, state)
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var canvas = page.canvas
        var y = MARGIN
        val maxWidth = PAGE_W - 2 * MARGIN

        for (line in lines) {
            paint.textSize = line.size
            paint.typeface = if (line.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.color = 0xFF1B1B1B.toInt()
            y += line.gap
            for (part in wrap(line.text, paint, maxWidth)) {
                val lineHeight = line.size * 1.35f
                if (y + lineHeight > PAGE_H - MARGIN) {
                    doc.finishPage(page)
                    pageNo++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                    canvas = page.canvas
                    y = MARGIN
                }
                y += lineHeight
                canvas.drawText(part, MARGIN, y, paint)
            }
        }
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val result = ArrayList<String>()
        var rest = text
        while (rest.isNotEmpty()) {
            val count = paint.breakText(rest, true, maxWidth, null).coerceAtLeast(1)
            if (count >= rest.length) { result += rest; break }
            val cut = rest.lastIndexOf(' ', count - 1).let { if (it <= 0) count else it }
            result += rest.substring(0, cut).trimEnd()
            rest = rest.substring(cut).trimStart()
        }
        return result
    }

    private fun compose(context: Context, state: HomeUiState): List<Line> {
        val unknown = context.getString(R.string.unknown)
        val status = state.status
        fun bytes(v: Long?) = if (v == null) unknown else Formatter.formatShortFileSize(context, v)
        val out = ArrayList<Line>()

        out += Line(context.getString(R.string.app_name) + " — " + context.getString(R.string.nav_report), 20f, true)
        val time = state.checkedAtMillis?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(it))
        } ?: unknown
        out += Line(context.getString(R.string.report_last_check) + ": " + time, 11f)

        out += Line(context.getString(R.string.report_card_health), 14f, true, 16f)
        val result = state.result
        out += Line(
            if (result == null) unknown
            else "${result.score}/100 — " + context.getString(healthLabelRes(result.level)),
            12f
        )

        out += Line(context.getString(R.string.report_card_ram), 14f, true, 14f)
        out += Line(context.getString(R.string.row_total) + ": " + bytes(status?.ramTotalBytes), 12f)
        out += Line(
            context.getString(R.string.row_used) + ": " + bytes(status?.ramUsedBytes) +
                (status?.busyRamPercent?.let { " ($it%)" } ?: ""),
            12f
        )
        out += Line(context.getString(R.string.row_free) + ": " + bytes(status?.ramAvailableBytes), 12f)

        out += Line(context.getString(R.string.report_card_storage), 14f, true, 14f)
        out += Line(context.getString(R.string.row_total) + ": " + bytes(status?.storageTotalBytes), 12f)
        out += Line(context.getString(R.string.row_used) + ": " + bytes(status?.storageUsedBytes), 12f)
        out += Line(context.getString(R.string.row_free) + ": " + bytes(status?.storageFreeBytes), 12f)

        out += Line(context.getString(R.string.stat_battery), 14f, true, 14f)
        out += Line(context.getString(R.string.row_percent) + ": " + (status?.batteryPercent?.let { "$it%" } ?: unknown), 12f)
        out += Line(
            context.getString(R.string.stat_temperature) + ": " +
                (status?.batteryTempC?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: unknown),
            12f
        )

        out += Line(context.getString(R.string.feature_diagnostics), 14f, true, 14f)
        val shown = setOf(DiagKind.RAM, DiagKind.STORAGE, DiagKind.BATTERY, DiagKind.TEMPERATURE)
        DiagnosticsEngine.run(status, null).filter { it.kind in shown }.forEach { item ->
            val label = when (item.kind) {
                DiagKind.RAM -> R.string.stat_ram
                DiagKind.STORAGE -> R.string.stat_storage
                DiagKind.BATTERY -> R.string.stat_battery
                else -> R.string.stat_temperature
            }
            val st = when (item.status) {
                DiagStatus.GOOD -> R.string.diag_good
                DiagStatus.ATTENTION -> R.string.diag_attention
                DiagStatus.CHECK -> R.string.diag_check
                DiagStatus.UNAVAILABLE -> R.string.diag_unavailable
            }
            out += Line(context.getString(label) + ": " + context.getString(st), 12f)
        }

        out += Line(context.getString(R.string.report_card_recs), 14f, true, 14f)
        if (state.recommendations.isEmpty()) {
            out += Line(context.getString(R.string.result_ok), 12f)
        } else {
            state.recommendations.forEach { rec ->
                val (title, reason) = when (rec.type) {
                    RecommendationType.STORAGE_LOW -> R.string.rec_storage_title to R.string.rec_storage_reason
                    RecommendationType.LARGE_FILES -> R.string.rec_large_title to R.string.rec_large_reason
                    RecommendationType.MEMORY_HIGH -> R.string.rec_memory_title to R.string.rec_memory_reason
                    RecommendationType.TEMPERATURE_HIGH -> R.string.rec_temp_title to R.string.rec_temp_reason
                    RecommendationType.BATTERY_LOW -> R.string.rec_battery_title to R.string.rec_battery_reason
                    RecommendationType.BATTERY_HEALTH -> R.string.rec_bhealth_title to R.string.rec_bhealth_reason
                }
                out += Line("• " + context.getString(title), 12f, true, 4f)
                out += Line(context.getString(reason), 11f)
            }
        }

        out += Line(context.getString(R.string.diag_note), 10f, false, 18f)
        return out
    }
}
