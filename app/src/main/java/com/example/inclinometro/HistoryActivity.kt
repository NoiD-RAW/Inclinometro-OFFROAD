package com.example.inclinometro

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Formatos comunes del histórico. */
object Fmt {
    private val es = Locale("es", "ES")
    fun date(ms: Long): String = SimpleDateFormat("EEE dd/MM/yyyy HH:mm", es).format(Date(ms)).replaceFirstChar { it.uppercase() }
    fun time(ms: Long): String = SimpleDateFormat("HH:mm:ss", es).format(Date(ms))
    fun km(m: Float) = if (m < 1000) "${m.roundToInt()} m" else String.format(es, "%.1f km", m / 1000f)
    fun mb(b: Long) = String.format(es, "%.1f MB", b / 1048576f)
    fun deg(v: Float) = "${v.roundToInt()}°"
}

/** Histórico de salidas: lista con filtro/orden, renombrar, borrar y exportar; acceso al detalle y al mapa de puntos críticos. */
class HistoryActivity : Activity() {
    private val theme get() = Themes.current
    private val ui get() = theme.ui
    private val accent get() = Themes.accent(theme)
    private val db by lazy { TripDb.get(this) }
    private var byAngle = false
    private var onlyExceeded = false
    private var exporting: Pair<Trip, String>? = null
    private lateinit var scroll: ScrollView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Recorder.active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scroll = ScrollView(this); setContentView(scroll)
    }
    override fun onResume() { super.onResume(); render() }

    private fun button(t: String, filled: Boolean, onClick: () -> Unit) = ui.button(this, t, filled, accent, onClick)
    private fun row(vararg views: View) = LinearLayout(this).apply {
        for ((i, v) in views.withIndex()) addView(v, LinearLayout.LayoutParams(0, dp(52), 1f).apply { topMargin = dp(8); if (i > 0) leftMargin = dp(8) })
    }

    private fun render() {
        val y = scroll.scrollY
        val trips = db.trips(byAngle).let { l -> if (onlyExceeded) l.filter { it.exceeded } else l }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(28)) }
        col.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(button("←  Volver", false) { finish() }, LinearLayout.LayoutParams(dp(130), dp(50)))
            addView(ui.label(this@HistoryActivity, "Salidas", 22f, ui.text).apply { setPadding(dp(16), 0, 0, 0) }, LinearLayout.LayoutParams(-2, -2))
        })
        col.addView(row(button("Mapa de puntos críticos", true) { startActivity(Intent(this, HotspotsActivity::class.java)) }))
        col.addView(row(
            button(if (byAngle) "Orden: mayor ángulo" else "Orden: más recientes", false) { byAngle = !byAngle; render() },
            button(if (onlyExceeded) "Ver: solo sobre límite" else "Ver: todas", false) { onlyExceeded = !onlyExceeded; render() }))

        val size = db.sizeBytes(this)
        col.addView(ui.note(this, "Las salidas ocupan ${Fmt.mb(size)} (unos ${Fmt.mb(90L * 3600 * 1000 / Config.sampleMs)} por hora de grabación)."))
        if (size > 200L * 1048576) col.addView(ui.note(this, "Ocupan bastante espacio: puedes borrar las antiguas con «Liberar espacio».").apply { setTextColor(theme.caution) })
        col.addView(row(button("Liberar espacio", false) { freeSpace() }))

        if (trips.isEmpty()) col.addView(ui.note(this, if (onlyExceeded) "Ninguna salida ha superado los límites." else "Aún no hay salidas. Inicia una desde la pantalla principal.").apply { setPadding(0, dp(24), 0, 0) })
        for (t in trips) col.addView(card(t), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        scroll.removeAllViews(); scroll.background = ui.backgroundDrawable(this)
        scroll.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(col, LinearLayout.LayoutParams(if (resources.configuration.screenWidthDp > 760) dp(720) else -1, -2))
        })
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun card(t: Trip): View {
        val recording = Recorder.active && Config.activeTrip == t.id
        val views = ArrayList<View>()
        if (recording) views += TextView(this).apply { text = "●  Grabando ahora"; setTextColor(Chrome.REC); textSize = 15f; setTypeface(typeface, Typeface.BOLD) }
        if (t.exceeded) views += TextView(this).apply {                                   // superó los límites del vehículo
            text = "⚠  Superó los límites del vehículo"; setTextColor(Color.WHITE); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(6), dp(10), dp(6)); background = GradientDrawable().apply { setColor(theme.danger); cornerRadius = dp(6).toFloat() }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(6) }
        }
        val dur = if (recording) System.currentTimeMillis() - t.start else t.duration
        views += ui.note(this, "${Fmt.date(t.start)}  ·  ${Recorder.clock(dur)}" + (if (t.hasGps) "  ·  ${Fmt.km(t.distance)}" else "  ·  sin GPS"))
        views += ui.label(this, "Cabeceo máx ${Fmt.deg(t.maxPitch)}  (arriba ${Fmt.deg(t.max[0])} · abajo ${Fmt.deg(t.max[1])})", 15f, ui.text)
        views += ui.label(this, "Alabeo máx ${Fmt.deg(t.maxRoll)}  (izq. ${Fmt.deg(t.max[2])} · der. ${Fmt.deg(t.max[3])})", 15f, ui.text)
        views += ui.note(this, "Zona de peligro: ${if (t.danger == 0) "ninguna vez" else if (t.danger == 1) "1 vez" else "${t.danger} veces"}  ·  límites ${Fmt.deg(t.limitPitch)} / ${Fmt.deg(t.limitRoll)}")
        views += row(button("Ver", true) { startActivity(Intent(this, TripDetailActivity::class.java).putExtra("trip", t.id)) },
            button("Renombrar", false) { Dialogs.prompt(this, "Nombre de la salida", t.name) { db.rename(t.id, it); render() } })
        views += row(button("Exportar", false) { export(t) },
            button("Borrar", false) {
                if (recording) Toast.makeText(this, "Finaliza la salida antes de borrarla", Toast.LENGTH_LONG).show()
                else Dialogs.confirm(this, "Borrar salida", "Se borrará «${t.name}» con todos sus datos. No se puede deshacer.", "Borrar") { db.delete(t.id); render() }
            })
        return ui.section(this, t.name, accent, views)
    }

    // ---------- exportar ----------
    private fun export(t: Trip) {
        Dialogs.pick(this, "Exportar «${t.name}»", listOf("CSV: todos los datos, para hoja de cálculo", "GPX: el recorrido, para mapas y navegadores GPS")) { i ->
            if (i == 0) saveAs(t, "csv")
            else if (!t.hasGps) Toast.makeText(this, "Esta salida no tiene posiciones GPS: exporta en CSV", Toast.LENGTH_LONG).show() else saveAs(t, "gpx")
        }
    }
    private fun saveAs(t: Trip, ext: String) {
        exporting = t to ext
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (ext == "csv") "text/csv" else "application/gpx+xml").putExtra(Intent.EXTRA_TITLE, Export.fileName(t, ext))
        try { startActivityForResult(i, 21) }
        catch (_: Exception) {                                                          // sin selector de archivos: a la carpeta de la app
            val dir = File(getExternalFilesDir(null), "exportes").apply { mkdirs() }
            val f = File(dir, Export.fileName(t, ext))
            try { f.outputStream().use { write(t, ext, it) }; Toast.makeText(this, "Guardado en ${f.path}", Toast.LENGTH_LONG).show() }
            catch (e: Exception) { Toast.makeText(this, "No se pudo exportar: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }
    private fun write(t: Trip, ext: String, out: java.io.OutputStream) { val s = db.samples(t.id); if (ext == "csv") Export.csv(t, s, out) else Export.gpx(t, s, out) }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val (t, ext) = exporting ?: return; exporting = null
        val uri: Uri = data?.data ?: return
        if (req != 21 || res != RESULT_OK) return
        try { contentResolver.openOutputStream(uri)?.use { write(t, ext, it) }; Toast.makeText(this, "Exportada", Toast.LENGTH_SHORT).show() }
        catch (e: Exception) { Toast.makeText(this, "No se pudo exportar: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    // ---------- liberar espacio ----------
    private fun freeSpace() {
        val day = 24L * 3600 * 1000; val now = System.currentTimeMillis()
        val options = listOf("Borrar las de más de 3 meses" to now - 90 * day, "Borrar las de más de 1 año" to now - 365 * day, "Borrar todas" to Long.MAX_VALUE)
        Dialogs.pick(this, "Liberar espacio", options.map { it.first }) { i ->
            val (label, before) = options[i]
            Dialogs.confirm(this, label, "Se borrarán esas salidas con todos sus datos (la que se esté grabando no). No se puede deshacer.", "Borrar") {
                val n = db.deleteOlderThan(before, Config.activeTrip)
                Toast.makeText(this, "Borradas $n salidas", Toast.LENGTH_LONG).show(); render()
            }
        }
    }
}
