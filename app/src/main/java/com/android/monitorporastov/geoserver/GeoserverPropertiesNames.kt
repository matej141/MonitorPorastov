package com.android.monitorporastov.geoserver

object GeoserverPropertiesNames {

    const val openGisWFSUrl = "\"http://www.opengis.net/wfs\""
    const val openGisGMLUrl = "\"http://www.opengis.net/gml\""
    const val openGisOGCUrl = "\"http://www.opengis.net/ogc\""
    const val xsiUrl = "\"http://www.w3.org/2001/XMLSchema-instance\""
    const val schemaLocationUrl = "http://geoserver.org/geoserver_skeagis"
    private const val basicUrl = "http://services.skeagis:7492/geoserver/"

    const val workspaceName = "geoserver_skeagis"
    const val damagesLayerName = "porasty"
    const val photosLayerName = "fotografie"
    const val damagesTypeName = "$workspaceName:$damagesLayerName"
    const val photosTypeName = "$workspaceName:$photosLayerName"
    private const val baseDescribeFeatureTypeUrl = "${basicUrl}wfs?SERVICE=WFS&amp;REQUEST=" +
    "DescribeFeatureType&amp;VERSION=1.0.0&amp;TYPENAME="
    const val damagesDescribeFeatureTypeUrl = "${baseDescribeFeatureTypeUrl}$damagesTypeName"
    const val photosDescribeFeatureTypeUrl = "${baseDescribeFeatureTypeUrl}$photosTypeName"

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

    const val srsName = "\"urn:ogc:def:crs:EPSG::4326\""

    const val urlNameOfIdParameter = "id"
    const val urlNameOfUsernameParameter = "meno"
}