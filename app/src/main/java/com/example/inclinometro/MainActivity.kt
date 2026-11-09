package com.example.inclinometro

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.File
import kotlin.math.max

/** Inclinómetro OFFROAD. La pantalla principal la dibuja el tema activo; los datos vienen de [Engine].
 *  Al minimizar desde la pantalla principal pasa sola a mini ventana (PiP), si está activado. */
class MainActivity : Activity() {

    var appTheme: Theme = Themes.current; private set
    private lateinit var root: FrameLayout
    private lateinit var dash: Dash
    private lateinit var settings: ScrollView
    private lateinit var settingsScreen: SettingsScreen
    private val state = DashState()
    private val onEngine: () -> Unit = { dash.update(state.fromEngine()) }

    private val reqOf = mapOf("side" to 11, "rear" to 12, "top" to 13)
    private val chain = mutableListOf<String>()          // "Subir fotos": lateral, trasera y superior, una tras otra

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        appTheme = Themes.current
        settingsScreen = SettingsScreen(this)
        root = FrameLayout(this).apply { setBackgroundColor(appTheme.ui.background) }
        dash = createDash()
        settings = settingsScreen.build(); settings.visibility = View.GONE
        root.addView(dash.view, 0, FrameLayout.LayoutParams(-1, -1)); root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        refreshVehicle(); updatePipParams()
        // Sin permiso de ubicación la app funciona igual, pero sin rumbo GPS, altitud ni velocidad: se pide al abrir mientras no esté concedido.
        if (!Engine.hasLocationPermission(this) && Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
    }

