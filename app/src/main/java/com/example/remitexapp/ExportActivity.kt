package com.example.remitexapp

import DatabaseHelper
import android.content.ContentValues
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*

class ExportActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper

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
            exportDataToFile(data)
        }

        val buttonTransferZurueck = findViewById<Button>(R.id.buttonTransferZurueck)
        buttonTransferZurueck.setOnClickListener {
            finish()  // Aktuelle Activity beenden und zur vorherigen zurückkehren
        }
    }

    private fun exportDataToFile(data: List<Array<String>>) {
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

            showSnackbar("Daten erfolgreich exportiert nach $fileName.")
        } ?: run {
            showSnackbar("Fehler beim Exportieren der Daten.")
        }
    }

    private fun showSnackbar(message: String) {
        val rootView = window.decorView.rootView
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).apply {
            setBackgroundTint(Color.BLUE)  // Hintergrundfarbe
            setTextColor(Color.WHITE)  // Textfarbe
            show()
        }
    }
}
