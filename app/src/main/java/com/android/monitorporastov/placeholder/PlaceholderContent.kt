package com.android.monitorporastov.placeholder

object PlaceholderContent {

    var ITEMS_COUNT = 0
    val ITEMS: MutableList<PlaceholderItem> = ArrayList()


    fun addItem(item: PlaceholderItem) {
        ITEMS.add(item)
        ITEMS_COUNT++
    }

}

