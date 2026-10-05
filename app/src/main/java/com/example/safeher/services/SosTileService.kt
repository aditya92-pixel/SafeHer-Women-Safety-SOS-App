package com.example.safeher.services

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.safeher.utils.CameraHelper
import com.example.safeher.utils.LocationHelper
import com.example.safeher.utils.PreferencesHelper

@RequiresApi(Build.VERSION_CODES.N)
class SosTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val contacts = PreferencesHelper.getContacts(this)
        LocationHelper.sendSosWithLocation(
            this,
            contacts,
            "[QUICK TILE SOS] Emergency trigger activated from Quick Settings!"
        )
        CameraHelper.capturePhotosOnSos(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "SafeHer SOS"
            updateTile()
        }
    }
}
