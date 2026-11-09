package com.example.inclinometro.themes.topo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.inclinometro.Chrome
import com.example.inclinometro.Dash
import com.example.inclinometro.IconButtonView
import com.example.inclinometro.DashActions
import com.example.inclinometro.DashState
import com.example.inclinometro.Theme
import com.example.inclinometro.ThemeUi

/** Tema "Topográfico": tres relojes sobre curvas de nivel (antes, la app Inclinómetro 3 Relojes). */
object TopoTheme : Theme {
    override val id = "topo"
    override val name = "Topográfico"
    override val defaultAccent = Color.rgb(63, 208, 178)
    override val accentPresets = intArrayOf(Color.rgb(63, 208, 178), Color.rgb(255, 122, 26), Color.rgb(230, 57, 53), Color.rgb(255, 196, 30),
        Color.rgb(76, 200, 80), Color.rgb(45, 130, 255), Color.rgb(150, 90, 255), Color.rgb(255, 80, 160))
    override val pipAspect = Rational(7, 3)          // tres relojes en fila, dentro del límite del sistema
    override val caution = Color.rgb(255, 170, 40)
    override val danger = Color.rgb(160, 22, 22)
    override fun createDash(ctx: Context, actions: DashActions): Dash = TopoDash(ctx, actions)

    override val ui = object : ThemeUi {
        override val background = Color.rgb(16, 16, 18)
        override val text = Color.rgb(235, 235, 238)
        override val muted = Color.rgb(165, 170, 176)
        private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
        override fun backgroundDrawable(ctx: Context): Drawable = ColorDrawable(background)
        override fun label(ctx: Context, t: String, size: Float, color: Int) = TextView(ctx).apply {
            text = t; textSize = size; setTextColor(color); setTypeface(null, Typeface.BOLD); setPadding(0, dp(ctx, 16), 0, dp(ctx, 4))
        }
        override fun note(ctx: Context, t: String) = TextView(ctx).apply { text = t; textSize = 13f; setTextColor(muted); setPadding(0, 0, 0, dp(ctx, 8)) }
        override fun button(ctx: Context, t: String, filled: Boolean, accent: Int, onClick: () -> Unit) = Button(ctx).apply {
            text = t; isAllCaps = false; setTypeface(null, Typeface.BOLD)
            setTextColor(if (filled) Color.rgb(14, 22, 24) else Color.rgb(235, 235, 238))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 12).toFloat()
                if (filled) setColor(accent) else { setColor(Color.rgb(34, 36, 40)); setStroke(dp(ctx, 2), accent) }
            }
            setOnClickListener { onClick() }
        }
        override fun section(ctx: Context, title: String, accent: Int, views: List<View>) = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(ctx, title.uppercase(), 14f, accent))
            for (v in views) addView(v, v.layoutParams ?: LinearLayout.LayoutParams(-1, -2))
        }
    }
}

/** Pantalla principal: barra superior con la rueda de Ajustes, cabeceo, brújula y alabeo en fila o en columna (según el espacio) y la fila de botones debajo. */
class TopoDash(ctx: Context, private val actions: DashActions) : LinearLayout(ctx), Dash {
    private val pitchView = GaugeView(ctx, "Cabeceo", false)
    private val rollView = GaugeView(ctx, "Alabeo", true)
    private val compassView = CompassView(ctx)
    private val gauges = LinearLayout(ctx).apply { addView(pitchView); addView(compassView); addView(rollView) }
    private val buttonRow = LinearLayout(ctx)
    private val topBar = LinearLayout(ctx)
    private val gear = IconButtonView(ctx, { c, x, y, r, p -> Chrome.drawGear(c, x, y, r, accent, p) }, "Ajustes") { actions.onSettings() }
    override val view: View get() = this
    override var accent: Int = TopoTheme.defaultAccent
        set(v) { field = v; pitchView.accent = v; rollView.accent = v; compassView.accent = v; gear.invalidate(); buildButtons() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL; setBackgroundColor(TopoTheme.ui.background)
        buttonRow.apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        topBar.apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL or Gravity.END; addView(gear, LayoutParams(dp(56), dp(56))) }
        addView(topBar, LayoutParams(-1, -2)); addView(gauges, LayoutParams(-1, 0, 1f)); addView(buttonRow, LayoutParams(-1, -2))
        applyLayout(true); accent = TopoTheme.defaultAccent
        gauges.addOnLayoutChangeListener { _, l, t, r, bt, _, _, _, _ ->           // en pantalla dividida elige fila o columna
            val w = (r - l).toFloat(); val h = (bt - t).toFloat()
            if (w > 0 && h > 0) {
                val horizontal = minOf(w / 3f / 190f, h / 200f) >= minOf(w / 190f, h / 3f / 200f)
                if ((gauges.orientation == HORIZONTAL) != horizontal) gauges.post { applyLayout(horizontal) }
            }
        }
    }
    override fun onMeasure(wSpec: Int, hSpec: Int) {                               // para las miniaturas, que no pasan por el post()
        val w = MeasureSpec.getSize(wSpec).toFloat(); val h = MeasureSpec.getSize(hSpec).toFloat()
        if (w > 0 && h > 0) { val horizontal = minOf(w / 3f / 190f, h / 200f) >= minOf(w / 190f, h / 3f / 200f); if ((gauges.orientation == HORIZONTAL) != horizontal) applyLayout(horizontal) }
        super.onMeasure(wSpec, hSpec)
    }

    private fun buildButtons() {
        buttonRow.removeAllViews()
        val ui = TopoTheme.ui
        buttonRow.addView(ui.button(context, "Calibrar a 0°", true, accent) { actions.onZero() }, LayoutParams(0, dp(48), 1f).apply { setMargins(dp(8), dp(4), dp(8), dp(8)) })
    }
    private fun applyLayout(horizontal: Boolean) {
        gauges.orientation = if (horizontal) HORIZONTAL else VERTICAL
        for (v in listOf(pitchView, compassView, rollView))
            v.layoutParams = if (horizontal) LayoutParams(0, -1, 1f) else LayoutParams(-1, 0, 1f)
    }

    override fun setVehicle(side: Bitmap, rear: Bitmap, top: Bitmap) { pitchView.vehicle = side; rollView.vehicle = rear; compassView.vehicle = top }
    override fun update(s: DashState) {
        pitchView.angle = s.pitch; rollView.angle = s.roll
        compassView.update(s.heading, s.kmh, s.gps && s.kmh >= 4f, s.altitude)
    }
    override fun setPip(pip: Boolean) { val v = if (pip) View.GONE else View.VISIBLE; buttonRow.visibility = v; topBar.visibility = v }
}
