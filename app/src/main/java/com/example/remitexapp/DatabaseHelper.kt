package com.example.remitexapp

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

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

    // Helfermethode zur Cursor-Extraktion
    @SuppressLint("Range")
    private fun extractDataFromCursor(cursor: Cursor): Array<String> {
        val fahrernummer = cursor.getString(cursor.getColumnIndex(COLUMN_FAHRERNUMMER))
        val containernummer = cursor.getString(cursor.getColumnIndex(COLUMN_CONTAINERNUMMER))
        val fuellmenge = cursor.getString(cursor.getColumnIndex(COLUMN_FUELLMENGE))
        val tag = cursor.getString(cursor.getColumnIndex(COLUMN_TAG))
        val uhrzeit = cursor.getString(cursor.getColumnIndex(COLUMN_UHRZEIT))
        val exportdatum = cursor.getString(cursor.getColumnIndex(COLUMN_EXPORTDATUM)) ?: ""
        return arrayOf(fahrernummer, containernummer, fuellmenge, tag, uhrzeit, exportdatum)
    }

    // Auswahlliste der ReExport Activity mit Daten aus der Datenbank füllen
    fun getAllData(): List<Array<String>> {
        val data = mutableListOf<Array<String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COLUMN_EXPORTDATUM IS NOT NULL ORDER BY $COLUMN_EXPORTDATUM DESC", null)
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    data.add(extractDataFromCursor(it))
                } while (it.moveToNext())
            }
        }
        db.close()
        return data
    }

    // Fahrernummer Spinner Filter in ExportActivity mit Werten der Datenbank füllen
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

    // Gefilterte Daten für den Export bereitstellen
    fun getSelectedData(fahrernummer: String, tag: String): List<Array<String>> {
        val data = mutableListOf<Array<String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COLUMN_FAHRERNUMMER = ? AND $COLUMN_TAG = ?", arrayOf(fahrernummer, tag))
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    data.add(extractDataFromCursor(it))
                } while (it.moveToNext())
            }
        }
        db.close()
        return data
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

    // Methode, um nur Containerfotos zu bekommen, die noch nicht exportiert wurden
    fun getUnexportedPhotoUrisForContainer(context: Context, data: List<Array<String>>): List<Uri> {
        return getPhotoUris(context, data) { containernummer -> getPhotosForContainer(containernummer) }
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
    fun getAllPhotosForContainer(containernummer: String): List<String> {
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
