package com.example.remitexapp

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createtable = ("CREATE TABLE " + TABLE_NAME + "("
                + COLUMN_FAHRERNUMMER + " TEXT,"
                + COLUMN_CONTAINERNUMMER + " INTEGER,"
                + COLUMN_FUELLMENGE + " INTEGER,"
                + COLUMN_TAG + " TEXT,"
                + COLUMN_UHRZEIT + " TEXT,"
                + COLUMN_EXPORTDATUM + " TEXT)")
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

    // Datensatz aktualisieren, wenn in der Datenbank der zu erfassende Container am Tag bereits schon mal erfasst wurde und noch mal erfasst werden soll
    fun updateContainerRecord(fahrernummer: String, containernummer: String, fuellmenge: Int, tag: String, uhrzeit: String): Int {
        val db = this.writableDatabase
        val contentValues = ContentValues()

        contentValues.put(COLUMN_FAHRERNUMMER, fahrernummer)
        contentValues.put(COLUMN_CONTAINERNUMMER, containernummer)
        contentValues.put(COLUMN_FUELLMENGE, fuellmenge)
        contentValues.put(COLUMN_TAG, tag)
        contentValues.put(COLUMN_UHRZEIT, uhrzeit)

        return db.update(TABLE_NAME, contentValues, "$COLUMN_CONTAINERNUMMER = ? AND $COLUMN_TAG = ?", arrayOf(containernummer, tag))
    }

}

