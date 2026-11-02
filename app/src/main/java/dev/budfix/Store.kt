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

    /** Opt-in fallback: open Bluetooth settings and tap the buds when Android refuses to reconnect them. */
    fun tapFallback(c: Context): Boolean = prefs(c).getBoolean("tapFallback", false)
    fun setTapFallback(c: Context, on: Boolean) = prefs(c).edit().putBoolean("tapFallback", on).apply()

    /** Set while a restart + reconnect is running, so checks don't start a second one. */
    fun setFixing(c: Context, on: Boolean) =
        prefs(c).edit().putLong("fixingAt", if (on) System.currentTimeMillis() else 0L).apply()

    fun fixing(c: Context): Boolean =
        System.currentTimeMillis() - prefs(c).getLong("fixingAt", 0L) < 3 * 60_000L

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
