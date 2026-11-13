import CoreLocation
import Foundation
import SQLite3

/// Salidas: iniciar, finalizar y grabar la que está en curso (lo mismo que Recorder y RecordingService en Android).
/// Con permiso de ubicación sigue grabando aunque la app pase a segundo plano (iOS muestra el indicador azul de ubicación).
final class Recorder {
  static let shared = Recorder()
  static let failureNotification = Notification.Name("InclinometroRecorderFailure")

  // activeTrip conserva una salida interrumpida; «grabando» solo es cierto mientras se escribe.
  var active: Bool { running }
  var start: Int64 { Config.activeStart }
  private let stateLock = NSLock()
  private var isRunning = false
  var running: Bool { stateLock.lock(); defer { stateLock.unlock() }; return isRunning }
  private func setRunning(_ value: Bool) { stateLock.lock(); isRunning = value; stateLock.unlock() }
  private(set) var lastFailure: String?
  private var haltedOnError = false
  private let database: TripDb

  private var trip: Int64 = -1
  private let maxima = MaxTracker(windowMs: 500)
  private var danger = 0, lastDangerEntries = 0
  private var dist: Float = 0, lastLoc: CLLocation?, hasGps = false
  private var distanceSince = Date.distantFuture
  private var lastFixDate: Date?
  private var samples = 0
  private var timer: Timer?

  init(database: TripDb = .shared) { self.database = database }

  func begin() throws {
    if running { return }
    do {
      if Config.activeTrip < 0 {
        let now = nowMs()
        let f = DateFormatter(); f.locale = Locale(identifier: "es_ES"); f.dateFormat = "dd/MM/yyyy HH:mm"
        let id = try database.createTrip(name: "Salida " + f.string(from: Date()), start: now, sampleMs: Int64(Config.sampleMs))
        Config.activeTrip = id
        Config.activeStart = now
      }
      // Tras un error, «Iniciar» retoma la salida pendiente sin perderla ni crear otra.
      try launch()
    } catch { fail(error); throw error }
  }

  func stop() throws {
    guard Config.activeTrip >= 0 else { return }
    do {
      if trip < 0 { try loadTrip() }
      try persist()
      releaseRecording()
      trip = -1; Config.activeTrip = -1; Config.activeStart = 0
      haltedOnError = false; lastFailure = nil
    } catch { fail(error); throw error }
  }

  /// La limpieza no guarda: llamar a save() desde aquí causaría recursión si falla SQLite.
  private func releaseRecording() {
    timer?.invalidate(); timer = nil
    Engine.shared.listeners["rec"] = nil
    Engine.shared.setBackground(false)
    Engine.shared.release("rec")
    setRunning(false)
  }
  private func fail(_ error: Error) {
    releaseRecording()
    haltedOnError = true
    let recovery = Config.activeTrip >= 0
      ? "La salida pendiente se conserva. Revisa el espacio disponible y pulsa Iniciar para retomarla."
      : "No se ha iniciado ninguna salida. Revisa el espacio disponible e inténtalo de nuevo."
    NSLog("Inclinómetro: error al guardar una salida: %@", error.localizedDescription)
    let message = "Grabación detenida. \(recovery)"
    lastFailure = message
    NotificationCenter.default.post(name: Self.failureNotification, object: self, userInfo: ["message": message])
  }

  /// Si el sistema cerró la app con una salida en curso, la retoma al volver a abrirla.
  func ensureRunning() {
    guard Config.activeTrip >= 0, !running, !haltedOnError else { return }
    do { try launch() } catch { fail(error) }
  }

  private func loadTrip() throws {
    let id = Config.activeTrip
    guard let t = try database.trip(id) else {
      // Puede quedar un identificador huérfano de una versión anterior, pero no debe recibir muestras.
      trip = -1; Config.activeTrip = -1; Config.activeStart = 0
      throw TripDbError(operation: "retomar la salida", code: SQLITE_NOTFOUND, reason: "La salida guardada ya no existe")
    }
    // Si se reintenta en este proceso, conserva los máximos y contadores que no se pudieron guardar.
    if trip != id {
      trip = id
      maxima.set(t.max); danger = t.danger; dist = t.distance; hasGps = t.hasGps; samples = t.samples
    }
  }
  private func launch() throws {
    try loadTrip()
    try persist()
    lastLoc = nil; lastFixDate = nil; distanceSince = Date()
    lastDangerEntries = Alerts.dangerEntries
    setRunning(true)
    let e = Engine.shared
    e.acquire("rec"); e.setBackground(true)
    e.listeners["rec"] = { [weak self] in self?.onTick() }
    timer?.invalidate()
    try writeSample()
    haltedOnError = false; lastFailure = nil
  }

  private func onTick() {
    let e = Engine.shared
    maxima.windowMs = Int64(Config.maxWindowMs)
    maxima.feed(uptimeMs(), pitch: e.pitch, roll: e.roll)
    if Alerts.dangerEntries != lastDangerEntries { danger += Alerts.dangerEntries - lastDangerEntries; lastDangerEntries = Alerts.dangerEntries }
    guard let loc = recordingLocation() else { lastLoc = nil; lastFixDate = nil; return }
    if loc !== lastLoc, loc.horizontalAccuracy >= 0 && loc.horizontalAccuracy <= 35 {   // distancia: solo puntos fiables y con movimiento real
      if let previous = lastFixDate, !(0...5).contains(loc.timestamp.timeIntervalSince(previous)) { lastLoc = nil }
      lastFixDate = loc.timestamp
      if let p = lastLoc {
        let d = Float(p.distance(from: loc))
        if d >= Swift.max(10, 2 * Float(loc.horizontalAccuracy)) || loc.speed >= 0.7 { dist += d; lastLoc = loc }
      } else { lastLoc = loc }
    }
  }

  /// Cada inicio o reanudación necesita su propia posición: nunca unir viajes anteriores ni pérdidas de señal.
  private func recordingLocation() -> CLLocation? {
    let e = Engine.shared
    guard e.gpsFresh, let loc = e.lastLoc, loc.timestamp >= distanceSince else { return nil }
    return loc
  }

  private func writeSample() throws {
    guard trip >= 0, running else { return }
    let e = Engine.shared, loc = recordingLocation()
    let fresh = loc != nil
    try database.addSample(trip: trip, t: nowMs(), pitch: e.pitch, roll: e.roll,
                            lat: fresh ? loc?.coordinate.latitude : nil, lon: fresh ? loc?.coordinate.longitude : nil,
                            alt: fresh ? e.altitude : nil, kmh: fresh ? e.kmh : nil)
    if fresh { hasGps = true }
    samples += 1
    if samples % 10 == 0 { try persist() }                                        // si el sistema cierra la app, se pierde como mucho lo último
    let t = Timer(timeInterval: Double(Config.sampleMs) / 1000, repeats: false) { [weak self] _ in
      guard let self else { return }
      do { try self.writeSample() } catch { self.fail(error) }
    }
    RunLoop.main.add(t, forMode: .common)                                          // sigue aunque se esté desplazando una lista
    timer = t
  }

  private func persist() throws {
    if trip >= 0 { try database.updateTrip(trip, end: nowMs(), max: maxima.max, danger: danger, dist: dist, hasGps: hasGps) }
  }

  /// Guardado del ciclo de vida: informa del error y deja de grabar sin perder el id pendiente.
  func save() {
    guard running else { return }
    do { try persist() } catch { fail(error) }
  }

  /// "1:02:03" o "12:03".
  static func clock(_ ms: Int64) -> String {
    let s = ms / 1000
    return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String(format: "%02d:%02d", s / 60, s % 60)
  }
}
