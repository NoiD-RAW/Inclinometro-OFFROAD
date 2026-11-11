package dev.arklay.inclinometro.themes.topo

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Brújula con el rumbo del GPS. El mapa de curvas de nivel y la rosa giran; el vehículo (vista superior) queda fijo. */
class CompassView(ctx: Context) : View(ctx) {
    private var heading = 0f
    private var hasHeading = false
    private var kmh = 0f
    private var moving = false
    private var altitude: Float? = null
    var accent: Int = Color.rgb(63, 208, 178)
        set(v) { field = v; invalidate() }
    var vehicle: Bitmap? = null
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cards = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

    fun update(h: Float?, speedKmh: Float, mov: Boolean, alt: Float?) { if (h != null) { heading = h; hasHeading = true }; kmh = speedKmh; moving = mov; altitude = alt; invalidate() }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, outline: Boolean = false) {
        p.textSize = size; p.textAlign = Paint.Align.CENTER
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (outline) { p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = Color.BLACK; c.drawText(s, x, y, p) }
        p.style = Paint.Style.FILL; p.color = color; c.drawText(s, x, y, p)
    }
    private fun minorColor(): Int { val h = FloatArray(3); Color.colorToHSV(accent, h); h[2] *= 0.58f; return Color.HSVToColor(h) }

    override fun onDraw(c: Canvas) {
        val sc = min(width / 190f, height / 214f)                               // mismo lienzo que los relojes de inclinación
        c.save(); c.translate((width - 190 * sc) / 2, (height - 214 * sc) / 2); c.scale(sc, sc)
        val live = hasHeading && moving
        val h = heading.roundToInt().mod(360)
        val label = if (hasHeading) "%03d° %s".format(h, cards[((heading + 22.5f) / 45f).toInt().mod(8)]) else "---°"
        text(c, label, 95f, 40f, 24f, if (live) accent else Color.rgb(140, 150, 155), true)

        p.style = Paint.Style.FILL; p.color = Color.BLACK; c.drawCircle(95f, 112f, 56f, p)
        p.style = Paint.Style.STROKE; p.color = Color.rgb(68, 68, 68); p.strokeWidth = 1f; c.drawCircle(95f, 112f, 56f, p)
        c.save(); c.clipPath(Path().apply { addCircle(95f, 112f, 56f, Path.Direction.CW) }); c.translate(95f, 112f); c.rotate(-heading)
        Topo.draw(c, p, accent, minorColor()); c.restore()

        c.save(); c.translate(95f, 112f); p.style = Paint.Style.STROKE; p.color = Color.WHITE
        for (d in 0 until 360 step 10) {
            val a = Math.toRadians((d - heading).toDouble()); val sx = sin(a).toFloat(); val sy = -cos(a).toFloat()
            val ln = if (d % 30 == 0) 8f else 4f; p.strokeWidth = if (d % 30 == 0) 2f else 1.2f
            c.drawLine(62f * sx, 62f * sy, (62f + ln) * sx, (62f + ln) * sy, p)
        }
        p.style = Paint.Style.FILL
        c.save(); c.rotate(-heading); p.color = Color.rgb(229, 57, 53)
        c.drawPath(Path().apply { moveTo(0f, -61f); lineTo(-4.5f, -69f); lineTo(4.5f, -69f); close() }, p); c.restore()    // norte
        p.color = Color.WHITE
        c.drawPath(Path().apply { moveTo(0f, -62f); lineTo(-5f, -72f); lineTo(5f, -72f); close() }, p)                       // rumbo (fijo)
        for ((d, t) in listOf(0 to "N", 90 to "E", 180 to "S", 270 to "O")) {
            val a = Math.toRadians((d - heading).toDouble())
            text(c, t, 47f * sin(a).toFloat(), -47f * cos(a).toFloat() + 5f, 14f, if (t == "N") Color.rgb(255, 90, 80) else Color.WHITE, true, true)
        }
        p.style = Paint.Style.FILL; p.color = Color.rgb(13, 14, 16); c.drawCircle(0f, 0f, 38f, p)
        p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = 2f; c.drawCircle(0f, 0f, 38f, p)
        c.restore()

        vehicle?.let { v ->                                                     // vista superior, ajustada al círculo
            val f = 62f / v.height                                              // alto fijo: el vehículo siempre ocupa lo mismo
            val fw = min(f, 62f / v.width)                                      // si fuese muy ancho, se limita al ancho
            c.save(); c.translate(95f, 112f); c.scale(fw, fw); p.isFilterBitmap = true; p.style = Paint.Style.FILL
            c.drawBitmap(v, -v.width / 2f, -v.height / 2f, p); c.restore()
        }
        val st = when { !hasHeading -> "esperando GPS"; !moving -> "parado"; else -> "${kmh.roundToInt()} km/h" }
        val foot = altitude?.let { "$st · ${it.roundToInt()} m" } ?: "Brújula · $st"          // con GPS, también la altitud
        text(c, foot, 95f, 192f, 13f, Color.rgb(207, 216, 220))
        c.restore()
    }
}
