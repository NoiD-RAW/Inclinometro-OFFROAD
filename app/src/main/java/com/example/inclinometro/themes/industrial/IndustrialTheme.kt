package com.example.inclinometro.themes.industrial

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Rational
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.inclinometro.Dash
import com.example.inclinometro.DashActions
import com.example.inclinometro.R
import com.example.inclinometro.Theme
import com.example.inclinometro.ThemeUi

/** Tema "Industrial": acero cepillado, tornillos y biseles (antes, la app Inclinómetro Offroad de cuatro relojes). */
object IndustrialTheme : Theme {
    override val id = "industrial"
    override val name = "Industrial"
    override val defaultAccent = Color.rgb(255, 122, 26)
    override val accentPresets = intArrayOf(Color.rgb(255, 122, 26), Color.rgb(230, 57, 53), Color.rgb(255, 196, 30), Color.rgb(76, 200, 80),
        Color.rgb(0, 200, 220), Color.rgb(45, 130, 255), Color.rgb(150, 90, 255), Color.rgb(255, 80, 160))
    override val pipAspect = Rational(16, 9)
    override val vehicleKinds = listOf("side", "rear")
    override val caution = Color.rgb(255, 184, 0)
    override val danger = Color.rgb(170, 18, 18)
    override fun createDash(ctx: Context, actions: DashActions): Dash = DashView(ctx, actions)

    override val ui = object : ThemeUi {
        override val background = Color.rgb(16, 16, 18)
        override val text = Color.rgb(235, 235, 238)
        override val muted = Color.rgb(165, 165, 171)
        private var font: Typeface? = null
        private var fontLoaded = false
        /** Gunplay si el archivo está en assets/fonts/; si no, negrita del sistema. */
        private fun TextView.titleFont() {
            if (!fontLoaded) {
                fontLoaded = true
                for (n in arrayOf("fonts/gunplay.ttf", "fonts/gunplay.otf", "fonts/Gunplay.ttf", "fonts/Gunplay.otf")) { if (font == null) try { font = Typeface.createFromAsset(context.assets, n) } catch (_: Exception) {} }
            }
            val f = font; if (f != null) setTypeface(f) else setTypeface(null, Typeface.BOLD)
        }
        private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
        private fun shade(c: Int, sk: Float, vk: Float): Int { val a = FloatArray(3); Color.colorToHSV(c, a); a[1] = (a[1] * sk).coerceIn(0f, 1f); a[2] = (a[2] * vk).coerceIn(0f, 1f); return Color.HSVToColor(a) }

        override fun backgroundDrawable(ctx: Context): Drawable =
            BitmapDrawable(ctx.resources, BitmapFactory.decodeResource(ctx.resources, R.drawable.bg_metal)).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        override fun label(ctx: Context, t: String, size: Float, color: Int) = TextView(ctx).apply {
            text = t.uppercase(); textSize = size; setTextColor(color); titleFont(); setPadding(0, dp(ctx, 2), 0, dp(ctx, 8))
        }
        override fun note(ctx: Context, t: String) = TextView(ctx).apply {
            text = t.uppercase(); textSize = 11f; setTextColor(muted); titleFont(); setPadding(0, dp(ctx, 6), 0, dp(ctx, 4))
        }
        override fun button(ctx: Context, t: String, filled: Boolean, accent: Int, onClick: () -> Unit) = Button(ctx).apply {
            text = t.uppercase(); isAllCaps = false; titleFont()
            setTextColor(if (filled) Color.rgb(30, 20, 10) else Color.rgb(235, 235, 238))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                if (filled) intArrayOf(shade(accent, 0.62f, 1.1f), shade(accent, 1f, 0.7f)) else intArrayOf(Color.rgb(66, 66, 71), Color.rgb(30, 30, 33))).apply {
                cornerRadius = dp(ctx, 12).toFloat(); setStroke(dp(ctx, 2), if (filled) Color.rgb(8, 8, 10) else Color.rgb(96, 96, 102))
            }
            setOnClickListener { onClick() }
        }
        /** Panel metálico (marco, pantalla hundida y tornillos) con su título. */
        override fun section(ctx: Context, title: String, accent: Int, views: List<View>) = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; background = MetalPanelDrawable(ctx); setPadding(dp(ctx, 26), dp(ctx, 26), dp(ctx, 26), dp(ctx, 24))
            addView(label(ctx, title, 15f, accent))
            for (v in views) addView(v, v.layoutParams ?: LinearLayout.LayoutParams(-1, -2))
        }
    }
}
