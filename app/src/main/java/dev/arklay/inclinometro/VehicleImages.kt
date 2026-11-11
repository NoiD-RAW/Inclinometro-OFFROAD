package dev.arklay.inclinometro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Imágenes del vehículo: "side" (lateral, morro a la izquierda), "rear" (trasera o frontal) y "top" (superior, morro arriba).
 *  Cada tema guarda las suyas (van_<tema>_<tipo>.png); si el usuario no ha subido ninguna, se usa la que trae el tema. */
object VehicleImages {
    class Result(val bitmap: Bitmap, val seemsToHaveBackground: Boolean)
    val kinds = listOf("side", "rear", "top")

    private fun file(ctx: Context, theme: Theme, kind: String) = File(ctx.filesDir, "van_${theme.id}_$kind.png")

    fun load(ctx: Context, theme: Theme, kind: String): Bitmap {
        val f = file(ctx, theme, kind)
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { return it }
        return BitmapFactory.decodeResource(ctx.resources, theme.vehicleDefault(kind))
    }
    fun save(ctx: Context, theme: Theme, kind: String, bmp: Bitmap) { FileOutputStream(file(ctx, theme, kind)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    fun reset(ctx: Context, theme: Theme) { for (k in kinds) file(ctx, theme, k).delete() }

    /** Gira o voltea la foto según su orientación EXIF (las fotos del móvil suelen guardarse de lado con esa marca). */
    fun upright(b: Bitmap, exif: java.io.InputStream): Bitmap {
        val o = try { android.media.ExifInterface(exif).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL) } catch (_: Exception) { return b }
        val m = android.graphics.Matrix()
        when (o) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return b
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    /**
     * Deja la imagen lista para los relojes:
     * 1) reduce las fotos enormes, 2) quita un fondo liso si la imagen no es transparente,
     * 3) recorta todo el margen vacío (así el vehículo ocupa siempre el mismo espacio) y
     * 4) fija el lado largo entre 500 y 900 px (ni borroso por pequeño ni pesado por grande).
     * El reloj después encaja el vehículo en su círculo, giros incluidos, sea cual sea su proporción.
     */
    fun prepare(src: Bitmap): Result {
        var b = src.copy(Bitmap.Config.ARGB_8888, true)
        val big = 1600f / max(b.width, b.height)
        if (big < 1f) b = Bitmap.createScaledBitmap(b, (b.width * big).toInt(), (b.height * big).toInt(), true)
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var transparent = 0; for (v in px) if (Color.alpha(v) < 40) transparent++
        var seemsBg = false
        if (transparent < w * h / 100) {                                   // sin transparencia: intento quitar un fondo liso
            val bg = px[0]
            fun near(c: Int) = abs(Color.red(c) - Color.red(bg)) + abs(Color.green(c) - Color.green(bg)) + abs(Color.blue(c) - Color.blue(bg)) < 60
            val seen = BooleanArray(w * h); val q = IntArray(w * h); var head = 0; var tail = 0
            fun push(i: Int) { if (!seen[i] && near(px[i])) { seen[i] = true; q[tail++] = i } }
            for (x in 0 until w) { push(x); push((h - 1) * w + x) }
            for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
            while (head < tail) {
                val i = q[head++]; val x = i % w; val y = i / w
                if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w)
            }
            for (i in 0 until w * h) if (seen[i]) px[i] = 0
            seemsBg = tail < w * h / 20                                      // quitó muy poco: el fondo no era liso
        }
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) if (Color.alpha(px[y * w + x]) > 40) { minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y) }
        if (maxX < minX) return Result(src, true)
        val out = Bitmap.createBitmap(maxX - minX + 1, maxY - minY + 1, Bitmap.Config.ARGB_8888)
        out.setPixels(px, minY * w + minX, w, 0, 0, out.width, out.height)
        val long = max(out.width, out.height).toFloat(); val k = if (long > 900f) 900f / long else if (long < 500f) 500f / long else 1f
        val fin = if (k == 1f) out else Bitmap.createScaledBitmap(out, max(1, (out.width * k).toInt()), max(1, (out.height * k).toInt()), true)
        return Result(fin, seemsBg)
    }
}
