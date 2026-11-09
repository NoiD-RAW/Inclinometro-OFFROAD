package com.example.inclinometro

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import java.util.Random
import kotlin.math.*

/** Pantalla principal con estética industrial metálica (paneles de acero cepillado, tornillos, biseles).
 *  Se reorganiza sola: vertical, horizontal o minivista (PiP / ventana pequeña). El color principal es configurable. */
class DashView(ctx: Context) : View(ctx) {
    private var roll = 0f; private var pitch = 0f
    private var heading: Float? = null; private var altitude: Float? = null
    private var side: Bitmap? = null; private var rear: Bitmap? = null
    var onZero: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    /** Color principal (por defecto naranja). Solo cambia las partes que eran naranjas. */
    var accent: Int = Color.rgb(255, 122, 26)
        set(v) { field = v; invalidate() }

    fun update(r: Float, pi: Float, h: Float?, alt: Float?) { roll = r; pitch = pi; heading = h; altitude = alt; invalidate() }
    fun setVehicle(s: Bitmap?, r: Bitmap?) { side = s; rear = r; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cond = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    /** Fuente de los rótulos: Gunplay si el archivo está en assets/fonts/ (gunplay.ttf o gunplay.otf); si no, la condensada del sistema. */
    private val titleFont: Typeface by lazy {
        var f: Typeface? = null
        for (n in arrayOf("fonts/gunplay.ttf", "fonts/gunplay.otf", "fonts/Gunplay.ttf", "fonts/Gunplay.otf")) { if (f == null) try { f = Typeface.createFromAsset(context.assets, n) } catch (_: Exception) {} }
        f ?: cond
    }
    private val cards = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    private val zeroC = PointF(); private var zeroR = 0f
    private val menuRect = RectF()
    private val silverTop = Color.rgb(248, 248, 252); private val silverBot = Color.rgb(158, 158, 166)
    private val clamp = Shader.TileMode.CLAMP

    private fun hsv(c: Int, sk: Float, vk: Float): Int { val a = FloatArray(3); Color.colorToHSV(c, a); a[1] = (a[1] * sk).coerceIn(0f, 1f); a[2] = (a[2] * vk).coerceIn(0f, 1f); return Color.HSVToColor(a) }
    private val aLight get() = hsv(accent, 0.62f, 1.1f)
    private val aDark get() = hsv(accent, 1f, 0.70f)

    // ---------- recursos gráficos extraídos del diseño original (res/drawable-nodpi) ----------
    private fun bmp(id: Int): Bitmap = BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false })
    private val bgTile by lazy { bmp(R.drawable.bg_metal) }
    private val aEdges by lazy { arrayOf(bmp(R.drawable.panel_tl), bmp(R.drawable.panel_tr), bmp(R.drawable.panel_bl), bmp(R.drawable.panel_br),
        bmp(R.drawable.panel_top), bmp(R.drawable.panel_bottom), bmp(R.drawable.panel_left), bmp(R.drawable.panel_right)) }
    private val aScrew by lazy { bmp(R.drawable.screw) }
    private val aPill by lazy { bmp(R.drawable.pill) }
    private val aZero by lazy { bmp(R.drawable.btn_zero) }
    private val aCompass by lazy { bmp(R.drawable.compass_face) }
    private val aBadge by lazy { bmp(R.drawable.alt_badge) }
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var ui = 1f                    // escala respecto al diseño original (1440 px de ancho)
    private var zeroDown = false

    /** Gira el tono de los gráficos naranjas (el gris metálico no se altera) hacia el color principal elegido. */
    private fun accentFilter(darker: Boolean): ColorMatrixColorFilter {
        val hsv = FloatArray(3); Color.colorToHSV(accent, hsv)
        val r = Math.toRadians((hsv[0] - 27f).toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
        val m = ColorMatrix(floatArrayOf(
            0.213f + 0.787f * c - 0.213f * s, 0.715f - 0.715f * c - 0.715f * s, 0.072f - 0.072f * c + 0.928f * s, 0f, 0f,
            0.213f - 0.213f * c + 0.143f * s, 0.715f + 0.285f * c + 0.140f * s, 0.072f - 0.072f * c - 0.283f * s, 0f, 0f,
            0.213f - 0.213f * c - 0.787f * s, 0.715f - 0.715f * c + 0.715f * s, 0.072f + 0.928f * c + 0.072f * s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f))
        if (darker) m.postConcat(ColorMatrix().apply { setScale(0.8f, 0.8f, 0.8f, 1f) })
        return ColorMatrixColorFilter(m)
    }
    private fun img(c: Canvas, b: Bitmap, cx: Float, cy: Float, scale: Float, f: ColorFilter? = null) {
        bp.colorFilter = f; c.drawBitmap(b, null, RectF(cx - b.width * scale / 2, cy - b.height * scale / 2, cx + b.width * scale / 2, cy + b.height * scale / 2), bp); bp.colorFilter = null
    }
    private fun tileFill(c: Canvas, r: RectF, rad: Float, alpha: Int) {
        val sh = BitmapShader(bgTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); sh.setLocalMatrix(Matrix().apply { setScale(ui, ui) })
        p.style = Paint.Style.FILL; p.shader = sh; p.alpha = alpha; c.drawRoundRect(r, rad, rad, p); p.shader = null; p.alpha = 255
    }

    // ---------- utilidades de dibujo ----------
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER, spacing: Float = 0f, tf: Typeface = titleFont) {
        p.style = Paint.Style.FILL; p.shader = null; p.color = color; p.textSize = size; p.textAlign = align; p.typeface = tf; p.letterSpacing = spacing
        c.drawText(s.uppercase(), x, y, p); p.letterSpacing = 0f
    }
    private fun metalText(c: Canvas, s: String, x: Float, y: Float, size: Float, top: Int, bot: Int, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f) {
        p.style = Paint.Style.FILL; p.textSize = size; p.textAlign = align; p.typeface = titleFont; p.letterSpacing = spacing
        p.shader = LinearGradient(0f, y - size * 0.75f, 0f, y, top, bot, clamp); c.drawText(s.uppercase(), x, y, p); p.shader = null; p.letterSpacing = 0f
    }
    private fun tw(s: String, size: Float): Float { p.typeface = titleFont; p.textSize = size; p.letterSpacing = 0f; return p.measureText(s.uppercase()) }
    private fun fit(s: String, size: Float, maxW: Float): Float { val w = tw(s, size); return if (w > maxW) size * maxW / w else size }
    private fun brush(c: Canvas, r: RectF, rad: Float, a: Int) = tileFill(c, r, rad, a)

    /** Panel de acero: marco con esquinas y bordes recortados del original, pantalla interior hundida y cuatro tornillos. */
    private fun metalPanel(c: Canvas, r: RectF) {
        val t = 14f * ui; val s = 44f * ui
        val d = RectF(r.left + t, r.top + t, r.right - t, r.bottom - t)
        p.style = Paint.Style.FILL; p.shader = LinearGradient(0f, d.top, 0f, d.bottom, Color.rgb(27, 27, 30), Color.rgb(14, 14, 16), clamp); c.drawRect(d, p); p.shader = null
        tileFill(c, d, 0f, 60)
        c.drawBitmap(aEdges[4], null, RectF(r.left + s, r.top, r.right - s, r.top + t), bp)
        c.drawBitmap(aEdges[5], null, RectF(r.left + s, r.bottom - t, r.right - s, r.bottom), bp)
        c.drawBitmap(aEdges[6], null, RectF(r.left, r.top + s, r.left + t, r.bottom - s), bp)
        c.drawBitmap(aEdges[7], null, RectF(r.right - t, r.top + s, r.right, r.bottom - s), bp)
        c.drawBitmap(aEdges[0], null, RectF(r.left, r.top, r.left + s, r.top + s), bp)
        c.drawBitmap(aEdges[1], null, RectF(r.right - s, r.top, r.right, r.top + s), bp)
        c.drawBitmap(aEdges[2], null, RectF(r.left, r.bottom - s, r.left + s, r.bottom), bp)
        c.drawBitmap(aEdges[3], null, RectF(r.right - s, r.bottom - s, r.right, r.bottom), bp)
        val ix = 37f * ui; val iy = 35f * ui; val sr = 20f * ui
        for (fx in 0..1) for (fy in 0..1) img(c, aScrew, if (fx == 0) r.left + ix else r.right - ix, if (fy == 0) r.top + iy else r.bottom - iy, 2f * sr / 48f)
    }

    private fun inset(c: Canvas, b: RectF) {
        p.style = Paint.Style.FILL; p.shader = LinearGradient(0f, b.top, 0f, b.bottom, Color.rgb(8, 8, 10), Color.rgb(26, 26, 29), clamp); c.drawRoundRect(b, 10f, 10f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = Color.rgb(78, 78, 84); c.drawRoundRect(b, 10f, 10f, p)
        p.strokeWidth = 1.5f; p.color = Color.BLACK; c.drawRoundRect(RectF(b.left + 2, b.top + 2, b.right - 2, b.bottom - 2), 8f, 8f, p)
    }

    // ---------- composición ----------
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val d = resources.displayMetrics.density
        ui = (min(w, h * 0.5625f) / 1440f).coerceAtLeast(0.2f)
        tileFill(c, RectF(0f, 0f, w, h), 0f, 255)
        zeroR = 0f; menuRect.setEmpty()
        val m0 = 0.015f * min(w, h)
        if (w / d < 300f || h / d < 220f) {                         // minivista: solo los dos relojes
            if (w >= h * 1.2f) { val gw = (w - 3 * m0) / 2; gauge(c, RectF(m0, m0, m0 + gw, h - m0), "ALABEO", "", roll, true, true); gauge(c, RectF(2 * m0 + gw, m0, w - m0, h - m0), "CABECEO", "", pitch, false, true) }
            else { val gh = (h - 3 * m0) / 2; gauge(c, RectF(m0, m0, w - m0, m0 + gh), "ALABEO", "", roll, true, true); gauge(c, RectF(m0, 2 * m0 + gh, w - m0, h - m0), "CABECEO", "", pitch, false, true) }
            return
        }
        if (h > w * 0.9f) {                                          // vertical: proporciones del diseño de referencia
            val m = 0.0222f * w; val hh = 0.0586f * h
            header(c, w, hh)
            gauge(c, RectF(m, 0.0695f * h, w - m, 0.3398f * h), "ALABEO", "", roll, true, false)
            gauge(c, RectF(m, 0.3535f * h, w - m, 0.623f * h), "CABECEO", "", pitch, false, false)
            compassCard(c, RectF(m, 0.636f * h, 0.492f * w, 0.779f * h)); altCard(c, RectF(0.515f * w, 0.636f * h, w - m, 0.779f * h))
            val y4 = 0.793f * h; val zh = 0.973f * h - y4
            val zr = min(zh / 2, 0.15f * w); if (zr > 20f) zero(c, w / 2, y4 + zh / 2, zr)
        } else {                                                     // horizontal
            val m = m0; val hh = 0.10f * h; val gh = 0.56f * h; val gw = (w - 3 * m) / 2
            header(c, w, hh)
            gauge(c, RectF(m, hh + m, m + gw, hh + m + gh), "ALABEO", "", roll, true, false)
            gauge(c, RectF(2 * m + gw, hh + m, w - m, hh + m + gh), "CABECEO", "", pitch, false, false)
            val y3 = hh + 2 * m + gh; val ch = h - y3 - m; val cw = 0.34f * w
            compassCard(c, RectF(m, y3, m + cw, y3 + ch)); altCard(c, RectF(2 * m + cw, y3, 2 * m + 2 * cw, y3 + ch))
            val zx0 = 3 * m + 2 * cw; val zr = min(ch / 2, (w - m - zx0) / 2)
            if (zr > 20f) zero(c, (zx0 + w - m) / 2, y3 + ch / 2, zr)
        }
    }

    private fun header(c: Canvas, w: Float, hh: Float) {
        p.style = Paint.Style.FILL; p.shader = LinearGradient(0f, 0f, 0f, hh, Color.rgb(48, 48, 52), Color.rgb(24, 24, 26), clamp); c.drawRect(0f, 0f, w, hh, p); p.shader = null
        brush(c, RectF(0f, 0f, w, hh), 0f, 255)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = Color.rgb(6, 6, 8); c.drawLine(0f, hh, w, hh, p)
        p.strokeWidth = 1.5f; p.color = Color.argb(120, 130, 130, 136); c.drawLine(0f, hh + 2f, w, hh + 2f, p)
        img(c, aScrew, 0.04f * w, 0.2f * hh, 0.2f * hh / 48f)
        var ts = hh * 0.52f; val t1 = "INCLINÓMETRO "; val t2 = "OFFROAD"
        for (i in 0 until 10) { if (2 * hh * 0.36f + hh * 0.2f + tw(t1, ts) + tw(t2, ts) <= w - 2.8f * hh) break; ts *= 0.9f }
        val w1 = tw(t1, ts); val w2 = tw(t2, ts); val lr = hh * 0.38f
        var x = (w - (2 * lr + hh * 0.2f + w1 + w2)) / 2; val lcx = x + lr; val lcy = hh / 2
        p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, lr * 0.12f); p.color = accent; c.drawCircle(lcx, lcy, lr, p)
        p.style = Paint.Style.FILL; p.shader = LinearGradient(lcx, lcy - lr, lcx, lcy + lr, aLight, aDark, clamp)
        c.drawPath(Path().apply { moveTo(lcx, lcy - lr * 0.9f); lineTo(lcx + lr * 0.2f, lcy - lr * 0.2f); lineTo(lcx + lr * 0.9f, lcy); lineTo(lcx + lr * 0.2f, lcy + lr * 0.2f)
            lineTo(lcx, lcy + lr * 0.9f); lineTo(lcx - lr * 0.2f, lcy + lr * 0.2f); lineTo(lcx - lr * 0.9f, lcy); lineTo(lcx - lr * 0.2f, lcy - lr * 0.2f); close() }, p); p.shader = null
        x += 2 * lr + hh * 0.2f; val by = lcy + ts * 0.34f
        metalText(c, t1, x, by, ts, silverTop, silverBot); metalText(c, t2, x + w1, by, ts, aLight, accent)
        val bx = w - hh * 0.9f; p.style = Paint.Style.FILL; p.color = accent
        for (i in -1..1) c.drawRoundRect(RectF(bx, lcy + i * hh * 0.17f - hh * 0.035f, bx + hh * 0.55f, lcy + i * hh * 0.17f + hh * 0.035f), 4f, 4f, p)
        menuRect.set(w - hh * 1.6f, 0f, w, hh)
    }

    private fun gauge(c: Canvas, r: RectF, title: String, sub: String, v: Float, isRear: Boolean, mini: Boolean) {
        metalPanel(c, r)
        val pw = r.width(); val ph = r.height(); val cx = r.centerX(); val cy = r.top + ph * 0.93f
        val R = min(pw * 0.378f, ph * (if (mini) 0.754f else 0.68f)); val k = 2.0f; val sweep = v * k * (if (isRear) -1f else 1f)    // el indicador del alabeo gira en el mismo sentido que el vehículo      // sin rótulo, el dial puede ser algo mayor
        if (!mini) metalText(c, title, cx, r.top + 0.105f * ph, 0.08f * ph, silverTop, silverBot, Paint.Align.CENTER)   // rótulo centrado
        val oval = RectF(cx - R, cy - R, cx + R, cy + R)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.BUTT; p.color = Color.rgb(96, 96, 102); p.strokeWidth = max(2f, 0.010f * R); c.drawArc(oval, 270f - 80f, 160f, false, p)
        if (abs(v) > 0.5f) {
            p.color = Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)); p.strokeWidth = max(8f, 0.05f * R); c.drawArc(oval, 270f, sweep, false, p)
            p.color = accent; p.strokeWidth = max(3f, 0.02f * R); c.drawArc(oval, 270f, sweep, false, p)
        }
        for (t in -40..40 step 2) {
            val a = Math.toRadians((t * k).toDouble()); val s = sin(a).toFloat(); val co = cos(a).toFloat()
            val major = t % 10 == 0; val edge = abs(t) == 40
            p.color = if (edge) accent else if (major) Color.rgb(232, 232, 236) else Color.rgb(150, 150, 156)
            p.strokeWidth = if (edge) max(4f, 0.03f * R) else if (major) max(2.5f, 0.016f * R) else max(1.2f, 0.008f * R)
            val r1 = R * 0.985f; val r2 = r1 - (if (major) 0.135f * R else 0.065f * R)
            c.drawLine(cx + r1 * s, cy - r1 * co, cx + r2 * s, cy - r2 * co, p)
        }
        val ls = max(9f, 0.085f * R)
        for (t in -40..40 step 10) {
            val a = Math.toRadians((t * k).toDouble()); val rr = R * 1.13f
            val lab = if (t == 0) "0°" else if (isRear) "${abs(t)}°" else if (t > 0) "+$t°" else "$t°"
            text(c, lab, cx + rr * sin(a).toFloat(), cy - rr * cos(a).toFloat() + ls * 0.35f, ls, if (abs(t) == 40) accent else Color.rgb(226, 226, 230))
        }
        c.save(); c.rotate(sweep, cx, cy); p.style = Paint.Style.FILL; p.color = accent
        c.drawPath(Path().apply { moveTo(cx, cy - R * 0.99f); lineTo(cx - 0.05f * R, cy - R * 1.07f); lineTo(cx + 0.05f * R, cy - R * 1.07f); close() }, p); c.restore()
        val bmp = if (isRear) rear else side; val tilt = if (isRear) -v else v
        val vcx = cx; val vcy = cy - 0.52f * R
        if (bmp != null) {
            val f = min(0.74f * R / bmp.width, 0.42f * R / bmp.height)
            c.save(); c.translate(vcx, vcy); c.rotate(tilt); c.scale(f, f); p.isFilterBitmap = true; p.style = Paint.Style.FILL
            c.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, p); c.restore()
            c.save(); c.translate(vcx, vcy); c.rotate(tilt); val gy = bmp.height * f / 2f + 0.03f * R
            p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, 0.014f * R)
            p.shader = LinearGradient(-0.5f * R, 0f, 0.5f * R, 0f, intArrayOf(accent and 0x00FFFFFF, accent, accent, accent and 0x00FFFFFF), floatArrayOf(0f, 0.25f, 0.75f, 1f), clamp)
            c.drawLine(-0.5f * R, gy, 0.5f * R, gy, p); p.shader = null; c.restore()
        }
        val pcy = r.top + ph * 0.887f; val tsz = 0.09f * ph; val vi = v.roundToInt()          // cifras algo más pequeñas
        // Alabeo: solo el valor, sin signo. Cabeceo: negativo cuando el morro está abajo, positivo cuando está arriba.
        val s = if (isRear) "${abs(vi)}°" else if (vi == 0) "0°" else if (vi > 0) "+$vi°" else "$vi°"
        val pwid = max(0.16f * pw, 2.4f * tsz)
        img(c, aPill, cx, pcy, pwid / 368f)                      // pastilla hexagonal del original
        metalText(c, s, cx, pcy + tsz * 0.36f, tsz, aLight, accent, Paint.Align.CENTER)
    }

    private fun compassCard(c: Canvas, r: RectF) {
        metalPanel(c, r); val pw = r.width(); val ph = r.height()
        val rd = min(0.37f * ph, 0.20f * pw); val dcx = r.left + 0.263f * pw; val dcy = r.top + 0.5f * ph
        img(c, aCompass, dcx, dcy, 2f * rd / 286f)                                   // esfera con bisel (sin aguja)
        val fr = rd * 0.87f
        c.save(); c.translate(dcx, dcy); c.rotate(heading ?: 0f); p.style = Paint.Style.FILL      // aguja: gira con el rumbo
        p.shader = LinearGradient(0f, -0.74f * fr, 0f, 0f, aLight, accent, clamp); c.drawPath(Path().apply { moveTo(0f, -0.74f * fr); lineTo(0.12f * fr, 0f); lineTo(-0.12f * fr, 0f); close() }, p); p.shader = null
        p.color = Color.rgb(74, 74, 80); c.drawPath(Path().apply { moveTo(0f, 0.74f * fr); lineTo(0.12f * fr, 0f); lineTo(-0.12f * fr, 0f); close() }, p)
        p.color = Color.rgb(205, 205, 210); c.drawCircle(0f, 0f, 0.10f * fr, p); p.color = Color.rgb(40, 40, 44); c.drawCircle(0f, 0f, 0.04f * fr, p); c.restore()
        metalText(c, "BRÚJULA", r.left + 0.5f * pw, r.top + 0.225f * ph, 0.145f * ph, silverTop, silverBot)
        val box = RectF(r.left + 0.515f * pw, r.top + 0.36f * ph, r.right - 0.043f * pw, r.top + 0.73f * ph); inset(c, box)
        val hd = heading
        val s1 = if (hd == null) "---" else cards[((hd + 22.5f) / 45f).toInt().mod(8)] + " "; val s2 = if (hd == null) "" else "%03d°".format(hd.roundToInt().mod(360))
        val sz = fit(s1 + s2, 0.2f * ph, box.width() * 0.88f); val sx = box.centerX() - tw(s1 + s2, sz) / 2; val by = box.centerY() + sz * 0.36f
        metalText(c, s1, sx, by, sz, aLight, accent); metalText(c, s2, sx + tw(s1, sz), by, sz, silverTop, silverBot)
    }

    private fun altCard(c: Canvas, r: RectF) {
        metalPanel(c, r); val pw = r.width(); val ph = r.height()
        val rd = min(0.37f * ph, 0.20f * pw); val bcx = r.left + 0.263f * pw; val bcy = r.top + 0.5f * ph
        img(c, aBadge, bcx, bcy, 2f * rd / 244f)                                     // insignia con montaña
        metalText(c, "ALTÍMETRO", r.left + 0.5f * pw, r.top + 0.225f * ph, 0.145f * ph, silverTop, silverBot)
        val box = RectF(r.left + 0.515f * pw, r.top + 0.36f * ph, r.right - 0.043f * pw, r.top + 0.73f * ph); inset(c, box)
        val al = altitude
        if (al == null) metalText(c, "---", box.centerX(), box.centerY() + 0.07f * ph, 0.2f * ph, aLight, accent, Paint.Align.CENTER)
        else {
            val n = String.format(Locale("es", "ES"), "%,d", al.roundToInt()); val sz = fit("$n m", 0.2f * ph, box.width() * 0.88f)
            val wn = tw("$n ", sz); val wm = tw("m", sz); val sx = box.centerX() - (wn + wm) / 2; val by = box.centerY() + sz * 0.36f
            metalText(c, n, sx, by, sz, aLight, accent); metalText(c, "m", sx + wn, by, sz, silverTop, silverBot)
        }
    }

    private fun zero(c: Canvas, cx: Float, cy: Float, r: Float) {
        zeroC.set(cx, cy); zeroR = r
        img(c, aZero, cx, cy, 2f * r / 428f, accentFilter(zeroDown))                 // botón del original; el naranja toma el color elegido
        val ink = Color.rgb(32, 22, 14); val my = cy - r * 0.14f; val ar = r * 0.32f
        p.style = Paint.Style.STROKE; p.shader = null; p.color = ink; p.strokeWidth = max(2f, 0.05f * r); c.drawCircle(cx, my, ar, p)
        c.drawLine(cx, my - ar * 1.35f, cx, my + ar * 1.35f, p); c.drawLine(cx - ar * 1.35f, my, cx + ar * 1.35f, my, p)
        p.style = Paint.Style.FILL; c.drawCircle(cx, my, ar * 0.22f, p)
        text(c, "ZERO", cx, cy + 0.66f * r, 0.27f * r, ink, Paint.Align.CENTER, 0f, titleFont)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val inZero = zeroR > 0 && hypot(e.x - zeroC.x, e.y - zeroC.y) <= zeroR
        val inMenu = !menuRect.isEmpty && menuRect.contains(e.x, e.y)
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { if (inZero) { zeroDown = true; invalidate() }; return inZero || inMenu }
            MotionEvent.ACTION_UP -> { zeroDown = false; invalidate(); if (inZero) onZero?.invoke() else if (inMenu) onMenu?.invoke() }
            MotionEvent.ACTION_CANCEL -> { zeroDown = false; invalidate() }
        }
        return true
    }
}
