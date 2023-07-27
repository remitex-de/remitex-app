package com.example.remitexapp

import DatabaseHelper
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ContainerErfassungActivity : AppCompatActivity() {
    private lateinit var containernummerInput: EditText
    private lateinit var barcodeView: BarcodeView
    private val REQUEST_CAMERA_PERMISSION = 200 // Request Code für Kamera Berechtigung

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_containererfassung)

        // Überprüfen Sie die Kameraberechtigung und fordern Sie sie an, wenn sie noch nicht erteilt wurde
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
        containernummerInput = findViewById(R.id.editTextContainernummer)
        val fahrernummer = intent.getStringExtra("fahrernummer")
        val fuellmengeInput = findViewById<EditText>(R.id.editTextFuellmenge)
        val erfassenButton = findViewById<Button>(R.id.buttonErfassen)
        val abmeldenButton = findViewById<Button>(R.id.buttonAbmelden)
        val scanBarcodeButton = findViewById<Button>(R.id.scanBarcodeButton)
        val cancelScanButton = findViewById<Button>(R.id.cancelScanButton)
        val lightButton = findViewById<Button>(R.id.lightButton)
        val button0 = findViewById<Button>(R.id.button0)
        val button25 = findViewById<Button>(R.id.button25)
        val button50 = findViewById<Button>(R.id.button50)
        val button75 = findViewById<Button>(R.id.button75)
        val button100 = findViewById<Button>(R.id.button100)
        val button120 = findViewById<Button>(R.id.button120)
        barcodeView = findViewById(R.id.barcode_view)
        var isFlashOn = false

        lightButton.setOnClickListener {
            isFlashOn = !isFlashOn
            barcodeView.setTorch(isFlashOn)
        }

        cancelScanButton.setOnClickListener {
            barcodeView.visibility = View.GONE
            cancelScanButton.visibility = View.GONE
            lightButton.visibility = View.GONE
            if (isFlashOn) {
                isFlashOn = false
                barcodeView.setTorch(false)
            }
        }

        scanBarcodeButton.setOnClickListener {
            barcodeView.visibility = View.VISIBLE
            cancelScanButton.visibility = View.VISIBLE
            lightButton.visibility = View.VISIBLE

            barcodeView.decodeSingle(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    barcodeView.visibility = View.GONE
                    cancelScanButton.visibility = View.GONE
                    lightButton.visibility = View.GONE
                    containernummerInput.setText(result.text)

                    // Setzen Sie den Fokus auf fuellmengeInput und verhindern Sie die automatische Öffnung der Tastatur
                    fuellmengeInput.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(fuellmengeInput.windowToken, 0)
                }

                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
            })
        }

        val buttonClickListener = View.OnClickListener { view ->
            val button = view as Button
            fuellmengeInput.setText(button.text)
        }

        button0.setOnClickListener(buttonClickListener)
        button25.setOnClickListener(buttonClickListener)
        button50.setOnClickListener(buttonClickListener)
        button75.setOnClickListener(buttonClickListener)
        button100.setOnClickListener(buttonClickListener)
        button120.setOnClickListener(buttonClickListener)

        erfassenButton.setOnClickListener {
            val fuellmenge = fuellmengeInput.text.toString().toIntOrNull() ?: 0
            val containernummer = containernummerInput.text.toString().toIntOrNull() ?: 0
            val currentDateTime = LocalDateTime.now()
            val currentDate = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val currentTime = currentDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

            // Erstellen einer Instanz von DatabaseHelper
            val dbHelper = DatabaseHelper(this)

            // Öffnen der Datenbank zum Schreiben
            val db = dbHelper.writableDatabase

            // Erstellen von ContentValues, um die Daten einzufügen
            val values = ContentValues().apply {
                put(DatabaseHelper.COLUMN_FAHRERNUMMER, fahrernummer)
                put(DatabaseHelper.COLUMN_CONTAINERNUMMER, containernummer)
                put(DatabaseHelper.COLUMN_FUELLMENGE, fuellmenge)
                put(DatabaseHelper.COLUMN_TAG, currentDate)
                put(DatabaseHelper.COLUMN_UHRZEIT, currentTime)
            }

            // Einfügen der Daten in die Datenbank
            val newRowId = db?.insert(DatabaseHelper.TABLE_NAME, null, values)

            // Erstellung einer Firebase-Instanz
            val database =
                FirebaseDatabase.getInstance("https://remitexapp-default-rtdb.europe-west1.firebasedatabase.app")
            val myRef = database.getReference("ContainerFuellmengen")

            // Erstellung einer Map für die Daten, die zur Firebase-Datenbank hinzugefügt werden
            val data = mapOf(
                "fahrernummer" to fahrernummer,
                "containernummer" to containernummer,
                "fuellmenge" to fuellmenge,
                "tag" to currentDate,
                "uhrzeit" to currentTime
            )

            // Überprüfen, ob das Einfügen erfolgreich war
            if (newRowId != -1L) {
                showMessageInToolbar("Erfassung lokal erfolgreich!") {
                    // Hinzufügen der Daten zur Firebase-Datenbank
                    myRef.push().setValue(data) { error, _ ->
                        if (error != null) {
                            // Ein Fehler ist aufgetreten
                            showMessageInToolbar("Fehler beim Schreiben in Firebase Datenbank: ${error.message}")
                        } else {
                            // Daten erfolgreich geschrieben
                            showMessageInToolbar("Erfassung online erfolgreich!")
                        }
                    }
                }
            } else {
                showMessageInToolbar("Fehler beim Erfassen in lokaler SQLite-Datenbank!") {
                    // Hinzufügen der Daten zur Firebase-Datenbank
                    myRef.push().setValue(data) { error, _ ->
                        if (error != null) {
                            // Ein Fehler ist aufgetreten
                            showMessageInToolbar("Fehler beim Schreiben in Firebase Datenbank: ${error.message}")
                        } else {
                            // Daten erfolgreich geschrieben
                            showMessageInToolbar("Erfassung online erfolgreich!")
                        }
                    }
                }
            }

            //Felder leeren
            containernummerInput.setText("")
            fuellmengeInput.setText("")

            //Fokus zurück zum ersten Eingabefeld
            containernummerInput.requestFocus()
        }


        abmeldenButton.setOnClickListener {
            startActivity(Intent(this, FahrernummerEingabeActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Die Kamera-Berechtigung wurde gewährt. Sie können hier Ihren Barcode-Scanning-Code ausführen.
                } else {
                    // Die Kamera-Berechtigung wurde nicht gewährt. Sie können hier einen Hinweis für den Benutzer anzeigen, dass die Berechtigung erforderlich ist.
                    showMessageInToolbar("Kamera-Berechtigung ist erforderlich, um Barcode zu scannen")
                }
            }
        }
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
