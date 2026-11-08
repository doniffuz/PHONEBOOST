package uz.phoneboost.app.data.optimize

import android.content.Context
import androidx.annotation.StringRes
import uz.phoneboost.app.R
import uz.phoneboost.app.data.device.DeviceStatusRepository
import uz.phoneboost.app.data.history.HistoryEntry
import uz.phoneboost.app.data.history.HistoryRepository
import uz.phoneboost.app.domain.DiagKind
import uz.phoneboost.app.domain.DiagStatus
import uz.phoneboost.app.domain.DiagnosticsEngine
import uz.phoneboost.app.domain.HealthScoreCalculator
import uz.phoneboost.app.domain.Recommendation
import uz.phoneboost.app.domain.RecommendationEngine
import java.io.File

data class NotPerformed(@StringRes val title: Int, @StringRes val reason: Int)

data class SmartResult(
    val score: Int?,
    val problems: List<DiagKind>,
    val performed: List<Int>,
    val freedBytes: Long,
    val notPerformed: List<NotPerformed>,
    val recommendations: List<Recommendation>
)

/**
 * Smart Optimize: 1) diagnostika, 2) muammolarni aniqlash, 3) xavfsiz amal (faqat ilovaning o‘z keshi),
 * 4) bajarilmaganlarni sabab bilan ko‘rsatish, 5) tavsiya, 6) tarixga yozish.
 * Funksiya bloklovchi: IO oqimidan chaqiring.
 */
class SmartOptimizer(context: Context) {
    private val ctx = context.applicationContext

    suspend fun run(): SmartResult {
        val status = DeviceStatusRepository(ctx).read()
        val core = setOf(DiagKind.RAM, DiagKind.STORAGE, DiagKind.BATTERY, DiagKind.TEMPERATURE)
        val problems = DiagnosticsEngine.run(status, null)
            .filter { it.kind in core && it.status != DiagStatus.GOOD }
            .map { it.kind }

        val freed = clearOwnCache()
        val performed = listOf(R.string.smart_act_cache)
        val notPerformed = listOf(
            NotPerformed(R.string.smart_na_kill_title, R.string.smart_na_kill_reason),
            NotPerformed(R.string.smart_na_cache_title, R.string.smart_na_cache_reason),
            NotPerformed(R.string.smart_na_files_title, R.string.smart_na_files_reason)
        )
        val score = HealthScoreCalculator.calculate(status)?.score
        val recs = RecommendationEngine.analyze(status)

        val history = HistoryRepository(ctx)
        runCatching {
            history.add(
                HistoryEntry.from(
                    status, score, HistoryEntry.SOURCE_MANUAL, HistoryEntry.TYPE_SMART,
                    "problems=${problems.size}; performed=${performed.size}; notPerformed=${notPerformed.size}"
                )
            )
            if (freed > 0) {
                history.add(
                    HistoryEntry.from(status, score, HistoryEntry.SOURCE_MANUAL, HistoryEntry.TYPE_CLEANUP, "freedBytes=$freed")
                )
            }
        }
        return SmartResult(score, problems, performed, freed, notPerformed, recs)
    }

    private fun clearOwnCache(): Long {
        var freed = 0L
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).forEach { dir ->
            runCatching {
                dir.listFiles()?.forEach { f -> freed += sizeOf(f); f.deleteRecursively() }
            }
        }
        return freed
    }

    private fun sizeOf(f: File): Long =
        if (f.isFile) f.length() else f.listFiles()?.sumOf { sizeOf(it) } ?: 0L
}
