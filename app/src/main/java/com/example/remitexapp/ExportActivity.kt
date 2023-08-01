package com.example.remitexapp

import DatabaseHelper
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private val scope = CoroutineScope(Dispatchers.IO + Job())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export)

        db = DatabaseHelper(this)

        // Filter-Felder
        val fahrernummerSpinner = findViewById<Spinner>(R.id.fahrernummerSpinner)
        val tagSpinner = findViewById<Spinner>(R.id.tagSpinner)

        // Eindeutige Werte für Filter-Felder aus der Datenbank holen
        val fahrernummerValues = db.getAllFahrernummern()
        val tagValues = db.getAllTags()

        // ArrayAdapter erstellen und an Spinner binden
        fahrernummerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fahrernummerValues)
        tagSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tagValues)

        // Button-Funktionen
        val buttonTransferExport = findViewById<Button>(R.id.buttonTransferExport)
        buttonTransferExport.setOnClickListener {
            val fahrernummer = fahrernummerSpinner.selectedItem as String
            val tag = tagSpinner.selectedItem as String
            val data = db.getSelectedData(fahrernummer, tag)
            val uri = exportDataToFile(data)
            uri?.let {
                sendEmailWithAttachment(uri)
            }
        }

        val buttonTransferZurueck = findViewById<Button>(R.id.buttonTransferZurueck)
        buttonTransferZurueck.setOnClickListener {
            finish()  // Aktuelle Activity beenden und zur vorherigen zurückkehren
        }
    }

    private fun exportDataToFile(data: List<Array<String>>): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "export_$timeStamp.txt"

        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                data.forEach { record ->
                    outputStream.write("${record.joinToString(",")}\n".toByteArray())
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            showMessageInToolbar("Daten erfolgreich exportiert nach $fileName.")
        } ?: run {
            showMessageInToolbar("Fehler beim Exportieren der Daten.")
        }
        return uri
    }

    fun sendEmailWithAttachment(uri: Uri) {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "vnd.android.cursor.dir/email"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("m.mischon@remitex.de"))
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Scan-Datenexport")
        }

        startActivity(Intent.createChooser(emailIntent, "Senden Sie E-Mail..."))
    }


    override fun onDestroy() {
        super.onDestroy()
        scope.cancel() // Coroutine-Scope wird abgebrochen, wenn die Activity zerstört wird
    }

    private fun showMessageInToolbar(message: String, onMessageHidden: (() -> Unit)? = null) {
        val toolbarMessage: TextView = findViewById(R.id.toolbar_message)
        toolbarMessage.text = message
        toolbarMessage.visibility = View.VISIBLE

        // Meldung nach einigen Sekunden wieder ausblenden
        Handler(Looper.getMainLooper()).postDelayed({
            toolbarMessage.visibility = View.GONE
            onMessageHidden?.invoke()
        }, 2000)
    }
}
