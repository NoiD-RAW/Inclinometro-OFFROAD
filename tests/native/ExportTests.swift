import Foundation

struct Trip { let name: String }
struct Samples {
  let t: [Int64] = [1_700_000_000_000, 1_700_000_001_000, 1_700_000_002_000]
  let pitch: [Float] = [55, -10, -12.5], roll: [Float] = [-45, 3, 8]
  let lat: [Double?] = [42, nil, 42.001], lon: [Double?] = [-1, nil, -1.001]
  let alt: [Float?] = [900, nil, nil], kmh: [Float?] = [10, nil, 12]
  var count: Int { t.count }
}

@main struct ExportTests {
  static func main() throws {
    let trip = Trip(name: "Ruta & <arroyo>")
    try Export.gpx(trip, Samples()).write(toFile: CommandLine.arguments[1], atomically: true, encoding: .utf8)
  }
}
