import ExpoModulesCore
import PhotosUI
import UIKit
import UniformTypeIdentifiers

/// Puente entre la interfaz (TypeScript) y la parte nativa de iOS: sensores, GPS, avisos, salidas e imágenes del vehículo.
/// Los diálogos y los avisos breves los dibuja la interfaz; la mini ventana (PiP) no existe en iOS para una app así.
public class InclinometroCoreModule: Module {
  private var observers: [NSObjectProtocol] = []
  private var foreground = false
  private var picker: PickerDelegate?
  private var exporter: ExportDelegate?

  private func toast(_ text: String, _ long: Bool = true) { sendEvent("onToast", ["text": text, "long": long]) }

  /// Igual que MainActivity: los sensores funcionan mientras la app está a la vista.
  private func enterForeground() {
    if foreground { return }
    foreground = true
    Alerts.appVisible = true
    Engine.shared.acquire("ui"); Recorder.shared.ensureRunning()
  }
  private func enterBackground() { if !foreground { return }; foreground = false; Alerts.appVisible = false; Engine.shared.release("ui"); Recorder.shared.save() }

  public func definition() -> ModuleDefinition {
    Name("InclinometroCore")
    Events("onPip", "onToast")

    OnCreate {
      DispatchQueue.main.async {
        _ = Engine.shared                                                                 // se crea en el hilo principal
        let nc = NotificationCenter.default
        self.observers.append(nc.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in self.enterForeground() })
        self.observers.append(nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { _ in self.enterBackground() })
        if UIApplication.shared.applicationState != .background { self.enterForeground() }
      }
    }
    OnDestroy { for o in self.observers { NotificationCenter.default.removeObserver(o) } }

    Constants([
      "platform": "ios",
      "decimalSeparator": Locale.current.decimalSeparator ?? ",",
    ])

    // ---------- lectura continua (la interfaz la consulta en cada fotograma) ----------
    Function("getState") { () -> [String: Any] in
      let e = Engine.shared.snapshot, r = Recorder.shared
      return [
        "roll": e.roll, "pitch": e.pitch,
        "heading": e.heading as Any, "altitude": e.altitude as Any,
        "kmh": e.kmh, "gps": e.gps,
        "pitchLevel": e.pitchLevel, "rollLevel": e.rollLevel,
        "max": e.max,
        "recording": r.active, "recordStart": Double(r.start), "activeTrip": Double(Config.activeTrip),
      ]
    }

    // ---------- ajustes ----------
    Function("getConfig") { () -> [String: Any] in
      [
        "flat": Config.flat, "rot": Config.rot, "invertPitch": Config.invertPitch,
        "offRoll": Double(Config.offRoll), "offPitch": Double(Config.offPitch),
        "limitPitch": Double(Config.limitPitch), "limitRoll": Double(Config.limitRoll), "cautionPct": Double(Config.cautionPct),
        "vibrate": Config.vibrate, "beep": Config.beep, "maxWindowMs": Double(Config.maxWindowMs), "sampleMs": Double(Config.sampleMs),
        "autoPip": Config.autoPip, "theme": Config.theme,
      ]
    }
    Function("setBool") { (key: String, v: Bool) in
      switch key {
      case "flat": Config.flat = v
      case "invertPitch": Config.invertPitch = v
      case "vibrate": Config.vibrate = v
      case "beep": Config.beep = v
      case "autoPip": Config.autoPip = v
      default: throw Exception(name: "Ajuste", description: "Ajuste desconocido: \(key)")
      }
    }
    Function("setNumber") { (key: String, n: Double) in
      switch key {
      case "rot": Config.rot = Int(n)
      case "limitPitch": Config.limitPitch = Float(n)
      case "limitRoll": Config.limitRoll = Float(n)
      case "cautionPct": Config.cautionPct = Float(n)
      case "maxWindowMs": Config.maxWindowMs = Int(n)
      case "sampleMs": Config.sampleMs = Int(n)
      default: throw Exception(name: "Ajuste", description: "Ajuste desconocido: \(key)")
      }
    }
    Function("setString") { (key: String, v: String) in
      if key == "theme" { Config.theme = v } else { throw Exception(name: "Ajuste", description: "Ajuste desconocido: \(key)") }
    }
    Function("getAccent") { (theme: String, def: Int) in Config.accent(theme, def) }
    Function("setAccent") { (theme: String, color: Int) in Config.setAccent(theme, color) }

