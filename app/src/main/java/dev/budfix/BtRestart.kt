package dev.budfix

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object BtRestart {

    /**
     * Turns Bluetooth off, waits for it to finish, and turns it back on.
     * Only works because the app targets SDK 32; see app/build.gradle.kts.
     *
     * Runs as NonCancellable: once Bluetooth is off, nothing may stop us from
     * turning it back on.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    suspend fun restart(ctx: Context): Boolean = withContext(NonCancellable) {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return@withContext false

        if (adapter.isEnabled) {
            if (!adapter.disable()) {
                Store.log(ctx, "Restart refused: this phone won't let the app turn Bluetooth off")
                return@withContext false
            }
            withTimeoutOrNull(10_000) {
                while (adapter.state != BluetoothAdapter.STATE_OFF) delay(250)
            }
            delay(1_000)
        }

        // Try a few times; the stack sometimes refuses enable() right after shutting down.
        repeat(3) { attempt ->
            if (adapter.isEnabled || adapter.state == BluetoothAdapter.STATE_TURNING_ON) {
                onRestarted(ctx)
                return@withContext true
            }
            if (adapter.enable()) {
                val on = withTimeoutOrNull(10_000) {
                    while (adapter.state != BluetoothAdapter.STATE_ON) delay(250)
                    true
                } ?: false
                if (on) {
                    onRestarted(ctx)
                    return@withContext true
                }
            }
            Store.log(ctx, "Turning Bluetooth back on failed (attempt ${attempt + 1} of 3)")
            delay(1_500)
        }

        Store.log(ctx, "Bluetooth is OFF and the app couldn't turn it back on")
        Notifier.btOff(ctx)
        false
    }

    private fun onRestarted(ctx: Context) {
        Store.log(ctx, "Bluetooth restarted")
        Notifier.clearBtOff(ctx)
        Notifier.result(ctx, "Bluetooth restarted", "Reconnecting your buds…")
    }
}
