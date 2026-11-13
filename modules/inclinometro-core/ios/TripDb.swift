import Foundation
import SQLite3

/// Una salida grabada. Los máximos van en grados positivos por sentido (ver MaxTracker).
struct Trip {
  let id: Int64; var name: String; let start: Int64; let end: Int64; let sampleMs: Int64
  let max: [Float]; let danger: Int; let distance: Float
  let limitPitch: Float; let limitRoll: Float; let cautionPct: Float; let hasGps: Bool; let samples: Int
  var duration: Int64 { Swift.max(0, end - start) }
}

/// Muestras de una salida en columnas. Sin GPS, lat/lon son nil.
struct Samples {
  var t: [Int64] = [], pitch: [Float] = [], roll: [Float] = []
  var lat: [Double?] = [], lon: [Double?] = [], alt: [Float?] = [], kmh: [Float?] = []
  var count: Int { t.count }
}

/// Punto con inclinación alta, para el mapa de puntos críticos.
struct Hotspot { let trip: Int64; let tripName: String; let t: Int64; let lat: Double; let lon: Double; let pitch: Float; let roll: Float; let ratio: Float }

private let TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

struct TripDbError: LocalizedError {
  let operation: String
  let code: Int32
  let reason: String
  var errorDescription: String? { "No se pudo \(operation): \(reason) (SQLite \(code))." }
}

/// Salidas guardadas en el propio dispositivo (SQLite, con las mismas tablas que en Android).
final class TripDb {
  static let shared = TripDb()
  private var db: OpaquePointer?
  private let configure: ((OpaquePointer) -> Void)?
  let url: URL

  private convenience init() {
    self.init(databaseURL: FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("salidas.db"))
  }

  /// La URL explícita permite comprobar los fallos con una base aislada, sin tocar los datos de la app.
  init(databaseURL: URL, configure: ((OpaquePointer) -> Void)? = nil) {
    url = databaseURL
    self.configure = configure
    // Los errores se propagan al usar la base; un fallo transitorio puede reintentarse sin reiniciar.
    try? openDatabase()
  }

  private func openDatabase() throws {
    do {
      try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
      let result = sqlite3_open_v2(url.path, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil)
      guard result == SQLITE_OK, db != nil else { throw failure("abrir las salidas", code: result) }
      if let db { configure?(db) }
      try exec("PRAGMA journal_mode=WAL")
      try exec("""
      CREATE TABLE IF NOT EXISTS trips (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, start INTEGER NOT NULL, end_t INTEGER NOT NULL,
        sample_ms INTEGER NOT NULL, max_pu REAL DEFAULT 0, max_pd REAL DEFAULT 0, max_rl REAL DEFAULT 0, max_rr REAL DEFAULT 0,
        danger INTEGER DEFAULT 0, dist REAL DEFAULT 0, lim_pitch REAL NOT NULL, lim_roll REAL NOT NULL, caution REAL NOT NULL, has_gps INTEGER DEFAULT 0)
      """)
      try exec("CREATE TABLE IF NOT EXISTS samples (trip INTEGER NOT NULL, t INTEGER NOT NULL, pitch REAL NOT NULL, roll REAL NOT NULL, lat REAL, lon REAL, alt REAL, kmh REAL)")
      try exec("CREATE INDEX IF NOT EXISTS samples_trip ON samples(trip, t)")
    } catch {
      if let db { sqlite3_close_v2(db) }
      db = nil
      throw error
    }
  }

  deinit { if let db { sqlite3_close_v2(db) } }

