package dev.arklay.inclinometro.core

fun main() {
    val tracker = MaxTracker(500L)
    tracker.feed(0, 25f, -12f)
    tracker.feed(60_000, 25f, -12f)
    check(tracker.max.contentEquals(floatArrayOf(0f, 0f, 0f, 0f))) { "A gap must not count as sustained tilt" }
    for (t in 60_100L..60_500L step 100) tracker.feed(t, 25f, -12f)
    check(tracker.max.contentEquals(floatArrayOf(25f, 0f, 0f, 12f)))
    tracker.set(floatArrayOf(0f, 0f, 0f, 0f))
    tracker.feed(60_600, 25f, -12f)
    check(tracker.max.all { it == 0f }) { "Restoring a trip must clear the previous window" }
    tracker.reset()
    for (t in 0L..500L step 100) tracker.feed(t, if (t == 300L) 40f else 10f, 0f)
    check(tracker.max[0] == 10f) { "A brief bump must not replace a sustained maximum" }
    tracker.windowMs = 0
    tracker.feed(600, -35f, 45f)
    check(tracker.max[1] == 35f && tracker.max[2] == 45f)
    println("Kotlin maximum filter: passed")
}
