package com.example.micswitcher

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings Tile: tap to cycle the system-wide communication device
 * (built-in mic -> wired -> USB -> Bluetooth -> back to built-in, depending
 * on what's actually connected). Reachable from anywhere via the QS panel,
 * no need to open the app.
 */
class MicTileService : TileService() {

    private val audioManager: AudioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val devices = CommunicationDeviceManager.availableDevices(audioManager)
        if (devices.isEmpty()) {
            refreshTile()
            return
        }
        CommunicationDeviceManager.cycleDevice(this, audioManager)
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val devices = CommunicationDeviceManager.availableDevices(audioManager)
        val current = CommunicationDeviceManager.currentDevice(audioManager)

        if (devices.isEmpty()) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.tile_label)
            tile.subtitle = getString(R.string.tile_no_inputs)
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.tile_label)
            tile.subtitle = current?.let { deviceLabel(it) } ?: getString(R.string.tile_tap_to_choose)
        }
        tile.updateTile()
    }

    private fun deviceLabel(device: AudioDeviceInfo): String {
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
        return name ?: MainActivity.typeLabel(device.type)
    }
}
