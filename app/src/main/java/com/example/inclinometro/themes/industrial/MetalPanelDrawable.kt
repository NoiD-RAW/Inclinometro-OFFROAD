package com.example.inclinometro.themes.industrial

import com.example.inclinometro.R

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable

/** Fondo de panel metálico (marco, pantalla hundida y tornillos) con los mismos recursos que la pantalla principal. */
class MetalPanelDrawable(ctx: Context) : Drawable() {
    private val res = ctx.resources
    private fun bmp(id: Int) = BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })
    private val e = intArrayOf(R.drawable.panel_tl, R.drawable.panel_tr, R.drawable.panel_bl, R.drawable.panel_br,
        R.drawable.panel_top, R.drawable.panel_bottom, R.drawable.panel_left, R.drawable.panel_right).map { bmp(it) }
    private val screw = bmp(R.drawable.screw)
    private val tile = bmp(R.drawable.bg_metal)
    private val ui = (res.displayMetrics.density * 0.45f).coerceIn(0.4f, 1.3f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun draw(c: Canvas) {
        val r = RectF(bounds); val t = 14f * ui; val s = 44f * ui
        val d = RectF(r.left + t, r.top + t, r.right - t, r.bottom - t)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, d.top, 0f, d.bottom, Color.rgb(27, 27, 30), Color.rgb(14, 14, 16), Shader.TileMode.CLAMP); c.drawRect(d, p)
        val sh = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); sh.setLocalMatrix(Matrix().apply { setScale(ui, ui) })
        p.shader = sh; p.alpha = 60; c.drawRect(d, p); p.shader = null; p.alpha = 255
        c.drawBitmap(e[4], null, RectF(r.left + s, r.top, r.right - s, r.top + t), p)
        c.drawBitmap(e[5], null, RectF(r.left + s, r.bottom - t, r.right - s, r.bottom), p)
        c.drawBitmap(e[6], null, RectF(r.left, r.top + s, r.left + t, r.bottom - s), p)
        c.drawBitmap(e[7], null, RectF(r.right - t, r.top + s, r.right, r.bottom - s), p)
        c.drawBitmap(e[0], null, RectF(r.left, r.top, r.left + s, r.top + s), p)
        c.drawBitmap(e[1], null, RectF(r.right - s, r.top, r.right, r.top + s), p)
        c.drawBitmap(e[2], null, RectF(r.left, r.bottom - s, r.left + s, r.bottom), p)
        c.drawBitmap(e[3], null, RectF(r.right - s, r.bottom - s, r.right, r.bottom), p)
        val ix = 37f * ui; val iy = 35f * ui; val sr = 20f * ui
        for (fx in 0..1) for (fy in 0..1) {
            val cx = if (fx == 0) r.left + ix else r.right - ix; val cy = if (fy == 0) r.top + iy else r.bottom - iy
            c.drawBitmap(screw, null, RectF(cx - sr, cy - sr, cx + sr, cy + sr), p)
        }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
