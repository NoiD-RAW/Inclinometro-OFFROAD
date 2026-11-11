package dev.arklay.inclinometro

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.abs
import kotlin.math.max

/** Una salida grabada. Los máximos van en grados positivos por sentido (ver [MaxTracker]). */
class Trip(
    val id: Long, var name: String, val start: Long, val end: Long, val sampleMs: Long,
    val max: FloatArray, val danger: Int, val distance: Float,
    val limitPitch: Float, val limitRoll: Float, val cautionPct: Float, val hasGps: Boolean, val samples: Int,
) {
    val duration get() = max(0L, end - start)
    val maxPitch get() = max(max[0], max[1])
    val maxRoll get() = max(max[2], max[3])
    /** La salida llegó a los límites del vehículo que había configurados al grabarla. */
    val exceeded get() = danger > 0 || maxPitch >= limitPitch || maxRoll >= limitRoll
}

/** Muestras de una salida en columnas (más ligero que un objeto por muestra). Sin GPS, lat/lon son NaN. */
class Samples(n: Int) {
    val t = LongArray(n); val pitch = FloatArray(n); val roll = FloatArray(n)
    val lat = DoubleArray(n); val lon = DoubleArray(n); val alt = FloatArray(n); val kmh = FloatArray(n)
    val size get() = t.size
    fun hasPos(i: Int) = !lat[i].isNaN()
}

/** Punto con inclinación alta, para el mapa de puntos críticos. */
class Hotspot(val trip: Long, val tripName: String, val t: Long, val lat: Double, val lon: Double, val pitch: Float, val roll: Float, val ratio: Float)

