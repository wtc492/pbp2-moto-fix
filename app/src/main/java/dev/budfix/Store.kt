package dev.budfix

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small wrapper around SharedPreferences: settings, a rolling log, and the auto-fix rate limit. */
object Store {
    private const val MAX_LOG_LINES = 200
    private const val AUTO_FIX_WINDOW_MS = 10 * 60_000L
    private const val AUTO_FIX_MAX_IN_WINDOW = 2

    fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("budfix", Context.MODE_PRIVATE)

    fun target(c: Context): String? = prefs(c).getString("target", null)
    fun setTarget(c: Context, address: String) = prefs(c).edit().putString("target", address).apply()

    fun autoFix(c: Context): Boolean = prefs(c).getBoolean("autoFix", false)
    fun setAutoFix(c: Context, on: Boolean) = prefs(c).edit().putBoolean("autoFix", on).apply()

    /** Last ACL (base link) state we saw via broadcast, as a fallback if the phone won't tell us directly. */
    fun aclUp(c: Context): Boolean = prefs(c).getBoolean("aclUp", false)
    fun setAclUp(c: Context, up: Boolean) = prefs(c).edit().putBoolean("aclUp", up).apply()

    /** Remembers that we just restarted Bluetooth, so the next good check can report success. */
    fun markRestarted(c: Context) =
        prefs(c).edit().putLong("restartedAt", System.currentTimeMillis()).apply()

    fun restartedRecently(c: Context, windowMs: Long = 3 * 60_000L): Boolean =
        System.currentTimeMillis() - prefs(c).getLong("restartedAt", 0L) < windowMs

    fun clearRestarted(c: Context) = prefs(c).edit().remove("restartedAt").apply()

    @Synchronized
    fun log(c: Context, line: String) {
        val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        val old = prefs(c).getString("log", "") ?: ""
        val updated = ("$ts  $line\n$old").lineSequence()
            .filter { it.isNotBlank() }
            .take(MAX_LOG_LINES)
            .joinToString("\n")
        prefs(c).edit().putString("log", updated).apply()
    }

    fun logText(c: Context): String = prefs(c).getString("log", "") ?: ""
    fun clearLog(c: Context) = prefs(c).edit().remove("log").apply()

    /**
     * Allows at most two automatic restarts per ten minutes, so a restart that
     * doesn't help can't turn into an endless loop. Returns true if one is allowed now.
     */
    @Synchronized
    fun tryReserveAutoFix(c: Context, now: Long = System.currentTimeMillis()): Boolean {
        val recent = (prefs(c).getString("fixes", "") ?: "")
            .split(",")
            .mapNotNull { it.toLongOrNull() }
            .filter { now - it < AUTO_FIX_WINDOW_MS }
        if (recent.size >= AUTO_FIX_MAX_IN_WINDOW) return false
        prefs(c).edit().putString("fixes", (recent + now).joinToString(",")).apply()
        return true
    }
}
