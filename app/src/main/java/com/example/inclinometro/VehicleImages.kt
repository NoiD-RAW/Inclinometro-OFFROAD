package com.example.inclinometro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

/** Imágenes del vehículo: "side" (vista lateral, morro a la izquierda) y "rear" (vista trasera o frontal). */
object VehicleImages {
    private fun file(ctx: Context, kind: String) = File(ctx.filesDir, "van_$kind.png")

    fun load(ctx: Context, kind: String): Bitmap {
        val f = file(ctx, kind)
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { return it }
        return BitmapFactory.decodeResource(ctx.resources, if (kind == "side") R.drawable.van_side else R.drawable.van_rear)
    }

    fun save(ctx: Context, kind: String, bmp: Bitmap) {
        FileOutputStream(file(ctx, kind)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun reset(ctx: Context) { file(ctx, "side").delete(); file(ctx, "rear").delete() }

    /** Reduce el tamaño, quita un fondo liso (si la foto no es ya transparente) y recorta los bordes vacíos. */
    fun prepare(src: Bitmap): Bitmap {
        val k = 900f / max(src.width, src.height)
        val scaled = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true) else src
        val b = scaled.copy(Bitmap.Config.ARGB_8888, true)
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val bg = px[0]
        if (Color.alpha(bg) > 250) {
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
            b.setPixels(px, 0, w, 0, 0, w, h)
        }
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) if (Color.alpha(px[y * w + x]) > 10) {
            if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        return if (maxX < minX) b else Bitmap.createBitmap(b, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }
}
