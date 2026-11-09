package com.example.inclinometro

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.roundToInt

/** Detalle de una salida: gráfico de cabeceo y alabeo enlazado con el mapa del recorrido.
 *  Tocar o arrastrar en el gráfico marca la posición en el mapa; tocar el recorrido marca ese instante en el gráfico.
 *  Sin cobertura, el gráfico y los datos funcionan igual aunque el mapa no cargue. */
class TripDetailActivity : Activity() {
    private val theme get() = Themes.current
    private val ui get() = theme.ui
    private val accent get() = Themes.accent(theme)
    private lateinit var trip: Trip
    private lateinit var s: Samples
    private lateinit var chart: TripChartView
    private lateinit var info: TextView
    private var map: MapView? = null
    private var track: TrackOverlay? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Recorder.active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val db = TripDb.get(this)
        trip = db.trip(intent.getLongExtra("trip", -1)) ?: run { finish(); return }
        s = db.samples(trip.id)
        build()
    }
    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); val cur = chart.cursor; build(); if (cur >= 0) select(cur, true) }
    override fun onResume() { super.onResume(); map?.onResume() }
    override fun onPause() { super.onPause(); map?.onPause() }

    private fun build() {
        map?.onDetach(); map = null
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.button(this@TripDetailActivity, "←  Volver", false, accent) { finish() }, LinearLayout.LayoutParams(dp(130), dp(50)))
            addView(ui.label(this@TripDetailActivity, trip.name, 20f, ui.text).apply { setPadding(dp(16), 0, 0, 0); maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val stats = ui.note(this, "${Fmt.date(trip.start)}  ·  ${Recorder.clock(trip.duration)}" + (if (trip.hasGps) "  ·  ${Fmt.km(trip.distance)}" else "") +
            "  ·  cabeceo máx ${Fmt.deg(trip.maxPitch)}  ·  alabeo máx ${Fmt.deg(trip.maxRoll)}  ·  peligro ${trip.danger}×")
        info = ui.label(this, "Toca o arrastra sobre el gráfico para ver un instante", 16f, ui.text).apply { setPadding(0, dp(4), 0, dp(4)) }

        chart = TripChartView(this, s, trip, theme, accent).apply { onCursor = { select(it, true) } }
        val mapBox: View = if (s.size > 0 && (0 until s.size).any { s.hasPos(it) }) {
            val m = Maps.mapView(this); map = m
            val t = TrackOverlay(this, s, trip, theme, accent).apply { onTap = { select(it, false) } }; track = t
            m.overlays.add(t)
            val idx = (0 until s.size).filter { s.hasPos(it) }
            Maps.fit(m, idx.map { s.lat[it] }, idx.map { s.lon[it] })
            m
        } else TextView(this).apply {
            text = "Esta salida no tiene posiciones GPS: solo se muestra el gráfico."; gravity = Gravity.CENTER; setTextColor(ui.muted); textSize = 15f
            background = GradientDrawable().apply { setColor(Color.rgb(22, 22, 25)); cornerRadius = dp(10).toFloat() }
        }

        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val body = LinearLayout(this).apply {
            orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (land) { addView(chart, LinearLayout.LayoutParams(0, -1, 1f)); addView(mapBox, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(10) }) }
            else { addView(chart, LinearLayout.LayoutParams(-1, 0, 0.42f)); addView(mapBox, LinearLayout.LayoutParams(-1, 0, 0.58f).apply { topMargin = dp(10) }) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = ui.backgroundDrawable(this@TripDetailActivity); setPadding(dp(12), dp(10), dp(12), dp(12))
            addView(header, LinearLayout.LayoutParams(-1, -2)); addView(stats); addView(info)
            addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    /** Marca el instante [i] en el gráfico y en el mapa, con sus ángulos exactos. */
    private fun select(i: Int, fromChart: Boolean) {
        if (!fromChart) chart.cursor = i
        val pos = s.hasPos(i)
        info.text = "${Fmt.time(s.t[i])}  ·  Cabeceo ${sign(s.pitch[i])}  ·  Alabeo ${sign(s.roll[i])}" +
            (if (!s.kmh[i].isNaN()) "  ·  ${s.kmh[i].roundToInt()} km/h" else "") + (if (!s.alt[i].isNaN()) "  ·  ${s.alt[i].roundToInt()} m" else "") +
            (if (!pos && map != null) "  ·  sin GPS en este instante" else "")
        val m = map ?: return
        track?.cursor = i
        if (pos && fromChart && !m.boundingBox.contains(s.lat[i], s.lon[i])) m.controller.animateTo(GeoPoint(s.lat[i], s.lon[i]))
        m.invalidate()
    }
    private fun sign(v: Float) = String.format("%+.1f°", v)
}
