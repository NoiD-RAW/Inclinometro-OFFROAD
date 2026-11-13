import CoreLocation
import Foundation

enum Config {
  static var activeTrip: Int64 = -1, activeStart: Int64 = 0
  static var sampleMs = 60_000, maxWindowMs = 500
  static var limitPitch: Float = 30, limitRoll: Float = 30, cautionPct: Float = 0.8
}
enum Alerts { static var dangerEntries = 0 }
final class Engine {
  static let shared = Engine()
  var pitch: Float = 0, roll: Float = 0, kmh: Float = 0
  var altitude: Float?, lastLoc: CLLocation?
  var gpsFresh = false
  var listeners: [String: () -> Void] = [:]
  func acquire(_ owner: String) {}
  func release(_ owner: String) {}
  func setBackground(_ on: Bool) {}
}

@main struct RecordingDistanceTests {
  static func location(_ latitude: Double, at timestamp: Date = Date()) -> CLLocation {
    CLLocation(coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: -1), altitude: 900,
               horizontalAccuracy: 5, verticalAccuracy: 5, course: 0, speed: 0, timestamp: timestamp)
  }
  static func tick(_ latitude: Double, at timestamp: Date = Date(), fresh: Bool = true) {
    Engine.shared.lastLoc = location(latitude, at: timestamp)
    Engine.shared.gpsFresh = fresh
    Engine.shared.listeners["rec"]?()
  }
  static func main() throws {
    let db = TripDb(databaseURL: URL(fileURLWithPath: CommandLine.arguments[1]).appendingPathComponent("distance.db"))
    let recorder = Recorder(database: db)
    Engine.shared.lastLoc = location(42, at: Date(timeIntervalSinceNow: -3600))
    try recorder.begin()
    let firstID = Config.activeTrip
    Engine.shared.listeners["rec"]?()
    tick(43)
    recorder.save()
    let initialDistance = try db.trip(firstID)!.distance
    precondition(initialDistance == 0, "Pre-trip travel must not count")
    tick(43.001)
    try recorder.stop()
    let moved = try db.trip(firstID)!.distance
    precondition(moved > 100 && moved < 120, "Movement inside the recording must still count")

    // Even a fix which is globally fresh must belong to this recording session.
    Engine.shared.lastLoc = location(42, at: Date(timeIntervalSinceNow: -1))
    Engine.shared.gpsFresh = true
    try recorder.begin()
    let secondID = Config.activeTrip
    let samples = try db.samples(secondID)
    precondition(samples.lat[0] == nil && samples.lon[0] == nil, "Do not save pre-start cached coordinates")
    Engine.shared.listeners["rec"]?()
    tick(43)
    try recorder.stop()
    let cachedDistance = try db.trip(secondID)!.distance
    precondition(cachedDistance == 0, "A fresh cached fix must not seed a new trip")

    try recorder.begin()
    let thirdID = Config.activeTrip
    tick(43)
    // No sensor or sampler callback occurs during the gap: the next callback must rebase itself.
    Thread.sleep(forTimeInterval: 5.1)
    tick(44)
    try recorder.stop()
    let gapDistance = try db.trip(thirdID)!.distance
    precondition(gapDistance == 0, "Do not bridge a GPS gap when no intervening tick ran")

    try recorder.begin()
    let fourthID = Config.activeTrip
    let simulatedTime = Date()
    tick(43, at: simulatedTime)
    // The Engine fixture supplies freshness while GPS timestamps advance without a real-time wait.
    // The distance anchor can be older than five seconds because of the 10 m jitter threshold.
    for second in 1...20 { tick(43 + Double(second) * 0.00001, at: simulatedTime.addingTimeInterval(Double(second))) }
    try recorder.stop()
    let slowDistance = try db.trip(fourthID)!.distance
    precondition(slowDistance > 15 && slowDistance < 25, "Continuous slow movement must survive the jitter filter")
    print("Recording distance/session/GPS gap regression: passed")
  }
}
