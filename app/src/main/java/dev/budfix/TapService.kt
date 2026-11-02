package dev.budfix

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional fallback for phones that won't let apps reconnect Bluetooth devices:
 * opens Bluetooth settings, taps the buds' row (exactly what you'd do by hand),
 * then goes back to whatever you were doing.
 *
 * It's limited to the Settings app (see res/xml/tap_service.xml), so it can't
 * see anything in other apps. It only acts when the phone is unlocked.
 */
class TapService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: TapService? = null

        fun isOn(): Boolean = instance != null

        /** Returns false if the service is off or the phone is locked or asleep. */
        fun tapDevice(ctx: Context, name: String): Boolean {
            val service = instance ?: return false
            val locked = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            val awake = ctx.getSystemService(PowerManager::class.java).isInteractive
            if (locked || !awake) return false
            service.start(name)
            return true
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingName: String? = null

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private fun start(name: String) = handler.post {
        pendingName = name
        Store.log(this, "Tap fallback: opening Bluetooth settings")
        startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        handler.postDelayed({ tryTap() }, 1_500)
        handler.postDelayed({
            if (pendingName != null) {
                Store.log(this, "Tap fallback: couldn't find \"$name\" in Bluetooth settings")
                done()
            }
        }, 10_000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (pendingName != null) tryTap()
    }

    private fun tryTap() {
        val name = pendingName ?: return
        val root = rootInActiveWindow ?: return
        val label = root.findAccessibilityNodeInfosByText(name)
            .firstOrNull { it.text?.toString()?.trim().equals(name, ignoreCase = true) }
            ?: return

        // The text itself usually isn't clickable; the row containing it is.
        var row: AccessibilityNodeInfo? = label
        while (row != null && !row.isClickable) row = row.parent

        if (row?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            pendingName = null
            Store.log(this, "Tap fallback: tapped \"$name\"")
            handler.postDelayed({ done() }, 1_500)
        }
    }

    private fun done() {
        pendingName = null
        handler.removeCallbacksAndMessages(null)
        performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
