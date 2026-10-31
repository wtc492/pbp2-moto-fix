package dev.budfix

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

object BtRestart {

    /**
     * Turns Bluetooth off, waits for it to finish, and turns it back on.
     * Only works because the app targets SDK 32; see app/build.gradle.kts.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    suspend fun restart(ctx: Context): Boolean {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return false

        if (adapter.isEnabled) {
            if (!adapter.disable()) {
                Store.log(ctx, "Restart refused: this phone won't let the app turn Bluetooth off")
                return false
            }
            withTimeoutOrNull(10_000) {
                while (adapter.state != BluetoothAdapter.STATE_OFF) delay(250)
            }
            delay(1_000)
        }

        val ok = adapter.enable()
        Store.log(
            ctx,
            if (ok) "Bluetooth restarted" else "Bluetooth is OFF and the app couldn't turn it back on. Turn it on manually."
        )
        return ok
    }
}
