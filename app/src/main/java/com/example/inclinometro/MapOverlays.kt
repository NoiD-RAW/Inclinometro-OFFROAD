package com.example.inclinometro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Mapas OpenStreetMap (osmdroid): gratis y sin clave. Las teselas vistas se guardan en caché para zonas sin cobertura. */
object Maps {
    fun init(ctx: Context) {
        Configuration.getInstance().apply {
            // La política de uso de OpenStreetMap pide identificar la app; un nombre genérico (com.example…) recibe teselas de «Access blocked».
            userAgentValue = "InclinometroOffroad/2.0 (+https://github.com/NoiD-RAW/Inclinometro-OFFROAD)"
            osmdroidBasePath = File(ctx.filesDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid-teselas")
        }
    }

    fun mapView(ctx: Context) = MapView(ctx).apply {
        setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); isTilesScaledToDpi = true
        minZoomLevel = 3.0; maxZoomLevel = 19.0
    }

    /** Encuadra los puntos (o centra si solo hay uno) cuando el mapa ya tiene tamaño. */
    fun fit(map: MapView, lats: List<Double>, lons: List<Double>) {
        if (lats.isEmpty()) return
        map.post {
            val n = lats.max(); val s = lats.min(); val e = lons.max(); val w = lons.min()
            if (n - s < 0.0005 && e - w < 0.0005) { map.controller.setZoom(17.0); map.controller.setCenter(GeoPoint((n + s) / 2, (e + w) / 2)) }
            else map.zoomToBoundingBox(BoundingBox(n, e, s, w).increaseByScale(1.25f), false)
        }
    }

    /** Color según la inclinación respecto al límite: verde → ámbar (zona de precaución) → rojo (límite). */
    fun inclineColor(ratio: Float, caution: Float, amber: Int): Int {
        val green = Color.rgb(60, 200, 90); val red = Color.rgb(235, 40, 40)
        return when {
            ratio >= 1f -> red
            ratio >= caution -> lerp(amber, red, (ratio - caution) / (1f - caution))
            ratio >= caution * 0.5f -> lerp(green, amber, (ratio - caution * 0.5f) / (caution * 0.5f))
            else -> green
        }
    }
    private fun lerp(a: Int, b: Int, t: Float): Int { val k = t.coerceIn(0f, 1f)
        return Color.rgb((Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()) }
}

/** Recorrido de una salida coloreado por inclinación, con marcadores en los máximos y el instante seleccionado. */
class TrackOverlay(ctx: Context, private val s: Samples, private val trip: Trip, private val theme: Theme, private val accent: Int) : Overlay() {
    var cursor = -1
    var onTap: ((Int) -> Unit)? = null
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val a = Point(); private val b = Point()
    /** Muestra con más cabeceo arriba/abajo y más alabeo izq./der. (solo las que tienen posición). */
    private val maxIdx = IntArray(4) { -1 }

