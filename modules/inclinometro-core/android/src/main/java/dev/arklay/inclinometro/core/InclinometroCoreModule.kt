package dev.arklay.inclinometro.core

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.facebook.react.ReactApplication
import expo.modules.kotlin.Promise
import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.File
import java.lang.ref.WeakReference
import java.text.DecimalFormatSymbols
import kotlin.math.max

/** Puente entre la interfaz (TypeScript) y la parte nativa: sensores, GPS, avisos, salidas, mini ventana y diálogos.
 *  Hace lo mismo que hacía MainActivity con el código de antes, que se ha mantenido igual (Engine, Alerts, Recorder, TripDb…). */
class InclinometroCoreModule : Module() {
  private val ctx: Context get() = appContext.reactContext ?: throw IllegalStateException("Sin contexto")
  private val activity: Activity? get() = appContext.currentActivity
  private val main = Handler(Looper.getMainLooper())

  private var observed: WeakReference<Activity>? = null
  private var started = false
  /** Rueda de la pantalla principal visible (no Ajustes ni histórico): solo entonces pasa sola a mini ventana. */
  private var pipAllowed = true
  private var pipW = 16; private var pipH = 9

  private val chain = mutableListOf<String>()          // "Subir fotos": lateral, trasera y superior, una tras otra
  private var chainTheme = ""
  private var chainPromise: Promise? = null
  private var chainCount = 0; private var chainBg = false
  private var exporting: Pair<Trip, String>? = null
  private var exportPromise: Promise? = null