  // ---------- utilidades ----------
  /// La interfaz consulta desde su hilo y la grabación escribe desde el principal: todo pasa por este cerrojo.
  private let lock = NSRecursiveLock()
  private func failure(_ operation: String, code: Int32) -> TripDbError {
    TripDbError(operation: operation, code: code,
                reason: db.map { String(cString: sqlite3_errmsg($0)) } ?? String(cString: sqlite3_errstr(code)))
  }
  private func connection() throws -> OpaquePointer {
    if db == nil { try openDatabase() }
    guard let db else { throw failure("abrir las salidas", code: SQLITE_CANTOPEN) }
    return db
  }
  private func exec(_ sql: String) throws {
    lock.lock(); defer { lock.unlock() }
    let db = try connection()
    let result = sqlite3_exec(db, sql, nil, nil, nil)
    guard result == SQLITE_OK else { throw failure("actualizar las salidas", code: result) }
  }
  private enum V { case i(Int64), d(Double), s(String), null }
  private func run(_ sql: String, _ args: [V] = [], row: ((OpaquePointer) -> Void)? = nil) throws {
    lock.lock(); defer { lock.unlock() }
    let db = try connection()
    var st: OpaquePointer?
    let prepared = sqlite3_prepare_v2(db, sql, -1, &st, nil)
    guard prepared == SQLITE_OK, let st else {
      if let st { sqlite3_finalize(st) }
      throw failure("preparar las salidas", code: prepared)
    }
    defer { sqlite3_finalize(st) }
    for (k, a) in args.enumerated() {
      let i = Int32(k + 1)
      let bound: Int32
      switch a {
      case .i(let v): bound = sqlite3_bind_int64(st, i, v)
      case .d(let v): bound = sqlite3_bind_double(st, i, v)
      case .s(let v): bound = sqlite3_bind_text(st, i, v, -1, TRANSIENT)
      case .null: bound = sqlite3_bind_null(st, i)
      }
      guard bound == SQLITE_OK else { throw failure("preparar los datos de la salida", code: bound) }
    }
    while true {
      let result = sqlite3_step(st)
      if result == SQLITE_ROW { row?(st) }
      else if result == SQLITE_DONE { return }
      else { throw failure("leer o guardar la salida", code: result) }
    }
  }
  private func str(_ st: OpaquePointer, _ i: Int32) -> String { sqlite3_column_text(st, i).map { String(cString: $0) } ?? "" }
  private func dbl(_ st: OpaquePointer, _ i: Int32) -> Double? { sqlite3_column_type(st, i) == SQLITE_NULL ? nil : sqlite3_column_double(st, i) }

  // ---------- salidas ----------
  func createTrip(name: String, start: Int64, sampleMs: Int64) throws -> Int64 {
    lock.lock(); defer { lock.unlock() }
    try run("INSERT INTO trips (name, start, end_t, sample_ms, lim_pitch, lim_roll, caution) VALUES (?, ?, ?, ?, ?, ?, ?)",
        [.s(name), .i(start), .i(start), .i(sampleMs), .d(Double(Config.limitPitch)), .d(Double(Config.limitRoll)), .d(Double(Config.cautionPct))])
    return sqlite3_last_insert_rowid(try connection())
  }

  func addSample(trip: Int64, t: Int64, pitch: Float, roll: Float, lat: Double?, lon: Double?, alt: Float?, kmh: Float?) throws {
    lock.lock(); defer { lock.unlock() }
    let pos = lat != nil && lon != nil
    try run("INSERT INTO samples (trip, t, pitch, roll, lat, lon, alt, kmh) SELECT ?, ?, ?, ?, ?, ?, ?, ? WHERE EXISTS (SELECT 1 FROM trips WHERE id=?)", [
      .i(trip), .i(t), .d(Double(pitch)), .d(Double(roll)),
      pos ? .d(lat!) : .null, pos ? .d(lon!) : .null, alt.map { .d(Double($0)) } ?? .null, kmh.map { .d(Double($0)) } ?? .null,
      .i(trip),
    ])
    try requireTripChange()
  }

  func updateTrip(_ id: Int64, end: Int64, max m: [Float], danger: Int, dist: Float, hasGps: Bool) throws {
    lock.lock(); defer { lock.unlock() }
    try run("UPDATE trips SET end_t=?, max_pu=?, max_pd=?, max_rl=?, max_rr=?, danger=?, dist=?, has_gps=? WHERE id=?",
        [.i(end), .d(Double(m[0])), .d(Double(m[1])), .d(Double(m[2])), .d(Double(m[3])), .i(Int64(danger)), .d(Double(dist)), .i(hasGps ? 1 : 0), .i(id)])
    try requireTripChange()
  }
  private func requireTripChange() throws {
    guard sqlite3_changes(try connection()) == 1 else {
      throw TripDbError(operation: "guardar la salida", code: SQLITE_NOTFOUND, reason: "La salida ya no existe")
    }
  }

  func rename(_ id: Int64, _ name: String) throws { try run("UPDATE trips SET name=? WHERE id=?", [.s(name), .i(id)]) }

  private func transaction<T>(_ body: () throws -> T) throws -> T {
    lock.lock(); defer { lock.unlock() }
    try exec("BEGIN IMMEDIATE")
    do { let result = try body(); try exec("COMMIT"); return result }
    catch { try? exec("ROLLBACK"); throw error }
  }
  private func deleteRows(_ id: Int64) throws {
    try run("DELETE FROM samples WHERE trip=?", [.i(id)])
    try run("DELETE FROM trips WHERE id=?", [.i(id)])
  }
  func delete(_ id: Int64) throws {
    try transaction { try deleteRows(id) }
  }