    AsyncFunction("calibrate") { Engine.shared.calibrate() }.runOnQueue(.main)
    AsyncFunction("resetCal") { Engine.shared.resetCal() }.runOnQueue(.main)
    AsyncFunction("resetMaxima") { Engine.shared.resetMaxima() }.runOnQueue(.main)

    // ---------- avisos ----------
    Function("canVibrate") { Alerts.canVibrate }
    AsyncFunction("testAlert") { () -> Bool in
      if Config.vibrate { Alerts.vibrate() }; if Config.beep { Alerts.beep() }
      return Config.beep || (Config.vibrate && Alerts.canVibrate)
    }.runOnQueue(.main)

    // ---------- permisos ----------
    Function("hasLocationPermission") { Engine.shared.hasLocationPermission }
    Function("hasNotificationPermission") { true }                                       // en iOS la grabación no usa notificación
    AsyncFunction("requestPermissions") { (location: Bool, _: Bool, promise: Promise) in
      if !location { promise.resolve(Engine.shared.hasLocationPermission); return }
      Engine.shared.requestPermission { promise.resolve($0) }
    }.runOnQueue(.main)

    // ---------- salidas ----------
    AsyncFunction("startTrip") { Recorder.shared.begin() }.runOnQueue(.main)
    AsyncFunction("stopTrip") { Recorder.shared.stop() }.runOnQueue(.main)

    Function("trips") { (byAngle: Bool) in TripDb.shared.trips(byAngle: byAngle).map(tripMap) }
    Function("trip") { (id: Double) -> [String: Any]? in TripDb.shared.trip(Int64(id)).map(tripMap) }
    Function("samples") { (id: Double) -> [String: Any] in
      let s = TripDb.shared.samples(Int64(id))
      return [
        "t": s.t.map { Double($0) }, "pitch": s.pitch.map { Double($0) }, "roll": s.roll.map { Double($0) },
        "lat": s.lat.map { $0 as Any }, "lon": s.lon.map { $0 as Any },
        "alt": s.alt.map { $0.map { Double($0) } as Any }, "kmh": s.kmh.map { $0.map { Double($0) } as Any },
      ]
    }
    Function("hotspots") {
      TripDb.shared.hotspots().map { h -> [String: Any] in
        ["trip": Double(h.trip), "tripName": h.tripName, "t": Double(h.t), "lat": h.lat, "lon": h.lon, "pitch": Double(h.pitch), "roll": Double(h.roll), "ratio": Double(h.ratio)]
      }
    }
    Function("renameTrip") { (id: Double, name: String) in TripDb.shared.rename(Int64(id), name) }
    Function("deleteTrip") { (id: Double) in TripDb.shared.delete(Int64(id)) }
    Function("deleteOlderThan") { (before: Double, keep: Double) in TripDb.shared.deleteOlderThan(Int64(min(before, 9e15)), keep: Int64(keep)) }
    Function("dbSize") { Double(TripDb.shared.sizeBytes()) }

    /// Guardar como… (selector de Archivos del sistema), como el selector de documentos de Android.
    AsyncFunction("exportTrip") { (id: Double, ext: String, promise: Promise) in
      guard let t = TripDb.shared.trip(Int64(id)), let vc = self.appContext?.utilities?.currentViewController() else { promise.resolve(nil); return }
      let s = TripDb.shared.samples(t.id)
      let url = FileManager.default.temporaryDirectory.appendingPathComponent(Export.fileName(t, ext))
      do { try (ext == "csv" ? Export.csv(t, s) : Export.gpx(t, s)).write(to: url, atomically: true, encoding: .utf8) }
      catch { self.toast("No se pudo exportar: \(error.localizedDescription)"); promise.resolve(nil); return }
      let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
      let d = ExportDelegate { ok in
        if ok { self.toast("Exportada", false) }
        try? FileManager.default.removeItem(at: url)
        self.exporter = nil; promise.resolve(nil)
      }
      self.exporter = d; picker.delegate = d
      picker.isModalInPresentation = true
      vc.present(picker, animated: true)
    }.runOnQueue(.main)

