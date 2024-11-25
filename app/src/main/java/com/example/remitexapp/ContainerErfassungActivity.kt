package com.example.remitexapp

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
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

    // Initialisierung der UI-Elemente und Variablen
    private lateinit var containernummerInput: TextView
    private lateinit var barcodeView: BarcodeView
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private val photos = mutableListOf<Bitmap>()
    private lateinit var fotoCounter: TextView
    private lateinit var currentPhotoPath: String

    // Berechtigungs-Launcher für mehrere Berechtigungen
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

        // Initialisierung des Datenbank-Helpers
        val dbHelper = DatabaseHelper(this)

        // Berechtigungen überprüfen und anfordern
        checkAndRequestPermissions()

        // UI-Elemente initialisieren
        containernummerInput = findViewById(R.id.editTextContainernummer)
        fotoCounter = findViewById(R.id.fotoCounter)
        val fahrernummer = intent.getStringExtra("fahrernummer")
        val fuellmengeInput = findViewById<TextView>(R.id.editTextFuellmenge)
        val erfassenButton = findViewById<Button>(R.id.buttonErfassen)
        val abmeldenButton = findViewById<Button>(R.id.buttonAbmelden)
        val scanBarcodeButton = findViewById<Button>(R.id.scanBarcodeButton)
        val lightButton = findViewById<Button>(R.id.lightButton)
        val buttonFotoMachen = findViewById<Button>(R.id.buttonFotoMachen)
        barcodeView = findViewById(R.id.barcode_view)
        var isFlashOn = false

        // Taschenlampenfunktion ein- und ausschalten
        lightButton.setOnClickListener {
            isFlashOn = !isFlashOn
            barcodeView.setTorch(isFlashOn)
        }

        // Barcode-Scanner umschalten
        scanBarcodeButton.setOnClickListener {
            toggleBarcodeScanner()
        }

        // Listener für die Eingabetasten setzen
        setupButtonListeners()

        // Registrierung des ActivityResultLaunchers für das Aufnehmen von Fotos
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handlePictureResult(result.resultCode)
        }

        // Klick-Listener für den "Foto machen"-Button
        buttonFotoMachen.setOnClickListener {
            dispatchTakePictureIntent()
        }

        // Klick-Listener für den "Erfassen"-Button
        erfassenButton.setOnClickListener {
            handleErfassenButtonClick(dbHelper, fahrernummer, fuellmengeInput)
        }

        // Klick-Listener für den "Abmelden"-Button
        abmeldenButton.setOnClickListener {
            startActivity(Intent(this, FahrernummerEingabeActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume() // Barcode-Scanner fortsetzen
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause() // Barcode-Scanner pausieren
    }

    // Überprüfung und Anforderung der Berechtigungen
    private fun checkAndRequestPermissions() {
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

        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissionsLauncher.launch(permissions)
        }
    }

    // Temporäre Fotodatei erstellen
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    // Uri für die Datei erhalten
    private fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(this, "com.example.remitexapp.fileprovider", file)
    }

    // Nachricht in der Toolbar anzeigen
    private fun showMessageInToolbar(message: String, onMessageHidden: (() -> Unit)? = null) {
        val toolbarMessage: TextView = findViewById(R.id.toolbar_message)
        toolbarMessage.text = message
        toolbarMessage.visibility = View.VISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            toolbarMessage.visibility = View.GONE
            onMessageHidden?.invoke()
        }, 3000)
    }

    // Ton abspielen, wenn Barcode erfolgreich gescannt wurde
    private fun playBeep() {
        try {
            val notification = MediaPlayer.create(this, Settings.System.DEFAULT_NOTIFICATION_URI)
            notification?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Intent zum Aufnehmen eines Fotos starten
    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureIntent.resolveActivity(packageManager)?.also {
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                null
            }
            photoFile?.also {
                val photoURI: Uri = getUriForFile(it)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureLauncher.launch(takePictureIntent)
            }
        }
    }

    // Behandlung des Ergebnisses der Fotoaufnahme
    private fun handlePictureResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
            bitmap?.let {
                AlertDialog.Builder(this)
                    .setTitle("Foto Bestätigung")
                    .setMessage("Ist das aufgenommene Foto in Ordnung?")
                    .setPositiveButton("Ja") { _, _ ->
                        photos.add(it)
                        fotoCounter.text = photos.size.toString()
                        fotoCounter.visibility = View.VISIBLE
                    }
                    .setNegativeButton("Nein") { _, _ ->
                        File(currentPhotoPath).delete()
                    }
                    .show()
            }
        }
    }

    // Barcode-Scanner umschalten
    private fun toggleBarcodeScanner() {
        if (barcodeView.visibility == View.VISIBLE) {
            barcodeView.visibility = View.GONE
            findViewById<Button>(R.id.lightButton).visibility = View.GONE
        } else {
            barcodeView.visibility = View.VISIBLE
            findViewById<Button>(R.id.lightButton).visibility = View.VISIBLE

            barcodeView.decodeSingle(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    barcodeView.visibility = View.GONE
                    findViewById<Button>(R.id.lightButton).visibility = View.GONE
                    val scannedValue = result.text
                    if (scannedValue.matches(Regex("\\d{5}"))) {
                        containernummerInput.text = scannedValue
                        playBeep()
                        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                            .hideSoftInputFromWindow(containernummerInput.windowToken, 0)
                    } else {
                        showMessageInToolbar("ACHTUNG: Bitte gültige Containernummer scannen.")
                    }
                }

                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
            })
        }
    }

    // Setup der Button-Listener für die Eingabetasten
    private fun setupButtonListeners() {
        val buttonIds = listOf(
            R.id.button0, R.id.button15, R.id.button25, R.id.button35,
            R.id.button50, R.id.button65, R.id.button75, R.id.button90,
            R.id.button100, R.id.button110
        )

        val buttonClickListener = View.OnClickListener { view ->
            val button = view as Button
            findViewById<TextView>(R.id.editTextFuellmenge).text = button.text
        }

        buttonIds.forEach { id ->
            findViewById<Button>(id).setOnClickListener(buttonClickListener)
        }
    }

    // Behandlung des Klicks auf den "Erfassen"-Button
    private fun handleErfassenButtonClick(dbHelper: DatabaseHelper, fahrernummer: String?, fuellmengeInput: TextView) {
        if (containernummerInput.text.isNullOrEmpty() || fuellmengeInput.text.isNullOrEmpty()) {
            showMessageInToolbar("Beide Felder müssen ausgefüllt sein!")
            return
        }

        val fuellmenge = fuellmengeInput.text.toString().toIntOrNull() ?: 0
        val containernummer = containernummerInput.text.toString().toIntOrNull() ?: 0
        val currentDateTime = LocalDateTime.now()
        val currentDate = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val currentTime = currentDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        val photoUris = saveAndCompressPhotos(containernummer)

        if (dbHelper.isContainerScannedToday(containernummer.toString(), currentDate)) {
            showUpdateDialog(dbHelper, fahrernummer, containernummer, fuellmenge, currentDate, currentTime, photoUris)
        } else {
            val newRowId = dbHelper.insertContainerRecord(
                fahrernummer.toString(),
                containernummer.toString(),
                fuellmenge,
                currentDate,
                currentTime,
                photoUris
            )
            if (newRowId != -1L) {
                showMessageInToolbar("Erfassung lokal erfolgreich!")
            } else {
                showMessageInToolbar("Fehler beim Erfassen in lokaler SQLite-Datenbank!")
            }

            clearFields()
        }
    }

    // Fotos speichern und komprimieren
    private fun saveAndCompressPhotos(containernummer: Int): MutableList<String> {
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

                    if (byteArray.size / 1024 < 300) {
                        fileOutputStream.write(byteArray)
                        fileOutputStream.close()
                        break
                    } else {
                        quality -= 10
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    fileOutputStream?.close()
                }
            } while (quality > 10)

            photoUris.add(file.absolutePath)
        }

        photos.clear()
        fotoCounter.visibility = View.GONE
        return photoUris
    }

    // Dialog zum Aktualisieren der Datensätze anzeigen
    private fun showUpdateDialog(
        dbHelper: DatabaseHelper, fahrernummer: String?, containernummer: Int, fuellmenge: Int,
        currentDate: String, currentTime: String, photoUris: MutableList<String>
    ) {
        AlertDialog.Builder(this)
            .setTitle("Hinweis")
            .setMessage("Der Container wurde heute bereits erfasst. Möchten Sie den Barcode erneut erfassen?")
            .setPositiveButton("Ja") { _, _ ->
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

        clearFields()
    }

    // Felder leeren
    private fun clearFields() {
        containernummerInput.text = ""
        findViewById<TextView>(R.id.editTextFuellmenge).text = ""
        // Entfernen: containernummerInput.requestFocus()
    }
}