import AudioToolbox
import AVFoundation
import UIKit

/// Niveles de aviso por eje, con histéresis para que no oscile cuando el ángulo ronda el umbral.
enum Alerts {
  static let OK = 0, CAUTION = 1, DANGER = 2
  /// Grados que hay que bajar por debajo de un umbral para salir de esa zona.
  private static let HYST: Float = 2

  private(set) static var pitchLevel = OK
  private(set) static var rollLevel = OK
  static var level: Int { Swift.max(pitchLevel, rollLevel) }
  /// Veces que se ha entrado en zona de peligro desde que arrancó la app (la grabación cuenta las de cada salida).
  private(set) static var dangerEntries = 0

  private static var lastFeedback: Int64 = 0
  /// Eje y lado del último aviso: ±1 alabeo (derecha/izquierda), ±2 cabeceo (arriba/abajo).
  private static var lastSide = 0
  private static var player: AVAudioPlayer?
  /// La app está a la vista. En segundo plano solo vibra: no pita.
  static var appVisible = false

  /// Nivel nuevo de un eje a partir del anterior: se entra al cruzar el umbral y se sale al bajar HYST grados por debajo.
  static func next(_ prev: Int, _ angle: Float, _ limit: Float, _ cautionPct: Float) -> Int {
    let a = abs(angle), c = limit * cautionPct
    var l = prev
    if a >= limit { l = DANGER } else if a >= c && l < CAUTION { l = CAUTION }
    if l == DANGER && a < limit - HYST { l = CAUTION }
    if l == CAUTION && a < c - HYST { l = OK }
    return l
  }

  /// Actualiza los niveles con los ángulos actuales. Al entrar en peligro avisa con vibración y/o pitido si están activados.
  static func update(pitch: Float, roll: Float) {
    let before = level, pBefore = pitchLevel, rBefore = rollLevel
    pitchLevel = next(pitchLevel, pitch, Config.limitPitch, Config.cautionPct)
    rollLevel = next(rollLevel, roll, Config.limitRoll, Config.cautionPct)
    if level == DANGER && before != DANGER { dangerEntries += 1 }
    // Se avisa cada vez que un eje entra en peligro, hacia el lado que sea (no solo cuando cambia el nivel general)
    var side = 0
    if rollLevel == DANGER && rBefore != DANGER { side = roll >= 0 ? 1 : -1 }
    else if pitchLevel == DANGER && pBefore != DANGER { side = pitch >= 0 ? 2 : -2 }
    if side != 0 { feedback(side) }
  }

  /// Vibración y pitido. Un bache que entra y sale del mismo lado avisa como mucho cada 3 s; cambiar de lado avisa siempre.
  private static func feedback(_ side: Int) {
    let now = uptimeMs()
    if side == lastSide && now - lastFeedback < 3000 { return }
    lastFeedback = now; lastSide = side
    if Config.vibrate { vibrate() }
    if Config.beep && appVisible { beep() }
  }

  /// Los iPad no tienen motor de vibración.
  static var canVibrate: Bool { UIDevice.current.userInterfaceIdiom == .phone }

  /// Dos pulsos, como el patrón 300 ms · pausa · 300 ms de Android.
  static func vibrate() {
    guard canVibrate else { return }
    AudioServicesPlaySystemSoundWithCompletion(kSystemSoundID_Vibrate) {
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) }
    }
  }

  /// Pitido de alarma de 0,6 s (dos tonos alternos, como TONE_CDMA_ALERT_CALL_GUARD). Suena aunque el móvil esté en silencio.
  static func beep() {
    do {
      let s = AVAudioSession.sharedInstance()
      try s.setCategory(.playback, mode: .default, options: [.duckOthers])
      try s.setActive(true)
      if player == nil { player = try AVAudioPlayer(data: tone()); player?.volume = 0.9 }
      player?.currentTime = 0
      player?.play()
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) }
    } catch { player = nil }
  }

  /// WAV de 0,6 s: 1319 Hz y 1175 Hz alternos cada 125 ms.
  private static func tone() -> Data {
    let rate = 44100, n = rate * 600 / 1000
    var pcm = Data(capacity: n * 2)
    var phase = 0.0
    for i in 0..<n {
      let f = (i / (rate / 8)) % 2 == 0 ? 1319.0 : 1175.0
      phase += 2 * Double.pi * f / Double(rate)
      let env = min(1, Double(min(i, n - i)) / 200)                                   // sin chasquidos al principio y al final
      var v = Int16(sin(phase) * 0.8 * env * Double(Int16.max)).littleEndian
      withUnsafeBytes(of: &v) { pcm.append(contentsOf: $0) }
    }
    var h = Data()
    func u32(_ x: UInt32) { var v = x.littleEndian; withUnsafeBytes(of: &v) { h.append(contentsOf: $0) } }
    func u16(_ x: UInt16) { var v = x.littleEndian; withUnsafeBytes(of: &v) { h.append(contentsOf: $0) } }
    h.append("RIFF".data(using: .ascii)!); u32(UInt32(36 + pcm.count)); h.append("WAVEfmt ".data(using: .ascii)!)
    u32(16); u16(1); u16(1); u32(UInt32(rate)); u32(UInt32(rate * 2)); u16(2); u16(16)
    h.append("data".data(using: .ascii)!); u32(UInt32(pcm.count))
    return h + pcm
  }
}
