package com.example.inclinometro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Rational
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.inclinometro.themes.industrial.IndustrialTheme
import com.example.inclinometro.themes.topo.TopoTheme

/** Un tema define todo el aspecto de la app (pantalla principal, Ajustes, histórico) sin tocar la lógica.
 *  Para añadir uno nuevo: crea un paquete en themes/, implementa esta interfaz y añádelo a [Themes.all]. Ver TEMAS.md. */
interface Theme {
    val id: String                      // se usa en las claves guardadas: no cambiarlo una vez publicado
    val name: String                    // nombre visible en el selector de tema
    val defaultAccent: Int
    val accentPresets: IntArray         // 8 colores para el selector de Ajustes
    val pipAspect: Rational             // proporción de la mini ventana
    val caution: Int                    // aviso de precaución (ámbar)
    val danger: Int                     // aviso de peligro (rojo): fondo de la app en zona de peligro
    val ui: ThemeUi                     // piezas de interfaz para Ajustes e histórico
    /** Vistas del vehículo que usa la pantalla del tema ("side", "rear", "top"): solo esas se piden en Ajustes. */
    val vehicleKinds: List<String> get() = VehicleImages.kinds
    /** Imagen del vehículo que trae el tema para "side", "rear" o "top". */
    fun vehicleDefault(kind: String): Int = when (kind) { "side" -> R.drawable.van_side; "rear" -> R.drawable.van_rear; else -> R.drawable.van_top }
    fun createDash(ctx: Context, actions: DashActions): Dash
}

/** La pantalla principal de un tema. Solo dibuja: los datos le llegan en [update]. */
interface Dash {
    val view: View
    var accent: Int
    fun setVehicle(side: Bitmap, rear: Bitmap, top: Bitmap)
    fun update(s: DashState)
    /** Entra o sale de la mini ventana (PiP): conviene ocultar los botones. */
    fun setPip(pip: Boolean) {}
}

/** Lo que pasa al tocar los controles de la pantalla principal. */
class DashActions(val onSettings: () -> Unit = {})

/** Datos que muestra la pantalla principal, iguales para todos los temas. */
class DashState {
    var roll = 0f; var pitch = 0f
    var heading: Float? = null; var altitude: Float? = null
    var kmh = 0f
    var gps = false                     // hay posición GPS reciente (velocidad válida)
    /** Nivel de aviso de cada eje: [Alerts.OK], [Alerts.CAUTION] o [Alerts.DANGER]. */
    var pitchLevel = Alerts.OK; var rollLevel = Alerts.OK
    val level get() = maxOf(pitchLevel, rollLevel)
    /** Máximos registrados en grados positivos: morro arriba, morro abajo, alabeo izquierda, alabeo derecha. */
    val max = FloatArray(4)

    fun fromEngine(): DashState {
        roll = Engine.roll; pitch = Engine.pitch; heading = Engine.heading; altitude = Engine.altitude
        kmh = Engine.kmh; gps = Engine.gpsFresh
        pitchLevel = Alerts.pitchLevel; rollLevel = Alerts.rollLevel
        Engine.maxima.max.copyInto(max); return this
    }
    companion object {
        /** Valores de ejemplo para las miniaturas del selector de tema. */
        fun demo() = DashState().apply {
            roll = 12f; pitch = -8f; heading = 45f; altitude = 852f; kmh = 23f; gps = true
            floatArrayOf(14f, 9f, 18f, 11f).copyInto(max)
        }
    }
}

/** Piezas de interfaz con el estilo del tema, para Ajustes e histórico. */
interface ThemeUi {
    val background: Int                 // color de fondo de las pantallas
    val text: Int                       // color de texto normal
    val muted: Int                      // color de texto secundario
    fun backgroundDrawable(ctx: Context): Drawable
    fun label(ctx: Context, t: String, size: Float, color: Int): TextView
    fun note(ctx: Context, t: String): TextView
    fun button(ctx: Context, t: String, filled: Boolean, accent: Int, onClick: () -> Unit): Button
    /** Un bloque de Ajustes con su título. */
    fun section(ctx: Context, title: String, accent: Int, views: List<View>): View
}

object Themes {
    val all: List<Theme> = listOf(IndustrialTheme, TopoTheme)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
    val current: Theme get() = byId(Config.theme)
    fun accent(t: Theme) = Config.accent(t.id, t.defaultAccent)

    private val previews = mutableMapOf<String, Bitmap>()
    /** Miniatura del tema: dibuja su pantalla principal real, a tamaño de pantalla, con datos de ejemplo y la reduce. */
    fun preview(ctx: Context, t: Theme, screenW: Int, screenH: Int, thumbW: Int): Bitmap {
        val key = "${t.id}:${accent(t)}:$screenW:$screenH:$thumbW"
        previews[key]?.let { return it }
        val dash = t.createDash(ctx, DashActions())
        dash.accent = accent(t)
        dash.setVehicle(VehicleImages.load(ctx, t, "side"), VehicleImages.load(ctx, t, "rear"), VehicleImages.load(ctx, t, "top"))
        dash.update(DashState.demo())
        val v = dash.view
        v.measure(View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, screenW, screenH)
        val k = thumbW.toFloat() / screenW
        val b = Bitmap.createBitmap(thumbW, (screenH * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(Color.rgb(16, 16, 18)); c.scale(k, k); v.draw(c)
        previews.keys.filter { it.startsWith("${t.id}:") }.forEach { previews.remove(it) }
        previews[key] = b
        return b
    }
}
