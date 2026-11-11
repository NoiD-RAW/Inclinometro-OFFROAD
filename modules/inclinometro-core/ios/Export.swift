import Foundation

/// Exportación de una salida a CSV (hoja de cálculo) o GPX (recorrido para mapas y GPS), con el mismo formato que en Android.
enum Export {
  private static func iso() -> DateFormatter {
    let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(identifier: "UTC"); f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    return f
  }
  private static func n(_ v: Float?) -> String { guard let v, !v.isNaN else { return "" }; return String(format: "%.1f", v + 0) }      // + 0: evita "-0.0"
  private static func n(_ v: Double?) -> String { guard let v, !v.isNaN else { return "" }; return String(format: "%.6f", v) }
  private static func date(_ f: DateFormatter, _ ms: Int64) -> String { f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000)) }

  static func fileName(_ t: Trip, _ ext: String) -> String {
    let ok = Set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789áéíóúñÁÉÍÓÚÑ _-")
    return String(t.name.map { ok.contains($0) ? $0 : "_" }).trimmingCharacters(in: .whitespaces) + ".\(ext)"
  }

  static func csv(_ t: Trip, _ s: Samples) -> String {
    let f = iso()
    var w = "fecha_utc,ms,cabeceo_grados,alabeo_grados,latitud,longitud,altitud_m,velocidad_kmh\n"
    for i in 0..<s.count {
      w += "\(date(f, s.t[i])),\(s.t[i]),\(n(s.pitch[i])),\(n(s.roll[i])),\(n(s.lat[i])),\(n(s.lon[i])),\(n(s.alt[i])),\(n(s.kmh[i]))\n"
    }
    return w
  }

  /// GPX con el recorrido; el cabeceo y el alabeo de cada punto van en <extensions>. Los puntos sin GPS se omiten.
  static func gpx(_ t: Trip, _ s: Samples) -> String {
    let f = iso(), name = t.name.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;")
    var w = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Inclinómetro OFFROAD\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n"
    w += "<trk><name>\(name)</name><trkseg>\n"
    for i in 0..<s.count where s.lat[i] != nil && s.lon[i] != nil {
      w += "<trkpt lat=\"\(n(s.lat[i]))\" lon=\"\(n(s.lon[i]))\">"
      if let a = s.alt[i] { w += "<ele>\(n(a))</ele>" }
      w += "<time>\(date(f, s.t[i]))</time><extensions><cabeceo>\(n(s.pitch[i]))</cabeceo><alabeo>\(n(s.roll[i]))</alabeo></extensions></trkpt>\n"
    }
    w += "</trkseg></trk>\n</gpx>\n"
    return w
  }
}
