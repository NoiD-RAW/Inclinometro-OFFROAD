package dev.arklay.inclinometro.themes.topo

import android.content.Context
import android.graphics.*
import android.view.View
import dev.arklay.inclinometro.Alerts
import kotlin.math.*

/** Reloj de inclinación (cabeceo o alabeo). Se dibuja en un lienzo de 190x214 unidades que se escala a la vista. */
class GaugeView(ctx: Context, private val title: String, private val rear: Boolean) : View(ctx) {
    var angle = 0f
        set(v) { field = v.coerceIn(-40f, 40f); invalidate() }                // la escala llega a ±40°
    var accent: Int = Color.rgb(63, 208, 178)                                 // color de las curvas de nivel
        set(v) { field = v; invalidate() }
    var vehicle: Bitmap? = null
        set(v) { field = v; invalidate() }
    /** Nivel de aviso (Alerts.OK / CAUTION / DANGER) y color ámbar del tema. */
    var level = Alerts.OK
    var caution = Color.rgb(255, 170, 40)
    /** Máximos registrados: lado negativo y positivo de la escala (grados positivos). */
    var maxLo = 0f; var maxHi = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface = Typeface.DEFAULT) {
        p.style = Paint.Style.FILL; p.color = color; p.textSize = size; p.textAlign = Paint.Align.CENTER; p.typeface = tf; c.drawText(s, x, y, p)
    }
    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, sw: Float) {
        p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = sw; c.drawLine(x1, y1, x2, y2, p)
    }
    private fun minorColor(): Int { val h = FloatArray(3); Color.colorToHSV(accent, h); h[2] *= 0.58f; return Color.HSVToColor(h) }

    override fun onDraw(c: Canvas) {
        val sc = min(width / 190f, height / 214f)
        c.save(); c.translate((width - 190 * sc) / 2, (height - 214 * sc) / 2); c.scale(sc, sc)
        val col = when (level) { Alerts.DANGER -> Color.WHITE; Alerts.CAUTION -> caution; else -> Color.rgb(80, 200, 120) }   // en peligro el fondo es rojo: cifra en blanco
        val n = angle.roundToInt()
        text(c, "${if (rear) abs(n) else n}°", 95f, 40f, 24f, col, mono)      // el alabeo se muestra siempre en positivo
        p.style = Paint.Style.FILL; p.color = Color.BLACK; c.drawCircle(95f, 112f, 56f, p)
        p.style = Paint.Style.STROKE; p.color = Color.rgb(68, 68, 68); p.strokeWidth = 1f; c.drawCircle(95f, 112f, 56f, p)
        if (level != Alerts.OK) { p.color = if (level == Alerts.DANGER) Color.WHITE else caution; p.strokeWidth = 4f; c.drawCircle(95f, 112f, 57f, p) }   // aro de aviso

        c.save(); c.clipPath(Path().apply { addCircle(95f, 112f, 56f, Path.Direction.CW) }); c.translate(95f, 112f)
        Topo.draw(c, p, accent, minorColor()); c.restore()                    // curvas de nivel fijas, en el color elegido

        val tilt = if (rear) -angle else angle
        c.save(); c.translate(95f, 112f)
        p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = 2f
        c.drawArc(RectF(-62f, -62f, 62f, 62f), -45f, 90f, false, p); c.drawArc(RectF(-62f, -62f, 62f, 62f), 135f, 90f, false, p)
        for (d in -40..40 step 5) {
            val r = Math.toRadians(d.toDouble()); val len = if (d % 10 == 0) 8f else 4f; val sw = if (d % 10 == 0) 2f else 1.2f
            for (s in intArrayOf(1, -1)) line(c, s * 62f * cos(r).toFloat(), -62f * sin(r).toFloat(), s * (62f + len) * cos(r).toFloat(), -(62f + len) * sin(r).toFloat(), Color.WHITE, sw)
        }
        for (s in intArrayOf(1, -1)) {
            text(c, "40", s * 60.5f, -47f, 11f, Color.WHITE); text(c, "20", s * 74.2f, -23f, 11f, Color.WHITE)
            text(c, "20", s * 74.2f, 31f, 11f, Color.WHITE); text(c, "40", s * 60.5f, 55f, 11f, Color.WHITE)
        }
        marker(c, (-tilt).coerceIn(-45f, 45f), false); marker(c, tilt.coerceIn(-45f, 45f), true)
        p.style = Paint.Style.FILL; p.color = Color.rgb(13, 14, 16); c.drawCircle(0f, 0f, 38f, p)
        p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = 2f; c.drawCircle(0f, 0f, 38f, p)
        line(c, -34f, 0f, 34f, 0f, Color.rgb(230, 255, 250), 1.5f)             // horizonte de referencia
        c.restore()

        vehicle?.let { v ->                                                     // el vehículo se inclina los mismos grados
            val f = 35f / hypot(v.width / 2f, v.height / 2f)                    // encaja siempre dentro del círculo, giro incluido
            c.save(); c.translate(95f, 112f); c.rotate(tilt); c.scale(f, f); p.isFilterBitmap = true; p.style = Paint.Style.FILL
            c.drawBitmap(v, -v.width / 2f, -v.height / 2f, p); c.restore()
        }
        val t = when (level) { Alerts.DANGER -> "$title · PELIGRO"; Alerts.CAUTION -> "$title · PRECAUCIÓN"; else -> title }
        text(c, t, 95f, 192f, 13f, if (level == Alerts.OK) Color.rgb(207, 216, 220) else col, if (level == Alerts.OK) Typeface.DEFAULT else Typeface.DEFAULT_BOLD)
        val mx = if (rear) "máx  izq ${maxLo.roundToInt()}° · der ${maxHi.roundToInt()}°" else "máx  -${maxLo.roundToInt()}° · +${maxHi.roundToInt()}°"
        text(c, mx, 95f, 209f, 11f, if (level == Alerts.DANGER) Color.rgb(240, 220, 220) else Color.rgb(140, 150, 155))   // máximos, discretos
        c.restore()
    }

    private fun marker(c: Canvas, deg: Float, mirror: Boolean) {
        c.save(); if (mirror) c.scale(-1f, 1f); c.rotate(-deg)
        p.style = Paint.Style.FILL; p.color = Color.rgb(229, 57, 53)
        c.drawPath(Path().apply { moveTo(61f, 0f); lineTo(69f, -4.5f); lineTo(69f, 4.5f); close() }, p); c.restore()
    }
}
