import Foundation

/// Ajustes guardados de toda la app (las mismas claves que en Android). Lo que depende del tema lleva el id del tema en la clave.
enum Config {
  private static let d = UserDefaults.standard

  private static func float(_ k: String, _ def: Float) -> Float { d.object(forKey: k) == nil ? def : d.float(forKey: k) }
  private static func bool(_ k: String, _ def: Bool) -> Bool { d.object(forKey: k) == nil ? def : d.bool(forKey: k) }
  private static func int(_ k: String, _ def: Int) -> Int { d.object(forKey: k) == nil ? def : d.integer(forKey: k) }

  // ---------- montaje del sensor y calibración (comunes a todos los temas) ----------
  static var flat: Bool { get { bool("flat", false) } set { d.set(newValue, forKey: "flat") } }
  static var rot: Int { get { int("rot", 0) } set { d.set(newValue, forKey: "rot") } }
  static var invertPitch: Bool { get { bool("invPitch", true) } set { d.set(newValue, forKey: "invPitch") } }
  static var offRoll: Float { get { float("offRoll", 0) } set { d.set(newValue, forKey: "offRoll") } }
  static var offPitch: Float { get { float("offPitch", 0) } set { d.set(newValue, forKey: "offPitch") } }

  // ---------- límites del vehículo y avisos ----------
  static var limitPitch: Float { get { float("limPitch", 30) } set { d.set(newValue, forKey: "limPitch") } }
  static var limitRoll: Float { get { float("limRoll", 30) } set { d.set(newValue, forKey: "limRoll") } }
  /// Fracción del límite a partir de la cual se avisa en ámbar (0,8 = 80 %).
  static var cautionPct: Float { get { float("caution", 0.8) } set { d.set(newValue, forKey: "caution") } }
  static var vibrate: Bool { get { bool("vibrate", true) } set { d.set(newValue, forKey: "vibrate") } }
  static var beep: Bool { get { bool("beep", false) } set { d.set(newValue, forKey: "beep") } }

  // ---------- máximos registrados ----------
  /// Tiempo que debe mantenerse un ángulo para contar como máximo (filtro de baches).
  static var maxWindowMs: Int { get { int("maxWindow", 500) } set { d.set(newValue, forKey: "maxWindow") } }
  static var maxima: [Float] {
    get { (0..<4).map { float("max\($0)", 0) } }
    set { for i in 0..<4 { d.set(newValue[i], forKey: "max\(i)") } }
  }

  // ---------- salidas ----------
  /// Intervalo entre muestras guardadas durante una salida.
  static var sampleMs: Int { get { int("sampleMs", 1000) } set { d.set(newValue, forKey: "sampleMs") } }
  /// Salida en curso (-1 si no hay) y su hora de inicio. Se guardan para retomarla si el sistema cierra la app.
  static var activeTrip: Int64 {
    get { d.object(forKey: "activeTrip") == nil ? -1 : Int64(d.integer(forKey: "activeTrip")) }
    set { d.set(Int(newValue), forKey: "activeTrip") }
  }
  static var activeStart: Int64 { get { Int64(d.integer(forKey: "activeStart")) } set { d.set(Int(newValue), forKey: "activeStart") } }

  // ---------- mini ventana (solo Android; se guarda igual para que los ajustes sean los mismos) ----------
  static var autoPip: Bool { get { bool("autoPip", true) } set { d.set(newValue, forKey: "autoPip") } }

  // ---------- tema y personalización por tema ----------
  static var theme: String { get { d.string(forKey: "theme") ?? "industrial" } set { d.set(newValue, forKey: "theme") } }
  static func accent(_ theme: String, _ def: Int) -> Int { int("accent_\(theme)", def) }
  static func setAccent(_ theme: String, _ c: Int) { d.set(c, forKey: "accent_\(theme)") }
}
