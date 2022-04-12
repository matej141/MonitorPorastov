package com.skeagis.monitorporastov.adapters.models

/**
 * Trieda, ktorú využívame na vytvorenie adaptéru do
 * alert dialogu s prvkami, ktoré obsahujú aj obrázok, aj text.
 */
class DialogItem(val text: String, val icon: Int) {
    override fun toString(): String {
        return text
    }
}