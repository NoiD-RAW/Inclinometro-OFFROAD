package com.example.inclinometro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Surface
import android.widget.Toast
import kotlin.math.atan2

/** Lectura de sensores y GPS, común a todos los temas. Se pone en marcha cuando alguien la necesita
 *  (la pantalla principal visible) y se detiene cuando ya nadie la usa. Todo ocurre en el hilo principal. */
object Engine : SensorEventListener {
    private lateinit var app: Context
    private lateinit var sm: SensorManager
    private lateinit var lm: LocationManager
    private val owners = mutableSetOf<Any>()
    /** Se llaman tras cada lectura nueva (inclinación o GPS). */
    val listeners = mutableSetOf<() -> Unit>()

    private val g = FloatArray(3)
    private var init = false
    var rawRoll = 0f; private set
    var rawPitch = 0f; private set
    /** Ángulos ya calibrados, sin recortar (los relojes recortan a ±40° al dibujar). */
    var roll = 0f; private set
    var pitch = 0f; private set
    private var gpsBearing: Float? = null        // rumbo por GPS (en movimiento)
    private var magHeading: Float? = null        // rumbo por magnetómetro, si la unidad lo tiene (también parado)
    var kmh = 0f; private set
    private var prevLoc: Location? = null
    var lastLoc: Location? = null; private set
    var altitude: Float? = null; private set
    private var gpsOn = false

    /** Rumbo mostrado: GPS si el vehículo se mueve; parado, magnetómetro si existe; si no, el último rumbo del GPS. */
    val heading: Float? get() = if (kmh >= 4f && gpsBearing != null) gpsBearing else (magHeading ?: gpsBearing)
    /** Hay una posición GPS reciente (de los últimos 5 s). */
    val gpsFresh: Boolean get() = lastLoc.let { it != null && System.currentTimeMillis() - it.time < 5000 }

    fun acquire(owner: Any, ctx: Context) {
        if (!::app.isInitialized) {
            app = ctx.applicationContext
            sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }
        if (owners.isEmpty()) start()
        owners += owner
    }
    fun release(owner: Any) { if (owners.remove(owner) && owners.isEmpty()) stop() }

    private fun start() {
        init = false
        val s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (s == null) Toast.makeText(app, "Esta unidad no tiene sensor de inclinación", Toast.LENGTH_LONG).show()
        else sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        val mag = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (mag != null) sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
        startGps()
    }
    private fun stop() {
        sm.unregisterListener(this)
        try { lm.removeUpdates(gps) } catch (_: SecurityException) {}
        gpsOn = false
    }

    fun hasLocationPermission(ctx: Context) = Build.VERSION.SDK_INT < 23 || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    /** Arranca el GPS si hay permiso (también se llama al concederlo). Sin permiso la app sigue funcionando, sin rumbo GPS, altitud ni velocidad. */
    fun startGps() {
        if (gpsOn || !::app.isInitialized || !hasLocationPermission(app)) return
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gps); gpsOn = true }
        catch (_: SecurityException) {} catch (_: IllegalArgumentException) { Toast.makeText(app, "Esta unidad no tiene GPS", Toast.LENGTH_LONG).show() }
    }

    // ---------- calibración ----------
    fun calibrate() { Config.offRoll = rawRoll; Config.offPitch = rawPitch; recompute() }
    fun resetCal() { Config.offRoll = 0f; Config.offPitch = 0f; recompute() }
    private fun wrap(a: Float): Float { var x = a % 360f; if (x > 180f) x -= 360f; if (x <= -180f) x += 360f; return x }
    private fun recompute() { roll = wrap(rawRoll - Config.offRoll); pitch = wrap(rawPitch - Config.offPitch); dispatch() }
    private fun dispatch() { for (l in listeners.toList()) l() }

    private fun displayRotation(): Int {
        val d = (app.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        return when (d?.rotation) { Surface.ROTATION_90 -> 1; Surface.ROTATION_180 -> 2; Surface.ROTATION_270 -> 3; else -> 0 }
    }

    private fun updateMagnetic(e: SensorEvent) {
        val rot = FloatArray(9); SensorManager.getRotationMatrixFromVector(rot, e.values)
        val out = FloatArray(9)
        if (Config.flat) System.arraycopy(rot, 0, out, 0, 9) else SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, out)   // pantalla vertical: la parte trasera apunta al frente
        val o = FloatArray(3); SensorManager.getOrientation(out, o)
        var az = Math.toDegrees(o[0].toDouble()).toFloat()
        lastLoc?.let { az += GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), System.currentTimeMillis()).declination }   // norte magnético -> verdadero
        az = (az + 360f) % 360f
        val old = magHeading
        magHeading = if (old == null) az else (old + 0.15f * (((az - old + 540f) % 360f) - 180f) + 360f) % 360f
        dispatch()
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_ROTATION_VECTOR || e.sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) { updateMagnetic(e); return }
        if (!init) { for (i in 0..2) g[i] = e.values[i]; init = true } else for (i in 0..2) g[i] += 0.12f * (e.values[i] - g[i])
        val sx: Float; val sy: Float; val sz = g[2]
        when ((displayRotation() + Config.rot) % 4) { 1 -> { sx = g[1]; sy = -g[0] }; 2 -> { sx = -g[0]; sy = -g[1] }; 3 -> { sx = -g[1]; sy = g[0] }; else -> { sx = g[0]; sy = g[1] } }
        val r: Double; val p: Double
        if (Config.flat) { r = atan2(sx, sz).toDouble(); p = atan2(sy, sz).toDouble() } else { r = atan2(sx, sy).toDouble(); p = atan2(-sz, sy).toDouble() }
        rawRoll = Math.toDegrees(r).toFloat(); rawPitch = Math.toDegrees(p).toFloat() * (if (Config.invertPitch) -1f else 1f)
        recompute()
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    // ---------- GPS: altitud, velocidad y rumbo ----------
    private val gps = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
            val okAcc = !loc.hasAccuracy() || loc.accuracy <= 35f
            if (loc.hasBearing() && kmh >= 2f && okAcc) gpsBearing = loc.bearing                       // rumbo que da el propio GPS
            val p = prevLoc
            if (p == null) prevLoc = loc
            else if (okAcc && p.distanceTo(loc) >= 6f) { gpsBearing = (p.bearingTo(loc) + 360f) % 360f; prevLoc = loc }   // o calculado entre dos puntos
            if (loc.hasAltitude()) altitude = loc.altitude.toFloat()
            lastLoc = loc
            dispatch()
        }
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
        @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    }
}
