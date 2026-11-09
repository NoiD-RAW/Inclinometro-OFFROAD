package com.example.inclinometro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Controles comunes de la pantalla principal (iconos de ajustes e historial), para que se vean y respondan igual en todos los temas. */
object Chrome {
    /** Área táctil mínima recomendada: 48 dp. */
    fun minTouch(ctx: Context) = 48f * ctx.resources.displayMetrics.density

    /** Rueda dentada de 8 dientes con agujero central, de radio exterior [r]. */
    fun drawGear(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, p: Paint) {
        val teeth = 8; val ri = r * 0.74f; val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
        for (i in 0 until teeth * 4) {                                     // cada diente: base, punta, punta, base
            val a = 2 * PI * (i - 1.5) / (teeth * 4)
            val rr = if (i % 4 == 1 || i % 4 == 2) r else ri
            val x = cx + rr * sin(a).toFloat(); val y = cy - rr * cos(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); path.addCircle(cx, cy, r * 0.32f, Path.Direction.CW)
        p.style = Paint.Style.FILL; p.shader = null; p.color = color; c.drawPath(path, p)
    }

    /** Márgenes que la vista [v] debe dejar libres por la barra de estado, la de navegación o el notch (0 si ya no los toca). */
    fun safeInsets(v: View): Rect {
        val ins = v.rootWindowInsets ?: return Rect()
        @Suppress("DEPRECATION") var l = ins.systemWindowInsetLeft
        @Suppress("DEPRECATION") var t = ins.systemWindowInsetTop
        @Suppress("DEPRECATION") var r = ins.systemWindowInsetRight
        @Suppress("DEPRECATION") var b = ins.systemWindowInsetBottom
        if (Build.VERSION.SDK_INT >= 28) ins.displayCutout?.let { l = max(l, it.safeInsetLeft); t = max(t, it.safeInsetTop); r = max(r, it.safeInsetRight); b = max(b, it.safeInsetBottom) }
        val loc = IntArray(2); v.getLocationInWindow(loc); val root = v.rootView
        return Rect(max(0, l - loc[0]), max(0, t - loc[1]), max(0, r - (root.width - loc[0] - v.width)), max(0, b - (root.height - loc[1] - v.height)))
    }
}

/** Botón de icono (rueda dentada…) para temas hechos con vistas. Nunca más pequeño que 48×48 dp. */
class IconButtonView(ctx: Context, private val draw: (Canvas, Float, Float, Float, Paint) -> Unit, description: String, onClick: () -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        contentDescription = description; isClickable = true; setOnClickListener { onClick() }
        minimumWidth = Chrome.minTouch(ctx).toInt(); minimumHeight = Chrome.minTouch(ctx).toInt()
    }
    override fun onDraw(c: Canvas) {
        val r = 0.36f * minOf(width, height) * (if (isPressed) 0.9f else 1f)
        draw(c, width / 2f, height / 2f, r, p)
    }
    override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }
}
