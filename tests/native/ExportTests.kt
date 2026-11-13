package dev.arklay.inclinometro.core

class Trip(val name: String)
class Samples {
    val t = longArrayOf(1_700_000_000_000, 1_700_000_001_000, 1_700_000_002_000)
    val pitch = floatArrayOf(55f, -10f, -12.5f)
    val roll = floatArrayOf(-45f, 3f, 8f)
    val lat = doubleArrayOf(42.0, Double.NaN, 42.001)
    val lon = doubleArrayOf(-1.0, Double.NaN, -1.001)
    val alt = floatArrayOf(900f, Float.NaN, Float.NaN)
    val kmh = floatArrayOf(10f, Float.NaN, 12f)
    val size get() = t.size
    fun hasPos(i: Int) = !lat[i].isNaN() && !lon[i].isNaN()
}

fun main(args: Array<String>) {
    java.io.File(args[0]).outputStream().use { Export.gpx(Trip("Ruta & <arroyo>"), Samples(), it) }
}
