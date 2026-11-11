import CoreLocation
import CoreMotion
import UIKit

/// Lectura de sensores y GPS, común a todos los temas (la misma lógica que Engine.kt). Se pone en marcha cuando alguien la necesita
/// (la app visible o una salida grabándose) y se detiene cuando ya nadie la usa. Todo ocurre en el hilo principal.
final class Engine: NSObject, CLLocationManagerDelegate {
  static let shared = Engine()

  private let motion = CMMotionManager()
  /// Se crea en el hilo principal (los avisos de ubicación llegan al hilo que lo creó).
  private var lmStore: CLLocationManager?
  private var lm: CLLocationManager {
    if let l = lmStore { return l }
    let l = CLLocationManager()
    l.delegate = self; l.desiredAccuracy = kCLLocationAccuracyBest; l.distanceFilter = kCLDistanceFilterNone
    l.pausesLocationUpdatesAutomatically = false; l.activityType = .automotiveNavigation
    lmStore = l; return l
  }
  private var owners = Set<String>()
  /// Se llaman tras cada lectura nueva (inclinación o GPS).
  var listeners: [String: () -> Void] = [:]
  private var permissionWaiters: [(Bool) -> Void] = []

  /// Gravedad con el criterio de Android (m/s², apuntando hacia arriba en ejes del dispositivo).
  private var g: [Float] = [0, 0, 0]
  private var initG = false
  private(set) var rawRoll: Float = 0
  private(set) var rawPitch: Float = 0
  /// Ángulos ya calibrados, sin recortar (los relojes recortan a ±40° al dibujar).
  private(set) var roll: Float = 0
  private(set) var pitch: Float = 0
  private var gpsBearing: Float?                 // rumbo por GPS (en movimiento)
  private var magHeading: Float?                 // rumbo por magnetómetro (también parado)
  private var declination: Float = 0             // norte magnético -> verdadero (lo da la brújula del sistema)
  private(set) var kmh: Float = 0
  private var prevLoc: CLLocation?
  private(set) var lastLoc: CLLocation?
  private(set) var altitude: Float?
  private var gpsOn = false
  private var motionTick = 0
  private var simTimer: Timer?
  /// Máximos registrados (se guardan en Config y no dependen del tema).
  let maxima = MaxTracker(windowMs: 500)
  private var maxLoaded = false, maxDirty = false
  private var lastMaxSave: Int64 = 0

  /// Rumbo mostrado: GPS si el vehículo se mueve; parado, magnetómetro si existe; si no, el último rumbo del GPS.
  var heading: Float? { kmh >= 4 && gpsBearing != nil ? gpsBearing : (magHeading ?? gpsBearing) }
  /// Hay una posición GPS reciente (de los últimos 5 s).
  var gpsFresh: Bool { if let l = lastLoc { return Date().timeIntervalSince(l.timestamp) < 5 }; return false }

  /// Copia de lo que muestra la interfaz: la lee desde su propio hilo sin tocar el estado que cambia el hilo principal.
  struct Snapshot { var roll = 0.0, pitch = 0.0, heading: Double? = nil, altitude: Double? = nil, kmh = 0.0, gps = false, pitchLevel = 0, rollLevel = 0, max = [0.0, 0.0, 0.0, 0.0] }
  private let snapLock = NSLock()
  private var snap = Snapshot()
  var snapshot: Snapshot { snapLock.lock(); defer { snapLock.unlock() }; return snap }
  private func publish() {
    let s = Snapshot(roll: Double(roll), pitch: Double(pitch), heading: heading.map { Double($0) }, altitude: altitude.map { Double($0) }, kmh: Double(kmh),
                     gps: gpsFresh, pitchLevel: Alerts.pitchLevel, rollLevel: Alerts.rollLevel, max: maxima.max.map { Double($0) })
    snapLock.lock(); snap = s; snapLock.unlock()
  }

  private override init() { super.init() }

  func acquire(_ owner: String) {
    if !maxLoaded { maxima.set(Config.maxima); maxLoaded = true; publish() }
    let first = owners.isEmpty
    owners.insert(owner)
    if first { start() }
  }
  func release(_ owner: String) { if owners.remove(owner) != nil && owners.isEmpty { stop() } }