  private val lifecycle = LifecycleEventObserver { _, e ->
    when (e) {
      Lifecycle.Event.ON_START -> onStart()
      Lifecycle.Event.ON_STOP -> { observed?.get()?.let { releasePipResume(it) }; onStop() }
      else -> {}
    }
  }
  /** En mini ventana la actividad queda en pausa y React Native dejaría de dibujar (las vistas de Android, en cambio, seguían):
   *  mientras dure, se mantiene activo; al cerrarla vuelve a pausarse. */
  private var pipResumed = false
  private fun reactHost(a: Activity) = (a.application as? ReactApplication)?.reactHost
  private val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
    sendEvent("onPip", mapOf("pip" to info.isInPictureInPictureMode))
    val a = observed?.get() ?: return@Consumer
    main.post {
      if (info.isInPictureInPictureMode) { if (!pipResumed) { reactHost(a)?.onHostResume(a); pipResumed = true } }
      else releasePipResume(a)
    }
  }
  private fun releasePipResume(a: Activity) {
    if (!pipResumed) return
    pipResumed = false
    if ((a as? ComponentActivity)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) reactHost(a)?.onHostPause(a)
  }

  /** Igual que MainActivity: los sensores funcionan mientras la app está visible (también en mini ventana). */
  private fun onStart() {
    if (started) return
    started = true
    Alerts.appVisible = true
    Engine.acquire(this, ctx); Recorder.ensureRunning(ctx)
  }
  private fun onStop() { if (!started) return; started = false; Alerts.appVisible = false; Engine.release(this) }

  /** Siempre en el hilo principal: toca la ventana y el ciclo de vida de la actividad. */
  private fun attach() {
    val a = activity ?: return
    if (observed?.get() === a) return
    observed = WeakReference(a)
    a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    (a as? ComponentActivity)?.let { ca ->
      ca.lifecycle.addObserver(lifecycle)
      ca.addOnPictureInPictureModeChangedListener(pipListener)
      if (ca.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onStart()
    } ?: onStart()
  }

  override fun definition() = ModuleDefinition {
    Name("InclinometroCore")
    Events("onPip")

    OnCreate { Config.init(ctx); main.post { attach() } }
    OnActivityEntersForeground { main.post { attach() } }                     // puede llegar desde el hilo de JS al crear el módulo
    OnDestroy { main.post { onStop() } }
    OnUserLeavesActivity { if (Config.autoPip && pipAllowed) enterPip(true) }   // al pulsar Inicio desde la pantalla principal

    Constants(
      "platform" to "android",
      "decimalSeparator" to DecimalFormatSymbols.getInstance().decimalSeparator.toString()
    )

    // ---------- lectura continua (la interfaz la consulta en cada fotograma) ----------
    Function("getState") {
      val loc = Engine.lastLoc
      mapOf(
        "roll" to Engine.roll.toDouble(), "pitch" to Engine.pitch.toDouble(),
        "heading" to Engine.heading?.toDouble(), "altitude" to Engine.altitude?.toDouble(),
        "kmh" to Engine.kmh.toDouble(), "gps" to Engine.gpsFresh,
        "pitchLevel" to Alerts.pitchLevel, "rollLevel" to Alerts.rollLevel,
        "max" to Engine.maxima.max.map { it.toDouble() },
        "recording" to Recorder.active, "recordStart" to Recorder.start.toDouble(), "activeTrip" to Config.activeTrip.toDouble(),
        "hasLoc" to (loc != null)
      )
    }

    // ---------- ajustes ----------
    Function("getConfig") {
      mapOf(
        "flat" to Config.flat, "rot" to Config.rot, "invertPitch" to Config.invertPitch,
        "offRoll" to Config.offRoll.toDouble(), "offPitch" to Config.offPitch.toDouble(),
        "limitPitch" to Config.limitPitch.toDouble(), "limitRoll" to Config.limitRoll.toDouble(), "cautionPct" to Config.cautionPct.toDouble(),
        "vibrate" to Config.vibrate, "beep" to Config.beep, "maxWindowMs" to Config.maxWindowMs.toDouble(), "sampleMs" to Config.sampleMs.toDouble(),
        "autoPip" to Config.autoPip, "theme" to Config.theme
      )
    }
    Function("setBool") { key: String, v: Boolean ->
      when (key) {
        "flat" -> Config.flat = v
        "invertPitch" -> Config.invertPitch = v
        "vibrate" -> Config.vibrate = v
        "beep" -> Config.beep = v
        "autoPip" -> { Config.autoPip = v; main.post { updatePipParams() } }
        else -> throw IllegalArgumentException("Ajuste desconocido: $key")
      }
    }
    Function("setNumber") { key: String, n: Double ->
      when (key) {
        "rot" -> Config.rot = n.toInt()
        "limitPitch" -> Config.limitPitch = n.toFloat()
        "limitRoll" -> Config.limitRoll = n.toFloat()
        "cautionPct" -> Config.cautionPct = n.toFloat()
        "maxWindowMs" -> Config.maxWindowMs = n.toLong()
        "sampleMs" -> Config.sampleMs = n.toLong()
        else -> throw IllegalArgumentException("Ajuste desconocido: $key")
      }
    }
    Function("setString") { key: String, v: String ->
      if (key == "theme") Config.theme = v else throw IllegalArgumentException("Ajuste desconocido: $key")
    }
    Function("getAccent") { theme: String, def: Int -> Config.accent(theme, def) }
    Function("setAccent") { theme: String, color: Int -> Config.setAccent(theme, color) }

    AsyncFunction("calibrate") { Engine.calibrate() }.runOnQueue(Queues.MAIN)
    AsyncFunction("resetCal") { Engine.resetCal() }.runOnQueue(Queues.MAIN)
    AsyncFunction("resetMaxima") { Engine.resetMaxima() }.runOnQueue(Queues.MAIN)

    // ---------- avisos ----------
    Function("canVibrate") { Alerts.canVibrate(ctx) }
    AsyncFunction("testAlert") {
      if (Config.vibrate) Alerts.vibrate(ctx); if (Config.beep) Alerts.beep()
      Config.beep || (Config.vibrate && Alerts.canVibrate(ctx))
    }.runOnQueue(Queues.MAIN)

    // ---------- permisos ----------
    Function("hasLocationPermission") { Engine.hasLocationPermission(ctx) }
    Function("hasNotificationPermission") {
      Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    /** Pide ubicación y/o notificaciones. Al conceder la ubicación arranca el GPS, como en onRequestPermissionsResult. */
    AsyncFunction("requestPermissions") { location: Boolean, notifications: Boolean, promise: Promise ->
      val perms = ArrayList<String>()
      if (location) { perms += Manifest.permission.ACCESS_FINE_LOCATION; perms += Manifest.permission.ACCESS_COARSE_LOCATION }
      if (notifications && Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
      val pm = appContext.permissions
      if (perms.isEmpty() || pm == null || Build.VERSION.SDK_INT < 23) { promise.resolve(Engine.hasLocationPermission(ctx)); return@AsyncFunction }
      pm.askForPermissions({ _ ->
        main.post { if (Engine.hasLocationPermission(ctx)) Engine.startGps(); promise.resolve(Engine.hasLocationPermission(ctx)) }
      }, *perms.toTypedArray())
    }

    // ---------- salidas ----------
    AsyncFunction("startTrip") { Recorder.start(ctx) }.runOnQueue(Queues.MAIN)
    AsyncFunction("stopTrip") { Recorder.stop(ctx) }.runOnQueue(Queues.MAIN)

    Function("trips") { byAngle: Boolean -> TripDb.get(ctx).trips(byAngle).map { tripMap(it) } }
    Function("trip") { id: Double -> TripDb.get(ctx).trip(id.toLong())?.let { tripMap(it) } }
    Function("samples") { id: Double ->
      val s = TripDb.get(ctx).samples(id.toLong())
      mapOf(
        "t" to s.t.map { it.toDouble() }, "pitch" to s.pitch.map { it.toDouble() }, "roll" to s.roll.map { it.toDouble() },
        "lat" to s.lat.map { if (it.isNaN()) null else it }, "lon" to s.lon.map { if (it.isNaN()) null else it },
        "alt" to s.alt.map { if (it.isNaN()) null else it.toDouble() }, "kmh" to s.kmh.map { if (it.isNaN()) null else it.toDouble() }
      )
    }
    Function("hotspots") {
      TripDb.get(ctx).hotspots().map { h ->
        mapOf("trip" to h.trip.toDouble(), "tripName" to h.tripName, "t" to h.t.toDouble(), "lat" to h.lat, "lon" to h.lon,
          "pitch" to h.pitch.toDouble(), "roll" to h.roll.toDouble(), "ratio" to h.ratio.toDouble())
      }
    }
    Function("renameTrip") { id: Double, name: String -> TripDb.get(ctx).rename(id.toLong(), name) }
    Function("deleteTrip") { id: Double -> TripDb.get(ctx).delete(id.toLong()) }
    Function("deleteOlderThan") { before: Double, keep: Double -> TripDb.get(ctx).deleteOlderThan(before.toLong(), keep.toLong()) }
    Function("dbSize") { TripDb.get(ctx).sizeBytes(ctx).toDouble() }

    /** Guardar como… (selector de archivos del sistema); sin selector, a la carpeta de la app. Avisa con un Toast, como antes. */
    AsyncFunction("exportTrip") { id: Double, ext: String, promise: Promise ->
      val t = TripDb.get(ctx).trip(id.toLong()) ?: run { promise.resolve(null); return@AsyncFunction }
      val a = activity
      exporting = t to ext; exportPromise = promise
      val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType(if (ext == "csv") "text/csv" else "application/gpx+xml").putExtra(Intent.EXTRA_TITLE, Export.fileName(t, ext))
      try { a!!.startActivityForResult(i, 21) }
      catch (_: Exception) {                                                          // sin selector de archivos: a la carpeta de la app
        exporting = null; exportPromise = null
        val dir = File(ctx.getExternalFilesDir(null), "exportes").apply { mkdirs() }
        val f = File(dir, Export.fileName(t, ext))
        try { f.outputStream().use { write(t, ext, it) }; toast("Guardado en ${f.path}", true) }
        catch (e: Exception) { toast("No se pudo exportar: ${e.message}", true) }
        promise.resolve(null)
      }
    }.runOnQueue(Queues.MAIN)

    // ---------- imágenes del vehículo ----------
    Function("vehicleUri") { theme: String, kind: String ->
      VehicleImages.custom(ctx, theme, kind)?.let { Uri.fromFile(it).toString() + "?v=" + it.lastModified() }
    }
    Function("resetVehicle") { theme: String -> VehicleImages.reset(ctx, theme) }
    AsyncFunction("pickVehicle") { theme: String, kinds: List<String>, promise: Promise ->
      chainPromise?.resolve(chainCount)
      chain.clear(); chain.addAll(kinds); chainTheme = theme; chainPromise = promise; chainCount = 0; chainBg = false
      pick(chain.first())
    }.runOnQueue(Queues.MAIN)
    AsyncFunction("loadFolder") { theme: String ->
      val dir = ctx.getExternalFilesDir(null) ?: return@AsyncFunction 0
      var n = 0
      for ((kind, names) in listOf("side" to listOf("lateral.png", "lateral.jpg"), "rear" to listOf("trasera.png", "trasera.jpg", "frontal.png", "frontal.jpg"), "top" to listOf("superior.png", "superior.jpg"))) {
        val f = names.map { File(dir, it) }.firstOrNull { it.exists() } ?: continue
        BitmapFactory.decodeFile(f.path)?.let { b -> VehicleImages.save(ctx, theme, kind, VehicleImages.prepare(f.inputStream().use { VehicleImages.upright(b, it) }).bitmap); n++ }
      }
      toast(if (n > 0) "Cargadas $n imagen(es)" else "No encontré lateral.png, trasera.png ni superior.png en ${dir.path}", true)
      n
    }.runOnQueue(Queues.MAIN)

    OnActivityResult { _, r ->
      when (r.requestCode) {
        11, 12, 13 -> onPicked(r.requestCode, r.resultCode, r.data)
        21 -> onExport(r.resultCode, r.data)
      }
    }

    // ---------- mini ventana (PiP) ----------
    Function("setPip") { allowed: Boolean, w: Int, h: Int -> pipAllowed = allowed; pipW = w; pipH = h; main.post { updatePipParams() } }
    AsyncFunction("enterPip") { silent: Boolean -> enterPip(silent) }.runOnQueue(Queues.MAIN)
    Function("isInPip") { Build.VERSION.SDK_INT >= 24 && activity?.isInPictureInPictureMode == true }

    // ---------- pantalla, avisos breves y diálogos ----------
    Function("demoArg") { null as String? }                                            // solo existe en el simulador de iOS
    Function("keepScreenOn") { on: Boolean ->
      main.post { activity?.window?.let { if (on) it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    }
    Function("toast") { text: String, long: Boolean -> toast(text, long) }
    AsyncFunction("confirm") { title: String, message: String, ok: String, promise: Promise ->
      val a = activity ?: run { promise.resolve(false); return@AsyncFunction }
      Dialogs.confirm(a, title, message, ok, onCancel = { promise.resolve(false) }) { promise.resolve(true) }
    }.runOnQueue(Queues.MAIN)
    AsyncFunction("choice") { title: String, message: String, yes: String, no: String, promise: Promise ->
      val a = activity ?: run { promise.resolve(false); return@AsyncFunction }
      Dialogs.choice(a, title, message, yes, no, { promise.resolve(true) }, { promise.resolve(false) })
    }.runOnQueue(Queues.MAIN)
    AsyncFunction("pick") { title: String, items: List<String>, promise: Promise ->
      val a = activity ?: run { promise.resolve(-1); return@AsyncFunction }
      Dialogs.pick(a, title, items, onCancel = { promise.resolve(-1) }) { promise.resolve(it) }
    }.runOnQueue(Queues.MAIN)
    AsyncFunction("prompt") { title: String, initial: String, ok: String, promise: Promise ->
      val a = activity ?: run { promise.resolve(null); return@AsyncFunction }
      Dialogs.prompt(a, title, initial, ok, onCancel = { promise.resolve(null) }) { promise.resolve(it) }
    }.runOnQueue(Queues.MAIN)
  }

  private fun toast(text: String, long: Boolean) = main.post { Toast.makeText(ctx, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }

  private fun tripMap(t: Trip) = mapOf(
    "id" to t.id.toDouble(), "name" to t.name, "start" to t.start.toDouble(), "end" to t.end.toDouble(), "sampleMs" to t.sampleMs.toDouble(),
    "max" to t.max.map { it.toDouble() }, "danger" to t.danger, "distance" to t.distance.toDouble(),
    "limitPitch" to t.limitPitch.toDouble(), "limitRoll" to t.limitRoll.toDouble(), "cautionPct" to t.cautionPct.toDouble(),
    "hasGps" to t.hasGps, "samples" to t.samples
  )

  // ---------- exportar ----------
  private fun write(t: Trip, ext: String, out: java.io.OutputStream) { val s = TripDb.get(ctx).samples(t.id); if (ext == "csv") Export.csv(t, s, out) else Export.gpx(t, s, out) }
  private fun onExport(res: Int, data: Intent?) {
    val (t, ext) = exporting ?: return; exporting = null
    val p = exportPromise; exportPromise = null
    val uri = data?.data
    if (res == Activity.RESULT_OK && uri != null) {
      try { ctx.contentResolver.openOutputStream(uri)?.use { write(t, ext, it) }; toast("Exportada", false) }
      catch (e: Exception) { toast("No se pudo exportar: ${e.message}", true) }
    }
    p?.resolve(null)
  }

  // ---------- imágenes del vehículo (se guardan en el tema activo) ----------
  private val reqOf = mapOf("side" to 11, "rear" to 12, "top" to 13)
  private fun pick(kind: String) {
    try { activity!!.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, reqOf[kind]!!) }
    catch (e: Exception) { chain.clear(); toast("Esta unidad no tiene selector de archivos. Usa «Cargar desde carpeta».", true); finishChain() }
  }
  private fun finishChain() {
    if (chainBg) toast("La imagen parece tener fondo. Para un resultado limpio usa un PNG sin fondo.", true)
    chainPromise?.resolve(chainCount); chainPromise = null
  }
  private fun onPicked(req: Int, res: Int, data: Intent?) {
    val kind = reqOf.entries.firstOrNull { it.value == req }?.key ?: return
    val uri = data?.data
    if (res != Activity.RESULT_OK || uri == null) { chain.clear(); finishChain(); return }
    try {
      val cr = ctx.contentResolver
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
      var sample = 1; while (max(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
      val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
      val src = raw?.let { b -> cr.openInputStream(uri)?.use { VehicleImages.upright(b, it) } ?: b }
      if (src != null) {
        val r = VehicleImages.prepare(src); VehicleImages.save(ctx, chainTheme, kind, r.bitmap); chainCount++
        if (r.seemsToHaveBackground) toast("La imagen parece tener fondo. Para un resultado limpio usa un PNG sin fondo.", true)
      }
    } catch (e: Exception) { toast("No se pudo leer la imagen", true) }
    if (chain.firstOrNull() == kind) chain.removeAt(0)
    if (chain.isNotEmpty()) pick(chain.first()) else { chainPromise?.resolve(chainCount); chainPromise = null }
  }

  // ---------- mini ventana (PiP) ----------
  private fun pipSupported() = Build.VERSION.SDK_INT >= 26 && ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
  private fun pipParams(): PictureInPictureParams {
    val b = PictureInPictureParams.Builder().setAspectRatio(Rational(pipW, pipH))
    if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(Config.autoPip && pipAllowed)     // solo desde la pantalla principal
    return b.build()
  }
  private fun updatePipParams() { val a = activity ?: return; if (Build.VERSION.SDK_INT >= 31 && pipSupported()) try { a.setPictureInPictureParams(pipParams()) } catch (_: Exception) {} }
  private fun enterPip(silent: Boolean) {
    val a = activity ?: return
    if (!pipSupported()) {
      if (!silent) toast("Esta unidad no incluye la función de mini ventana (PiP). Prueba la pantalla dividida del sistema.", true)
      return
    }
    if (a.isInPictureInPictureMode) return
    try {
      if (!a.enterPictureInPictureMode(pipParams()) && !silent) toast("No se pudo abrir la mini ventana en esta unidad", true)
    } catch (e: Exception) { if (!silent) toast("No se pudo abrir la mini ventana: ${e.message}", true) }
  }
}
