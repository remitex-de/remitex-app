package com.example.remitexapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.isEmpty
import androidx.core.util.size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

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

        // Nur die gewünschten Spalten extrahieren
        val filteredData = db.filterAndFormatDataForListView(data)

        // Adapter für die ListView erstellen
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, filteredData)
        list.adapter = adapter

        // Export Button-Funktion
        val buttonExportAndSend = findViewById<Button>(R.id.buttonExportAndSend)
        buttonExportAndSend.setOnClickListener {
            handleExportAndSend(data)
        }

        // Zurück Button Funktion
        val buttonReExportZurueck = findViewById<Button>(R.id.buttonReExportZurueck)
        buttonReExportZurueck.setOnClickListener {
            finish()
        }
    }

    private fun handleExportAndSend(data: List<Array<String>>) {
        // Ausgewählte Datensätze erneut exportieren und senden
        val selectedItems = list.checkedItemPositions
        if (selectedItems.isEmpty()) {
            showMessageInToolbar("Bitte wählen Sie Elemente in der Liste aus")
        } else {
            val selectedData = mutableListOf<Array<String>>()
            for (i in 0 until selectedItems.size) {
                if (selectedItems.valueAt(i)) {
                    // Verwende die Originaldaten mit dem Index aus der Auswahl
                    selectedData.add(data[selectedItems.keyAt(i)])
                }
            }
            val uri = db.exportDataToFile(this, data)
            uri?.let {
                showMessageInToolbar("Daten erfolgreich exportiert.")
            } ?: showMessageInToolbar("Fehler beim Exportieren der Daten.")
            val photoUris = db.getAllPhotoUrisForContainer(this, selectedData)
            uri?.let {
                db.sendEmailWithAttachments(this, it, photoUris, BuildConfig.EXPORT_EMAIL)
            }
        }
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