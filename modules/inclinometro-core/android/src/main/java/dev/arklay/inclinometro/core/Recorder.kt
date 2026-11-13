package dev.arklay.inclinometro.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Salidas: iniciar, finalizar y saber si hay una en curso. La grabación la hace [RecordingService]. */
object Recorder {
    val active get() = Config.activeTrip >= 0
    val start get() = Config.activeStart

    fun start(ctx: Context) {
        if (active) return
        val now = System.currentTimeMillis()
        val name = "Salida " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date(now))
        Config.activeTrip = TripDb.get(ctx).createTrip(name, now, Config.sampleMs); Config.activeStart = now
        launch(ctx)
    }
    fun stop(ctx: Context) { if (active) ctx.startService(Intent(ctx, RecordingService::class.java).setAction(RecordingService.STOP)) }
    /** Si el sistema cerró la app con una salida en curso, la retoma al volver a abrirla. */
    fun ensureRunning(ctx: Context) {
        if (!active || RecordingService.running) return
        try {
            launch(ctx)
        } catch (e: RuntimeException) {
            // No hay una promesa de JS en la recuperación del ciclo de vida; launch ya ha finalizado la salida y avisado.
            if (e !is SecurityException && e !is IllegalStateException) throw e
        }
    }
    private fun launch(ctx: Context) {
        val i = Intent(ctx, RecordingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (e: RuntimeException) {
            if (e !is SecurityException && e !is IllegalStateException) throw e
            startFailed(ctx, e)
            throw e // el puente rechaza startTrip: la interfaz no debe anunciar una salida iniciada
        }
    }

    /** Un inicio rechazado no debe dejar una salida que parezca seguir grabando ni reintentarse al abrir la app. */
    fun startFailed(ctx: Context, error: RuntimeException) {
        Config.activeTrip = -1; Config.activeStart = 0
        Log.w("InclinometroRecorder", "No se pudo iniciar el servicio de grabación", error)
        Toast.makeText(ctx, "No se pudo iniciar la grabación. Revisa los permisos e inténtalo con la app abierta.", Toast.LENGTH_LONG).show()
    }

    /** "1:02:03" o "12:03". */
    fun clock(ms: Long): String { val s = ms / 1000; return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format("%02d:%02d", s / 60, s % 60) }
}

/** Graba la salida en curso aunque la app pase a segundo plano (por ejemplo, al cambiar a la música) o se apague la pantalla.
 *  Va en primer plano con una notificación fija mientras graba. */
class RecordingService : Service() {
    companion object {
        const val STOP = "stop"
        private const val CHANNEL = "salidas"
        var running = false; private set
    }

    private val h = Handler(Looper.getMainLooper())
    private var trip = -1L
    private val maxima = MaxTracker(500L)
    private var danger = 0; private var lastDangerEntries = 0
    private var dist = 0f; private var lastLoc: Location? = null; private var hasGps = false
    private var distanceSince = 0L
    private var lastFixTime: Long? = null
    private var samples = 0
    private var wake: PowerManager.WakeLock? = null

