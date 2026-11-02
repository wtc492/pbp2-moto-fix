package dev.budfix

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.delay
import java.lang.reflect.InvocationTargetException

/**
 * Asks Android to connect the buds, the way tapping them in Bluetooth settings does.
 *
 * The methods used here are hidden Android APIs. Many phones restrict them to system
 * apps, so each attempt logs exactly what happened ("accepted", "blocked", ...). If
 * they're blocked on this phone, the tap fallback (TapService) is the alternative.
 */
object Reconnect {

    /** Returns true if Android accepted at least one connect request. */
    @SuppressLint("MissingPermission")
    suspend fun request(ctx: Context, address: String): Boolean {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        if (!adapter.isEnabled) return false
        val device = adapter.getRemoteDevice(address)

        var accepted = false
        for ((profile, label) in listOf(BluetoothProfile.A2DP to "media", BluetoothProfile.HEADSET to "calls")) {
            val result = Checker.withProxy(ctx, adapter, profile) { proxy ->
                callHidden(proxy, "connect", device)
            } ?: "profile unavailable"
            Store.log(ctx, "Reconnect $label: $result")
            if (result == "accepted") accepted = true
        }

        if (!accepted) {
            // Whole-device connect (newer Android). Usually system-only, but cheap to try.
            val result = callHidden(device, "connect")
            Store.log(ctx, "Reconnect device: $result")
            if (result == "accepted") accepted = true
        }
        return accepted
    }

    /** If the buds are connected but audio stays on the phone, point media audio at them. */
    @SuppressLint("MissingPermission")
    suspend fun makeActive(ctx: Context, address: String) {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val device = adapter.getRemoteDevice(address)
        val result = Checker.withProxy(ctx, adapter, BluetoothProfile.A2DP) { proxy ->
            callHidden(proxy, "setActiveDevice", device)
        } ?: "profile unavailable"
        Store.log(ctx, "Make buds the audio output: $result")
        delay(500)
    }

    private fun callHidden(target: Any, method: String, vararg args: BluetoothDevice): String = try {
        val m = target.javaClass.getMethod(method, *Array(args.size) { BluetoothDevice::class.java })
        when (val r = m.invoke(target, *args)) {
            is Boolean -> if (r) "accepted" else "refused"
            // BluetoothStatusCodes.SUCCESS == 0
            is Int -> if (r == 0) "accepted" else "refused (code $r)"
            else -> "accepted"
        }
    } catch (e: NoSuchMethodException) {
        "blocked by Android (not available to apps)"
    } catch (e: InvocationTargetException) {
        val t = e.targetException
        if (t is SecurityException) "blocked (needs a system permission)" else "failed (${t.javaClass.simpleName})"
    } catch (t: Throwable) {
        "failed (${t.javaClass.simpleName})"
    }
}
