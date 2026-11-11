import CoreLocation
import Foundation

/// Salidas: iniciar, finalizar y grabar la que está en curso (lo mismo que Recorder y RecordingService en Android).
/// Con permiso de ubicación sigue grabando aunque la app pase a segundo plano (iOS muestra el indicador azul de ubicación).
final class Recorder {
  static let shared = Recorder()

  var active: Bool { Config.activeTrip >= 0 }
  var start: Int64 { Config.activeStart }
  private(set) var running = false

  private var trip: Int64 = -1
  private let maxima = MaxTracker(windowMs: 500)
  private var danger = 0, lastDangerEntries = 0
  private var dist: Float = 0, lastLoc: CLLocation?, hasGps = false
  private var samples = 0
  private var timer: Timer?

  func begin() {
    if active { return }
    let now = nowMs()
    let f = DateFormatter(); f.locale = Locale(identifier: "es_ES"); f.dateFormat = "dd/MM/yyyy HH:mm"
    Config.activeTrip = TripDb.shared.createTrip(name: "Salida " + f.string(from: Date()), start: now, sampleMs: Int64(Config.sampleMs))
    Config.activeStart = now
    launch()
  }

  func stop() {
    guard active else { return }
    save(); trip = -1; Config.activeTrip = -1
    timer?.invalidate(); timer = nil
    Engine.shared.listeners["rec"] = nil
    Engine.shared.setBackground(false)
    Engine.shared.release("rec")
    running = false
  }

  /// Si el sistema cerró la app con una salida en curso, la retoma al volver a abrirla.
  func ensureRunning() { if active && !running { launch() } }

  private func launch() {
    let id = Config.activeTrip
    trip = id; running = true
    if let t = TripDb.shared.trip(id) { maxima.set(t.max); danger = t.danger; dist = t.distance; hasGps = t.hasGps; samples = t.samples }   // retomar
    else { maxima.reset(); danger = 0; dist = 0; hasGps = false; samples = 0 }
    lastLoc = nil
    lastDangerEntries = Alerts.dangerEntries
    let e = Engine.shared
    e.acquire("rec"); e.setBackground(true)
    e.listeners["rec"] = { [weak self] in self?.onTick() }
    timer?.invalidate()
    sample()
  }

  private func onTick() {
    let e = Engine.shared
    maxima.windowMs = Int64(Config.maxWindowMs)
    maxima.feed(uptimeMs(), pitch: e.pitch, roll: e.roll)
    if Alerts.dangerEntries != lastDangerEntries { danger += Alerts.dangerEntries - lastDangerEntries; lastDangerEntries = Alerts.dangerEntries }
    if let loc = e.lastLoc, loc !== lastLoc, loc.horizontalAccuracy < 0 || loc.horizontalAccuracy <= 35 {   // distancia: solo puntos fiables y con movimiento real
      if let p = lastLoc {
        let d = Float(p.distance(from: loc))
        if d >= Swift.max(10, 2 * Float(loc.horizontalAccuracy)) || loc.speed >= 0.7 { dist += d; lastLoc = loc }
      } else { lastLoc = loc }
    }
  }

  private func sample() {
    guard trip >= 0 else { return }
    let e = Engine.shared, fresh = e.gpsFresh, loc = e.lastLoc
    TripDb.shared.addSample(trip: trip, t: nowMs(), pitch: e.pitch, roll: e.roll,
                            lat: fresh ? loc?.coordinate.latitude : nil, lon: fresh ? loc?.coordinate.longitude : nil,
                            alt: fresh ? e.altitude : nil, kmh: fresh ? e.kmh : nil)
    if fresh { hasGps = true }
    samples += 1
    if samples % 10 == 0 { save() }                                                // si el sistema cierra la app, se pierde como mucho lo último
    let t = Timer(timeInterval: Double(Config.sampleMs) / 1000, repeats: false) { [weak self] _ in self?.sample() }
    RunLoop.main.add(t, forMode: .common)                                          // sigue aunque se esté desplazando una lista
    timer = t
  }

  func save() { if trip >= 0 { TripDb.shared.updateTrip(trip, end: nowMs(), max: maxima.max, danger: danger, dist: dist, hasGps: hasGps) } }

  /// "1:02:03" o "12:03".
  static func clock(_ ms: Int64) -> String {
    let s = ms / 1000
    return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String(format: "%02d:%02d", s / 60, s % 60)
  }
}
