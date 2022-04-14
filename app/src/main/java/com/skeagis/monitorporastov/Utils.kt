package com.skeagis.monitorporastov

import android.app.Activity
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import okhttp3.MediaType
import okhttp3.RequestBody


// https://stackoverflow.com/questions/39489887/call-method-from-kotlin-class
object Utils {
    // https://dev.to/rohitjakhar/hide-keyboard-in-android-using-kotlin-in-20-second-18gp
    // mozno uzitocne na editText https://stackoverflow.com/questions/52469649/kotlin-hide-soft-keyboard-on-android-8
    fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    fun createRequestBody(requestBodyText: String): RequestBody {
        return RequestBody.create(MediaType.parse("text/xml"), requestBodyText)
    }

    fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun createErrorMessageText(errorMessage: String): String {
        val completeErrorMessage = "Ak problém pretrváva, kontaktuje podporu."
        if (errorMessage.isEmpty()) {
            return completeErrorMessage
        }
        return "Vyskytla sa chyba: $errorMessage\n$completeErrorMessage"
    }

    fun createErrorMessageAD(context: Context, errorMessage: String) {
        val errorMessageTxt = createErrorMessageText(errorMessage)
        AlertDialog.Builder(context)
            .setTitle("Vyskytla sa chyba")
            .setMessage(errorMessageTxt)
            .setNegativeButton("Ok") { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    fun noNetworkAvailable(context: Context): AlertDialog {
        val ad = AlertDialog.Builder(context)
            .setTitle("Nemáte prístup na internet")
            .setMessage("Pre správne fungovanie aplikácie skontrolujte, prosím, pripojenie do siete.")
            .setNegativeButton(R.string.ok_text) { dialog, _ -> dialog.cancel() }
            .create()

        return ad
    }

    fun editableToCharArray(editable: Editable?): CharArray {
        if (editable == null) {
            return charArrayOf()
        }

        val lengthOfEditable = editable.length
        val charArray = CharArray(lengthOfEditable)
        editable.getChars(0, lengthOfEditable, charArray, 0)
        return charArray
    }

    fun EditText.afterTextChanged(afterTextChanged: (Editable) -> Unit) {
        this.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                afterTextChanged.invoke(editable)
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }

}