package com.android.monitorporastov

import android.content.Context
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.views.overlay.TilesOverlay

class TilesOverlayRepaired(mapTileProviderBase: MapTileProviderBase, context: Context?) :
    TilesOverlay(mapTileProviderBase, context) {
    var withUserData = false
    var layerName = ""
}