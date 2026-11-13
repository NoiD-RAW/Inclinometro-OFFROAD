import Foundation
import CoreLocation
import SQLite3

// Only hardware and preferences are replaced. TripDb, Recorder and MaxTracker are production files.
enum Config {
  static var limitPitch: Float = 30, limitRoll: Float = 30, cautionPct: Float = 0.8
  static var activeTrip: Int64 = -1, activeStart: Int64 = 0
  static var sampleMs = 1, maxWindowMs = 500
}
enum Alerts { static var dangerEntries = 0 }
final class Engine {
  static let shared = Engine()
  var pitch: Float = 0, roll: Float = 0, altitude: Float?, kmh: Float = 0
  var gpsFresh = false, lastLoc: CLLocation?
  var listeners: [String: () -> Void] = [:]
  var owners = Set<String>()
  var background = false
  func acquire(_ owner: String) { owners.insert(owner) }
  func release(_ owner: String) { owners.remove(owner) }
  func setBackground(_ value: Bool) { background = value }
}

@main struct StorageTests {
  static func check(_ value: Bool, _ message: String = "Storage assertion failed") { precondition(value, message) }
  static func expectFailure(_ label: String, _ action: () throws -> Void) {
    do { try action(); preconditionFailure("Expected failure: \(label)") }
    catch { print("Expected \(label): \(error.localizedDescription)") }
  }
  static func execute(_ db: OpaquePointer, _ sql: String) {
    precondition(sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK, "Fault setup failed: \(sql)")
  }
  static func reset() {
    Config.activeTrip = -1; Config.activeStart = 0
    Engine.shared.listeners.removeAll(); Engine.shared.owners.removeAll(); Engine.shared.background = false
  }
  static func main() throws {
    let root = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
    var serial = 0
    func store(_ name: String) -> (TripDb, OpaquePointer) {
      serial += 1
      var handle: OpaquePointer?
      let db = TripDb(databaseURL: root.appendingPathComponent("\(serial)-\(name).db")) { handle = $0 }
      return (db, handle!)
    }
    var failures = 0
    let observer = NotificationCenter.default.addObserver(forName: Recorder.failureNotification, object: nil, queue: nil) { note in
      failures += 1
      let message = note.userInfo?["message"] as? String
      precondition(message != nil && !message!.contains("SQLite"), "Product message must stay friendly")
    }
    defer { NotificationCenter.default.removeObserver(observer) }

    // Open failures remain errors; no fake identifier or recorder state is returned.
    reset()
    let unavailable = TripDb(databaseURL: root) // A directory cannot be opened as a SQLite database.
    let unavailableRecorder = Recorder(database: unavailable)
    expectFailure("open") { try unavailableRecorder.begin() }
    precondition(Config.activeTrip == -1 && !unavailableRecorder.active)
    expectFailure("read after failed open") { _ = try unavailable.trips(byAngle: false) }

    // Removing a temporary filesystem obstruction must let the same instance recover.
    reset()
    let blockedURL = root.appendingPathComponent("temporarily-blocked.db", isDirectory: true)
    try FileManager.default.createDirectory(at: blockedURL, withIntermediateDirectories: true)
    let retryStore = TripDb(databaseURL: blockedURL)
    let retryRecorder = Recorder(database: retryStore)
    expectFailure("temporarily blocked open") { try retryRecorder.begin() }
    try FileManager.default.removeItem(at: blockedURL)
    try retryRecorder.begin()
    precondition(retryRecorder.active && Config.activeTrip > 0, "Opening must retry after the storage problem is resolved")
    try retryRecorder.stop()

    // Real SQLITE_FULL without filling the user's filesystem.
    reset()
    let (full, fullHandle) = store("full")
    let saved = try full.createTrip(name: "existing", start: 1, sampleMs: 1)
    try full.addSample(trip: saved, t: 1, pitch: 0, roll: 0, lat: nil, lon: nil, alt: nil, kmh: nil)
    execute(fullHandle, "PRAGMA max_page_count=5")
    expectFailure("SQLITE_FULL insert") {
      _ = try full.createTrip(name: String(repeating: "x", count: 100_000), start: 2, sampleMs: 1)
    }
    check(try full.trips(byAngle: false).map(\.id) == [saved], "Failed insertion must not return or create a trip")
    execute(fullHandle, "PRAGMA max_page_count=1000000")
    expectFailure("orphan sample") { try full.addSample(trip: 999, t: 1, pitch: 0, roll: 0, lat: nil, lon: nil, alt: nil, kmh: nil) }
    check(try full.samples(999).count == 0)

    // Bind and prepare failures are surfaced, rather than becoming an empty success.
    let (binding, bindingHandle) = store("binding")
    sqlite3_limit(bindingHandle, SQLITE_LIMIT_LENGTH, 100)
    expectFailure("bind") { _ = try binding.createTrip(name: String(repeating: "a", count: 1000), start: 1, sampleMs: 1) }
    sqlite3_limit(bindingHandle, SQLITE_LIMIT_LENGTH, 1_000_000_000)
    execute(bindingHandle, "DROP TABLE trips")
    expectFailure("prepare") { _ = try binding.trips(byAngle: false) }

    // A failed create never activates a row ID left by an earlier insertion.
    reset()
    let (creation, creationHandle) = store("creation")
    execute(creationHandle, "CREATE TRIGGER fail_create BEFORE INSERT ON trips BEGIN SELECT RAISE(FAIL, 'injected create failure'); END")
    let creationRecorder = Recorder(database: creation)
    expectFailure("recorder create") { try creationRecorder.begin() }
    precondition(Config.activeTrip == -1 && !creationRecorder.active && Engine.shared.owners.isEmpty)

    // A first-sample failure preserves the valid trip for an explicit retry, while stopping resources.
    reset()
    let (sampling, samplingHandle) = store("sampling")
    execute(samplingHandle, "CREATE TRIGGER fail_sample BEFORE INSERT ON samples BEGIN SELECT RAISE(FAIL, 'injected sample failure'); END")
    let samplingRecorder = Recorder(database: sampling)
    expectFailure("first sample") { try samplingRecorder.begin() }
    let pending = Config.activeTrip
    precondition(pending > 0 && !samplingRecorder.active && !samplingRecorder.running)
    precondition(Engine.shared.owners.isEmpty && Engine.shared.listeners["rec"] == nil && !Engine.shared.background)
    check(try sampling.trip(pending) != nil)
    let stoppedFailures = failures
    samplingRecorder.ensureRunning()
    precondition(failures == stoppedFailures, "Foreground transitions must not create an automatic error loop")
    execute(samplingHandle, "DROP TRIGGER fail_sample")
    try samplingRecorder.begin()
    precondition(Config.activeTrip == pending && samplingRecorder.active)
    check(try sampling.trips(byAngle: false).count == 1, "Retry must resume the existing trip")
    try samplingRecorder.stop()
    precondition(Config.activeTrip == -1 && Config.activeStart == 0 && !samplingRecorder.active)

    // Actual Timer callback: a later write failure stops once and does not reschedule itself.
    reset()
    let (periodic, periodicHandle) = store("periodic")
    let periodicRecorder = Recorder(database: periodic)
    try periodicRecorder.begin()
    let periodicID = Config.activeTrip
    execute(periodicHandle, "CREATE TRIGGER fail_periodic BEFORE INSERT ON samples BEGIN SELECT RAISE(FAIL, 'injected periodic failure'); END")
    let previousFailures = failures
    RunLoop.main.run(until: Date().addingTimeInterval(0.03))
    precondition(!periodicRecorder.active && Config.activeTrip == periodicID)
    precondition(failures == previousFailures + 1, "Only one failure should be reported")
    check(try periodic.samples(periodicID).count == 1)
    RunLoop.main.run(until: Date().addingTimeInterval(0.02))
    precondition(failures == previousFailures + 1)
    execute(periodicHandle, "DROP TRIGGER fail_periodic")
    try periodicRecorder.stop()

    // Checkpoint/stop failures retain the active ID and data until a successful retry.
    reset()
    let (checkpoint, checkpointHandle) = store("checkpoint")
    let checkpointRecorder = Recorder(database: checkpoint)
    try checkpointRecorder.begin()
    let checkpointID = Config.activeTrip
    execute(checkpointHandle, "CREATE TRIGGER fail_save BEFORE UPDATE ON trips BEGIN SELECT RAISE(FAIL, 'injected save failure'); END")
    checkpointRecorder.save()
    precondition(!checkpointRecorder.active && Config.activeTrip == checkpointID)
    expectFailure("stop checkpoint") { try checkpointRecorder.stop() }
    precondition(Config.activeTrip == checkpointID && checkpointRecorder.lastFailure != nil)
    execute(checkpointHandle, "DROP TRIGGER fail_save")
    try checkpointRecorder.stop()
    precondition(Config.activeTrip == -1 && checkpointRecorder.lastFailure == nil)
    check(try checkpoint.samples(checkpointID).count == 1)

    // A failed multi-statement deletion must not discard samples while keeping their trip.
    let (deletion, deletionHandle) = store("deletion")
    let deletionID = try deletion.createTrip(name: "kept", start: 1, sampleMs: 1)
    try deletion.addSample(trip: deletionID, t: 1, pitch: 5, roll: 5, lat: nil, lon: nil, alt: nil, kmh: nil)
    execute(deletionHandle, "CREATE TRIGGER fail_delete BEFORE DELETE ON trips BEGIN SELECT RAISE(FAIL, 'injected delete failure'); END")
    expectFailure("delete rollback") { try deletion.delete(deletionID) }
    check(try deletion.trip(deletionID) != nil && deletion.samples(deletionID).count == 1)
    print("Swift SQLite/recorder fault regressions: passed")
  }
}
