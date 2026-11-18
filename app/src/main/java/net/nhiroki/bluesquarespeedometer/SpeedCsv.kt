package net.nhiroki.bluesquarespeedometer

import java.io.BufferedReader

data class SpeedPoint(val time: Long, val speed: Double)

object SpeedCsv {
    fun parse(reader: BufferedReader): List<SpeedPoint> {
        val header = reader.readLine()?.removePrefix("\uFEFF")?.split(',')?.map { it.trim().trim('"') }
            ?: error("CSV 为空")
        val timeIndex = header.indexOf("time_unix_ms")
        val speedIndex = header.indexOf("speed_m_s")
        require(timeIndex >= 0 && speedIndex >= 0) { "需要 time_unix_ms 和 speed_m_s 列（速度单位 m/s）" }
        val points = mutableListOf<SpeedPoint>()
        reader.lineSequence().forEach { line ->
            val fields = line.split(',').map { it.trim().trim('"') }
            val time = fields.getOrNull(timeIndex)?.toLongOrNull()
            val speed = fields.getOrNull(speedIndex)?.toDoubleOrNull()
            if (time != null && speed != null && speed.isFinite() && speed >= 0) {
                require(points.size < 200000) { "记录过多，请拆分为少于 200000 行" }
                points.add(SpeedPoint(time, speed))
            }
        }
        require(points.isNotEmpty()) { "没有有效的时间和速度记录" }
        return points.sortedBy { it.time }
    }
}
