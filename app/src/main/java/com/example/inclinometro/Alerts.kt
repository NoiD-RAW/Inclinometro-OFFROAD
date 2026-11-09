package com.example.inclinometro

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.abs

/** Niveles de aviso por eje, con histéresis para que no oscile cuando el ángulo ronda el umbral. */
object Alerts {
    const val OK = 0; const val CAUTION = 1; const val DANGER = 2
    /** Grados que hay que bajar por debajo de un umbral para salir de esa zona. */
    private const val HYST = 2f

    var pitchLevel = OK; private set
    var rollLevel = OK; private set
    val level get() = maxOf(pitchLevel, rollLevel)
    /** Veces que se ha entrado en zona de peligro desde que arrancó la app (la grabación cuenta las de cada salida). */
    var dangerEntries = 0; private set

    private var tone: ToneGenerator? = null
    private var lastFeedback = 0L

    fun reset() { pitchLevel = OK; rollLevel = OK }

    /** Nivel nuevo de un eje a partir del anterior: se entra al cruzar el umbral y se sale al bajar HYST grados por debajo. */
    fun next(prev: Int, angle: Float, limit: Float, cautionPct: Float): Int {
        val a = abs(angle); val c = limit * cautionPct
        var l = prev
        if (a >= limit) l = DANGER else if (a >= c && l < CAUTION) l = CAUTION
        if (l == DANGER && a < limit - HYST) l = CAUTION
        if (l == CAUTION && a < c - HYST) l = OK
        return l
    }

    /** Actualiza los niveles con los ángulos actuales. Al entrar en peligro avisa con vibración y/o pitido si están activados. */
    fun update(ctx: Context, pitch: Float, roll: Float) {
        val before = level
        pitchLevel = next(pitchLevel, pitch, Config.limitPitch, Config.cautionPct)
        rollLevel = next(rollLevel, roll, Config.limitRoll, Config.cautionPct)
        if (level == DANGER && before != DANGER) { dangerEntries++; feedback(ctx) }
    }

    private fun feedback(ctx: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFeedback < 3000) return                                        // como mucho un aviso cada 3 s
        lastFeedback = now
        if (Config.vibrate) try {
            @Suppress("DEPRECATION") val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
            else @Suppress("DEPRECATION") v.vibrate(longArrayOf(0, 250, 120, 250), -1)
        } catch (_: Exception) {}
        if (Config.beep) try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
        } catch (_: Exception) {}
    }
}
