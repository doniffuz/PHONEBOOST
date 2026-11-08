package uz.phoneboost.app.data.automation

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import uz.phoneboost.app.MainActivity
import uz.phoneboost.app.R

object AutomationNotifier {
    private const val CHANNEL_ID = "pb_alerts"

    /** Bildirishnoma ruxsati/sozlamasi yoqilganmi (Android 13+ da POST_NOTIFICATIONS ni ham hisobga oladi). */
    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // canNotify() yuqorida tekshiriladi
    fun notify(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.auto_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