/** Salidas guardadas en el propio dispositivo (base de datos SQLite de Android). */
class TripDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "salidas.db", null, 1) {
    companion object {
        @Volatile private var inst: TripDb? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: TripDb(ctx.applicationContext).also { inst = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, start INTEGER NOT NULL, end_t INTEGER NOT NULL,
            sample_ms INTEGER NOT NULL, max_pu REAL DEFAULT 0, max_pd REAL DEFAULT 0, max_rl REAL DEFAULT 0, max_rr REAL DEFAULT 0,
            danger INTEGER DEFAULT 0, dist REAL DEFAULT 0, lim_pitch REAL NOT NULL, lim_roll REAL NOT NULL, caution REAL NOT NULL, has_gps INTEGER DEFAULT 0)""")
        db.execSQL("""CREATE TABLE samples (trip INTEGER NOT NULL, t INTEGER NOT NULL, pitch REAL NOT NULL, roll REAL NOT NULL,
            lat REAL, lon REAL, alt REAL, kmh REAL)""")
        db.execSQL("CREATE INDEX samples_trip ON samples(trip, t)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}

    fun createTrip(name: String, start: Long, sampleMs: Long): Long = writableDatabase.insert("trips", null, ContentValues().apply {
        put("name", name); put("start", start); put("end_t", start); put("sample_ms", sampleMs)
        put("lim_pitch", Config.limitPitch); put("lim_roll", Config.limitRoll); put("caution", Config.cautionPct)
    })

    fun addSample(trip: Long, t: Long, pitch: Float, roll: Float, lat: Double?, lon: Double?, alt: Float?, kmh: Float?) {
        writableDatabase.insert("samples", null, ContentValues().apply {
            put("trip", trip); put("t", t); put("pitch", pitch); put("roll", roll)
            if (lat != null && lon != null) { put("lat", lat); put("lon", lon) }
            if (alt != null) put("alt", alt); if (kmh != null) put("kmh", kmh)
        })
    }

    fun updateTrip(id: Long, end: Long, maxv: FloatArray, danger: Int, dist: Float, hasGps: Boolean) {
        writableDatabase.update("trips", ContentValues().apply {
            put("end_t", end); put("max_pu", maxv[0]); put("max_pd", maxv[1]); put("max_rl", maxv[2]); put("max_rr", maxv[3])
            put("danger", danger); put("dist", dist); put("has_gps", if (hasGps) 1 else 0)
        }, "id=?", arrayOf(id.toString()))
    }

    fun rename(id: Long, name: String) { writableDatabase.update("trips", ContentValues().apply { put("name", name) }, "id=?", arrayOf(id.toString())) }

    fun delete(id: Long) {
        writableDatabase.apply { beginTransaction(); try { delete("samples", "trip=?", arrayOf(id.toString())); delete("trips", "id=?", arrayOf(id.toString())); setTransactionSuccessful() } finally { endTransaction() } }
    }

    private val tripCols = "id, name, start, end_t, sample_ms, max_pu, max_pd, max_rl, max_rr, danger, dist, lim_pitch, lim_roll, caution, has_gps, (SELECT COUNT(*) FROM samples WHERE trip = trips.id)"
    private fun readTrip(c: android.database.Cursor) = Trip(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getLong(4),
        floatArrayOf(c.getFloat(5), c.getFloat(6), c.getFloat(7), c.getFloat(8)), c.getInt(9), c.getFloat(10), c.getFloat(11), c.getFloat(12), c.getFloat(13), c.getInt(14) != 0, c.getInt(15))

    /** Todas las salidas: por fecha (más reciente primero) o por mayor ángulo alcanzado. */
    fun trips(byAngle: Boolean = false): List<Trip> {
        val order = if (byAngle) "MAX(MAX(max_pu, max_pd), MAX(max_rl, max_rr)) DESC, start DESC" else "start DESC"
        readableDatabase.rawQuery("SELECT $tripCols FROM trips ORDER BY $order", null).use { c ->
            val out = ArrayList<Trip>(c.count); while (c.moveToNext()) out += readTrip(c); return out
        }
    }
    fun trip(id: Long): Trip? = readableDatabase.rawQuery("SELECT $tripCols FROM trips WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) readTrip(c) else null }

    fun samples(trip: Long): Samples = readableDatabase.rawQuery("SELECT t, pitch, roll, lat, lon, alt, kmh FROM samples WHERE trip=? ORDER BY t", arrayOf(trip.toString())).use { c ->
        val s = Samples(c.count); var i = 0
        while (c.moveToNext()) {
            s.t[i] = c.getLong(0); s.pitch[i] = c.getFloat(1); s.roll[i] = c.getFloat(2)
            s.lat[i] = if (c.isNull(3)) Double.NaN else c.getDouble(3); s.lon[i] = if (c.isNull(4)) Double.NaN else c.getDouble(4)
            s.alt[i] = if (c.isNull(5)) Float.NaN else c.getFloat(5); s.kmh[i] = if (c.isNull(6)) Float.NaN else c.getFloat(6); i++
        }
        s
    }

    /** Puntos con GPS por encima de la zona de precaución (con los límites actuales), de todas las salidas.
     *  Para no saturar el mapa, en cada celda de ~10 m se queda solo el punto más inclinado. */
    fun hotspots(): List<Hotspot> {
        val limP = Config.limitPitch; val limR = Config.limitRoll; val min = Config.cautionPct
        val best = HashMap<Long, Hotspot>()
        readableDatabase.rawQuery("""SELECT s.trip, t.name, s.t, s.lat, s.lon, s.pitch, s.roll FROM samples s JOIN trips t ON t.id = s.trip
            WHERE s.lat IS NOT NULL AND (ABS(s.pitch) >= CAST(? AS REAL) OR ABS(s.roll) >= CAST(? AS REAL))""", arrayOf((limP * min).toString(), (limR * min).toString())).use { c ->
            while (c.moveToNext()) {
                val p = c.getFloat(5); val r = c.getFloat(6)
                val ratio = max(abs(p) / limP, abs(r) / limR)
                val lat = c.getDouble(3); val lon = c.getDouble(4)
                val cell = (Math.round(lat * 10000) shl 32) xor Math.round(lon * 10000)
                val old = best[cell]
                if (old == null || ratio > old.ratio) best[cell] = Hotspot(c.getLong(0), c.getString(1), c.getLong(2), lat, lon, p, r, ratio)
            }
        }
        return best.values.sortedBy { it.ratio }                                      // los más fuertes se dibujan encima
    }

    /** Lo que ocupan las salidas en disco, en bytes. */
    fun sizeBytes(ctx: Context): Long = listOf("salidas.db", "salidas.db-journal", "salidas.db-wal").sumOf { ctx.getDatabasePath(it).let { f -> if (f.exists()) f.length() else 0L } }

    /** Borra las salidas que empezaron antes de [before] (excepto [keep], la que se está grabando). Devuelve cuántas. */
    fun deleteOlderThan(before: Long, keep: Long): Int {
        val ids = readableDatabase.rawQuery("SELECT id FROM trips WHERE start < ? AND id != ?", arrayOf(before.toString(), keep.toString())).use { c -> List(c.count) { c.moveToNext(); c.getLong(0) } }
        for (id in ids) delete(id)
        writableDatabase.execSQL("VACUUM")
        return ids.size
    }
}
