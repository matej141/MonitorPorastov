//package com.android.ciernikmasterdetail.placeholder
//
//import com.android.ciernikmasterdetail.PhotosFind
//import com.android.ciernikmasterdetail.PlaceholderItem
//import java.util.*
//
//
//object PlaceholderContent {
//
//    val ITEMS: MutableList<PlaceholderItem> = ArrayList()
//
//    val ITEM_MAP: MutableMap<String, PlaceholderItem> = HashMap()
//
//    private val photos = PhotosFind().findPhotos()
//
//
//    init {
//        for (i in 1..photos.size) {
//            addItem(createPlaceholderItem(i))
//        }
//    }
//
//    private fun addItem(item: PlaceholderItem) {
//        ITEMS.add(item)
//        ITEM_MAP[item.id] = item
//    }
//
//    private fun createPlaceholderItem(position: Int): PlaceholderItem {
//        return PlaceholderItem(position.toString(), photos[position-1])
//    }
//}
//
