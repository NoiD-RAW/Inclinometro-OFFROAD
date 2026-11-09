package com.example.inclinometro

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Rational
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.atan2

/** Inclinómetro Offroad. Las tres rayas abren Ajustes; al minimizar desde el inclinómetro pasa solo a mini ventana (PiP). */
class MainActivity : Activity(), SensorEventListener {

    private val reqSide = 11
    private val reqRear = 12
    private var accent = Color.rgb(255, 122, 26)

    private lateinit var sm: SensorManager
    private lateinit var lm: LocationManager
    private lateinit var dash: DashView
    private lateinit var settings: ScrollView
    private lateinit var slotSide: ImageView
    private lateinit var slotRear: ImageView
    private lateinit var modeBtn: Button
    private lateinit var axesBtn: Button
    private lateinit var invBtn: Button
    private var invertPitch = true            // el cabeceo sale invertido por defecto (según cómo esté colocada la pantalla)
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    private val g = FloatArray(3)
    private var init = false
    private var flat = false
    private var extraRot = 0
    private var rawRoll = 0f; private var rawPitch = 0f
    private var offRoll = 0f; private var offPitch = 0f
    private var roll = 0f; private var pitch = 0f
    private var gpsBearing: Float? = null        // rumbo por GPS (en movimiento)
    private var magHeading: Float? = null        // rumbo por magnetómetro, si la unidad lo tiene (también parado)
    private var lastKmh = 0f
    private var prevLoc: Location? = null
    private var lastLoc: Location? = null
    private var altitude: Float? = null
    private var chain = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        flat = prefs.getBoolean("flat", false); extraRot = prefs.getInt("rot", 0)
        offRoll = prefs.getFloat("offRoll", 0f); offPitch = prefs.getFloat("offPitch", 0f)
        accent = prefs.getInt("accent", accent)
        invertPitch = prefs.getBoolean("invPitch", true)

