package dev.arklay.inclinometro

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Gráfico de cabeceo y alabeo en el tiempo, con las líneas de límite y las zonas de peligro sombreadas.
 *  Al tocar o arrastrar marca un instante y avisa con [onCursor]. */
@SuppressLint("ViewConstructor")
class TripChartView(ctx: Context, private val s: Samples, private val trip: Trip, private val theme: Theme, private val accent: Int) : View(ctx) {
    var cursor = -1
        set(v) { field = v; invalidate() }
    var onCursor: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rollColor = Color.rgb(235, 235, 238)
    private val grid = Color.rgb(64, 64, 70)
    private val t0 = if (s.size > 0) s.t[0] else 0L
    private val span = if (s.size > 1) max(1L, s.t[s.size - 1] - t0) else 1L
    private val yMax: Float
    private val padL = 44 * d; private val padR = 10 * d; private val padT = 30 * d; private val padB = 24 * d

    init {
        var m = max(trip.limitPitch, trip.limitRoll)
        for (i in 0 until s.size) m = max(m, max(abs(s.pitch[i]), abs(s.roll[i])))
        yMax = max(10f, ceil(m * 1.15f / 10f) * 10f)
    }

    private fun x(i: Int) = padL + (width - padL - padR) * (s.t[i] - t0).toFloat() / span
    private fun y(v: Float) = padT + (height - padT - padB) * (0.5f - v.coerceIn(-yMax, yMax) / (2 * yMax))
    private fun danger(i: Int) = abs(s.pitch[i]) >= trip.limitPitch || abs(s.roll[i]) >= trip.limitRoll

    override fun onDraw(c: Canvas) {
        val top = padT; val bot = height - padB; val left = padL; val right = width - padR
        p.style = Paint.Style.FILL; p.color = Color.rgb(14, 14, 16); c.drawRect(left, top, right, bot, p)
        if (s.size < 2) { text(c, "Sin datos suficientes", (left + right) / 2, (top + bot) / 2, 14 * d, theme.ui.muted, Paint.Align.CENTER); return }

        // zonas de peligro, sombreadas en todo el alto
        p.color = Color.argb(110, Color.red(theme.danger), Color.green(theme.danger), Color.blue(theme.danger))
        var i = 0
        while (i < s.size) {
            if (!danger(i)) { i++; continue }
            val a = i; while (i < s.size && danger(i)) i++
            c.drawRect(x(a), top, max(x(min(i, s.size - 1)), x(a) + 2 * d), bot, p)
        }
        // rejilla cada 10°
        p.style = Paint.Style.STROKE; p.strokeWidth = 1 * d
        var g = -yMax
        while (g <= yMax + 0.1f) {
            p.color = if (g == 0f) Color.rgb(130, 130, 136) else grid; c.drawLine(left, y(g), right, y(g), p)
            text(c, if (g > 0) "+${g.toInt()}°" else "${g.toInt()}°", left - 6 * d, y(g) + 4 * d, 11 * d, theme.ui.muted, Paint.Align.RIGHT)
            g += 10f
        }
        // líneas de límite (discontinuas, del color de cada serie)
        p.pathEffect = DashPathEffect(floatArrayOf(8 * d, 6 * d), 0f); p.strokeWidth = 1.5f * d
        p.color = accent; c.drawLine(left, y(trip.limitPitch), right, y(trip.limitPitch), p); c.drawLine(left, y(-trip.limitPitch), right, y(-trip.limitPitch), p)
        p.color = rollColor; c.drawLine(left, y(trip.limitRoll), right, y(trip.limitRoll), p); c.drawLine(left, y(-trip.limitRoll), right, y(-trip.limitRoll), p)
        p.pathEffect = null
        // series: por cada columna de píxeles, mínimo y máximo, así no se pierden los picos en salidas largas
        series(c, s.pitch, accent); series(c, s.roll, rollColor)
        // eje de tiempo y leyenda
        text(c, Fmt.time(s.t[0]).substring(0, 5), left, height - 6 * d, 11 * d, theme.ui.muted, Paint.Align.LEFT)
        text(c, Fmt.time(s.t[s.size - 1]).substring(0, 5), right, height - 6 * d, 11 * d, theme.ui.muted, Paint.Align.RIGHT)
        text(c, Recorder.clock(span), (left + right) / 2, height - 6 * d, 11 * d, theme.ui.muted, Paint.Align.CENTER)
        legend(c, left, 18 * d)
        // instante seleccionado
        if (cursor in 0 until s.size) {
            val cx = x(cursor); p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = Color.WHITE; c.drawLine(cx, top, cx, bot, p)
            p.style = Paint.Style.FILL; p.color = accent; c.drawCircle(cx, y(s.pitch[cursor]), 5 * d, p); p.color = rollColor; c.drawCircle(cx, y(s.roll[cursor]), 5 * d, p)
        }
    }

