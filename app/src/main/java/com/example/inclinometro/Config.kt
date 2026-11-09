package com.example.inclinometro

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/** Ajustes guardados de toda la app. Las claves de la versión anterior ("flat", "rot", "offRoll"…) se mantienen,
 *  así al actualizar no se pierde la calibración. Lo que depende del tema lleva el id del tema en la clave. */
object Config {
    lateinit var prefs: SharedPreferences; private set

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        migrate(ctx)
    }

    // ---------- montaje del sensor y calibración (comunes a todos los temas) ----------
    var flat: Boolean get() = prefs.getBoolean("flat", false); set(v) = prefs.edit().putBoolean("flat", v).apply()
    var rot: Int get() = prefs.getInt("rot", 0); set(v) = prefs.edit().putInt("rot", v).apply()
    var invertPitch: Boolean get() = prefs.getBoolean("invPitch", true); set(v) = prefs.edit().putBoolean("invPitch", v).apply()
    var offRoll: Float get() = prefs.getFloat("offRoll", 0f); set(v) = prefs.edit().putFloat("offRoll", v).apply()
    var offPitch: Float get() = prefs.getFloat("offPitch", 0f); set(v) = prefs.edit().putFloat("offPitch", v).apply()

    // ---------- límites del vehículo y avisos ----------
    var limitPitch: Float get() = prefs.getFloat("limPitch", 30f); set(v) = prefs.edit().putFloat("limPitch", v).apply()
    var limitRoll: Float get() = prefs.getFloat("limRoll", 30f); set(v) = prefs.edit().putFloat("limRoll", v).apply()
    /** Fracción del límite a partir de la cual se avisa en ámbar (0,8 = 80 %). */
    var cautionPct: Float get() = prefs.getFloat("caution", 0.8f); set(v) = prefs.edit().putFloat("caution", v).apply()
    var vibrate: Boolean get() = prefs.getBoolean("vibrate", true); set(v) = prefs.edit().putBoolean("vibrate", v).apply()
    var beep: Boolean get() = prefs.getBoolean("beep", false); set(v) = prefs.edit().putBoolean("beep", v).apply()

    // ---------- máximos registrados ----------
    /** Tiempo que debe mantenerse un ángulo para contar como máximo (filtro de baches). */
    var maxWindowMs: Long get() = prefs.getLong("maxWindow", 500L); set(v) = prefs.edit().putLong("maxWindow", v).apply()
    var maxima: FloatArray
        get() = FloatArray(4) { prefs.getFloat("max$it", 0f) }
        set(v) { val e = prefs.edit(); for (i in 0..3) e.putFloat("max$i", v[i]); e.apply() }

    // ---------- mini ventana ----------
    var autoPip: Boolean get() = prefs.getBoolean("autoPip", true); set(v) = prefs.edit().putBoolean("autoPip", v).apply()

    // ---------- tema y personalización por tema ----------
    var theme: String get() = prefs.getString("theme", "industrial")!!; set(v) = prefs.edit().putString("theme", v).apply()
    fun accent(themeId: String, def: Int) = prefs.getInt("accent_$themeId", def)
    fun setAccent(themeId: String, c: Int) = prefs.edit().putInt("accent_$themeId", c).apply()

    /** La app de cuatro relojes metálicos usaba "accent" y van_side/van_rear.png: pasan a ser los del tema Industrial. */
    private fun migrate(ctx: Context) {
        if (prefs.getBoolean("migrated_v2", false)) return
        val e = prefs.edit()
        if (prefs.contains("accent")) e.putInt("accent_industrial", prefs.getInt("accent", 0)).remove("accent")
        for (k in listOf("side", "rear")) File(ctx.filesDir, "van_$k.png").let { if (it.exists()) it.renameTo(File(ctx.filesDir, "van_industrial_$k.png")) }
        e.putBoolean("migrated_v2", true).apply()
    }
}