    private val onTick: () -> Unit = {
        maxima.windowMs = Config.maxWindowMs
        maxima.feed(SystemClock.elapsedRealtime(), Engine.pitch, Engine.roll)
        if (Alerts.dangerEntries != lastDangerEntries) { danger += Alerts.dangerEntries - lastDangerEntries; lastDangerEntries = Alerts.dangerEntries }
        val loc = recordingLocation()
        if (loc == null) { lastLoc = null; lastFixTime = null }
        else if (loc !== lastLoc && (!loc.hasAccuracy() || loc.accuracy <= 35f)) {      // distancia: solo puntos fiables y con movimiento real
            if (lastFixTime?.let { loc.time - it !in 0..5000 } == true) lastLoc = null
            lastFixTime = loc.time
            val p = lastLoc
            if (p == null) lastLoc = loc
            else { val d = p.distanceTo(loc); if (d >= maxOf(10f, 2f * loc.accuracy) || (loc.hasSpeed() && loc.speed >= 0.7f)) { dist += d; lastLoc = loc } }
        }
    }
    /** Cada inicio o reanudación necesita su propia posición: nunca unir viajes anteriores ni pérdidas de señal. */
    private fun recordingLocation(): Location? = Engine.lastLoc?.takeIf { Engine.gpsFresh && it.time >= distanceSince }
    private val sampler = object : Runnable {
        override fun run() {
            if (trip < 0) return
            val loc = recordingLocation(); val fresh = loc != null
            TripDb.get(this@RecordingService).addSample(trip, System.currentTimeMillis(), Engine.pitch, Engine.roll,
                if (fresh) loc?.latitude else null, if (fresh) loc?.longitude else null, if (fresh) Engine.altitude else null, if (fresh) Engine.kmh else null)
            if (fresh) hasGps = true
            if (++samples % 10 == 0) save()                                         // si el sistema cierra la app, se pierde como mucho lo último
            h.postDelayed(this, Config.sampleMs)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        Config.init(this)                                                           // el sistema puede reabrir el servicio sin abrir la app
        if (i?.action == STOP) { finish(); return START_NOT_STICKY }
        val id = Config.activeTrip
        if (id < 0) { finish(); return START_NOT_STICKY }
        try {
            startForeground()
        } catch (e: RuntimeException) {
            if (e !is SecurityException && e !is IllegalStateException) throw e
            finish()
            Recorder.startFailed(this, e)
            return START_NOT_STICKY
        }
        if (trip != id) begin(id)
        return START_STICKY                                                         // si el sistema la cierra, se vuelve a abrir y sigue la misma salida
    }

    private fun begin(id: Long) {
        trip = id; running = true
        TripDb.get(this).trip(id)?.let { t -> maxima.set(t.max); danger = t.danger; dist = t.distance; hasGps = t.hasGps; samples = t.samples }   // retomar
        lastLoc = null; lastFixTime = null; distanceSince = System.currentTimeMillis()
        lastDangerEntries = Alerts.dangerEntries
        Engine.acquire(this, this); Engine.listeners += onTick
        wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "inclinometro:salida").apply { acquire(12 * 3600 * 1000L) }
        h.removeCallbacks(sampler); h.post(sampler)
    }

    private fun save() {
        if (trip >= 0) TripDb.get(this).updateTrip(trip, System.currentTimeMillis(), maxima.max, danger, dist, hasGps)
    }

    private fun finish() {
        try {
            save()
        } finally {
            trip = -1; Config.activeTrip = -1; Config.activeStart = 0
            releaseRecording()
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
            stopSelf()
        }
    }

    /** Android 15+ limita dataSync a seis horas en segundo plano: conservar la salida y finalizar sin reiniciarla. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        finish()
        Toast.makeText(this, "Android ha finalizado la grabación en segundo plano. La salida se ha guardado.", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        try {
            save()
        } finally {
            releaseRecording()
            super.onDestroy()
        }
    }

    private fun releaseRecording() {
        running = false
        h.removeCallbacks(sampler); Engine.listeners -= onTick; Engine.release(this)
        wake?.let { if (it.isHeld) it.release() }
        wake = null
    }

    private fun startForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Salida en curso", NotificationManager.IMPORTANCE_LOW))
        val launch = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION") val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val n = b.setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Grabando salida")
            .setContentText("Se guarda el cabeceo, el alabeo y la posición. Finalízala desde la app.")
            .setUsesChronometer(true).setWhen(Recorder.start).setOngoing(true).setContentIntent(open).build()
        // Con permiso de ubicación, tipo «location» (GPS en segundo plano); sin él, la salida se graba igual pero sin posición.
        if (Build.VERSION.SDK_INT >= 29) {
            val type = if (Engine.hasLocationPermission(this)) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            startForeground(1, n, type)
        } else startForeground(1, n)
    }
}
