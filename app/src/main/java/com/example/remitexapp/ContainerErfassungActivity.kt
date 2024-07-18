package com.example.remitexapp

// import android.content.Context
// import com.google.firebase.database.FirebaseDatabase
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class ContainerErfassungActivity : AppCompatActivity() {
    private lateinit var containernummerInput: EditText
    private lateinit var barcodeView: BarcodeView
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private val photos = mutableListOf<Bitmap>()
    private lateinit var fotoCounter: TextView
    private lateinit var currentPhotoPath: String
    private val requestcamerapermission = 200 // Request Code für Kamera Berechtigung

    // Hinzufügen des requestPermissionsLauncher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            val isGranted = it.value
            if (!isGranted) {
                showMessageInToolbar("Berechtigungen sind erforderlich, um fortzufahren.")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_containererfassung)

        // Initialisiere dbHelper
        val dbHelper = DatabaseHelper(this)

        // Berechtigungen anfordern
        checkAndRequestPermissions()

        // Überprüfen Sie die erforderlichen Berechtigungen und fordern Sie sie an, wenn sie noch nicht erteilt wurden
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissionsLauncher.launch(permissions)
        }

        containernummerInput = findViewById(R.id.editTextContainernummer)
        fotoCounter = findViewById(R.id.fotoCounter)
        val fahrernummer = intent.getStringExtra("fahrernummer")
        val fuellmengeInput = findViewById<EditText>(R.id.editTextFuellmenge)
        val erfassenButton = findViewById<Button>(R.id.buttonErfassen)
        val abmeldenButton = findViewById<Button>(R.id.buttonAbmelden)
        val scanBarcodeButton = findViewById<Button>(R.id.scanBarcodeButton)
        val lightButton = findViewById<Button>(R.id.lightButton)
        val button0 = findViewById<Button>(R.id.button0)
        val button15 = findViewById<Button>(R.id.button15)
        val button25 = findViewById<Button>(R.id.button25)
        val button35 = findViewById<Button>(R.id.button35)
        val button50 = findViewById<Button>(R.id.button50)
        val button65 = findViewById<Button>(R.id.button65)
        val button75 = findViewById<Button>(R.id.button75)
        val button90 = findViewById<Button>(R.id.button90)
        val button100 = findViewById<Button>(R.id.button100)
        val button110 = findViewById<Button>(R.id.button110)
        val buttonFotoMachen = findViewById<Button>(R.id.buttonFotoMachen)
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
                        val scannedValue = result.text
                        if (scannedValue.matches(Regex("\\d{5}"))) {
                            containernummerInput.setText(scannedValue)
                            // Taschenlampe ausschalten, falls eingeschaltet
                            if (isFlashOn) {
                                barcodeView.setTorch(false)
                                isFlashOn = false
                            }
                            // Abspielen des Bestätigungstons
                            playBeep()
                            // Setzen Sie den Fokus auf fuellmengeInput und verhindern Sie die automatische Öffnung der Tastatur
                            fuellmengeInput.requestFocus()
                            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(fuellmengeInput.windowToken, 0)
                        } else {
                            showMessageInToolbar("ACHTUNG: Bitte gültige Containernummer scannen oder manuell eintippen.")
                        }
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
        button15.setOnClickListener(buttonClickListener)
        button25.setOnClickListener(buttonClickListener)
        button35.setOnClickListener(buttonClickListener)
        button50.setOnClickListener(buttonClickListener)
        button65.setOnClickListener(buttonClickListener)
        button75.setOnClickListener(buttonClickListener)
        button90.setOnClickListener(buttonClickListener)
        button100.setOnClickListener(buttonClickListener)
        button110.setOnClickListener(buttonClickListener)

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
                bitmap?.let {
                    // Temporär das Bild speichern, um es zu bestätigen
                    AlertDialog.Builder(this)
                        .setTitle("Foto Bestätigung")
                        .setMessage("Ist das aufgenommene Foto in Ordnung?")
                        .setPositiveButton("Ja") { _, _ ->
                            photos.add(it)
                            fotoCounter.text = photos.size.toString()
                            fotoCounter.visibility = View.VISIBLE
                        }
                        .setNegativeButton("Nein") { _, _ ->
                            // Foto wird verworfen
                            File(currentPhotoPath).delete()
                        }
                        .show()
                }
            }
        }

        buttonFotoMachen.setOnClickListener {
            dispatchTakePictureIntent()
        }

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

            // Fotos speichern und komprimieren
            val photoUris = mutableListOf<String>()
            val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ContainerFotos")

            if (!directory.exists()) {
                directory.mkdirs()
            }

            photos.forEachIndexed { index, bitmap ->
                val filename = if (index == 0) {
                    "${containernummer}.jpg"
                } else {
                    "${containernummer}_$index.jpg"
                }

                val file = File(directory, filename)
                var quality = 90
                var fileOutputStream: FileOutputStream? = null

                do {
                    try {
                        fileOutputStream = FileOutputStream(file)
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
                        val byteArray = byteArrayOutputStream.toByteArray()

                        // Check if the size is less than 600 KB
                        if (byteArray.size / 1024 < 600) {
                            fileOutputStream.write(byteArray)
                            fileOutputStream.close()
                            break
                        } else {
                            // Reduce the quality and try again
                            quality -= 10
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        fileOutputStream?.close()
                    }
                } while (quality > 10)  // Stop if quality drops below a certain threshold

                photoUris.add(file.absolutePath)
            }

            // Leere die Liste der Fotos nach dem Speichern
            photos.clear()
            fotoCounter.visibility = View.GONE

            // Überprüfen Sie, ob der Container bereits für den heutigen Tag erfasst wurde
            if (dbHelper.isContainerScannedToday(containernummer.toString(), currentDate)) {
                AlertDialog.Builder(this)
                    .setTitle("Hinweis")
                    .setMessage("Der Container wurde heute bereits erfasst. Möchten Sie den Barcode erneut erfassen?")
                    .setPositiveButton("Ja") { _, _ ->
                        // Hier führen Sie den Code zum Überschreiben des alten Datensatzes aus
                        if (fahrernummer != null) {
                            val rowsUpdated = dbHelper.updateContainerRecord(
                                fahrernummer,
                                containernummer.toString(),
                                fuellmenge,
                                currentDate,
                                currentTime,
                                photoUris
                            )
                            if (rowsUpdated > 0) {
                                showMessageInToolbar("Container erfolgreich aktualisiert")
                            } else {
                                showMessageInToolbar("Aktualisierung des Datensatzes fehlgeschlagen")
                            }
                        }
                    }
                    .setNegativeButton("Nein", null)
                    .show()

                // Felder leeren
                containernummerInput.setText("")
                fuellmengeInput.setText("")

                // Fokus zurück zum ersten Eingabefeld
                containernummerInput.requestFocus()

            } else {
                // Fahren Sie fort, um die Daten in die Datenbank einzufügen, wenn der Container heute nicht erfasst wurde
                val newRowId = dbHelper.insertContainerRecord(
                    fahrernummer.toString(),
                    containernummer.toString(),
                    fuellmenge,
                    currentDate,
                    currentTime,
                    photoUris
                )
                /*
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
                */

                // Überprüfen, ob das Einfügen erfolgreich war
                if (newRowId != -1L) {
                    showMessageInToolbar("Erfassung lokal erfolgreich!")
                    /*
                    {
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
                    */

                } else {
                    showMessageInToolbar("Fehler beim Erfassen in lokaler SQLite-Datenbank!")
                    /*
                    {
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
                    */
                }

                // Felder leeren
                containernummerInput.setText("")
                fuellmengeInput.setText("")

                // Fokus zurück zum ersten Eingabefeld
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
    // Überprüfen Sie die erforderlichen Berechtigungen und fordern Sie sie an
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
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
    }
    //Temporäre Fotodatei erstellen
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }
    private fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(this, "com.example.remitexapp.fileprovider", file)
    }

    // Meldung am oberen Bildschirm anzeigen
    private fun showMessageInToolbar(message: String, onMessageHidden: (() -> Unit)? = null) {
        val toolbarMessage: TextView = findViewById(R.id.toolbar_message)
        toolbarMessage.text = message
        toolbarMessage.visibility = View.VISIBLE

        // Meldung nach einigen Sekunden wieder ausblenden
        Handler(Looper.getMainLooper()).postDelayed({
            toolbarMessage.visibility = View.GONE
            onMessageHidden?.invoke()
        }, 3000)
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
    // Containerfoto aufnehmen
    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Stellen Sie sicher, dass es eine Aktivität gibt, die das Intent handhaben kann
        takePictureIntent.resolveActivity(packageManager)?.also {
            // Erstellen Sie die Datei, in der das Foto gespeichert werden soll
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                // Fehlerbehandlung, wenn die Datei nicht erstellt werden kann
                null
            }
            // Fahren Sie nur fort, wenn die Datei erfolgreich erstellt wurde
            photoFile?.also {
                val photoURI: Uri = getUriForFile(it)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureLauncher.launch(takePictureIntent)
            }
        }
    }
}