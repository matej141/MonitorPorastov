package com.android.monitorporastov.geoserver

import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.damagesLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.photosLayerName

object GeoserverPropertiesNames {

    object UrlsNames{
        const val openGisWFSUrl = "\"http://www.opengis.net/wfs\""
        const val openGisGMLUrl = "\"http://www.opengis.net/gml\""
        const val openGisOGCUrl = "\"http://www.opengis.net/ogc\""
        const val xsiUrl = "\"http://www.w3.org/2001/XMLSchema-instance\""
        const val schemaLocationUrl = "http://geoserver.org/geoserver_skeagis"
        const val basicUrl = "http://services.skeagis.sk:7492/geoserver/"
        const val getCapabilitiesUrl = "${basicUrl}ows?service=wms&version=1.3.0&request=GetCapabilities"

        private const val baseDescribeFeatureTypeUrl = "${basicUrl}wfs?SERVICE=WFS&amp;REQUEST=" +
                "DescribeFeatureType&amp;VERSION=1.0.0&amp;TYPENAME="
        const val damagesDescribeFeatureTypeUrl = "${baseDescribeFeatureTypeUrl}$damagesLayerName"
        const val photosDescribeFeatureTypeUrl = "${baseDescribeFeatureTypeUrl}$photosLayerName"
    }


    object DatabasePropertiesName{
        const val propertyNameOfId = "id"
        const val propertyNameOfDamageRecordName = "nazov"
        const val propertyNameOfDamageType = "typ_poskodenia"
        const val propertyNameOfDamageDescription = "popis_poskodenia"

        const val propertyNameOfPerimeter = "obvod"
        const val propertyNameOfArea = "obsah"
        const val propertyNameOfGeometry = "geom"
        const val propertyNameOfUser = "pouzivatel"

        const val propertyNameOfPhotography = "fotografia"
        const val propertyNameOfUniqueId = "unique_id"
        const val propertyNameOfDatetime = "datetime"
    }

    object UrlFilterParametersNames{
        const val urlNameOfIdParameter = "id"
        const val urlNameOfUsernameParameter = "meno"
    }

    object LayersNames {
        const val workspaceName = "geoserver_skeagis"
        const val damagesLayer = "poskodenia"
        const val userDamagesLayer = "poskodenia_sql"
        const val photosLayer = "fotografie"
        private const val ortofotoLayer = "Ortofoto"
        private const val BPEJLayer = "BPEJ"
        private const val C_parcelLayer = "ParcelyRegistraC"
        private const val E_parcelLayer = "ParcelyRegistraE"
        private const val LPISLayer = "LPIS"
        private const val JPRLLayer = "JPRL"
        private const val watercourseLayer = "VodneToky"
        private const val vrstevnice10mLayer = "VrstevniceSR10m"
        private const val vrstevnice50mLayer = "VrstevniceSR50m"

        const val damagesLayerName = "$workspaceName:$damagesLayer"
        const val userDamagesLayerName = "$workspaceName:$userDamagesLayer"
        const val photosLayerName = "$workspaceName:$photosLayer"
        const val ortofotoLayerName = "$workspaceName:$ortofotoLayer"
        const val BPEJLayerName = "$workspaceName:$BPEJLayer"
        const val C_parcelLayerName = "$workspaceName:$C_parcelLayer"
        const val E_parcelLayerName = "$workspaceName:$E_parcelLayer"
        const val LPISLayerName = "$workspaceName:$LPISLayer"
        const val JPRLLayerName = "$workspaceName:$JPRLLayer"
        const val watercourseLayerName = "$workspaceName:$watercourseLayer"
        const val vrstevnice10mLayerName = "$workspaceName:$vrstevnice10mLayer"
        const val vrstevnice50mLayerName = "$workspaceName:$vrstevnice50mLayer"
        const val defaultLayerName = "default"
    }

    const val srsName = "\"urn:ogc:def:crs:EPSG::4326\""









}