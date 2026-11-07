package uz.phoneboost.app.data.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import uz.phoneboost.app.R
import uz.phoneboost.app.data.device.DeviceStatusRepository
import uz.phoneboost.app.data.history.HistoryEntry
import uz.phoneboost.app.data.history.HistoryRepository
import uz.phoneboost.app.domain.HealthScoreCalculator
import java.util.concurrent.TimeUnit

/**
 * Fon diagnostikasi: telefonni o‘qiydi, natijani tarixga yozadi va (foydalanuvchi yoqqan bo‘lsa) ogohlantiradi.
 * Hech narsani o‘chirmaydi va tizim sozlamalarini o‘zgartirmaydi.
 */
class AutomationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = AutomationPrefs(ctx)
        val forced = inputData.getBoolean(KEY_FORCE, false)
        if (!forced && !prefs.enabled) return Result.success()

        val status = DeviceStatusRepository(ctx).read()
        val score = HealthScoreCalculator.calculate(status)?.score
        HistoryRepository(ctx).add(HistoryEntry.from(status, score, HistoryEntry.SOURCE_AUTO))
        prefs.lastRunMillis = System.currentTimeMillis()

        if (prefs.alerts) {
            val temp = status.batteryTempC
            val storage = status.freeStorageFraction
            val battery = status.batteryPercent
            if (temp != null && temp >= 42f) alert(prefs, "temp", 1001, R.string.auto_notif_temp_title, R.string.auto_notif_temp_text)
            if (storage != null && storage < 0.10f) alert(prefs, "storage", 1002, R.string.auto_notif_storage_title, R.string.auto_notif_storage_text)
            if (battery != null && battery < 15 && status.isCharging != true) alert(prefs, "battery", 1003, R.string.auto_notif_battery_title, R.string.auto_notif_battery_text)
        }
        return Result.success()
    }

    /** Bir xil ogohlantirish 12 soatdan tez takrorlanmaydi. */
    private fun alert(prefs: AutomationPrefs, kind: String, id: Int, title: Int, text: Int) {
        if (System.currentTimeMillis() - prefs.lastAlert(kind) < TimeUnit.HOURS.toMillis(12)) return
        AutomationNotifier.notify(applicationContext, id, applicationContext.getString(title), applicationContext.getString(text))
        prefs.markAlert(kind)
    }

    companion object {
        private const val UNIQUE = "pb_auto_diagnostics"
        private const val KEY_FORCE = "force"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (enabled) {
                val req = PeriodicWorkRequestBuilder<AutomationWorker>(12, TimeUnit.HOURS).build()
                wm.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
            } else {
                wm.cancelUniqueWork(UNIQUE)
            }
        }

        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<AutomationWorker>().setInputData(workDataOf(KEY_FORCE to true)).build()
            WorkManager.getInstance(context.applicationContext).enqueue(req)
        }
    }
}
