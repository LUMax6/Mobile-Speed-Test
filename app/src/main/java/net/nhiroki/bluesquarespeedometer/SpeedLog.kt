package net.nhiroki.bluesquarespeedometer

import android.content.Context
import android.location.Location
import java.io.File
import java.util.concurrent.Executors

/** All file operations are serialized off the UI thread. */
object SpeedLog {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile var error: String? = null
        private set

    fun append(context: Context, location: Location) {
        val app = context.applicationContext
        val row = "${location.time},${location.latitude},${location.longitude},${if (location.hasSpeed()) location.speed.toString() else ""}\n"
        worker.execute {
            try {
                val file = File(app.filesDir, "speed_records.csv")
                if (!file.exists()) file.writeText("time_unix_ms,latitude_deg,longitude_deg,speed_m_s\n")
                file.appendText(row)
                error = null
            } catch (e: Exception) { error = e.localizedMessage ?: "写入失败" }
        }
    }

    fun snapshot(context: Context, callback: (File?, String?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            try {
                val source = File(app.filesDir, "speed_records.csv")
                if (!source.exists()) {
                    callback(null, "暂无记录，请先在测速页面获取定位")
                } else {
                    val directory = File(app.cacheDir, "exports")
                    directory.mkdirs()
                    val copy = File.createTempFile("speed_records_", ".csv", directory)
                    source.copyTo(copy, overwrite = true)
                    callback(copy, error)
                }
            } catch (e: Exception) { callback(null, e.localizedMessage ?: "读取失败") }
        }
    }
}
