package com.skeagis.monitorporastov.geoserver.factories

import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfGeometry
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfUniqueId
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfUser
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.openGisGMLUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.openGisOGCUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.srsName
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

    fun createFilterStringByUniqueId(uniqueId: String): String {
        return "<Filter xmlns:ogc=$openGisOGCUrl " +
                "   xmlns:gml=$openGisGMLUrl>" +
                "           <PropertyIsEqualTo>\n" +
                "               <PropertyName>$propertyNameOfUniqueId</PropertyName>\n" +
                "                   <Literal>" +
                "                       $uniqueId" +
                "                   </Literal>\n" +
                "           </PropertyIsEqualTo>" +
                "</Filter>"
    }

    private fun createCoordinateString(geoPoint: GeoPoint): String {
        return "${geoPoint.latitude},${geoPoint.longitude}"
    }
}