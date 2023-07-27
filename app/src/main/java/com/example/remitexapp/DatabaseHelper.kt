import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

@Suppress("NAME_SHADOWING")
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "ContainerDatenbank"
        const val TABLE_NAME = "ContainerFuellmengen"
        const val COLUMN_FAHRERNUMMER = "Fahrernummer"
        const val COLUMN_CONTAINERNUMMER = "Containernummer"
        const val COLUMN_FUELLMENGE = "Fuellmenge"
        const val COLUMN_TAG = "Tag"
        const val COLUMN_UHRZEIT = "Uhrzeit"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_TABLE = ("CREATE TABLE " + TABLE_NAME + "("
                + COLUMN_FAHRERNUMMER + " TEXT,"
                + COLUMN_CONTAINERNUMMER + " INTEGER,"
                + COLUMN_FUELLMENGE + " INTEGER,"
                + COLUMN_TAG + " TEXT,"
                + COLUMN_UHRZEIT + " TEXT" + ")")
        db.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME)
        onCreate(db)
    }

    // Fahrernummer Spinner Filter in ExportActivity mit Werten der Datenbank füllen
    fun getAllFahrernummern(): List<String> {
        val fahrernummern = ArrayList<String>()
        val selectQuery = "SELECT DISTINCT $COLUMN_FAHRERNUMMER FROM $TABLE_NAME"

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

    @SuppressLint("Range")
    fun getAllTags(): List<String> {
        val tagsList = ArrayList<String>()
        val db = readableDatabase
        val selectQuery = "SELECT DISTINCT $COLUMN_TAG FROM $TABLE_NAME"
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


}

