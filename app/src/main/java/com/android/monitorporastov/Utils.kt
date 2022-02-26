package com.android.monitorporastov

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import com.android.monitorporastov.databinding.FragmentMapBinding
import com.android.monitorporastov.fragments.MapFragment
import com.android.monitorporastov.model.DamageData
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit


// https://stackoverflow.com/questions/39489887/call-method-from-kotlin-class
object Utils {

    private var selectedRecord: DamageData = DamageData()

    fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor("dano", "test"))
            .connectTimeout(100, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    fun setSelectedItem(selectedItem: DamageData) {
        this.selectedRecord = selectedItem
    }

    private fun createRequestBody(requestBodyText: String): RequestBody {
        return RequestBody.create(MediaType.parse("text/xml"), requestBodyText)
    }

    private fun createDeleteRecordTransactionString(): String {
        return "<Transaction xmlns=\"http://www.opengis.net/wfs\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://opengeo.org/geoserver_skuska " +
                "http://services.skeagis.sk:7492/geoserver/wfs?SERVICE=WFS&amp;REQUEST=" +
                "DescribeFeatureType&amp;VERSION=1.0.0&amp;TYPENAME=geoserver_skuska:porasty\" " +
                "xmlns:gml=\"http://www.opengis.net/gml\" version=\"1.0.0\" service=\"WFS\" " +
                "xmlns:geoserver_skuska=\"http://opengeo.org/geoserver_skuska\">\n" +
                "    <Delete xmlns=\"http://www.opengis.net/wfs\" " +
                "typeName=\"geoserver_skuska:porasty\">\n" +
                "        <Filter xmlns=\"http://www.opengis.net/ogc\">\n" +
                "            <PropertyIsEqualTo>" +
                "<PropertyName>id</PropertyName>" +
                "<Literal>${selectedRecord.id}</Literal>" +
                "</PropertyIsEqualTo>\n" +
                "        </Filter>\n" +
                "    </Delete>\n" +
                "</Transaction>\n"
    }

    suspend fun handleDeletingOfRecord(context: Context, progressBar: ProgressBar): Boolean {
        val deferredBoolean = CompletableDeferred<Boolean>()
        CoroutineScope(Dispatchers.Main).launch {
            if (!showDeleteRecordAD(context)) {
                deferredBoolean.complete(false)
            }
            progressBar.visibility = View.VISIBLE
            val resultAwaiting = async { deleteRecord()}
            val result = resultAwaiting.await()
            deferredBoolean.complete(result)
            progressBar.visibility = View.GONE
        }
        return deferredBoolean.await()
    }

    private suspend fun showDeleteRecordAD(context: Context): Boolean {
        val deferredBoolean = CompletableDeferred<Boolean>()
        CoroutineScope(Dispatchers.Main).launch {
            AlertDialog.Builder(context)  //
                .setTitle(R.string.if_delete_record_title)
                .setPositiveButton(R.string.button_positive_text) { _, _ ->
                      deferredBoolean.complete(true)  }
                .setNegativeButton(R.string.button_negative_text) { dialog, _ ->
                    deferredBoolean.complete(false)
                    dialog.cancel() }
                .create()
                .show()
        }
        return deferredBoolean.await()
    }

    private suspend fun deleteRecord(): Boolean {
        val deleteRecordString = createDeleteRecordTransactionString()
        val requestBody = createRequestBody(deleteRecordString)
        val deferredBoolean: Deferred<Boolean> = CoroutineScope(Dispatchers.Main).async<Boolean> {
            val deferred = async { postToGeoserver(requestBody) }
            deferred.await()
        }
        return deferredBoolean.await()
    }

    suspend fun postToGeoserver(requestBody: RequestBody): Boolean {
        val deferredBoolean: CompletableDeferred<Boolean> = CompletableDeferred<Boolean>()
        val service = RetroService.getServiceWithScalarsFactory(Utils.createOkHttpClient())
        CoroutineScope(Dispatchers.IO).launch {
            val response = service.postToGeoserver(requestBody)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    // binding.progressBar.visibility = View.GONE
                    val r: String? = response.body()

                    Log.d("MODELL", "Success!!!!!!!!!!!!!!!!!!!")
                    if (r != null) {
                        Log.d("MODELL", r)
                    }
                    if (r != null && r.contains("SUCCESS")) {
                        Log.d("MODELL", "Fotky úspešné....")
                    }
                    deferredBoolean.complete(true)
                } else {
                    Log.d("MODELL", "Error: ${response.message()}")
                    deferredBoolean.complete(false)
                }
            }
        }
        return deferredBoolean.await()
    }

    // https://dev.to/rohitjakhar/hide-keyboard-in-android-using-kotlin-in-20-second-18gp
    // mozno uzitocne na editText https://stackoverflow.com/questions/52469649/kotlin-hide-soft-keyboard-on-android-8
    fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

}