    // ---------- imágenes del vehículo ----------
    Function("vehicleUri") { (theme: String, kind: String) -> String? in
      guard let f = VehicleImages.custom(theme, kind) else { return nil }
      let m = (try? FileManager.default.attributesOfItem(atPath: f.path)[.modificationDate] as? Date)?.timeIntervalSince1970 ?? 0
      return f.absoluteString + "?v=\(Int(m * 1000))"
    }
    Function("resetVehicle") { (theme: String) in VehicleImages.reset(theme) }
    /// "Subir fotos": lateral, trasera y superior, una tras otra.
    AsyncFunction("pickVehicle") { (theme: String, kinds: [String], promise: Promise) in
      guard let vc = self.appContext?.utilities?.currentViewController() else { promise.resolve(0); return }
      var queue = kinds, count = 0
      func next() {
        guard let kind = queue.first else { self.picker = nil; promise.resolve(count); return }
        var cfg = PHPickerConfiguration(); cfg.filter = .images; cfg.selectionLimit = 1
        let p = PHPickerViewController(configuration: cfg)
        let d = PickerDelegate { item in
          guard let item, item.canLoadObject(ofClass: UIImage.self) else { queue.removeAll(); next(); return }   // cancelado: se acaba la cadena
          item.loadObject(ofClass: UIImage.self) { obj, _ in
            DispatchQueue.main.async {
              if let img = obj as? UIImage, let r = VehicleImages.prepare(img) {
                VehicleImages.save(theme, kind, r.image); count += 1
                if r.seemsToHaveBackground { self.toast("La imagen parece tener fondo. Para un resultado limpio usa un PNG sin fondo.") }
              } else { self.toast("No se pudo leer la imagen") }
              queue.removeFirst(); next()
            }
          }
        }
        self.picker = d; p.delegate = d
        p.isModalInPresentation = true                                                    // se cierra con «Cancelar», así siempre hay respuesta
        vc.present(p, animated: true)
      }
      next()
    }.runOnQueue(.main)
    AsyncFunction("loadFolder") { (_: String) in 0 }                                          // solo Android

    // ---------- mini ventana (solo Android) ----------
    Function("setPip") { (_: Bool, _: Int, _: Int) in }
    AsyncFunction("enterPip") { (_: Bool) in }
    Function("isInPip") { false }

    // ---------- pantalla, avisos breves y diálogos (en iOS los dibuja la interfaz) ----------
    /// Solo en el simulador: argumento de arranque -demo (abrir pantallas o iniciar/finalizar una salida sin poder tocar la pantalla).
    Function("demoArg") { () -> String? in
      #if targetEnvironment(simulator)
      return UserDefaults.standard.string(forKey: "demo")
      #else
      return nil
      #endif
    }
    Function("keepScreenOn") { (on: Bool) in DispatchQueue.main.async { UIApplication.shared.isIdleTimerDisabled = on } }
    Function("toast") { (_: String, _: Bool) in }
    AsyncFunction("confirm") { (_: String, _: String, _: String) in false }
    AsyncFunction("choice") { (_: String, _: String, _: String, _: String) in false }
    AsyncFunction("pick") { (_: String, _: [String]) in -1 }
    AsyncFunction("prompt") { (_: String, _: String, _: String) -> String? in nil }
  }
}

private func tripMap(_ t: Trip) -> [String: Any] {
  [
    "id": Double(t.id), "name": t.name, "start": Double(t.start), "end": Double(t.end), "sampleMs": Double(t.sampleMs),
    "max": t.max.map { Double($0) }, "danger": t.danger, "distance": Double(t.distance),
    "limitPitch": Double(t.limitPitch), "limitRoll": Double(t.limitRoll), "cautionPct": Double(t.cautionPct),
    "hasGps": t.hasGps, "samples": t.samples,
  ]
}

private final class PickerDelegate: NSObject, PHPickerViewControllerDelegate {
  let done: (NSItemProvider?) -> Void
  init(_ done: @escaping (NSItemProvider?) -> Void) { self.done = done }
  func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
    picker.dismiss(animated: true) { self.done(results.first?.itemProvider) }
  }
}

private final class ExportDelegate: NSObject, UIDocumentPickerDelegate {
  let done: (Bool) -> Void
  init(_ done: @escaping (Bool) -> Void) { self.done = done }
  func documentPicker(_ c: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { done(true) }
  func documentPickerWasCancelled(_ c: UIDocumentPickerViewController) { done(false) }
}