        dash = DashView(this).apply { onZero = { calibrate() }; onMenu = { showSettings() } }     // las tres rayas abren Ajustes directamente
        dash.accent = accent
        settings = buildSettings(); settings.visibility = View.GONE
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 16, 18))
            addView(dash, FrameLayout.LayoutParams(-1, -1)); addView(settings, FrameLayout.LayoutParams(-1, -1))
        })
        refreshVehicle(); refreshLabels(); updatePipParams()
    }

    // ---------- navegación y mini ventana automática ----------
    private fun showDash() { settings.visibility = View.GONE; dash.visibility = View.VISIBLE; updatePipParams() }
    private fun showSettings() { dash.visibility = View.GONE; settings.visibility = View.VISIBLE; updatePipParams() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (settings.visibility == View.VISIBLE) showDash() else super.onBackPressed() }

    private fun pipSupported() = Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    private fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(dash.visibility == View.VISIBLE)          // solo desde el inclinómetro
        return b.build()
    }
    private fun updatePipParams() { if (Build.VERSION.SDK_INT >= 31 && pipSupported()) try { setPictureInPictureParams(pipParams()) } catch (_: Exception) {} }
    /** Al minimizar desde la pantalla del inclinómetro, la app pasa sola a mini ventana (si la unidad la admite). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (dash.visibility == View.VISIBLE && pipSupported() && !isInPictureInPictureMode) try { enterPictureInPictureMode(pipParams()) } catch (_: Exception) {}
    }
    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); rebuildSettings() }

    // ---------- pantalla de Ajustes ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val titleTypeface: Typeface? by lazy {
        var f: Typeface? = null
        for (n in arrayOf("fonts/gunplay.ttf", "fonts/gunplay.otf", "fonts/Gunplay.ttf", "fonts/Gunplay.otf")) { if (f == null) try { f = Typeface.createFromAsset(assets, n) } catch (_: Exception) {} }
        f
    }
    private fun TextView.titleFont() { val f = titleTypeface; if (f != null) setTypeface(f) else setTypeface(null, Typeface.BOLD) }
    private fun shade(c: Int, sk: Float, vk: Float): Int { val a = FloatArray(3); Color.colorToHSV(c, a); a[1] = (a[1] * sk).coerceIn(0f, 1f); a[2] = (a[2] * vk).coerceIn(0f, 1f); return Color.HSVToColor(a) }

    private fun label(t: String, size: Float, color: Int) = TextView(this).apply {
        text = t.uppercase(); textSize = size; setTextColor(color); titleFont(); setPadding(0, dp(2), 0, dp(8))
    }
    private fun noteText(t: String) = TextView(this).apply {
        text = t.uppercase(); textSize = 11f; setTextColor(Color.rgb(165, 165, 171)); titleFont(); setPadding(0, dp(6), 0, dp(4))
    }
    private fun button(t: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = t.uppercase(); isAllCaps = false; titleFont()
        setTextColor(if (filled) Color.rgb(30, 20, 10) else Color.rgb(235, 235, 238))
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            if (filled) intArrayOf(shade(accent, 0.62f, 1.1f), shade(accent, 1f, 0.7f)) else intArrayOf(Color.rgb(66, 66, 71), Color.rgb(30, 30, 33))).apply {
            cornerRadius = dp(12).toFloat(); setStroke(dp(2), if (filled) Color.rgb(8, 8, 10) else Color.rgb(96, 96, 102))
        }
        setOnClickListener { onClick() }
    }
    private fun btnLp() = LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) }
    private fun panel(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = MetalPanelDrawable(this@MainActivity); setPadding(dp(26), dp(26), dp(26), dp(24))
        for (v in views) addView(v, v.layoutParams ?: LinearLayout.LayoutParams(-1, -2))
    }

    private fun slotImage(req: Int) = ImageView(this).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
        background = GradientDrawable().apply { setColor(Color.rgb(22, 22, 25)); setStroke(dp(2), Color.rgb(90, 90, 96), dp(6).toFloat(), dp(4).toFloat()); cornerRadius = dp(10).toFloat() }
        setOnClickListener { chain = false; pick(req) }
    }
    private fun slotColumn(iv: ImageView, caption: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), 0)
        addView(iv, LinearLayout.LayoutParams(-1, dp(92)))
        addView(TextView(context).apply { text = caption.uppercase(); titleFont(); textSize = 10f; setTextColor(Color.rgb(170, 170, 176)); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
    }

    private val presets = intArrayOf(Color.rgb(255, 122, 26), Color.rgb(230, 57, 53), Color.rgb(255, 196, 30), Color.rgb(76, 200, 80),
        Color.rgb(0, 200, 220), Color.rgb(45, 130, 255), Color.rgb(150, 90, 255), Color.rgb(255, 80, 160))
    private fun swatch(c: Int) = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(dp(if (c == accent) 4 else 2), if (c == accent) Color.WHITE else Color.rgb(20, 20, 22)) }
        setOnClickListener { setAccent(c) }
    }
    private fun colorSwatches() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        for (row in 0..1) addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            for (i in 0..3) addView(swatch(presets[row * 4 + i]), LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(8), dp(5), dp(8), dp(5)) })
        })
    }

    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)
    private fun parseHex(s: String): Int? {
        var t = s.trim().removePrefix("#")
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        if (t.length != 6 || !t.all { it in "0123456789abcdefABCDEF" }) return null
        return Color.parseColor("#$t")
    }
    private fun hexRow() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(noteText("Código de color HEX (por ejemplo #3FD0B2):"))
        val dot = View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent); setStroke(dp(2), Color.WHITE) } }
        val field = EditText(context).apply {
            setText(hex(accent)); titleFont(); setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); hint = "#3FD0B2"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(7)); imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply { setColor(Color.rgb(24, 26, 29)); setStroke(dp(2), Color.rgb(90, 92, 98)); cornerRadius = dp(10).toFloat() }
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        fun applyHex() {
            val c = parseHex(field.text.toString())
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
            if (c == null) Toast.makeText(context, "Código no válido. Ejemplo: #3FD0B2", Toast.LENGTH_LONG).show() else setAccent(c)
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { parseHex(s.toString())?.let { (dot.background as GradientDrawable).setColor(it) } }
        })
        field.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_DONE) { applyHex(); true } else false }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(dot, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(10) })
            addView(field, LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("Aplicar", true) { applyHex() }, LinearLayout.LayoutParams(dp(104), dp(44)).apply { leftMargin = dp(10) })
        })
    }
    private fun setAccent(c: Int) { accent = c; dash.accent = c; prefs.edit().putInt("accent", c).apply(); rebuildSettings() }
    private fun rebuildSettings() {
        val root = settings.parent as? FrameLayout ?: return
        val vis = settings.visibility; root.removeView(settings)
        settings = buildSettings(); settings.visibility = vis; root.addView(settings, FrameLayout.LayoutParams(-1, -1)); refreshVehicle(); refreshLabels()
    }

    /** Ajustes: tres paneles metálicos. En pantallas anchas van en dos columnas; en estrechas, uno bajo otro, centrados. */
    private fun buildSettings(): ScrollView {
        slotSide = slotImage(reqSide); slotRear = slotImage(reqRear)
        modeBtn = button("", false) { flat = !flat; resetCal(); refreshLabels() }
        axesBtn = button("", false) { extraRot = (extraRot + 1) % 4; resetCal(); refreshLabels() }
        invBtn = button("", false) { invertPitch = !invertPitch; resetCal(); prefs.edit().putBoolean("invPitch", invertPitch).apply(); refreshLabels() }
        val vehicle = panel(
            label("Personaliza tu vehículo", 15f, accent),
            LinearLayout(this).apply { addView(slotColumn(slotSide, "Vista lateral (PNG)"), LinearLayout.LayoutParams(0, -2, 1f)); addView(slotColumn(slotRear, "Vista trasera o frontal (PNG)"), LinearLayout.LayoutParams(0, -2, 1f)) },
            noteText("PNG sin fondo. La lateral, con el morro a la izquierda."),
            button("Subir fotos", true) { chain = true; pick(reqSide) }.apply { layoutParams = btnLp() },
            button("Cargar desde carpeta de la app", false) { loadFolder() }.apply { layoutParams = btnLp() },
            noteText("Sin selector de archivos: copia lateral.png y trasera.png a la carpeta de la app."),
            button("Restablecer vehículo original", false) { VehicleImages.reset(this); refreshVehicle() }.apply { layoutParams = btnLp() })
        val color = panel(label("Color principal", 15f, accent), colorSwatches(), hexRow())
        val sensor = panel(label("Montaje del sensor", 15f, accent), modeBtn.apply { layoutParams = btnLp() }, axesBtn.apply { layoutParams = btnLp() }, invBtn.apply { layoutParams = btnLp() })

        val wide = resources.configuration.screenWidthDp >= 840
        val body = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (wide) {
                addView(vehicle, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(color, LinearLayout.LayoutParams(-1, -2)); addView(sensor, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
            } else {
                addView(vehicle, LinearLayout.LayoutParams(-1, -2))
                addView(color, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
                addView(sensor, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            }
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(button("Volver", false) { showDash() }, LinearLayout.LayoutParams(dp(120), dp(44)))
            addView(label("Ajustes", 22f, Color.WHITE).apply { setPadding(dp(16), 0, 0, 0) }, LinearLayout.LayoutParams(-2, -2))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(16), dp(12), dp(16), dp(28))
            addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            addView(body, LinearLayout.LayoutParams(if (!wide && resources.configuration.screenWidthDp > 700) dp(640) else -1, -2))
        }
        return ScrollView(this).apply {
            background = BitmapDrawable(resources, BitmapFactory.decodeResource(resources, R.drawable.bg_metal)).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
            addView(col)
        }
    }

    private fun refreshLabels() {
        modeBtn.text = (if (flat) "Montaje: plano" else "Montaje: vertical").uppercase()
        axesBtn.text = "Ejes: ${extraRot * 90}°  (toca para girar 90°)".uppercase()
        invBtn.text = (if (invertPitch) "Cabeceo: invertido" else "Cabeceo: normal").uppercase()
    }
    private fun refreshVehicle() {
        val s = VehicleImages.load(this, "side"); val r = VehicleImages.load(this, "rear")
        dash.setVehicle(s, r); slotSide.setImageBitmap(s); slotRear.setImageBitmap(r)
    }

    private fun pick(req: Int) {
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, req) }
        catch (e: Exception) { Toast.makeText(this, "Esta unidad no tiene selector de archivos. Usa «Cargar desde carpeta».", Toast.LENGTH_LONG).show() }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (res != RESULT_OK || uri == null) { chain = false; return }
        try {
            val src = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (src != null) { VehicleImages.save(this, if (req == reqSide) "side" else "rear", VehicleImages.prepare(src)); refreshVehicle() }
        } catch (e: Exception) { Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_LONG).show() }
        if (req == reqSide && chain) { chain = false; pick(reqRear) }
    }
    private fun loadFolder() {
        val dir = getExternalFilesDir(null) ?: return
        var n = 0
        for ((kind, names) in listOf("side" to listOf("lateral.png", "lateral.jpg"), "rear" to listOf("trasera.png", "trasera.jpg", "frontal.png", "frontal.jpg"))) {
            val f = names.map { File(dir, it) }.firstOrNull { it.exists() } ?: continue
            BitmapFactory.decodeFile(f.path)?.let { VehicleImages.save(this, kind, VehicleImages.prepare(it)); n++ }
        }
        refreshVehicle()
        Toast.makeText(this, if (n > 0) "Cargadas $n imagen(es)" else "No encontré lateral.png ni trasera.png en ${dir.path}", Toast.LENGTH_LONG).show()
    }

    // ---------- calibración ----------
    private fun calibrate() { offRoll = rawRoll; offPitch = rawPitch; save(); Toast.makeText(this, "Calibrado a 0°", Toast.LENGTH_SHORT).show() }
    private fun resetCal() { offRoll = 0f; offPitch = 0f; save() }
    private fun save() = prefs.edit().putBoolean("flat", flat).putInt("rot", extraRot).putFloat("offRoll", offRoll).putFloat("offPitch", offPitch).apply()
    private fun wrap(a: Float): Float { var x = a % 360f; if (x > 180f) x -= 360f; if (x <= -180f) x += 360f; return x }

    /** Rumbo mostrado: GPS si el vehículo se mueve; parado, magnetómetro si existe; si no, el último rumbo del GPS. */
    private fun heading(): Float? = if (lastKmh >= 4f && gpsBearing != null) gpsBearing else (magHeading ?: gpsBearing)
    private fun push() = dash.update(roll, pitch, heading(), altitude)

    // ---------- sensores (siguen activos en mini ventana: se registran en onStart/onStop) ----------
    override fun onStart() {
        super.onStart()
        val s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (s == null) Toast.makeText(this, "Esta unidad no tiene sensor de inclinación", Toast.LENGTH_LONG).show()
        else sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        val mag = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (mag != null) sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
        startGps()
    }
    override fun onStop() {
        super.onStop(); sm.unregisterListener(this)
        try { lm.removeUpdates(gps) } catch (_: SecurityException) {}
    }

    private fun updateMagnetic(e: SensorEvent) {
        val rot = FloatArray(9); SensorManager.getRotationMatrixFromVector(rot, e.values)
        val out = FloatArray(9)
        if (flat) System.arraycopy(rot, 0, out, 0, 9) else SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, out)   // pantalla vertical: la parte trasera apunta al frente
        val o = FloatArray(3); SensorManager.getOrientation(out, o)
        var az = Math.toDegrees(o[0].toDouble()).toFloat()
        lastLoc?.let { az += GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), System.currentTimeMillis()).declination }   // norte magnético -> verdadero
        az = (az + 360f) % 360f
        val old = magHeading
        magHeading = if (old == null) az else (old + 0.15f * (((az - old + 540f) % 360f) - 180f) + 360f) % 360f
        push()
    }

    @Suppress("DEPRECATION")
    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_ROTATION_VECTOR || e.sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) { updateMagnetic(e); return }
        if (!init) { for (i in 0..2) g[i] = e.values[i]; init = true } else for (i in 0..2) g[i] += 0.12f * (e.values[i] - g[i])
        val disp = when (windowManager.defaultDisplay.rotation) { Surface.ROTATION_90 -> 1; Surface.ROTATION_180 -> 2; Surface.ROTATION_270 -> 3; else -> 0 }
        val sx: Float; val sy: Float; val sz = g[2]
        when ((disp + extraRot) % 4) { 1 -> { sx = g[1]; sy = -g[0] }; 2 -> { sx = -g[0]; sy = -g[1] }; 3 -> { sx = -g[1]; sy = g[0] }; else -> { sx = g[0]; sy = g[1] } }
        val r: Double; val p: Double
        if (flat) { r = atan2(sx, sz).toDouble(); p = atan2(sy, sz).toDouble() } else { r = atan2(sx, sy).toDouble(); p = atan2(-sz, sy).toDouble() }
        rawRoll = Math.toDegrees(r).toFloat(); rawPitch = Math.toDegrees(p).toFloat() * (if (invertPitch) -1f else 1f)
        roll = wrap(rawRoll - offRoll).coerceIn(-40f, 40f); pitch = wrap(rawPitch - offPitch).coerceIn(-40f, 40f)
        push()
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    // ---------- GPS: altitud, velocidad y rumbo ----------
    private val gps = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            lastKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
            val okAcc = !loc.hasAccuracy() || loc.accuracy <= 35f
            if (loc.hasBearing() && lastKmh >= 2f && okAcc) gpsBearing = loc.bearing                       // rumbo que da el propio GPS
            val p = prevLoc
            if (p == null) prevLoc = loc
            else if (okAcc && p.distanceTo(loc) >= 6f) { gpsBearing = (p.bearingTo(loc) + 360f) % 360f; prevLoc = loc }   // o calculado entre dos puntos
            if (loc.hasAltitude()) altitude = loc.altitude.toFloat()
            lastLoc = loc
            push()
        }
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
        @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    }
    private fun startGps() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1); return
        }
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gps) }
        catch (_: SecurityException) {} catch (_: IllegalArgumentException) { Toast.makeText(this, "Esta unidad no tiene GPS", Toast.LENGTH_LONG).show() }
    }
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) startGps()
    }
}
