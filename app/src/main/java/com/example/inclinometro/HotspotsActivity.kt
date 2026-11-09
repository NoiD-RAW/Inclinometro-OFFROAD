package com.example.inclinometro

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.osmdroid.views.MapView

/** Mapa de puntos críticos: los lugares de todas las salidas donde se superó la zona de precaución, coloreados por intensidad. */
class HotspotsActivity : Activity() {
    private val theme get() = Themes.current
    private val ui get() = theme.ui
    private val accent get() = Themes.accent(theme)
    private var map: MapView? = null
    private lateinit var info: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Recorder.active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val pts = TripDb.get(this).hotspots()
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.button(this@HotspotsActivity, "←  Volver", false, accent) { finish() }, LinearLayout.LayoutParams(dp(130), dp(50)))
            addView(ui.label(this@HotspotsActivity, "Puntos críticos", 20f, ui.text).apply { setPadding(dp(16), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val legend = ui.note(this, if (pts.isEmpty()) "Aún no hay puntos por encima de la zona de precaución en salidas con GPS."
            else "${pts.size} puntos por encima del ${(Config.cautionPct * 100).toInt()} % de los límites actuales (${Fmt.deg(Config.limitPitch)} cabeceo / ${Fmt.deg(Config.limitRoll)} alabeo). Ámbar: precaución · rojo: límite superado. Toca un punto.")
        info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }

        val m = Maps.mapView(this); map = m
        val overlay = HotspotOverlay(this, pts, theme).apply { onTap = { show(it) } }
        m.overlays.add(overlay)
        Maps.fit(m, pts.map { it.lat }, pts.map { it.lon })
        if (pts.isEmpty()) { m.controller.setZoom(6.0); m.controller.setCenter(org.osmdroid.util.GeoPoint(40.4, -3.7)) }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = ui.backgroundDrawable(this@HotspotsActivity); setPadding(dp(12), dp(10), dp(12), dp(12))
            addView(header, LinearLayout.LayoutParams(-1, -2)); addView(legend)
            addView(m, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(info, LinearLayout.LayoutParams(-1, -2))
        })
    }
    override fun onResume() { super.onResume(); map?.onResume() }
    override fun onPause() { super.onPause(); map?.onPause() }

    /** Datos del punto tocado y enlace a su salida. */
    private fun show(h: Hotspot) {
        info.removeAllViews(); info.visibility = View.VISIBLE
        info.addView(ui.label(this, "Cabeceo ${String.format("%+.1f°", h.pitch)}  ·  Alabeo ${String.format("%+.1f°", h.roll)}", 18f, ui.text).apply { setPadding(0, dp(10), 0, 0) })
        info.addView(ui.note(this, "${Fmt.date(h.t)}  ·  ${h.tripName}"))
        info.addView(ui.button(this, "Ver salida", true, accent) {
            startActivity(Intent(this, TripDetailActivity::class.java).putExtra("trip", h.trip))
        }, LinearLayout.LayoutParams(-1, dp(52)))
    }
}
