package com.example.remitexapp

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReExportActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var list: ListView
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_re_export)

        db = DatabaseHelper(this)
        list = findViewById(R.id.list)

        // Alle Datensätze aus der Datenbank holen
        val data = db.getAllData()

        // Konvertiere die Daten zu einer Liste von Strings für die Anzeige
        val dataStrings = data.map { it.joinToString(", ") }

        // Adapter für die ListView erstellen
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, dataStrings)
        list.adapter = adapter

        // Export Button-Funktion
        val buttonExportAndSend = findViewById<Button>(R.id.buttonExportAndSend)
        buttonExportAndSend.setOnClickListener {
            // Ausgewählte Datensätze erneut exportieren und senden
            val selectedItems = list.checkedItemPositions
            if (selectedItems.size() == 0) {
                // Zeigen Sie die Nachricht an, wenn keine Elemente ausgewählt wurden
                showMessageInToolbar("Bitte wählen Sie Elemente in der Liste aus")
            } else {
                val selectedData = mutableListOf<Array<String>>()
                for (i in 0 until selectedItems.size()) {
                    if (selectedItems.valueAt(i)) {
                        selectedData.add(data[selectedItems.keyAt(i)])
                    }
                }
                val uri = exportDataToFile(selectedData)
                val photoUris = db.getAllPhotoUrisForContainer(this, selectedData)
                uri?.let {
                    db.sendEmailWithAttachments(this, it, photoUris, "c.fluegel@remitex.de")
                }
            }
        }



        // Zurück Button Funktion Aktuelle Activity beenden und zur vorherigen zurückkehren
        val buttonReExportZurueck = findViewById<Button>(R.id.buttonReExportZurueck)
        buttonReExportZurueck.setOnClickListener {
            finish()
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
                        outputStream.write((record.joinToString(",") + "\r\n").toByteArray())
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