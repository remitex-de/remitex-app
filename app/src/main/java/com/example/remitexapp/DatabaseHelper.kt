package com.example.remitexapp

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

@Suppress("NAME_SHADOWING")
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 3
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    // Auswahlliste der ReExport Activity mit Daten aus der Datenbank füllen
    @SuppressLint("Range")
    fun getAllData(): List<Array<String>> {
        val data = mutableListOf<Array<String>>()

        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COLUMN_EXPORTDATUM IS NOT NULL ORDER BY $COLUMN_EXPORTDATUM DESC", null)

        if (cursor.moveToFirst()) {
            do {
                val fahrernummer = cursor.getString(cursor.getColumnIndex(COLUMN_FAHRERNUMMER))
                val containernummer = cursor.getString(cursor.getColumnIndex(COLUMN_CONTAINERNUMMER))
                val fuellmenge = cursor.getString(cursor.getColumnIndex(COLUMN_FUELLMENGE))
                val tag = cursor.getString(cursor.getColumnIndex(COLUMN_TAG))
                val uhrzeit = cursor.getString(cursor.getColumnIndex(COLUMN_UHRZEIT))
                val exportdatum = cursor.getString(cursor.getColumnIndex(COLUMN_EXPORTDATUM))

                data.add(arrayOf(fahrernummer, containernummer, fuellmenge, tag, uhrzeit, exportdatum))
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return data
    }

    // Fahrernummer Spinner Filter in ExportActivity mit Werten der Datenbank füllen
    fun getAllFahrernummern(): List<String> {
        val fahrernummern = ArrayList<String>()

        val selectQuery = """
        SELECT DISTINCT $COLUMN_FAHRERNUMMER 
        FROM $TABLE_NAME 
        WHERE $COLUMN_FAHRERNUMMER IN (
            SELECT DISTINCT $COLUMN_FAHRERNUMMER 
            FROM $TABLE_NAME 
            WHERE $COLUMN_EXPORTDATUM IS NULL OR $COLUMN_EXPORTDATUM = ''
        )
    """

        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)
        if (cursor.moveToFirst()) {
            do {
                fahrernummern.add(cursor.getString(0))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return fahrernummern
    }

    // Daten im Datum Spinner Filter in ExportActivity basierend auf der ausgewählten fahrernummer filtern und in den Spinner füllen
    fun getTagsForFahrernummer(fahrernummer: String): List<String> {
        val tags = mutableListOf<String>()
        val db = this.readableDatabase

        val query = """
        SELECT DISTINCT $COLUMN_TAG 
        FROM $TABLE_NAME 
        WHERE $COLUMN_FAHRERNUMMER = ? AND ($COLUMN_EXPORTDATUM IS NULL OR $COLUMN_EXPORTDATUM = '')
    """
        val cursor = db.rawQuery(query, arrayOf(fahrernummer))

        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(COLUMN_TAG)
            if (columnIndex != -1) {
                do {
                    tags.add(cursor.getString(columnIndex))
                } while (cursor.moveToNext())
            }
        }
        cursor.close()
        return tags
    }

    // Gefilterte Daten für den Export bereit stellen
    @SuppressLint("Range")
    fun getSelectedData(fahrernummer: String, tag: String): List<Array<String>> {
        val data = mutableListOf<Array<String>>()

        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COLUMN_FAHRERNUMMER = ? AND $COLUMN_TAG = ?", arrayOf(fahrernummer, tag))

        if (cursor.moveToFirst()) {
            do {
                val fahrernummer = cursor.getString(cursor.getColumnIndex(COLUMN_FAHRERNUMMER))
                val containernummer = cursor.getString(cursor.getColumnIndex(COLUMN_CONTAINERNUMMER))
                val fuellmenge = cursor.getString(cursor.getColumnIndex(COLUMN_FUELLMENGE))
                val tag = cursor.getString(cursor.getColumnIndex(COLUMN_TAG))
                val uhrzeit = cursor.getString(cursor.getColumnIndex(COLUMN_UHRZEIT))

                data.add(arrayOf(fahrernummer, containernummer, fuellmenge, tag, uhrzeit))
            } while (cursor.moveToNext())
        }

        cursor.close()
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

    // Überprüfen, ob in der Datenbank der zu erfassende Container am Tag bereits schon mal erfasst wurde
    @SuppressLint("Range")
    fun isContainerScannedToday(containernummer: String, tag: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COLUMN_CONTAINERNUMMER = ? AND $COLUMN_TAG = ?", arrayOf(containernummer, tag))

        val exists = cursor.moveToFirst()

        cursor.close()
        db.close()

        return exists
    }

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

    // Datensatz aktualisieren, wenn in der Datenbank der zu erfassende Container am Tag bereits schon mal erfasst wurde und noch mal erfasst werden soll
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
        val photoUris = mutableListOf<Uri>()
        data.forEach { record ->
            val containernummer = record[1]
            val fotos = getAllPhotosForContainer(containernummer)
            val authority = "${context.packageName}.fileprovider"

            // Debugging-Ausgabe
            println("Überprüfe Fotos für Containernummer: $containernummer")

            fotos.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(context, authority, file)
                    photoUris.add(uri)
                    // Debugging-Ausgabe
                    println("Foto gefunden: $path, URI: $uri")
                } else {
                    // Debugging-Ausgabe
                    println("Foto nicht gefunden: $path")
                }
            }
        }
        // Debugging-Ausgabe
        println("Gesamtanzahl gefundener Fotos: ${photoUris.size}")
        return photoUris
    }

    // Methode, um nur Containerfotos zu bekommen, die noch nicht exportiert wurden
    fun getUnexportedPhotoUrisForContainer(context: Context, data: List<Array<String>>): List<Uri> {
        val photoUris = mutableListOf<Uri>()
        data.forEach { record ->
            val containernummer = record[1]
            val fotos = getPhotosForContainer(containernummer)
            val authority = "${context.packageName}.fileprovider"

            // Debugging-Ausgabe
            println("Überprüfe Fotos für Containernummer: $containernummer")

            fotos.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(context, authority, file)
                    photoUris.add(uri)
                    // Debugging-Ausgabe
                    println("Foto gefunden: $path, URI: $uri")
                } else {
                    // Debugging-Ausgabe
                    println("Foto nicht gefunden: $path")
                }
            }
        }
        // Debugging-Ausgabe
        println("Gesamtanzahl gefundener Fotos: ${photoUris.size}")
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
            // Grant permission to read the attachments for the email client
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Granting URI permissions to all relevant email client packages
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
        val photos = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_PHOTO_URIS FROM $TABLE_NAME WHERE $COLUMN_CONTAINERNUMMER = ?", arrayOf(containernummer))
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(COLUMN_PHOTO_URIS)
            if (columnIndex >= 0) {
                val photoUris = cursor.getString(columnIndex)
                photos.addAll(photoUris.split(","))
                // Debugging-Ausgabe
                println("Fotos für Containernummer $containernummer: $photos")
            } else {
                // Debugging-Ausgabe
                println("Spalte $COLUMN_PHOTO_URIS nicht gefunden für Containernummer $containernummer")
            }
        } else {
            // Debugging-Ausgabe
            println("Keine Datensätze für Containernummer $containernummer gefunden")
        }
        cursor.close()
        return photos
    }

    // Containerfotos für noch nicht exportierte Datensätze einbeziehen
    fun getPhotosForContainer(containernummer: String): List<String> {
        val photos = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_PHOTO_URIS FROM $TABLE_NAME WHERE $COLUMN_CONTAINERNUMMER = ? AND ($COLUMN_EXPORTDATUM IS NULL OR $COLUMN_EXPORTDATUM = '')", arrayOf(containernummer))
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(COLUMN_PHOTO_URIS)
            if (columnIndex >= 0) {
                val photoUris = cursor.getString(columnIndex)
                photos.addAll(photoUris.split(","))
                // Debugging-Ausgabe
                println("Fotos für Containernummer $containernummer: $photos")
            } else {
                // Debugging-Ausgabe
                println("Spalte $COLUMN_PHOTO_URIS nicht gefunden für Containernummer $containernummer")
            }
        } else {
            // Debugging-Ausgabe
            println("Keine Datensätze für Containernummer $containernummer gefunden")
        }
        cursor.close()
        return photos
    }
}

