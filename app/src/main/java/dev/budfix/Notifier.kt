package dev.budfix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
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

    private const val RESULT_CHANNEL = "results"
    private const val RESULT_ID = 3

    /** Status of a restart: "restarted", then "fixed" or "not back yet". Updates in place. */
    fun result(c: Context, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(RESULT_CHANNEL, "Fix results", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            c, 4, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_buds)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .build()
        try {
            nm.notify(RESULT_ID, n)
        } catch (e: SecurityException) {
            Store.log(c, "Couldn't show notification (notifications blocked for Bud Fix)")
        }
    }

    private const val BT_OFF_ID = 2

    /** Last resort if Bluetooth stays off: one tap opens Android's "turn on Bluetooth?" prompt. */
    fun btOff(c: Context) {
        ensureChannel(c)
        val turnOn = PendingIntent.getActivity(
            c, 3,
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_buds)
            .setContentTitle("Bluetooth is off")
            .setContentText("Bud Fix couldn't turn it back on. Tap to turn it on.")
            .setContentIntent(turnOn)
            .setAutoCancel(true)
            .build()
        try {
            c.getSystemService(NotificationManager::class.java).notify(BT_OFF_ID, n)
        } catch (e: SecurityException) {
            Store.log(c, "Couldn't show notification (notifications blocked for Bud Fix)")
        }
    }

    fun clearBtOff(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(BT_OFF_ID)
    }
}
