package com.example.remitexapp

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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

    // Berechtigungs-Launcher für mehrere Berechtigungen
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (permission, isGranted) ->
            if (!isGranted) {
                showMessageInToolbar("Die Berechtigung $permission wurde nicht erteilt.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export)

        db = DatabaseHelper(this)

        // Filter-Felder
        val fahrernummerSpinner = findViewById<Spinner>(R.id.fahrernummerSpinner)
        val tagSpinner = findViewById<Spinner>(R.id.tagSpinner)

        // Eindeutige Werte für Fahrernummer-Feld aus der Datenbank holen und sortieren
        val fahrernummerValues = db.getAllFahrernummern().sorted()

        // ArrayAdapter erstellen und an fahrernummerSpinner binden
        fahrernummerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fahrernummerValues)

        // Listener für Fahrernummer-Auswahl
        fahrernummerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>, selectedItemView: View, position: Int, id: Long) {
                val selectedFahrernummer = fahrernummerSpinner.selectedItem as String

                // Daten für die ausgewählte Fahrernummer holen und Adapter für tagSpinner aktualisieren
                val updatedTagValues = db.getTagsForFahrernummer(selectedFahrernummer)
                val tagAdapter = ArrayAdapter(this@ExportActivity, android.R.layout.simple_dropdown_item_1line, updatedTagValues)
                tagSpinner.adapter = tagAdapter
            }

            // Wenn nichts ausgewählt wurde
            override fun onNothingSelected(parentView: AdapterView<*>) {
                showMessageInToolbar("Keine Daten zum Exportieren verfügbar.")
            }
        }

        // Button-Funktionen
        val buttonTransferExport = findViewById<Button>(R.id.buttonTransferExport)
        buttonTransferExport.setOnClickListener {
            handleExport(fahrernummerSpinner, tagSpinner)
        }

        val buttonTransferZurueck = findViewById<Button>(R.id.buttonTransferZurueck)
        buttonTransferZurueck.setOnClickListener {
            finish()  // Aktuelle Activity beenden und zur vorherigen zurückkehren
        }

        val buttonReExport = findViewById<Button>(R.id.buttonReExport)
        buttonReExport.setOnClickListener {
            startActivity(Intent(this, ReExportActivity::class.java))
        }
    }

    // Methode zur Verarbeitung des Exports
    private fun handleExport(fahrernummerSpinner: Spinner, tagSpinner: Spinner) {
        val fahrernummer = fahrernummerSpinner.selectedItem as? String
        val tag = tagSpinner.selectedItem as? String

        if (fahrernummer == null || tag == null) {
            showMessageInToolbar("Bitte wählen Sie gültige Optionen aus.")
            return
        }

        // Datenbankabfrage mit korrekter Reihenfolge
        val data = db.getFilteredExportDataOrdered(fahrernummer, tag)
        if (data.isEmpty()) {
            showMessageInToolbar("Keine Daten zum Exportieren verfügbar.")
            return
        }

        // Berechtigungen anfordern
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            )
        } else {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }

        // Exportdatei erstellen
        val uri = db.exportDataToFile(this, data)

        // Fotos abrufen und E-Mail senden
        val photoUris = db.getAllPhotoUrisForContainer(this, data)
        uri?.let {
            db.sendEmailWithAttachments(this, it, photoUris, "c.fluegel@remitex.de")

            // Erfolgsmeldung
            showMessageInToolbar("Daten erfolgreich exportiert.")

            // Exportdatum in der Datenbank aktualisieren
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentDate = sdf.format(Date())
            db.updateExportDate(fahrernummer, tag, currentDate)
        } ?: showMessageInToolbar("Fehler beim Exportieren der Daten.")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel() // Coroutine-Scope wird abgebrochen, wenn die Activity zerstört wird
    }

    // Methode zur Anzeige von Meldungen in der Toolbar
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