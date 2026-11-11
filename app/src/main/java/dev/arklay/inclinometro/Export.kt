package dev.arklay.inclinometro

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Exportación de una salida a CSV (hoja de cálculo) o GPX (recorrido para mapas y GPS). */
object Export {
    private fun iso() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private fun n(v: Float) = if (v.isNaN()) "" else String.format(Locale.US, "%.1f", v + 0f)      // + 0f: evita "-0.0"
    private fun n(v: Double) = if (v.isNaN()) "" else String.format(Locale.US, "%.6f", v)

    fun fileName(t: Trip, ext: String) = t.name.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ _-]"), "_").trim() + ".$ext"

    fun csv(t: Trip, s: Samples, out: OutputStream) {
        val f = iso()
        out.bufferedWriter().use { w ->
            w.write("fecha_utc,ms,cabeceo_grados,alabeo_grados,latitud,longitud,altitud_m,velocidad_kmh\n")
            for (i in 0 until s.size) w.write("${f.format(Date(s.t[i]))},${s.t[i]},${n(s.pitch[i])},${n(s.roll[i])},${n(s.lat[i])},${n(s.lon[i])},${n(s.alt[i])},${n(s.kmh[i])}\n")
        }
    }

    /** GPX con el recorrido; el cabeceo y el alabeo de cada punto van en <extensions>. Los puntos sin GPS se omiten. */
    fun gpx(t: Trip, s: Samples, out: OutputStream) {
        val f = iso(); val name = t.name.replace("&", "&amp;").replace("<", "&lt;")
        out.bufferedWriter().use { w ->
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Inclinómetro OFFROAD\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            w.write("<trk><name>$name</name><trkseg>\n")
            for (i in 0 until s.size) if (s.hasPos(i)) {
                w.write("<trkpt lat=\"${n(s.lat[i])}\" lon=\"${n(s.lon[i])}\">")
                if (!s.alt[i].isNaN()) w.write("<ele>${n(s.alt[i])}</ele>")
                w.write("<time>${f.format(Date(s.t[i]))}</time><extensions><cabeceo>${n(s.pitch[i])}</cabeceo><alabeo>${n(s.roll[i])}</alabeo></extensions></trkpt>\n")
            }
            w.write("</trkseg></trk>\n</gpx>\n")
        }
    }
}