  private func start() {
    initG = false
    let frames = CMMotionManager.availableAttitudeReferenceFrames()
    if motion.isDeviceMotionAvailable {
      motion.deviceMotionUpdateInterval = 0.02                                           // como SENSOR_DELAY_GAME
      let frame: CMAttitudeReferenceFrame = frames.contains(.xMagneticNorthZVertical) ? .xMagneticNorthZVertical : .xArbitraryCorrectedZVertical
      motion.startDeviceMotionUpdates(using: frame, to: .main) { [weak self] m, _ in
        guard let self, let m else { return }
        self.onGravity(Float(m.gravity.x), Float(m.gravity.y), Float(m.gravity.z))
        if frame == .xMagneticNorthZVertical, m.magneticField.accuracy != .uncalibrated {
          self.motionTick += 1
          if self.motionTick % 3 == 0 { self.updateMagnetic(m) }                           // a ritmo de SENSOR_DELAY_UI
        }
      }
    } else if motion.isAccelerometerAvailable {
      motion.accelerometerUpdateInterval = 0.02
      motion.startAccelerometerUpdates(to: .main) { [weak self] a, _ in
        guard let self, let a else { return }
        self.onGravity(Float(a.acceleration.x), Float(a.acceleration.y), Float(a.acceleration.z))
      }
    } else {
      #if targetEnvironment(simulator)
      // El simulador no tiene sensores: la inclinación se puede fijar con el argumento de arranque -simGravity "x,y,z" (m/s², como adb emu sensor set acceleration)
      simTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
        let p = (UserDefaults.standard.string(forKey: "simGravity") ?? "0,9.81,0").split(separator: ",").compactMap { Float($0.trimmingCharacters(in: .whitespaces)) }
        if p.count == 3 { self?.onGravity(-p[0] / 9.81, -p[1] / 9.81, -p[2] / 9.81) }
        if let h = UserDefaults.standard.string(forKey: "simHeading"), let v = Float(h) { self?.magHeading = v }
        if let h = UserDefaults.standard.string(forKey: "simAltitude"), let v = Float(h) { self?.altitude = v }
      }
      #endif
    }
    startGps()
  }

  private func stop() {
    saveMaxima()
    motion.stopDeviceMotionUpdates(); motion.stopAccelerometerUpdates()
    simTimer?.invalidate(); simTimer = nil
    lm.stopUpdatingLocation(); lm.stopUpdatingHeading()
    gpsOn = false
  }

  /// Se puede consultar desde cualquier hilo.
  var hasLocationPermission: Bool { [.authorizedWhenInUse, .authorizedAlways].contains(lmStore?.authorizationStatus ?? CLLocationManager().authorizationStatus) }
  /// Pide la ubicación «mientras se usa la app». Responde cuando el usuario decide.
  func requestPermission(_ done: @escaping (Bool) -> Void) {
    if lm.authorizationStatus != .notDetermined { done(hasLocationPermission); return }
    permissionWaiters.append(done)
    lm.requestWhenInUseAuthorization()
  }
  func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
    guard m.authorizationStatus != .notDetermined else { return }
    if hasLocationPermission { startGps(); setBackground(Recorder.shared.active) }   // permiso concedido con una salida ya en marcha
    let w = permissionWaiters; permissionWaiters.removeAll()
    for f in w { f(hasLocationPermission) }
  }
  /// Arranca el GPS si hay permiso (también se llama al concederlo). Sin permiso la app sigue funcionando, sin rumbo GPS, altitud ni velocidad.
  func startGps() {
    if gpsOn || owners.isEmpty || !hasLocationPermission { return }
    lm.startUpdatingLocation()
    if CLLocationManager.headingAvailable() { lm.startUpdatingHeading() }
    gpsOn = true
  }
  /// Con una salida en curso, iOS deja seguir leyendo la posición (y la app sigue viva) aunque se cambie a otra app.
  func setBackground(_ on: Bool) {
    if !hasLocationPermission { return }
    lm.allowsBackgroundLocationUpdates = on
    lm.showsBackgroundLocationIndicator = on
  }

  // ---------- calibración ----------
  func calibrate() { Config.offRoll = rawRoll; Config.offPitch = rawPitch; recompute() }
  func resetCal() { Config.offRoll = 0; Config.offPitch = 0; recompute() }
  private func wrap(_ a: Float) -> Float { var x = a.truncatingRemainder(dividingBy: 360); if x > 180 { x -= 360 }; if x <= -180 { x += 360 }; return x }
  private func recompute() { roll = wrap(rawRoll - Config.offRoll); pitch = wrap(rawPitch - Config.offPitch); dispatch() }

  // ---------- máximos ----------
  func resetMaxima() { maxima.reset(); saveMaxima(); dispatch() }
  private func saveMaxima() { if maxLoaded { Config.maxima = maxima.max; maxDirty = false; lastMaxSave = uptimeMs() } }
  /// Con cada lectura: máximos (filtrados) y nivel de aviso. Los máximos se guardan como mucho cada 2 s.
  private func track() {
    let now = uptimeMs()
    maxima.windowMs = Int64(Config.maxWindowMs)
    if maxima.feed(now, pitch: pitch, roll: roll) { maxDirty = true }
    if maxDirty && now - lastMaxSave > 2000 { saveMaxima() }
    Alerts.update(pitch: pitch, roll: roll)
  }
  private func dispatch() { publish(); for l in Array(listeners.values) { l() } }

  /// Giro de la pantalla en pasos de 90°, con el mismo sentido que Display.getRotation en Android.
  private func displayRotation() -> Int {
    let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
    switch scene?.interfaceOrientation {
    case .landscapeRight: return 1                       // girado a la izquierda (botón de inicio a la derecha), como ROTATION_90
    case .portraitUpsideDown: return 2
    case .landscapeLeft: return 3
    default: return 0
    }
  }

  /// Lectura de gravedad de iOS (en g, hacia el suelo): se pasa al criterio de Android y se calcula como en Engine.kt.
  private func onGravity(_ x: Float, _ y: Float, _ z: Float) {
    let v: [Float] = [-x * 9.81, -y * 9.81, -z * 9.81]
    if !initG { g = v; initG = true } else { for i in 0..<3 { g[i] += 0.12 * (v[i] - g[i]) } }
    let sx: Float, sy: Float, sz = g[2]
    switch (displayRotation() + Config.rot) % 4 {
    case 1: sx = -g[1]; sy = g[0]
    case 2: sx = -g[0]; sy = -g[1]
    case 3: sx = g[1]; sy = -g[0]
    default: sx = g[0]; sy = g[1]
    }
    let r: Float, p: Float
    if Config.flat { r = atan2(sx, sz); p = atan2(sy, sz) } else { r = atan2(sx, sy); p = atan2(-sz, sy) }
    rawRoll = r * 180 / .pi; rawPitch = p * 180 / .pi * (Config.invertPitch ? -1 : 1)
    roll = wrap(rawRoll - Config.offRoll); pitch = wrap(rawPitch - Config.offPitch)
    track(); dispatch()
  }

  /// Rumbo con gravedad y campo magnético, como SensorManager.getRotationMatrix + getOrientation:
  /// plano, hacia donde apunta la parte de arriba; vertical, hacia donde apunta la parte trasera.
  private func updateMagnetic(_ m: CMDeviceMotion) {
    let A = [-Float(m.gravity.x), -Float(m.gravity.y), -Float(m.gravity.z)]
    let E = [Float(m.magneticField.field.x), Float(m.magneticField.field.y), Float(m.magneticField.field.z)]
    func cross(_ a: [Float], _ b: [Float]) -> [Float] { [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]] }
    func norm(_ a: [Float]) -> [Float]? { let n = (a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).squareRoot(); return n < 0.1 ? nil : a.map { $0 / n } }
    guard let H = norm(cross(E, A)), let a = norm(A) else { return }
    let M = cross(a, H)
    let dev: [Float] = Config.flat ? [0, 1, 0] : [0, 0, -1]
    let east = H[0] * dev[0] + H[1] * dev[1] + H[2] * dev[2], north = M[0] * dev[0] + M[1] * dev[1] + M[2] * dev[2]
    var az = atan2(east, north) * 180 / .pi + declination
    az = (az + 360).truncatingRemainder(dividingBy: 360)
    if let old = magHeading {
      magHeading = (old + 0.15 * ((az - old + 540).truncatingRemainder(dividingBy: 360) - 180) + 360).truncatingRemainder(dividingBy: 360)
    } else { magHeading = az }
    dispatch()
  }

  func locationManager(_ m: CLLocationManager, didUpdateHeading h: CLHeading) {
    if h.trueHeading >= 0 && h.headingAccuracy >= 0 {
      var dd = Float(h.trueHeading - h.magneticHeading)
      if dd > 180 { dd -= 360 }; if dd < -180 { dd += 360 }
      declination = dd
    }
  }

  // ---------- GPS: altitud, velocidad y rumbo ----------
  func locationManager(_ m: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
    for loc in locs { onLocation(loc) }
  }
  private func onLocation(_ loc: CLLocation) {
    if loc.horizontalAccuracy < 0 || -loc.timestamp.timeIntervalSinceNow > 5 { return }      // posiciones guardadas de antes: el GPS de Android solo da lecturas nuevas
    kmh = loc.speed >= 0 ? Float(loc.speed) * 3.6 : 0
    let okAcc = loc.horizontalAccuracy < 0 || loc.horizontalAccuracy <= 35
    if loc.course >= 0 && kmh >= 2 && okAcc { gpsBearing = Float(loc.course) }                    // rumbo que da el propio GPS
    if let p = prevLoc {
      if okAcc && p.distance(from: loc) >= 6 { gpsBearing = bearing(p, loc); prevLoc = loc }     // o calculado entre dos puntos
    } else { prevLoc = loc }
    // Altitud sobre el elipsoide WGS84, la misma que da Location.getAltitude() en Android
    if loc.verticalAccuracy >= 0 { altitude = Float(loc.ellipsoidalAltitude) }
    lastLoc = loc
    dispatch()
  }
  private func bearing(_ a: CLLocation, _ b: CLLocation) -> Float {
    let la1 = a.coordinate.latitude * .pi / 180, la2 = b.coordinate.latitude * .pi / 180, dl = (b.coordinate.longitude - a.coordinate.longitude) * .pi / 180
    let y = sin(dl) * cos(la2), x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dl)
    return (Float(atan2(y, x) * 180 / .pi) + 360).truncatingRemainder(dividingBy: 360)
  }
}
