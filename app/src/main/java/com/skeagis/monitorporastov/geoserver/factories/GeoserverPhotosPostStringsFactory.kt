package com.skeagis.monitorporastov.geoserver.factories

import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfId
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfPhotography
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfUniqueId
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.photosLayer
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.photosLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.workspaceName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.openGisGMLUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.openGisOGCUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.openGisWFSUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.photosDescribeFeatureTypeUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.schemaLocationUrl
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.xsiUrl

object GeoserverPhotosPostStringsFactory {

    fun createUpdatePhotosTransactionString(
        deletedIndexesList: List<Int>,
        hexOfPhotosList: List<String>,
        uniqueId: String,
    ): String {
        val deletePhotosString = createDeletePhotosString(deletedIndexesList)
        val insertPhotosString = createInsertPhotosString(hexOfPhotosList, uniqueId)
        var updatePhotosTransactionString = ""
        if (deletePhotosString.isEmpty() && insertPhotosString.isEmpty()) {
            return updatePhotosTransactionString
        }
        updatePhotosTransactionString += createHeaderOfPhotosTransaction()
        if (deletePhotosString.isNotEmpty()) {
            updatePhotosTransactionString += deletePhotosString
        }
        if (insertPhotosString.isNotEmpty()) {
            updatePhotosTransactionString += insertPhotosString
        }
        updatePhotosTransactionString += "" +
                "</Transaction>"
        return updatePhotosTransactionString
    }

    private fun createHeaderOfPhotosTransaction(): String {
        return "<Transaction xmlns=$openGisWFSUrl\n" +
                "               xmlns:xsi=$xsiUrl\n" +
                "               xmlns:$workspaceName=\"$schemaLocationUrl\"\n" +
                "               xmlns:gml=$openGisGMLUrl service=\"WFS\"\n" +
                "               xsi:schemaLocation=\"$schemaLocationUrl " +
                "               $photosDescribeFeatureTypeUrl\" " +
                "               version=\"1.0.0\">\n"
    }

    private fun createDeletePhotosString(deletedIndexesList: List<Int>): String {
        val deleteFilterString = createDeleteFilterString(deletedIndexesList)
        if (deleteFilterString.isEmpty()) {
            return ""
        }
        return "" +
                "<Delete xmlns=$openGisWFSUrl typeName=\"$photosLayerName\">\n" +
                "           <Filter xmlns=$openGisOGCUrl>\n" +
                "               <Or>\n" +
                "                   $deleteFilterString" +
                "               </Or>\n" +
                "           </Filter>\n" +
                "</Delete>"
    }

    private fun createDeleteFilterString(deletedIndexesList: List<Int>): String {
        var deleteFilterString = ""
        deletedIndexesList.forEach {
            deleteFilterString +=
                "       <PropertyIsEqualTo>\n" +
                        "       <PropertyName>$propertyNameOfId</PropertyName>\n" +
                        "       <Literal>$it</Literal>\n" +
                        "</PropertyIsEqualTo>\n"
        }
        return deleteFilterString
    }

    fun createDeletePhotosStringWithUniqueId(uniqueId: String): String {
        return createHeaderOfPhotosTransaction() +
                "<Delete xmlns=$openGisWFSUrl typeName=\"$photosLayerName\">\n" +
                "           <Filter xmlns=$openGisOGCUrl>\n" +
                "               <PropertyIsEqualTo>\n" +
                "                   <PropertyName>$propertyNameOfUniqueId</PropertyName>\n" +
                "                       <Literal>$uniqueId</Literal>\n" +
                "               </PropertyIsEqualTo>\n" +
                "           </Filter>\n" +
                "</Delete>" +
                "</Transaction>"
    }

    private fun createInsertPhotosString(hexOfPhotosList: List<String>, uniqueId: String): String {
        val photoStrings = createPhotoStrings(hexOfPhotosList, uniqueId)
        if (photoStrings.isEmpty()) {
            return ""
        }
        return "" +
                "<Insert xmlns=$openGisWFSUrl>\n" +
                "        $photoStrings" +
                "</Insert>\n"
    }

    private fun createPhotoStrings(hexOfPhotosList: List<String>, uniqueId: String): String {
        val photoHexStrings = hexOfPhotosList.filter { it != "" }
        var photoStrings = ""
        photoHexStrings.forEach {
            val line =
                "" +
                        "<$photosLayer xmlns=\"$schemaLocationUrl\">\n" +
                        "       <$propertyNameOfPhotography xmlns=\"$schemaLocationUrl\">" +
                        "           $it" +
                        "       </$propertyNameOfPhotography>\n " +
                        "       <$propertyNameOfUniqueId xmlns=\"$schemaLocationUrl\">" +
                        "           $uniqueId" +
                        "       </$propertyNameOfUniqueId>\n" +
                        "</$photosLayer>\n"
            photoStrings += line
        }
        return photoStrings
    }

    fun createInsertPhotosTransactionString(
        hexOfPhotosList: List<String>,
        uniqueId: String,
    ): String {
        val photoStrings = createPhotoStrings(hexOfPhotosList, uniqueId)
        return createHeaderOfPhotosTransaction() +
                "   <Insert xmlns=$openGisWFSUrl>\n" +
                "       $photoStrings" +
                "   </Insert>\n" +
                "</Transaction>"
    }

}