  private let tripCols = "id, name, start, end_t, sample_ms, max_pu, max_pd, max_rl, max_rr, danger, dist, lim_pitch, lim_roll, caution, has_gps, (SELECT COUNT(*) FROM samples WHERE trip = trips.id)"
  private func readTrip(_ c: OpaquePointer) -> Trip {
    Trip(id: sqlite3_column_int64(c, 0), name: str(c, 1), start: sqlite3_column_int64(c, 2), end: sqlite3_column_int64(c, 3), sampleMs: sqlite3_column_int64(c, 4),
         max: (5...8).map { Float(sqlite3_column_double(c, Int32($0))) }, danger: Int(sqlite3_column_int(c, 9)), distance: Float(sqlite3_column_double(c, 10)),
         limitPitch: Float(sqlite3_column_double(c, 11)), limitRoll: Float(sqlite3_column_double(c, 12)), cautionPct: Float(sqlite3_column_double(c, 13)),
         hasGps: sqlite3_column_int(c, 14) != 0, samples: Int(sqlite3_column_int(c, 15)))
  }

  /// Todas las salidas: por fecha (más reciente primero) o por mayor ángulo alcanzado.
  func trips(byAngle: Bool) throws -> [Trip] {
    let order = byAngle ? "MAX(MAX(max_pu, max_pd), MAX(max_rl, max_rr)) DESC, start DESC" : "start DESC"
    var out: [Trip] = []
    try run("SELECT \(tripCols) FROM trips ORDER BY \(order)") { out.append(self.readTrip($0)) }
    return out
  }
  func trip(_ id: Int64) throws -> Trip? {
    var t: Trip?
    try run("SELECT \(tripCols) FROM trips WHERE id=?", [.i(id)]) { t = self.readTrip($0) }
    return t
  }

  func samples(_ trip: Int64) throws -> Samples {
    var s = Samples()
    try run("SELECT t, pitch, roll, lat, lon, alt, kmh FROM samples WHERE trip=? ORDER BY t", [.i(trip)]) { c in
      s.t.append(sqlite3_column_int64(c, 0)); s.pitch.append(Float(sqlite3_column_double(c, 1))); s.roll.append(Float(sqlite3_column_double(c, 2)))
      let lat = self.dbl(c, 3), lon = self.dbl(c, 4)
      s.lat.append(lat != nil && lon != nil ? lat : nil); s.lon.append(lat != nil && lon != nil ? lon : nil)
      s.alt.append(self.dbl(c, 5).map { Float($0) }); s.kmh.append(self.dbl(c, 6).map { Float($0) })
    }
    return s
  }

  /// Puntos con GPS por encima de la zona de precaución (con los límites actuales), de todas las salidas.
  /// Para no saturar el mapa, en cada celda de ~10 m se queda solo el punto más inclinado.
  func hotspots() throws -> [Hotspot] {
    let limP = Config.limitPitch, limR = Config.limitRoll, mn = Config.cautionPct
    var best: [String: Hotspot] = [:]
    try run("""
      SELECT s.trip, t.name, s.t, s.lat, s.lon, s.pitch, s.roll FROM samples s JOIN trips t ON t.id = s.trip
      WHERE s.lat IS NOT NULL AND (ABS(s.pitch) >= ? OR ABS(s.roll) >= ?)
      """, [.d(Double(limP * mn)), .d(Double(limR * mn))]) { c in
      let p = Float(sqlite3_column_double(c, 5)), r = Float(sqlite3_column_double(c, 6))
      let ratio = Swift.max(abs(p) / limP, abs(r) / limR)
      let lat = sqlite3_column_double(c, 3), lon = sqlite3_column_double(c, 4)
      let cell = "\(Int64((lat * 10000).rounded())):\(Int64((lon * 10000).rounded()))"
      if let old = best[cell], old.ratio >= ratio { return }
      best[cell] = Hotspot(trip: sqlite3_column_int64(c, 0), tripName: self.str(c, 1), t: sqlite3_column_int64(c, 2), lat: lat, lon: lon, pitch: p, roll: r, ratio: ratio)
    }
    return best.values.sorted { $0.ratio < $1.ratio }                                // los más fuertes se dibujan encima
  }

  /// Lo que ocupan las salidas en disco, en bytes.
  func sizeBytes() -> Int64 {
    ["", "-wal", "-shm", "-journal"].reduce(0) { sum, ext in
      let size = (try? FileManager.default.attributesOfItem(atPath: url.path + ext)[.size] as? Int64) ?? nil
      return sum + (size ?? 0)
    }
  }

  /// Borra las salidas que empezaron antes de [before] (excepto [keep], la que se está grabando). Devuelve cuántas.
  func deleteOlderThan(_ before: Int64, keep: Int64) throws -> Int {
    let count = try transaction {
      var ids: [Int64] = []
      try run("SELECT id FROM trips WHERE start < ? AND id != ?", [.i(before), .i(keep)]) { ids.append(sqlite3_column_int64($0, 0)) }
      for id in ids { try deleteRows(id) }
      return ids.count
    }
    try exec("PRAGMA wal_checkpoint(TRUNCATE)"); try exec("VACUUM")
    return count
  }
}