    private fun series(c: Canvas, v: FloatArray, color: Int) {
        val path = Path(); val cols = max(1, (width - padL - padR).toInt()); var i = 0; var first = true
        val per = max(1, s.size / cols)
        while (i < s.size) {
            var lo = v[i]; var hi = v[i]; val end = min(s.size, i + per)
            for (j in i until end) { lo = min(lo, v[j]); hi = max(hi, v[j]) }
            val xx = x(i)
            if (first) { path.moveTo(xx, y(v[i])); first = false }
            if (per == 1) path.lineTo(xx, y(v[i])) else { path.lineTo(xx, y(hi)); path.lineTo(xx, y(lo)) }
            i = end
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = color; p.strokeJoin = Paint.Join.ROUND; c.drawPath(path, p)
    }

    private fun legend(c: Canvas, x0: Float, yy: Float) {
        var x = x0; val sz = 12 * d
        for ((label, col) in listOf("Cabeceo" to accent, "Alabeo" to rollColor)) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = col; c.drawLine(x, yy - 4 * d, x + 18 * d, yy - 4 * d, p)
            text(c, label, x + 24 * d, yy, sz, theme.ui.text, Paint.Align.LEFT); x += 24 * d + p.measureText(label) + 18 * d
        }
        p.style = Paint.Style.FILL; p.color = Color.argb(160, Color.red(theme.danger), Color.green(theme.danger), Color.blue(theme.danger))
        c.drawRect(x, yy - 11 * d, x + 18 * d, yy + 1 * d, p); text(c, "Peligro", x + 24 * d, yy, sz, theme.ui.text, Paint.Align.LEFT); x += 24 * d + p.measureText("Peligro") + 18 * d
        p.pathEffect = DashPathEffect(floatArrayOf(5 * d, 4 * d), 0f); p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = theme.ui.muted
        c.drawLine(x, yy - 4 * d, x + 18 * d, yy - 4 * d, p); p.pathEffect = null; text(c, "Límite", x + 24 * d, yy, sz, theme.ui.text, Paint.Align.LEFT)
    }

    private fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align) {
        p.style = Paint.Style.FILL; p.color = color; p.textSize = size; p.textAlign = align; c.drawText(t, x, y, p)
    }

    /** Instante más cercano a la coordenada x (búsqueda binaria por tiempo). */
    private fun indexAt(px: Float): Int {
        val target = t0 + (((px - padL) / (width - padL - padR)).coerceIn(0f, 1f) * span).toLong()      // en Long: un Float pierde segundos con marcas de tiempo tan grandes
        var lo = 0; var hi = s.size - 1
        while (lo < hi) { val m = (lo + hi) / 2; if (s.t[m] < target) lo = m + 1 else hi = m }
        return if (lo > 0 && abs(s.t[lo - 1] - target) < abs(s.t[lo] - target)) lo - 1 else lo
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (s.size < 2) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val i = indexAt(e.x); if (i != cursor) { cursor = i; onCursor?.invoke(i) }
            }
        }
        return true
    }
}