    override fun onStart() { super.onStart(); Engine.listeners += onEngine; Engine.acquire(this, this) }          // sigue activo en mini ventana
    override fun onStop() { super.onStop(); Engine.listeners -= onEngine; Engine.release(this) }
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) Engine.startGps()
    }

    // ---------- tema ----------
    private fun createDash() = appTheme.createDash(this, DashActions(onSettings = { showSettings() }, onZero = { calibrate() })).also { it.accent = Themes.accent(appTheme) }

    /** Cambia de tema al momento: la calibración y el resto de datos no dependen de la pantalla, así que no se pierde nada. */
    fun applyTheme(id: String) {
        Config.theme = id; appTheme = Themes.byId(id)
        val vis = dash.view.visibility
        root.removeView(dash.view); dash = createDash(); dash.view.visibility = vis
        root.addView(dash.view, 0, FrameLayout.LayoutParams(-1, -1)); root.setBackgroundColor(appTheme.ui.background)
        dash.update(state.fromEngine()); refreshVehicle(); rebuildSettings(); updatePipParams()
    }
    fun setAccent(c: Int) { Config.setAccent(appTheme.id, c); dash.accent = c; rebuildSettings() }

    // ---------- navegación ----------
    fun showDash() { settings.visibility = View.GONE; dash.view.visibility = View.VISIBLE; updatePipParams() }
    private fun showSettings() { dash.view.visibility = View.GONE; settings.visibility = View.VISIBLE; updatePipParams() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (settings.visibility == View.VISIBLE) showDash() else super.onBackPressed() }
    fun rebuildSettings() {
        val vis = settings.visibility; val y = settings.scrollY
        root.removeView(settings)
        settings = settingsScreen.build(); settings.visibility = vis; root.addView(settings, FrameLayout.LayoutParams(-1, -1)); refreshVehicle()
        settings.post { settings.scrollTo(0, y) }                                 // conserva la posición al tocar un botón
    }
    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); rebuildSettings() }

    // ---------- mini ventana (PiP) ----------
    private fun pipSupported() = Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    private fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(appTheme.pipAspect)
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(Config.autoPip && dash.view.visibility == View.VISIBLE)     // solo desde la pantalla principal
        return b.build()
    }
    fun updatePipParams() { if (Build.VERSION.SDK_INT >= 31 && pipSupported()) try { setPictureInPictureParams(pipParams()) } catch (_: Exception) {} }
    fun enterPip(silent: Boolean) {
        if (!pipSupported()) {
            if (!silent) Toast.makeText(this, "Esta unidad no incluye la función de mini ventana (PiP). Prueba la pantalla dividida del sistema.", Toast.LENGTH_LONG).show()
            return
        }
        if (isInPictureInPictureMode) return
        try {
            showDash()
            if (!enterPictureInPictureMode(pipParams()) && !silent) Toast.makeText(this, "No se pudo abrir la mini ventana en esta unidad", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { if (!silent) Toast.makeText(this, "No se pudo abrir la mini ventana: ${e.message}", Toast.LENGTH_LONG).show() }
    }
    /** Al pulsar Inicio desde la pantalla principal, la app pasa a mini ventana en vez de cerrarse (si la opción está activada). */
    override fun onUserLeaveHint() { super.onUserLeaveHint(); if (Config.autoPip && dash.view.visibility == View.VISIBLE) enterPip(true) }
    override fun onPictureInPictureModeChanged(inPip: Boolean, cfg: Configuration) { super.onPictureInPictureModeChanged(inPip, cfg); dash.setPip(inPip) }

    // ---------- calibración ----------
    private fun calibrate() { Engine.calibrate(); Toast.makeText(this, "Calibrado a 0°", Toast.LENGTH_SHORT).show() }

    // ---------- imágenes del vehículo (se guardan en el tema activo) ----------
    private fun refreshVehicle() {
        val s = VehicleImages.load(this, appTheme, "side"); val r = VehicleImages.load(this, appTheme, "rear"); val t = VehicleImages.load(this, appTheme, "top")
        dash.setVehicle(s, r, t)
        settingsScreen.slots["side"]?.setImageBitmap(s); settingsScreen.slots["rear"]?.setImageBitmap(r); settingsScreen.slots["top"]?.setImageBitmap(t)
    }
    fun resetVehicle() { VehicleImages.reset(this, appTheme); refreshVehicle(); rebuildSettings() }
    fun pickChain(kinds: List<String>) { chain.clear(); chain.addAll(kinds); pick(chain.first()) }
    private fun pick(kind: String) {
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, reqOf[kind]!!) }
        catch (e: Exception) { chain.clear(); Toast.makeText(this, "Esta unidad no tiene selector de archivos. Usa «Cargar desde carpeta».", Toast.LENGTH_LONG).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val kind = reqOf.entries.firstOrNull { it.value == req }?.key ?: return
        val uri = data?.data
        if (res != RESULT_OK || uri == null) { chain.clear(); return }
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1; while (max(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
            val src = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            if (src != null) {
                val r = VehicleImages.prepare(src); VehicleImages.save(this, appTheme, kind, r.bitmap); refreshVehicle()
                if (r.seemsToHaveBackground) Toast.makeText(this, "La imagen parece tener fondo. Para un resultado limpio usa un PNG sin fondo.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) { Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_LONG).show() }
        if (chain.firstOrNull() == kind) chain.removeAt(0)
        if (chain.isNotEmpty()) pick(chain.first()) else rebuildSettings()
    }

    fun loadFolder() {
        val dir = getExternalFilesDir(null) ?: return
        var n = 0
        for ((kind, names) in listOf("side" to listOf("lateral.png", "lateral.jpg"), "rear" to listOf("trasera.png", "trasera.jpg", "frontal.png", "frontal.jpg"), "top" to listOf("superior.png", "superior.jpg"))) {
            val f = names.map { File(dir, it) }.firstOrNull { it.exists() } ?: continue
            BitmapFactory.decodeFile(f.path)?.let { VehicleImages.save(this, appTheme, kind, VehicleImages.prepare(it).bitmap); n++ }
        }
        refreshVehicle(); rebuildSettings()
        Toast.makeText(this, if (n > 0) "Cargadas $n imagen(es)" else "No encontré lateral.png, trasera.png ni superior.png en ${dir.path}", Toast.LENGTH_LONG).show()
    }
}
