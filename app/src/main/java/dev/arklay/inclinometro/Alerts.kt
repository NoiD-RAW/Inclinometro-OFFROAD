package dev.arklay.inclinometro

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    /** Eje y lado del último aviso: ±1 alabeo (derecha/izquierda), ±2 cabeceo (arriba/abajo). */
    private var lastSide = 0

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
        val before = level; val pBefore = pitchLevel; val rBefore = rollLevel
        pitchLevel = next(pitchLevel, pitch, Config.limitPitch, Config.cautionPct)
        rollLevel = next(rollLevel, roll, Config.limitRoll, Config.cautionPct)
        if (level == DANGER && before != DANGER) dangerEntries++
        // Se avisa cada vez que un eje entra en peligro, hacia el lado que sea (no solo cuando cambia el nivel general)
        val side = when {
            rollLevel == DANGER && rBefore != DANGER -> if (roll >= 0f) 1 else -1
            pitchLevel == DANGER && pBefore != DANGER -> if (pitch >= 0f) 2 else -2
            else -> 0
        }
        if (side != 0) feedback(ctx, side)
    }

    /** Vibración y pitido. Un bache que entra y sale del mismo lado avisa como mucho cada 3 s; cambiar de lado avisa siempre. */
    private fun feedback(ctx: Context, side: Int) {
        val now = SystemClock.elapsedRealtime()
        if (side == lastSide && now - lastFeedback < 3000) return
        lastFeedback = now; lastSide = side
        if (Config.vibrate) vibrate(ctx)
        if (Config.beep) beep()
    }

    fun vibrator(ctx: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    } catch (_: Exception) { null }

    /** La unidad tiene motor de vibración (muchas radios de coche y tablets no lo tienen). */
    fun canVibrate(ctx: Context) = vibrator(ctx)?.hasVibrator() == true

    /** Se marca como alarma: sin uso declarado, muchos móviles ignoran la vibración de las apps (modo silencio, intensidad de notificaciones a cero…). */
    fun vibrate(ctx: Context) {
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        val pattern = longArrayOf(0, 300, 150, 300)
        try {
            when {
                Build.VERSION.SDK_INT >= 33 -> v.vibrate(VibrationEffect.createWaveform(pattern, -1), VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
                Build.VERSION.SDK_INT >= 26 -> @Suppress("DEPRECATION") v.vibrate(VibrationEffect.createWaveform(pattern, -1),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                else -> @Suppress("DEPRECATION") v.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    fun beep() {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
        } catch (_: Exception) { tone = null }
    }
}
