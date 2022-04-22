package com.skeagis.monitorporastov.geoserver.factories

import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfArea
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfDamageDescription
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfDamageRecordName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfDamageType
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfDatetime
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfGeometry
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfPerimeter
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfUniqueId
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfUser
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.damagesLayer
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.damagesLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.workspaceName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.damagesDescribeFeatureTypeUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.openGisGMLUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.openGisOGCUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.openGisWFSUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.schemaLocationUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.xsiUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.srsName
import com.skeagis.monitorporastov.model.DamageData


object GeoserverDataPostStringsFactory {

    fun createUpdateDamageDataString(originalData: DamageData, updatedData: DamageData): String {
        val updatePropertiesString = createUpdatePropertiesString(originalData, updatedData)
        if (updatePropertiesString.isEmpty()) {
            return ""
        }
        return createHeaderOfTransaction() +
                "   <Update xmlns=$openGisWFSUrl\n" +
                "               typeName=\"$damagesLayerName\">\n" +
                "                   $updatePropertiesString\n" +
                "       <Filter xmlns=$openGisOGCUrl>\n" +
                "           <PropertyIsEqualTo>\n" +

                "               <PropertyName>$propertyNameOfUniqueId</PropertyName>\n" +
                "               <Literal>${originalData.unique_id}</Literal>\n" +

                "           </PropertyIsEqualTo>\n" +
                "       </Filter>\n" +
                "   </Update>\n" +
                "</Transaction>"
    }

    private fun createHeaderOfTransaction(): String {
        return "<Transaction xmlns=$openGisWFSUrl\n" +
                "               xmlns:$workspaceName=\"$schemaLocationUrl\"\n" +
                "               xmlns:xsi=$xsiUrl\n" +
                "               xsi:schemaLocation=\"$schemaLocationUrl " +
                "               $damagesDescribeFeatureTypeUrl\" " +
                "               version=\"1.0.0\" service=\"WFS\" xmlns:gml=$openGisGMLUrl>\n"
    }

    private fun createUpdatePropertiesString(originalData: DamageData, updatedData: DamageData): String {
        val name = updatedData.nazov
        val damageType = updatedData.typ_poskodenia
        val description = updatedData.popis_poskodenia
        var updatePropertiesString = ""
        if (name != originalData.nazov) {
            updatePropertiesString += createNamePropertyString(name)
        }
        if (damageType != originalData.typ_poskodenia) {
            updatePropertiesString += createTypePropertyString(damageType)
        }
        if (description != originalData.popis_poskodenia) {
            updatePropertiesString += createDescriptionPropertyString(description)
        }
        if (updatedData.changedShapeOfPolygon) {
            updatePropertiesString +=
                createPerimeterPropertyString(originalData.obvod.toString()) +
                        createAreaPropertyString(originalData.obsah.toString()) +
                        createGeometryPropertyString(originalData)
        }
        return updatePropertiesString
    }

    private fun createNamePropertyString(name: String): String {
        return createPropertyString(propertyNameOfDamageRecordName, name)
    }

    private fun createTypePropertyString(damageType: String): String {
        return createPropertyString(propertyNameOfDamageType, damageType)
    }

    private fun createDescriptionPropertyString(description: String): String {
        return createPropertyString(propertyNameOfDamageDescription, description)
    }

    private fun createPerimeterPropertyString(perimeterString: String): String {
        return createPropertyString(propertyNameOfPerimeter, perimeterString)
    }

    private fun createAreaPropertyString(areaString: String): String {
        return createPropertyString(propertyNameOfArea, areaString)
    }

    private fun createGeometryPropertyString(data: DamageData): String {
        val stringFromPoints = createStringFromPoints(data)
        return "" +
                "<Property xmlns=$openGisWFSUrl>\n" +
                "   <Name xmlns=$openGisWFSUrl>$propertyNameOfGeometry</Name>\n" +
                "   <Value xmlns=$openGisWFSUrl>\n" +
                "       <gml:Polygon srsName=$srsName>\n" +
                "           <gml:outerBoundaryIs>\n" +
                "               <gml:LinearRing>\n" +
                "                   <gml:coordinates cs=\",\" ts=\" \">" +
                "                       $stringFromPoints\n" +
                "                   </gml:coordinates>\n" +
                "               </gml:LinearRing>\n" +
                "           </gml:outerBoundaryIs>\n" +
                "       </gml:Polygon>\n" +
                "   </Value>\n" +
                "</Property>\n"
    }

