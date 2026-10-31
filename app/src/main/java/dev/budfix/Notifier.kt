package dev.budfix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object Notifier {
    private const val CHANNEL = "stuck"
    private const val ID = 1

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Stuck buds alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun stuck(c: Context, name: String) {
        ensureChannel(c)
        val fix = PendingIntent.getBroadcast(
            c, 1, Intent(c, FixReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            c, 2, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_buds)
            .setContentTitle("$name looks stuck")
            .setContentText("Shows as connected, but media audio isn't routed to it.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(c, R.drawable.ic_buds), "Restart Bluetooth", fix
                ).build()
            )
            .build()
        try {
            c.getSystemService(NotificationManager::class.java).notify(ID, n)
        } catch (e: SecurityException) {
            Store.log(c, "Couldn't show notification (notifications blocked for Bud Fix)")
        }
    }

    fun clearStuck(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
