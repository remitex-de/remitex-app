package com.example.remitexapp

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 4
        private const val DATABASE_NAME = "ContainerDatenbank"
        const val TABLE_NAME = "ContainerFuellmengen"
        const val COLUMN_FAHRERNUMMER = "Fahrernummer"
        const val COLUMN_CONTAINERNUMMER = "Containernummer"
        const val COLUMN_FUELLMENGE = "Fuellmenge"
        const val COLUMN_TAG = "Tag"
        const val COLUMN_UHRZEIT = "Uhrzeit"
        const val COLUMN_EXPORTDATUM = "Exportdatum"
        const val COLUMN_PHOTO_URIS = "photo_uris"
    }

    // Tabelle erstellen
    override fun onCreate(db: SQLiteDatabase) {
        val createtable = ("CREATE TABLE " + TABLE_NAME + "("
                + COLUMN_FAHRERNUMMER + " TEXT,"
                + COLUMN_CONTAINERNUMMER + " INTEGER,"
                + COLUMN_FUELLMENGE + " INTEGER,"
                + COLUMN_TAG + " TEXT,"
                + COLUMN_UHRZEIT + " TEXT,"
                + COLUMN_EXPORTDATUM + " TEXT,"
                + COLUMN_PHOTO_URIS + " TEXT)")
        db.execSQL(createtable)
    }

    // Tabelle aktualisieren (bei Versionswechsel)
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    // Auswahlliste der ReExport Activity mit Daten aus der Datenbank füllen
    fun getAllData(): List<Array<String>> {
        val data = mutableListOf<Array<String>>()
        val db = this.readableDatabase
        // Nur die gewünschten Spalten auswählen, ohne Exportdatum und photo_uris
        val cursor = db.rawQuery(
            "SELECT $COLUMN_FAHRERNUMMER, $COLUMN_CONTAINERNUMMER, $COLUMN_FUELLMENGE, $COLUMN_TAG, $COLUMN_UHRZEIT FROM $TABLE_NAME WHERE $COLUMN_EXPORTDATUM IS NOT NULL ORDER BY $COLUMN_EXPORTDATUM DESC",
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    // Extrahiere alle relevanten Spalten in der korrekten Reihenfolge
                    data.add(
                        arrayOf(
                            it.getString(it.getColumnIndexOrThrow(COLUMN_FAHRERNUMMER)),
                            it.getString(it.getColumnIndexOrThrow(COLUMN_CONTAINERNUMMER)),
                            it.getString(it.getColumnIndexOrThrow(COLUMN_FUELLMENGE)),
                            it.getString(it.getColumnIndexOrThrow(COLUMN_TAG)),
                            it.getString(it.getColumnIndexOrThrow(COLUMN_UHRZEIT))
                        )
                    )
                } while (it.moveToNext())
            }
        }
        db.close()
        return data
    }

    // Gefilterte Daten für den Export bereitstellen
    @SuppressLint("Range")
    fun getFilteredExportDataOrdered(fahrernummer: String, tag: String): List<Array<String>> {
        val db = this.readableDatabase
        val query = """
        SELECT Fahrernummer, Containernummer, Fuellmenge, 
               Tag, Uhrzeit
        FROM ContainerFuellmengen
        WHERE Fahrernummer = ? AND Tag = ?
    """
        val cursor = db.rawQuery(query, arrayOf(fahrernummer, tag))
        val data = mutableListOf<Array<String>>()

        if (cursor.moveToFirst()) {
            do {
                val record = arrayOf(
                    cursor.getString(cursor.getColumnIndexOrThrow("Fahrernummer")),
                    cursor.getString(cursor.getColumnIndexOrThrow("Containernummer")),
                    cursor.getString(cursor.getColumnIndexOrThrow("Fuellmenge")),
                    cursor.getString(cursor.getColumnIndexOrThrow("Tag")),
                    cursor.getString(cursor.getColumnIndexOrThrow("Uhrzeit"))
                )
                data.add(record)
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return data
    }


    // Spalten-Indexe für Tag, Uhrzeit, Fahrernummer, Containernummer, Fuellmenge für ReExport Activity
    fun filterAndFormatDataForListView(data: List<Array<String>>): List<String> {
        val columnIndices = listOf(0, 1, 2, 3, 4) // Passen Sie die Indizes an die Reihenfolge in der Datenbank an
        return data.map { record ->
            columnIndices.joinToString(", ") { index ->
                record[index]
            }
        }
    }

    // Fahrernummer Spinner Filter in ExportActivity mit Werten der Datenbank füllen
    @SuppressLint("Range")
    fun getAllFahrernummern(): List<String> {
        val fahrernummern = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.query(true, TABLE_NAME, arrayOf(COLUMN_FAHRERNUMMER), "$COLUMN_EXPORTDATUM IS NULL OR $COLUMN_EXPORTDATUM = ''", null, COLUMN_FAHRERNUMMER, null, null, null)

        if (cursor.moveToFirst()) {
            do {
                fahrernummern.add(cursor.getString(cursor.getColumnIndex(COLUMN_FAHRERNUMMER)))
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return fahrernummern
    }

    // Daten im Datum Spinner Filter in ExportActivity basierend auf der ausgewählten Fahrernummer filtern und in den Spinner füllen
    @SuppressLint("Range")
    fun getTagsForFahrernummer(fahrernummer: String): List<String> {
        val tags = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.query(true, TABLE_NAME, arrayOf(COLUMN_TAG), "$COLUMN_FAHRERNUMMER = ? AND ($COLUMN_EXPORTDATUM IS NULL OR $COLUMN_EXPORTDATUM = '')", arrayOf(fahrernummer), COLUMN_TAG, null, null, null)

        if (cursor.moveToFirst()) {
            do {
                tags.add(cursor.getString(cursor.getColumnIndex(COLUMN_TAG)))
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return tags
    }

    // Exportdatum in der Datenbank aktualisieren
    fun updateExportDate(fahrernummer: String, tag: String, exportdatum: String) {
        val db = this.writableDatabase
        val contentValues = ContentValues().apply {
            put(COLUMN_EXPORTDATUM, exportdatum)
        }
        db.update(TABLE_NAME, contentValues, "$COLUMN_FAHRERNUMMER = ? AND $COLUMN_TAG = ?", arrayOf(fahrernummer, tag))
        db.close()
    }

    // Überprüfen, ob in der Datenbank der zu erfassende Container am Tag bereits erfasst wurde
    fun isContainerScannedToday(containernummer: String, tag: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_NAME, null, "$COLUMN_CONTAINERNUMMER = ? AND $COLUMN_TAG = ?", arrayOf(containernummer, tag), null, null, null)
        val exists = cursor.moveToFirst()
        cursor.close()
        db.close()
        return exists
    }

    // Neuen Container-Datensatz einfügen
    fun insertContainerRecord(
        fahrernummer: String,
        containernummer: String,
        fuellmenge: Int,
        tag: String,
        uhrzeit: String,
        photoUris: List<String>?
    ): Long {
        val db = this.writableDatabase
        val contentValues = ContentValues().apply {
            put(COLUMN_FAHRERNUMMER, fahrernummer)
            put(COLUMN_CONTAINERNUMMER, containernummer)
            put(COLUMN_FUELLMENGE, fuellmenge)
            put(COLUMN_TAG, tag)
            put(COLUMN_UHRZEIT, uhrzeit)
            put(COLUMN_PHOTO_URIS, photoUris?.joinToString(","))
        }
        return db.insert(TABLE_NAME, null, contentValues)
    }

    // Container-Datensatz aktualisieren, wenn er bereits am Tag erfasst wurde
    fun updateContainerRecord(
        fahrernummer: String,
        containernummer: String,
        fuellmenge: Int,
        tag: String,
        uhrzeit: String,
        photoUris: List<String>?
    ): Int {
        val db = this.writableDatabase
        val contentValues = ContentValues().apply {
            put(COLUMN_FAHRERNUMMER, fahrernummer)
            put(COLUMN_CONTAINERNUMMER, containernummer)
            put(COLUMN_FUELLMENGE, fuellmenge)
            put(COLUMN_TAG, tag)
            put(COLUMN_UHRZEIT, uhrzeit)
            put(COLUMN_PHOTO_URIS, photoUris?.joinToString(","))
        }
        return db.update(TABLE_NAME, contentValues, "$COLUMN_CONTAINERNUMMER = ? AND $COLUMN_TAG = ?", arrayOf(containernummer, tag))
    }

    // Methode, um Containerfotos für alle Exporte zu bekommen
    fun getAllPhotoUrisForContainer(context: Context, data: List<Array<String>>): List<Uri> {
        return getPhotoUris(context, data) { containernummer -> getAllPhotosForContainer(containernummer) }
    }

    // Gemeinsame Logik zum Abrufen von Foto-URIs
    private fun getPhotoUris(context: Context, data: List<Array<String>>, photoRetriever: (String) -> List<String>): List<Uri> {
        val photoUris = mutableListOf<Uri>()
        val authority = "${context.packageName}.fileprovider"

        data.forEach { record ->
            val containernummer = record[1]
            val fotos = photoRetriever(containernummer)
            fotos.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(context, authority, file)
                    photoUris.add(uri)
                }
            }
        }
        return photoUris
    }

    // Methode zum Exportieren der Daten in eine Datei
    fun exportDataToFile(context: Context, data: List<Array<String>>): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "export_$timeStamp.txt"

        val resolver = context.contentResolver
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
        }

        return uri
    }


    // Methode, um E-Mail mit Anhängen zu senden
    fun sendEmailWithAttachments(context: Context, uri: Uri, photoUris: List<Uri>, emailAddress: String) {
        val emailIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "vnd.android.cursor.dir/email"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
            putExtra(Intent.EXTRA_SUBJECT, "Scan-Datenexport")
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>().apply {
                add(uri)
                addAll(photoUris)
            })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // URI-Berechtigungen für alle relevanten E-Mail-Client-Pakete gewähren
        val resolveInfoList = context.packageManager.queryIntentActivities(emailIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            for (photoUri in photoUris) {
                context.grantUriPermission(packageName, photoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(emailIntent, "Senden Sie E-Mail..."))
    }

    // Methode, um alle Fotos eines Containers zu bekommen, unabhängig vom Exportdatum
    private fun getAllPhotosForContainer(containernummer: String): List<String> {
        return getPhotoUrisForQuery("SELECT $COLUMN_PHOTO_URIS FROM $TABLE_NAME WHERE $COLUMN_CONTAINERNUMMER = ?", arrayOf(containernummer))
    }

    // Containerfotos für noch nicht exportierte Datensätze einbeziehen
    fun getPhotosForContainer(containernummer: String): List<String> {
        return getPhotoUrisForQuery("SELECT $COLUMN_PHOTO_URIS FROM $TABLE_NAME WHERE $COLUMN_CONTAINERNUMMER = ? AND ($COLUMN_EXPORTDATUM IS NULL OR $COLUMN_EXPORTDATUM = '')", arrayOf(containernummer))
    }

    // Gemeinsame Logik zum Abrufen von Foto-URIs für eine Abfrage
    private fun getPhotoUrisForQuery(query: String, selectionArgs: Array<String>): List<String> {
        val photos = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.rawQuery(query, selectionArgs)

        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(COLUMN_PHOTO_URIS)
            if (columnIndex >= 0) {
                val photoUris = cursor.getString(columnIndex)
                photos.addAll(photoUris.split(","))
            }
        }

        cursor.close()
        db.close()
        return photos
    }
}
