package dev.budfix

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one tap restarts Bluetooth. */
class FixTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Fix buds"
            subtitle = null
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        if (applicationInfo.targetSdkVersion >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        Store.log(this, "Tile: restarting Bluetooth")
        Jobs.fixNow(this)
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            subtitle = "Restarting…"
            updateTile()
        }
    }
}
