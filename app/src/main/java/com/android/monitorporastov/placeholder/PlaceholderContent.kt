package com.android.monitorporastov.placeholder

import java.util.HashMap

/**
 * Objekt určený na zobrazovanie zoznamu poškodení.
 */
object PlaceholderContent {

    var ITEMS_COUNT = 1
    val ITEM_MAP: MutableMap<Int, PlaceholderItem> = HashMap()

    private val placeItem = PlaceholderItem(0, "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
        "Poškodenie diviačou zverou",
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.")

    val ITEMS: MutableList<PlaceholderItem> = arrayListOf(placeItem)


    init {
        ITEM_MAP[0] = placeItem
    }

    fun addItem(item: PlaceholderItem) {
        ITEMS.add(item)
        ITEM_MAP[item.id] = item
        ITEMS_COUNT++
    }

    fun changeItem(item: PlaceholderItem, position: Int) {
        ITEMS[position] = item
        ITEM_MAP[item.id] = item
    }

}