    private fun createPropertyString(propertyName: String, value: String): String {
        return "" +
                "<Property xmlns=$openGisWFSUrl>\n" +
                "   <Name xmlns=$openGisWFSUrl>$propertyName</Name>\n" +
                "   <Value xmlns=$openGisWFSUrl>\n" +
                "       $value\n" +
                "   </Value>\n" +
                "</Property>\n"
    }

    private fun createStringFromPoints(data: DamageData): String {
        val geoPoints = data.coordinates
        var stringFromPoints = ""
        geoPoints.forEach { stringFromPoints += "${it.latitude},${it.longitude} " }
        return stringFromPoints
    }

    fun createInsertDataTransactionString(newDamageData: DamageData): String {
        val stringFromPoints = createStringFromPoints(newDamageData)

        return createHeaderOfTransaction() +
                "<Insert xmlns=$openGisWFSUrl>\n" +
                "   <$damagesLayer xmlns=\"$schemaLocationUrl\">\n" +
                "       <$propertyNameOfDamageRecordName xmlns=\"$schemaLocationUrl\">\n" +
                "           ${newDamageData.nazov}\n" +
                "       </$propertyNameOfDamageRecordName>\n" +
                "       <$propertyNameOfUser xmlns=\"$schemaLocationUrl\">\n" +
                "           ${newDamageData.pouzivatel}\n" +
                "       </$propertyNameOfUser>\n" +
                "       <$propertyNameOfDamageType xmlns=\"$schemaLocationUrl\">\n" +
                "           ${newDamageData.typ_poskodenia}\n" +
                "       </$propertyNameOfDamageType>\n" +
                "       <$propertyNameOfDamageDescription xmlns=\"$schemaLocationUrl\">\n" +
                "           ${newDamageData.popis_poskodenia}\n" +
                "       </$propertyNameOfDamageDescription>\n" +
                "       <$propertyNameOfPerimeter xmlns=\"$schemaLocationUrl\">\n" +
                "           ${newDamageData.obvod}\n" +
                "       </$propertyNameOfPerimeter>\n" +
                "       <$propertyNameOfArea xmlns=\"$schemaLocationUrl\">\n" +
                "           ${newDamageData.obsah}\n" +
                "       </$propertyNameOfArea>\n" +
                "       <$propertyNameOfUniqueId xmlns=\"$schemaLocationUrl\">\n" +
                "           ${newDamageData.unique_id}\n" +
                "       </$propertyNameOfUniqueId>\n" +
                "       <$propertyNameOfDatetime xmlns=\"$schemaLocationUrl\">\n" +
                "           ${newDamageData.datetime}\n" +
                "       </$propertyNameOfDatetime>\n" +
                "       <$propertyNameOfGeometry xmlns=\"$schemaLocationUrl\">\n" +
                "           <gml:Polygon srsName=$srsName>\n" +
                "               <gml:outerBoundaryIs>\n" +
                "                   <gml:LinearRing>\n" +
                "                       <gml:coordinates cs=\",\" ts=\" \">\n" +
                                            stringFromPoints + "\n" +
                "                       </gml:coordinates>\n" +
                "                   </gml:LinearRing>\n" +
                "               </gml:outerBoundaryIs>\n" +
                "           </gml:Polygon>\n" +
                "       </$propertyNameOfGeometry>\n" +
                "       </$damagesLayer>\n" +
                "</Insert>\n" +
                "</Transaction>\n"
    }

    fun createDeleteRecordTransactionString(uniqueId: String): String {
        return createHeaderOfTransaction() +
                "<Delete xmlns=$openGisWFSUrl " +
                "       typeName=\"$damagesLayerName\">\n" +
                "       <Filter xmlns=$openGisOGCUrl>\n" +
                "           <PropertyIsEqualTo>" +
                "               <PropertyName>$propertyNameOfUniqueId</PropertyName>" +
                "                   <Literal>$uniqueId</Literal>" +
                "           </PropertyIsEqualTo>\n" +
                "       </Filter>\n" +
                "</Delete>\n" +
                "</Transaction>\n"
    }
}