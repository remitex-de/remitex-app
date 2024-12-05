package com.example.remitexapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
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
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ContainerErfassungActivity : AppCompatActivity() {

    // Initialisierung der UI-Elemente und Variablen
    private lateinit var containernummerInput: TextView
    private lateinit var barcodeView: BarcodeView
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private val photos = mutableListOf<Uri>()
    private lateinit var fotoCounter: TextView
    private var currentPhotoUri: Uri? = null

    // Berechtigungs-Launcher für mehrere Berechtigungen
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Berechtigungen prüfen und auf fehlende Berechtigungen reagieren
        permissions.forEach { (permission, isGranted) ->
            when (permission) {
                Manifest.permission.READ_MEDIA_IMAGES -> {
                    if (!isGranted) {
                        showMessageInToolbar("Zugriff auf Fotos wurde eingeschränkt. Einige Funktionen könnten nicht verfügbar sein.")
                    }
                }
                Manifest.permission.CAMERA -> {
                    if (!isGranted) {
                        showMessageInToolbar("Kameraberechtigung erforderlich, um Fotos aufzunehmen.")
                    }
                }
            }
        }

        // Fehlende Berechtigungen melden
        //val missingPermissions = permissions.filter { !it.value }.keys
        //if (missingPermissions.isNotEmpty()) {
        //    showMessageInToolbar("Nicht alle Berechtigungen wurden erteilt. Einschränkungen möglich.")
        //}
    }

    // Methode zur Berechtigungsprüfung und -anfrage
    private fun checkAndRequestPermissions() {
        val essentialPermissions = mutableListOf<String>()

        // Kamera-Berechtigung für alle Versionen
        essentialPermissions.add(Manifest.permission.CAMERA)

        // WRITE_EXTERNAL_STORAGE für API <= 29
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            essentialPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // READ_MEDIA_IMAGES für API >= 33 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            essentialPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        }

        // READ_MEDIA_VISUAL_USER_SELECTED für API >= 34 (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            essentialPermissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }

        // Fehlende Berechtigungen filtern
        val missingPermissions = essentialPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        // Berechtigungen anfragen, falls erforderlich
        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
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
        val fuellmengeInput = findViewById<EditText>(R.id.editTextFuellmenge)
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
            handlePictureResult(result.resultCode) // Pass result.data to handlePictureResult
        }

        // Klick-Listener für den "Foto machen"-Button
        buttonFotoMachen.setOnClickListener {
            Log.d("PermissionsCheck", "CAMERA granted: ${checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED}")
            Log.d("PermissionsCheck", "READ_MEDIA_IMAGES granted: ${
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                } else {
                    "N/A (Not required)"
                }
            }")
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

    private fun hasRequiredPermissions(): Boolean {
        val cameraPermissionGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val readMediaImagesPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return cameraPermissionGranted && readMediaImagesPermissionGranted
    }

    // Intent zum Aufnehmen eines Fotos starten
    private fun dispatchTakePictureIntent() {
        if (!hasRequiredPermissions()) {
            showMessageInToolbar("Fotoaufnahme nicht möglich: Fehlende Berechtigungen.")
            checkAndRequestPermissions()
            return
        }

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        createImageUri()?.let { uri ->
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            // Store the URI for later use in handlePictureResult
            currentPhotoUri = uri
            takePictureLauncher.launch(takePictureIntent)
        } ?: run {
            Log.e("CameraIntent", "Fehler beim Erstellen der Bild-URI.")
            showMessageInToolbar("Fehler beim Erstellen der Bilddatei.")
        }
    }

    private fun createImageUri(): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "image_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    // Behandlung des Ergebnisses der Fotoaufnahme
    private fun handlePictureResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val imageUri = currentPhotoUri // Get the image URI from the result data

            imageUri?.let { uri ->
                AlertDialog.Builder(this)
                    .setTitle("Foto Bestätigung")
                    .setMessage("Ist das aufgenommene Foto in Ordnung?")
                    .setPositiveButton("Ja") { _, _ ->
                        photos.add(uri) // Add the URI to the list
                        fotoCounter.text = photos.size.toString()
                        fotoCounter.visibility = View.VISIBLE
                    }
                    .setNegativeButton("Nein") { _, _ ->
                        // Delete the image from MediaStore
                        contentResolver.delete(uri, null, null)
                    }
                    .show()
            }
            currentPhotoUri = null
        }
    }

    // Fotos speichern und komprimieren
    private fun saveAndCompressPhotos(containernummer: Int): MutableList<String> {
        val photoUris = mutableListOf<String>()
        val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ContainerFotos")

        if (!directory.exists() && !directory.mkdirs()) {
            Log.e("PhotoHandler", "Fehler beim Erstellen des Verzeichnisses: $directory")
            return photoUris
        }

        photos.forEachIndexed { index, uri -> // uri is already of type Uri
            try {
                val inputStream = contentResolver.openInputStream(uri) // No need to cast
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                bitmap?.let {
                    val filename = if (index == 0) {
                        "${containernummer}.jpg"
                    } else {
                        "${containernummer}_$index.jpg"
                    }

                    val file = File(directory, filename)
                    var quality = 90

                    try {
                        file.outputStream().use { outputStream ->
                            var compressed = false
                            do {
                                val byteArrayOutputStream = ByteArrayOutputStream()
                                bitmap.compress(
                                    Bitmap.CompressFormat.JPEG,
                                    quality,
                                    byteArrayOutputStream
                                )
                                val byteArray = byteArrayOutputStream.toByteArray()

                                if (byteArray.size / 1024 < 300) {
                                    outputStream.write(byteArray)
                                    compressed = true
                                } else {
                                    quality -= 10
                                }
                            } while (!compressed && quality > 10)
                        }

                        photoUris.add(file.absolutePath)
                    } catch (e: Exception) {
                        Log.e("PhotoHandler", "Fehler beim Speichern des Fotos: ${e.message}")
                        // Consider showing an error message to the user here
                    }
                }
            } catch (e: Exception) {
                Log.e("SaveAndCompressPhotos", "Fehler beim Laden des Bildes: ${e.message}")
                showMessageInToolbar("Fehler beim Laden des Bildes.")
            }
        }

        photos.clear()
        fotoCounter.visibility = View.GONE
        return photoUris // Moved outside the loop
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
            R.id.button0, R.id.button5, R.id.button10, R.id.button15,
            R.id.button25, R.id.button35, R.id.button50, R.id.button65,
            R.id.button75, R.id.button90, R.id.button100, R.id.button110
        )

        val buttonClickListener = View.OnClickListener { view ->
            val button = view as Button
            findViewById<TextView>(R.id.editTextFuellmenge).text = button.text
        }

        buttonIds.forEach { id ->
            findViewById<Button>(id).setOnClickListener(buttonClickListener)
        }
    }

    // Behandlung des Klicks auf den "Speichern"-Button
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