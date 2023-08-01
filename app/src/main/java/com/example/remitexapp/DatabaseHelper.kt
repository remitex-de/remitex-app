import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME)
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
        val selectQuery = "SELECT DISTINCT $COLUMN_FAHRERNUMMER FROM $TABLE_NAME WHERE $COLUMN_EXPORTDATUM IS NULL"

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

    // Datum Spinner Filter in ExportActivity mit Werten der Datenbank füllen
    @SuppressLint("Range")
    fun getAllTags(): List<String> {
        val tagsList = ArrayList<String>()
        val db = readableDatabase
        val selectQuery = "SELECT DISTINCT $COLUMN_TAG FROM $TABLE_NAME WHERE $COLUMN_EXPORTDATUM IS NULL ORDER BY $COLUMN_TAG DESC"
        val cursor: Cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                tagsList.add(cursor.getString(cursor.getColumnIndex(COLUMN_TAG)))
            } while (cursor.moveToNext())
        }

        cursor.close()
        return tagsList
    }

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

    fun updateExportDate(fahrernummer: String, tag: String, exportdatum: String) {
        val db = this.writableDatabase
        val contentValues = ContentValues().apply {
            put(COLUMN_EXPORTDATUM, exportdatum)
        }
        db.update(TABLE_NAME, contentValues, "$COLUMN_FAHRERNUMMER = ? AND $COLUMN_TAG = ?", arrayOf(fahrernummer, tag))
        db.close()
    }

}

