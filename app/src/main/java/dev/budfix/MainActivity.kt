package dev.budfix

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings screen: pick the buds, choose notify vs. auto-fix, and read the check log. */
class MainActivity : Activity() {

    private lateinit var deviceGroup: RadioGroup
    private lateinit var logView: TextView
    private lateinit var tapStatus: TextView

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "log") runOnUiThread { logView.text = Store.logText(this).ifEmpty { "(empty)" } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this) // on Android 13+, this triggers the notification permission prompt
        buildUi()
        if (hasBtPermission()) loadDevices()
        else requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
    }

    override fun onResume() {
        super.onResume()
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        logView.text = Store.logText(this).ifEmpty { "(empty)" }
        refreshTapStatus()
    }

    private fun refreshTapStatus() {
        tapStatus.text = when {
            !Store.tapFallback(this) -> "Off."
            TapService.isOn() -> "On and ready."
            else -> "Not enabled yet: turn on \"Bud Fix reconnect tapper\" in Accessibility settings."
        }
    }

    override fun onPause() {
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onPause()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        loadDevices()
    }

    // Apps targeting Android 11 (this one) get Bluetooth access at install time.
    private fun hasBtPermission() =
        applicationInfo.targetSdkVersion < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        root.addView(header("Earbuds to watch"))
        deviceGroup = RadioGroup(this)
        root.addView(deviceGroup)

        root.addView(header("When they get stuck"))
        root.addView(Switch(this).apply {
            text = "Restart Bluetooth automatically (off = just notify me)"
            isChecked = Store.autoFix(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Store.setAutoFix(this@MainActivity, on) }
        })

        root.addView(header("If the buds don't reconnect"))
        root.addView(TextView(this).apply {
            text = "After restarting Bluetooth, Bud Fix asks Android to reconnect the buds. " +
                "If your phone blocks that, this can open Bluetooth settings and tap the buds for you " +
                "(only while the phone is unlocked). It uses an accessibility service that can only see the Settings app."
        })
        root.addView(Switch(this).apply {
            text = "Tap the buds in Bluetooth settings for me"
            isChecked = Store.tapFallback(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Store.setTapFallback(this@MainActivity, on)
                refreshTapStatus()
                if (on && !TapService.isOn()) openAccessibilitySettings()
            }
        })
        tapStatus = TextView(this).apply { setPadding(0, dp(4), 0, 0) }
        root.addView(tapStatus)
        root.addView(button("Open Accessibility settings") { openAccessibilitySettings() })

        root.addView(header("Manual"))
        root.addView(button("Check now") {
            if (Store.target(this) == null) toast("Pick your earbuds first")
            else Jobs.scheduleCheck(this, "manual", 0)
        })
        root.addView(button("Restart Bluetooth now") {
            if (!hasBtPermission()) toast("Allow Nearby devices first")
            else { Store.log(this, "Manual: restarting Bluetooth"); Jobs.fixNow(this) }
        })

        root.addView(header("Log (newest first)"))
        root.addView(button("Clear log") { Store.clearLog(this) })
        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(logView)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    @SuppressLint("MissingPermission")
    private fun loadDevices() {
        deviceGroup.removeAllViews()
        if (!hasBtPermission()) {
            deviceGroup.addView(TextView(this).apply {
                text = "Bud Fix needs the Nearby devices permission to see your earbuds. " +
                    "Grant it in Settings > Apps > Bud Fix > Permissions."
            })
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val bonded = adapter?.bondedDevices?.sortedBy { it.name ?: "" }.orEmpty()
        if (bonded.isEmpty()) {
            deviceGroup.addView(TextView(this).apply { text = "No paired devices found. Is Bluetooth on?" })
            return
        }
        val target = Store.target(this)
        for (d in bonded) {
            deviceGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = "${d.name ?: "Unknown"}\n${d.address}"
                isChecked = d.address.equals(target, ignoreCase = true)
                setOnClickListener {
                    Store.setTarget(this@MainActivity, d.address)
                    Store.log(this@MainActivity, "Now watching ${d.name ?: d.address}")
                }
            })
        }
    }

    private fun openAccessibilitySettings() {
        toast("Find \"Bud Fix reconnect tapper\" and turn it on")
        startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
