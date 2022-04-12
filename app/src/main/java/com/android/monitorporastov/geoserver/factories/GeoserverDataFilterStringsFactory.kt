package com.android.monitorporastov.geoserver.factories

import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfGeometry
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfUser
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.openGisGMLUrl
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.openGisOGCUrl
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.srsName
import org.osmdroid.util.GeoPoint

object GeoserverDataFilterStringsFactory {

    fun createFilterStringByIntersectAndUsername(geoPoint: GeoPoint, username: String): String {
        val coordinateString = createCoordinateString(geoPoint)
        return "<Filter xmlns:ogc=$openGisOGCUrl " +
                "   xmlns:gml=$openGisGMLUrl>" +
                "       <And>" +
                "           <Intersects>" +
                "               <PropertyName>$propertyNameOfGeometry</PropertyName>" +
                "                   <gml:Point srsName=$srsName>" +
                "                       <gml:coordinates>$coordinateString</gml:coordinates>" +
                "                   </gml:Point>" +
                "           </Intersects>\n" +

                "           <PropertyIsEqualTo>\n" +
                "               <PropertyName>$propertyNameOfUser</PropertyName>\n" +
                "                   <Literal>" +
                "                       $username" +
                "                   </Literal>\n" +
                "           </PropertyIsEqualTo>" +
                "       </And>" +
                "</Filter>"
    }

    private fun createCoordinateString(geoPoint: GeoPoint): String {
        return "${geoPoint.latitude},${geoPoint.longitude}"
    }
}