import UIKit

/// Imágenes del vehículo: "side" (lateral, morro a la izquierda), "rear" (trasera o frontal) y "top" (superior, morro arriba).
/// Cada tema guarda las suyas (van_<tema>_<tipo>.png); si el usuario no ha subido ninguna, se usa la que trae el tema.
enum VehicleImages {
  static let kinds = ["side", "rear", "top"]

  static func file(_ theme: String, _ kind: String) -> URL {
    let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
    try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    return dir.appendingPathComponent("van_\(theme)_\(kind).png")
  }
  static func custom(_ theme: String, _ kind: String) -> URL? { let f = file(theme, kind); return FileManager.default.fileExists(atPath: f.path) ? f : nil }
  static func save(_ theme: String, _ kind: String, _ img: CGImage) { try? UIImage(cgImage: img).pngData()?.write(to: file(theme, kind)) }
  static func reset(_ theme: String) { for k in kinds { try? FileManager.default.removeItem(at: file(theme, k)) } }

  /// Deja la imagen lista para los relojes:
  /// 1) reduce las fotos enormes, 2) quita un fondo liso si la imagen no es transparente,
  /// 3) recorta todo el margen vacío (así el vehículo ocupa siempre el mismo espacio) y
  /// 4) fija el lado largo entre 500 y 900 px (ni borroso por pequeño ni pesado por grande).
  static func prepare(_ src: UIImage) -> (image: CGImage, seemsToHaveBackground: Bool)? {
    let s0 = src.size.width * src.scale, s1 = src.size.height * src.scale
    let big = 1600 / Swift.max(s0, s1)
    let k = big < 1 ? big : 1
    let w = Swift.max(1, Int(s0 * k)), h = Swift.max(1, Int(s1 * k))
    guard let px = render(src, w, h) else { return nil }
    var p = px
    func a(_ i: Int) -> Int { Int(p[i * 4 + 3]) }
    var transparent = 0
    for i in 0..<(w * h) where a(i) < 40 { transparent += 1 }
    var seemsBg = false
    if transparent < w * h / 100 {                                                     // sin transparencia: intento quitar un fondo liso
      let br = Int(p[0]), bg = Int(p[1]), bb = Int(p[2])
      func near(_ i: Int) -> Bool { abs(Int(p[i * 4]) - br) + abs(Int(p[i * 4 + 1]) - bg) + abs(Int(p[i * 4 + 2]) - bb) < 60 }
      var seen = [Bool](repeating: false, count: w * h), q = [Int](repeating: 0, count: w * h)
      var head = 0, tail = 0
      func push(_ i: Int) { if !seen[i] && near(i) { seen[i] = true; q[tail] = i; tail += 1 } }
      for x in 0..<w { push(x); push((h - 1) * w + x) }
      for y in 0..<h { push(y * w); push(y * w + w - 1) }
      while head < tail {
        let i = q[head]; head += 1; let x = i % w, y = i / w
        if x > 0 { push(i - 1) }; if x < w - 1 { push(i + 1) }; if y > 0 { push(i - w) }; if y < h - 1 { push(i + w) }
      }
      for i in 0..<(w * h) where seen[i] { p[i * 4] = 0; p[i * 4 + 1] = 0; p[i * 4 + 2] = 0; p[i * 4 + 3] = 0 }
      seemsBg = tail < w * h / 20                                                       // quitó muy poco: el fondo no era liso
    }
    var minX = w, minY = h, maxX = -1, maxY = -1
    for y in 0..<h { for x in 0..<w where a(y * w + x) > 40 { minX = Swift.min(minX, x); maxX = Swift.max(maxX, x); minY = Swift.min(minY, y); maxY = Swift.max(maxY, y) } }
    guard maxX >= minX, let full = image(p, w, h)?.cropping(to: CGRect(x: minX, y: minY, width: maxX - minX + 1, height: maxY - minY + 1)) else {
      return render(src, w, h).flatMap { image($0, w, h) }.map { ($0, true) }
    }
    let long = CGFloat(Swift.max(full.width, full.height))
    let f: CGFloat = long > 900 ? 900 / long : long < 500 ? 500 / long : 1
    if f == 1 { return (full, seemsBg) }
    let ow = Swift.max(1, Int(CGFloat(full.width) * f)), oh = Swift.max(1, Int(CGFloat(full.height) * f))
    guard let scaled = render(UIImage(cgImage: full), ow, oh).flatMap({ image($0, ow, oh) }) else { return (full, seemsBg) }
    return (scaled, seemsBg)
  }

  /// Píxeles RGBA (premultiplicados) de la imagen a ese tamaño.
  private static func render(_ img: UIImage, _ w: Int, _ h: Int) -> [UInt8]? {
    var buf = [UInt8](repeating: 0, count: w * h * 4)
    let ok = buf.withUnsafeMutableBytes { raw -> Bool in
      guard let ctx = CGContext(data: raw.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
      ctx.interpolationQuality = .high
      UIGraphicsPushContext(ctx)
      ctx.translateBy(x: 0, y: CGFloat(h)); ctx.scaleBy(x: 1, y: -1)
      img.draw(in: CGRect(x: 0, y: 0, width: w, height: h))
      UIGraphicsPopContext()
      return true
    }
    return ok ? buf : nil
  }
  private static func image(_ p: [UInt8], _ w: Int, _ h: Int) -> CGImage? {
    let data = Data(p) as CFData
    guard let prov = CGDataProvider(data: data) else { return nil }
    return CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
                   bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue), provider: prov, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
  }
}