    init {
        val best = FloatArray(4)
        for (i in 0 until s.size) if (s.hasPos(i)) {
            val v = floatArrayOf(s.pitch[i], -s.pitch[i], s.roll[i], -s.roll[i])
            for (k in 0..3) if (v[k] > best[k]) { best[k] = v[k]; maxIdx[k] = i }
        }
    }
    private fun ratio(i: Int) = max(abs(s.pitch[i]) / trip.limitPitch, abs(s.roll[i]) / trip.limitRoll)
    private fun geo(i: Int) = GeoPoint(s.lat[i], s.lon[i])

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val pr = map.projection
        p.style = Paint.Style.STROKE; p.strokeWidth = 6 * d
        var prev = -1
        for (i in 0 until s.size) {
            if (!s.hasPos(i)) { prev = -1; continue }
            if (prev >= 0 && s.t[i] - s.t[prev] < 30000) {                                   // sin unir huecos largos (sin GPS)
                pr.toPixels(geo(prev), a); pr.toPixels(geo(i), b)
                p.color = Maps.inclineColor(max(ratio(prev), ratio(i)), trip.cautionPct, theme.caution); c.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), p)
            }
            prev = i
        }
        val labels = arrayOf("▲", "▼", "◄", "►"); val placed = ArrayList<Point>()
        val names = arrayOf("Cab.", "Cab.", "Alab.", "Alab.")
        for (k in 0..3) {                                                                   // marcadores de los máximos
            val i = maxIdx[k]; if (i < 0) continue
            val v = if (k < 2) abs(s.pitch[i]) else abs(s.roll[i]); if (v < 1f) continue
            pr.toPixels(geo(i), a)
            val stack = placed.count { abs(it.x - a.x) < 60 * d && abs(it.y - a.y) < 30 * d }; placed += Point(a)   // si coinciden, una etiqueta encima de otra
            marker(c, a.x.toFloat(), a.y.toFloat(), "${names[k]} ${labels[k]} ${Fmt.deg(v)}", Maps.inclineColor(ratio(i), trip.cautionPct, theme.caution), stack)
        }
        if (cursor in 0 until s.size && s.hasPos(cursor)) {                               // instante seleccionado en el gráfico
            pr.toPixels(geo(cursor), a)
            p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawCircle(a.x.toFloat(), a.y.toFloat(), 13 * d, p)
            p.color = accent; c.drawCircle(a.x.toFloat(), a.y.toFloat(), 9 * d, p)
        }
    }

    private fun marker(c: Canvas, x: Float, y: Float, t: String, col: Int, stack: Int) {
        p.style = Paint.Style.FILL; p.textSize = 13 * d; p.textAlign = Paint.Align.CENTER
        val w = p.measureText(t) + 14 * d; val h = 24 * d; val by = y - 14 * d - h - stack * (h + 4 * d)
        p.color = Color.argb(230, 20, 20, 22); c.drawRoundRect(x - w / 2, by, x + w / 2, by + h, 6 * d, 6 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = col; c.drawRoundRect(x - w / 2, by, x + w / 2, by + h, 6 * d, 6 * d, p)
        c.drawLine(x, by + h, x, y, p)
        p.style = Paint.Style.FILL; c.drawCircle(x, y, 5 * d, p)
        p.color = Color.WHITE; c.drawText(t, x, by + h * 0.7f, p)
    }

    /** Tocar el recorrido selecciona el instante más cercano (a menos de 40 dp). */
    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val pr = map.projection; var best = -1; var bd = (40 * d) * (40 * d)
        for (i in 0 until s.size) if (s.hasPos(i)) {
            pr.toPixels(geo(i), a); val dx = a.x - e.x; val dy = a.y - e.y; val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = i }
        }
        if (best < 0) return false
        onTap?.invoke(best); return true
    }
}

/** Puntos críticos de todas las salidas, coloreados y con tamaño según la inclinación. */
class HotspotOverlay(ctx: Context, private val pts: List<Hotspot>, private val theme: Theme) : Overlay() {
    var selected: Hotspot? = null
    var onTap: ((Hotspot) -> Unit)? = null
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val a = Point()

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val pr = map.projection
        for (h in pts) {
            pr.toPixels(GeoPoint(h.lat, h.lon), a)
            val r = (6 + 6 * (h.ratio - Config.cautionPct).coerceIn(0f, 1f) / (1.2f - Config.cautionPct)) * d
            p.style = Paint.Style.FILL; p.color = Maps.inclineColor(h.ratio, Config.cautionPct, theme.caution); p.alpha = 210
            c.drawCircle(a.x.toFloat(), a.y.toFloat(), r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = Color.argb(200, 0, 0, 0); c.drawCircle(a.x.toFloat(), a.y.toFloat(), r, p)
        }
        selected?.let { h ->
            pr.toPixels(GeoPoint(h.lat, h.lon), a)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Color.WHITE; c.drawCircle(a.x.toFloat(), a.y.toFloat(), 16 * d, p)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val pr = map.projection; var best: Hotspot? = null; var bd = (36 * d) * (36 * d)
        for (h in pts) { pr.toPixels(GeoPoint(h.lat, h.lon), a); val dx = a.x - e.x; val dy = a.y - e.y; val dd = dx * dx + dy * dy; if (dd < bd) { bd = dd; best = h } }
        val h = best ?: return false
        selected = h; map.invalidate(); onTap?.invoke(h); return true
    }
}
