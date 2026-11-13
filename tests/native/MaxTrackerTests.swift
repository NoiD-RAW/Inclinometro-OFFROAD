import Foundation

@main struct MaxTrackerTests {
  static func main() {
    let tracker = MaxTracker(windowMs: 500)
    tracker.feed(0, pitch: 25, roll: -12)
    tracker.feed(60_000, pitch: 25, roll: -12)
    precondition(tracker.max == [0, 0, 0, 0], "A gap must not count as sustained tilt")
    for t in stride(from: Int64(60_100), through: 60_500, by: 100) { tracker.feed(t, pitch: 25, roll: -12) }
    precondition(tracker.max == [25, 0, 0, 12], "Continuous readings must still record maxima")
    tracker.set([0, 0, 0, 0])
    tracker.feed(60_600, pitch: 25, roll: -12)
    precondition(tracker.max == [0, 0, 0, 0], "Restoring a trip must clear the previous window")
    tracker.reset()
    for t in stride(from: Int64(0), through: 500, by: 100) { tracker.feed(t, pitch: t == 300 ? 40 : 10, roll: 0) }
    precondition(tracker.max[0] == 10, "A brief bump must not replace a sustained maximum")
    tracker.windowMs = 0
    tracker.feed(600, pitch: -35, roll: 45)
    precondition(tracker.max[1] == 35 && tracker.max[2] == 45, "Disabling the filter must count immediately")
    print("Swift maximum filter: passed")
  }
}
