package dev.budfix

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat

/** Wakes up on Bluetooth events for the chosen buds and schedules a check a few seconds later. */
class BtEventReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val target = Store.target(c) ?: return
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        if (!device?.address.equals(target, ignoreCase = true)) return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                Store.setAclUp(c, true)
                Jobs.scheduleCheck(c, "connected")
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Store.setAclUp(c, false)
                Jobs.cancelCheck(c)
                Notifier.clearStuck(c)
            }

            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_DISCONNECTED) {
                    Jobs.scheduleCheck(c, "media ${stateName(state)}")
                }
            }
        }
    }
}

/** Handles the "Restart Bluetooth" button on the stuck notification. */
class FixReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Store.log(c, "Notification: restarting Bluetooth")
        Jobs.fixNow(c)
    }
}
