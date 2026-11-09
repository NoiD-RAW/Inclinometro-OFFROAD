package com.example.inclinometro

import kotlin.math.max
import kotlin.math.min

/** Máximos de inclinación por sentido: un valor solo cuenta si se mantiene al menos [windowMs] (así un bache no deja un pico falso).
 *  Se toma el mínimo de la ventana: si durante 0,5 s el ángulo no ha bajado de 25°, el máximo es 25° aunque haya tocado 32° un instante. */
class MaxTracker(var windowMs: Long) {
    /** Grados (siempre positivos): 0 morro arriba, 1 morro abajo, 2 alabeo izquierda, 3 alabeo derecha. */
    val max = FloatArray(4)
    private val times = ArrayDeque<Long>()
    private val vals = ArrayDeque<FloatArray>()

    fun reset() { max.fill(0f); times.clear(); vals.clear() }
    fun set(values: FloatArray) { for (i in 0..3) max[i] = values[i] }

    /** Añade una lectura (t en ms). Devuelve true si algún máximo ha subido. */
    fun feed(t: Long, pitch: Float, roll: Float): Boolean {
        val d = floatArrayOf(max(0f, pitch), max(0f, -pitch), max(0f, roll), max(0f, -roll))
        times.addLast(t); vals.addLast(d)
        while (times.size > 1 && t - times[1] >= windowMs) { times.removeFirst(); vals.removeFirst() }   // ventana justa: la lectura más antigua tiene al menos windowMs
        if (t - times.first() < windowMs) return false                                                  // aún no hay historia suficiente
        var changed = false
        for (i in 0..3) {
            var m = Float.MAX_VALUE
            for (v in vals) m = min(m, v[i])
            if (m > max[i]) { max[i] = m; changed = true }
        }
        return changed
    }

    companion object {
        /** Sentido del alabeo: el reloj trasero gira el vehículo al revés que el ángulo, así que alabeo positivo = lado izquierdo abajo. */
        const val PITCH_UP = 0; const val PITCH_DOWN = 1; const val ROLL_LEFT = 2; const val ROLL_RIGHT = 3
    }
}
