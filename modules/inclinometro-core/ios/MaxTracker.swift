import Foundation

/// Máximos de inclinación por sentido: un valor solo cuenta si se mantiene al menos [windowMs] (así un bache no deja un pico falso).
/// Se toma el mínimo de la ventana: si durante 0,5 s el ángulo no ha bajado de 25°, el máximo es 25° aunque haya tocado 32° un instante.
final class MaxTracker {
  /// Grados (siempre positivos): 0 morro arriba, 1 morro abajo, 2 alabeo izquierda, 3 alabeo derecha.
  var max: [Float] = [0, 0, 0, 0]
  var windowMs: Int64
  private var times: [Int64] = []
  private var vals: [[Float]] = []

  init(windowMs: Int64) { self.windowMs = windowMs }

  func reset() { max = [0, 0, 0, 0]; times.removeAll(); vals.removeAll() }
  func set(_ values: [Float]) { for i in 0..<4 { max[i] = values[i] } }

  /// Añade una lectura (t en ms). Devuelve true si algún máximo ha subido.
  @discardableResult
  func feed(_ t: Int64, pitch: Float, roll: Float) -> Bool {
    let d: [Float] = [Swift.max(0, pitch), Swift.max(0, -pitch), Swift.max(0, roll), Swift.max(0, -roll)]
    times.append(t); vals.append(d)
    while times.count > 1 && t - times[1] >= windowMs { times.removeFirst(); vals.removeFirst() }   // ventana justa: la lectura más antigua tiene al menos windowMs
    if t - times[0] < windowMs { return false }                                                     // aún no hay historia suficiente
    var changed = false
    for i in 0..<4 {
      var m = Float.greatestFiniteMagnitude
      for v in vals { m = Swift.min(m, v[i]) }
      if m > max[i] { max[i] = m; changed = true }
    }
    return changed
  }
}

/// Milisegundos desde el arranque del sistema (como SystemClock.elapsedRealtime).
func uptimeMs() -> Int64 { Int64(ProcessInfo.processInfo.systemUptime * 1000) }
/// Milisegundos desde 1970 (como System.currentTimeMillis).
func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
