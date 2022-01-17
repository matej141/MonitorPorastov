package com.android.monitorporastov

import android.util.Log
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.wms.WMSTileSource
import org.osmdroid.api.IMapView
import org.osmdroid.wms.WMSEndpoint
import org.osmdroid.wms.WMSLayer
import java.lang.Exception
import java.lang.StringBuilder
import kotlin.math.pow

/**
 * Mierne prispôsobená origginálna trieda WmsTIleSource. Má zmenený napírklad formát súradnicového
 * sytému (crs namiesto srs), nehádže errory keď presiahnem bounding box a pod.
 */
open class WMSTileSourceRepaired(
    aName: String?,
    aBaseUrl: Array<String?>?,
    private val layer: String,
    private val crs: String,
    size: Int,
    private val style: String?
) : OnlineTileSourceBase(aName, 0, 22, size, "png", aBaseUrl) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val baseUrl = baseUrl
        val sb = StringBuilder(baseUrl)
        if (!baseUrl.endsWith("?")) sb.append("?")
        sb.append("request=GetMap")
        sb.append("&width=").append(tileSizePixels)
        sb.append("&height=").append(tileSizePixels)
        sb.append("&version=").append(version)
        sb.append("&layers=").append(layer)
        sb.append("&bbox=")
        if (crs == "EPSG:900913") {
            val bbox = getBoundingBox(MapTileIndex.getX(pMapTileIndex),
                MapTileIndex.getY(pMapTileIndex),
                MapTileIndex.getZoom(pMapTileIndex))
            sb.append(bbox[MINX]).append(",")
            sb.append(bbox[MINY]).append(",")
            sb.append(bbox[MAXX]).append(",")
            sb.append(bbox[MAXY])
        } else {
            // pridane
            try {
                val boundingBox = WMSTileSource.tile2boundingBox(MapTileIndex.getX(pMapTileIndex),
                    MapTileIndex.getY(pMapTileIndex),
                    MapTileIndex.getZoom(pMapTileIndex))
                sb.append(boundingBox.latSouth).append(",")
                sb.append(boundingBox.lonWest).append(",")
                sb.append(boundingBox.latNorth).append(",")
                sb.append(boundingBox.lonEast)
            }
            catch (e:Exception) {
                e.printStackTrace()
            }

        }

        //if (style != null)
        //sb.append("&styles=").append(style);
        sb.append("&crs=").append(crs)
        sb.append("&format=image/png")
        sb.append("&transparent=true")
        if (style != null) sb.append("&styles=").append(style)
        Log.i(IMapView.LOGTAG, sb.toString())
        return sb.toString()
    }

    // Return a web Mercator bounding box given tile x/y indexes and a zoom
    // level.
    private fun getBoundingBox(x: Int, y: Int, zoom: Int): DoubleArray {
        val tileSize = MAP_SIZE / 2.0.pow(zoom.toDouble())
        val minX = TILE_ORIGIN[ORIG_X] + x * tileSize
        val maxX = TILE_ORIGIN[ORIG_X] + (x + 1) * tileSize
        val minY = TILE_ORIGIN[ORIG_Y] - (y + 1) * tileSize
        val maxY = TILE_ORIGIN[ORIG_Y] - y * tileSize
        val bbox = DoubleArray(4)
        bbox[MINX] = minX
        bbox[MINY] = minY
        bbox[MAXX] = maxX
        bbox[MAXY] = maxY
        return bbox
    }

    companion object {
        protected const val MINX = 0
        protected const val MAXX = 1
        protected const val MINY = 2
        protected const val MAXY = 3

        // Web Mercator n/w corner of the map.
        private val TILE_ORIGIN = doubleArrayOf(-20037508.34789244, 20037508.34789244)

        //array indexes for that data
        private const val ORIG_X = 0
        private const val ORIG_Y = 1 // "

        // Size of square world map in meters, using WebMerc projection.
        private const val MAP_SIZE = 20037508.34789244 * 2
        private const val version = "1.3.0"
        fun createFrom(endpoint: WMSEndpoint, layer: WMSLayer): WMSTileSourceRepaired {
            val crs = "EPSG:4326"
            val s = if (layer.styles.isEmpty()) null else layer.styles[0]
            return WMSTileSourceRepaired(layer.name,
                arrayOf(endpoint.baseurl),
                layer.name,
                crs,
                layer.pixelSize,
                s)
        }
    }
}