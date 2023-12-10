package com.example.remitexapp

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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.media.MediaPlayer
import android.provider.Settings

class ContainerErfassungActivity : AppCompatActivity() {
    private lateinit var containernummerInput: EditText
    private lateinit var barcodeView: BarcodeView
    private val requestcamerapermission = 200 // Request Code für Kamera Berechtigung

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
                requestcamerapermission
            )
        }
        containernummerInput = findViewById(R.id.editTextContainernummer)
        val fahrernummer = intent.getStringExtra("fahrernummer")
        val fuellmengeInput = findViewById<EditText>(R.id.editTextFuellmenge)
        val erfassenButton = findViewById<Button>(R.id.buttonErfassen)
        val abmeldenButton = findViewById<Button>(R.id.buttonAbmelden)
        val scanBarcodeButton = findViewById<Button>(R.id.scanBarcodeButton)
        val lightButton = findViewById<Button>(R.id.lightButton)
        val button0 = findViewById<Button>(R.id.button0)
        val button10 = findViewById<Button>(R.id.button10)
        val button20 = findViewById<Button>(R.id.button20)
        val button30 = findViewById<Button>(R.id.button30)
        val button40 = findViewById<Button>(R.id.button40)
        val button50 = findViewById<Button>(R.id.button50)
        val button60 = findViewById<Button>(R.id.button60)
        val button70 = findViewById<Button>(R.id.button70)
        val button80 = findViewById<Button>(R.id.button80)
        val button90 = findViewById<Button>(R.id.button90)
        val button100 = findViewById<Button>(R.id.button100)
        val button120 = findViewById<Button>(R.id.button120)
        barcodeView = findViewById(R.id.barcode_view)
        var isFlashOn = false

        lightButton.setOnClickListener {
            isFlashOn = !isFlashOn
            barcodeView.setTorch(isFlashOn)
        }

        scanBarcodeButton.setOnClickListener {
            // Überprüfen Sie, ob die BarcodeView sichtbar ist
            if (barcodeView.visibility == View.VISIBLE) {
                // Wenn die BarcodeView sichtbar ist, setzen Sie sie und alle zugehörigen Elemente auf unsichtbar
                barcodeView.visibility = View.GONE
                lightButton.visibility = View.GONE
                if (isFlashOn) {
                    isFlashOn = false
                    barcodeView.setTorch(false)
                }
            } else {
                // Wenn die BarcodeView nicht sichtbar ist, machen Sie sie und alle zugehörigen Elemente sichtbar
                barcodeView.visibility = View.VISIBLE
                lightButton.visibility = View.VISIBLE

                barcodeView.decodeSingle(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult) {
                        barcodeView.visibility = View.GONE
                        lightButton.visibility = View.GONE
                        containernummerInput.setText(result.text)

                        // Taschenlampe ausschalten, falls eingeschaltet
                        if (isFlashOn) {
                            barcodeView.setTorch(false)
                            isFlashOn = false
                        }
                        // Abspielen des Bestätigungstons
                        playBeep()
                        // Setzen Sie den Fokus auf fuellmengeInput und verhindern Sie die automatische Öffnung der Tastatur
                        fuellmengeInput.requestFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(fuellmengeInput.windowToken, 0)
                    }

                    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
                })
            }
        }


        val buttonClickListener = View.OnClickListener { view ->
            val button = view as Button
            fuellmengeInput.setText(button.text)
        }

        button0.setOnClickListener(buttonClickListener)
        button10.setOnClickListener(buttonClickListener)
        button20.setOnClickListener(buttonClickListener)
        button30.setOnClickListener(buttonClickListener)
        button40.setOnClickListener(buttonClickListener)
        button50.setOnClickListener(buttonClickListener)
        button60.setOnClickListener(buttonClickListener)
        button70.setOnClickListener(buttonClickListener)
        button80.setOnClickListener(buttonClickListener)
        button90.setOnClickListener(buttonClickListener)
        button100.setOnClickListener(buttonClickListener)
        button120.setOnClickListener(buttonClickListener)

        erfassenButton.setOnClickListener {

            // Überprüfen, ob beide Felder ausgefüllt sind
            if (containernummerInput.text.isNullOrEmpty() || fuellmengeInput.text.isNullOrEmpty()) {
                // Wenn nicht, zeigen Sie eine Nachricht an und kehren Sie zurück
                showMessageInToolbar("Beide Felder müssen ausgefüllt sein!")
                return@setOnClickListener
            }

            val fuellmenge = fuellmengeInput.text.toString().toIntOrNull() ?: 0
            val containernummer = containernummerInput.text.toString().toIntOrNull() ?: 0
            val currentDateTime = LocalDateTime.now()
            val currentDate = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val currentTime = currentDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

            // Erstellen einer Instanz von com.example.remitexapp.DatabaseHelper
            val dbHelper = DatabaseHelper(this)

            // Überprüfen Sie, ob der Container bereits für den heutigen Tag erfasst wurde
            if (dbHelper.isContainerScannedToday(containernummer.toString(), currentDate)) {
                AlertDialog.Builder(this)
                    .setTitle("Hinweis")
                    .setMessage("Der Container wurde heute bereits erfasst. Möchten Sie den Barcode erneut erfassen?")
                    .setPositiveButton("Ja") { _, _ ->
                        // Hier führen Sie den Code zum Überschreiben des alten Datensatzes aus
                        if (fahrernummer != null) {
                            dbHelper.updateContainerRecord(fahrernummer, containernummer.toString(), fuellmenge, currentDate, currentTime)
                            val rowsUpdated = dbHelper.updateContainerRecord(fahrernummer, containernummer.toString(), fuellmenge, currentDate, currentTime)
                            if (rowsUpdated > 0) {
                                showMessageInToolbar("Container erfolgreich aktualisiert")
                            } else {
                                showMessageInToolbar("Aktualisierung des Datensatzes fehlgeschlagen")
                            }
                        }
                    }
                    .setNegativeButton("Nein", null)
                    .show()

                //Felder leeren
                containernummerInput.setText("")
                fuellmengeInput.setText("")

                //Fokus zurück zum ersten Eingabefeld
                containernummerInput.requestFocus()

            } else {
                // Fahren Sie fort, um die Daten in die Datenbank einzufügen, wenn der Container heute nicht erfasst wurde

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
            requestcamerapermission -> {
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
    // Ton abspielen, wenn Barcode erfolgreich gescanned wurde
    private fun playBeep() {
        try {
            val notification = MediaPlayer.create(this, Settings.System.DEFAULT_NOTIFICATION_URI)
            notification?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            // Optional: Behandlung von Ausnahmen, z.B. Anzeigen einer Fehlermeldung
        }
    }
}
