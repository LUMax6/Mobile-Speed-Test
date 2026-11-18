package net.nhiroki.bluesquarespeedometer

import android.content.Context
import android.location.Location
import android.util.AtomicFile
import java.io.File
import java.util.concurrent.Executors

/** All file operations are serialized off the UI thread. */
object SpeedLog {
    @Volatile var recording = false
    fun defaultName() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + "_speed_records.csv"
    private val worker = Executors.newSingleThreadExecutor()
    private const val LEGACY_HEADER = "time_unix_ms,local_time,latitude_deg,longitude_deg,speed_m_s"
    private const val HEADER = "$LEGACY_HEADER,speed_accuracy_m_s"
    private var preparedFile: File? = null
    @Volatile var error: String? = null
        private set

    fun append(context: Context, location: Location) {
        if (!recording) return
        val name = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(location.time)) + "_speed_records.csv"
        val localTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(location.time))
        val app = context.applicationContext
        val speed = location.speed.takeIf { location.hasSpeed() }?.toString().orEmpty()
        val accuracy = location.speedAccuracyOrNull()?.toString().orEmpty()
        val row = "${location.time},$localTime,${location.latitude},${location.longitude},$speed,$accuracy\n"
        worker.execute {
            try {
                val file = File(app.filesDir, name)
                if (preparedFile != file || !file.exists()) {
                    prepareFile(file)
                    preparedFile = file
                }
                file.appendText(row)
                error = null
            } catch (e: Exception) { error = e.localizedMessage ?: "写入失败" }
        }
    }

    private fun prepareFile(file: File) {
        if (!file.exists() || file.length() == 0L) {
            file.writeText("$HEADER\n")
            return
        }
        file.bufferedReader().use { reader ->
            when (reader.readLine()) {
                HEADER -> return
                LEGACY_HEADER -> {
                    val atomic = AtomicFile(file)
                    val stream = atomic.startWrite()
                    try {
                        val writer = stream.bufferedWriter(Charsets.UTF_8)
                        writer.appendLine(HEADER)
                        reader.forEachLine { writer.appendLine("$it,") }
                        writer.flush()
                        atomic.finishWrite(stream)
                    } catch (e: Exception) {
                        atomic.failWrite(stream)
                        throw e
                    }
                }
                else -> throw java.io.IOException("无法识别速度记录的 CSV 表头")
            }
        }
    }

    fun snapshot(context: Context, name: String = defaultName(), callback: (File?, String?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            try {
                val source = File(app.filesDir, name)
                if (!source.exists()) {
                    callback(null, "暂无记录，请先在测速页面获取定位")
                } else {
                    val directory = File(app.cacheDir, "exports")
                    directory.mkdirs()
                    val folder = File(directory, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
                    val copy = File(folder, source.name)
                    source.copyTo(copy, overwrite = true)
                    callback(copy, error)
                }
            } catch (e: Exception) { callback(null, e.localizedMessage ?: "读取失败") }
        }
    }
}
