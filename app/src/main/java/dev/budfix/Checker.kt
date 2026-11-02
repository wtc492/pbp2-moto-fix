package dev.budfix

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class BudStatus(
    val name: String,
    /** Base Bluetooth link. This is what makes Settings say "Connected". */
    val aclConnected: Boolean,
    /** Whether that came from the phone directly (false = inferred from broadcasts). */
    val aclDirect: Boolean,
    /** Media (A2DP) profile state. */
    val a2dp: Int,
    /** Calls (HFP) profile state. */
    val headset: Int,
    /** Whether Android's audio system actually lists the buds as a media output. */
    val routed: Boolean,
) {
    private val linkUp: Boolean
        get() = aclConnected ||
            a2dp == BluetoothProfile.STATE_CONNECTED ||
            headset == BluetoothProfile.STATE_CONNECTED

    /** The phantom state: the phone thinks the buds are connected, but media audio has nowhere to go. */
    val phantom: Boolean get() = linkUp && !routed

    fun describe(): String =
        "link=${if (aclConnected) "up" else "down"}${if (aclDirect) "" else "(inferred)"} " +
            "media=${stateName(a2dp)} calls=${stateName(headset)} audioRoute=${if (routed) "yes" else "NO"}"
}

fun stateName(state: Int): String = when (state) {
    BluetoothProfile.STATE_DISCONNECTED -> "off"
    BluetoothProfile.STATE_CONNECTING -> "connecting"
    BluetoothProfile.STATE_CONNECTED -> "on"
    BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
    else -> "?"
}

object Checker {

    @SuppressLint("MissingPermission")
    suspend fun check(ctx: Context, address: String): BudStatus? {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        if (!adapter.isEnabled) return null
        val device = adapter.getRemoteDevice(address)

        val a2dpState = profileState(ctx, adapter, BluetoothProfile.A2DP, device)
        val headsetState = profileState(ctx, adapter, BluetoothProfile.HEADSET, device)

        val audio = ctx.getSystemService(AudioManager::class.java)
        val routed = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) &&
                (it.address.isNullOrEmpty() || it.address.equals(address, ignoreCase = true))
        }

        val direct = aclConnectedDirect(device)
        return BudStatus(
            name = device.alias ?: device.name ?: address,
            aclConnected = direct ?: Store.aclUp(ctx),
            aclDirect = direct != null,
            a2dp = a2dpState,
            headset = headsetState,
            routed = routed,
        )
    }

    private suspend fun profileState(
        ctx: Context,
        adapter: BluetoothAdapter,
        profile: Int,
        device: BluetoothDevice,
    ): Int = withProxy(ctx, adapter, profile) { it.getConnectionState(device) } ?: -1

    /** Opens a profile proxy (media, calls, ...), runs [block] with it, and closes it. Null if unavailable. */
    suspend fun <T> withProxy(
        ctx: Context,
        adapter: BluetoothAdapter,
        profile: Int,
        block: (BluetoothProfile) -> T,
    ): T? {
        val proxy = withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine<BluetoothProfile?> { cont ->
                val ok = adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        if (cont.isActive) cont.resume(proxy)
                    }

                    override fun onServiceDisconnected(p: Int) {}
                }, profile)
                if (!ok && cont.isActive) cont.resume(null)
            }
        } ?: return null
        return try {
            block(proxy)
        } finally {
            adapter.closeProfileProxy(profile, proxy)
        }
    }

    /**
     * BluetoothDevice.isConnected() is a hidden method, but Android still allows apps
     * to call it. Returns null if this phone blocks it.
     */
    private fun aclConnectedDirect(device: BluetoothDevice): Boolean? = try {
        device.javaClass.getMethod("isConnected").invoke(device) as? Boolean
    } catch (t: Throwable) {
        null
    }
}
