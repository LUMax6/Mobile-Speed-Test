package net.nhiroki.bluesquarespeedometer

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** Session controls and location callbacks run on the main thread. File work runs in order on the worker. */
object SpeedLog {
    private class Session {
        // Accessed only by the file worker.
        var file: File? = null
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val SUFFIX = "_speed_records.csv"
    private const val HEADER = "time_unix_ms,local_time,latitude_deg,longitude_deg,speed_m_s,speed_accuracy_m_s"
    private var activeSession: Session? = null
    val recording: Boolean get() = activeSession != null

    fun start(context: Context, onError: (String) -> Unit) {
        if (recording) return
        val directory = context.filesDir
        val startedAt = SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS", Locale.US).format(Date())
        val session = Session()
        activeSession = session
        worker.execute {
            try {
                var file = File(directory, "$startedAt$SUFFIX")
                var sequence = 2
                while (!file.createNewFile()) {
                    file = File(directory, "${startedAt}_${sequence++}$SUFFIX")
                }
                try {
                    file.writeText("$HEADER\n")
                } catch (e: IOException) {
                    file.delete()
                    throw e
                }
                session.file = file
            } catch (e: IOException) {
                main.post {
                    if (activeSession === session) activeSession = null
                    onError(e.localizedMessage ?: "无法创建记录文件")
                }
            }
        }
    }

    fun stop() {
        activeSession = null
    }

    fun append(location: Location) {
        val session = activeSession ?: return
        val localTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(location.time))
        val speed = location.speed.takeIf { location.hasSpeed() }?.toString().orEmpty()
        val accuracy = location.speedAccuracyOrNull()?.toString().orEmpty()
        val row = "${location.time},$localTime,${location.latitude},${location.longitude},$speed,$accuracy\n"
        worker.execute {
            try {
                session.file?.appendText(row)
            } catch (e: IOException) {
                Log.e("SpeedLog", "Could not append a speed sample", e)
            }
        }
    }

    fun listRecords(context: Context, callback: (List<String>) -> Unit) {
        val directory = context.filesDir
        worker.execute {
            val names = directory.listFiles()
                ?.filter { it.isFile && (it.name.endsWith(SUFFIX) || it.name == "speed_records.csv") }
                ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
                ?.map { it.name }.orEmpty()
            main.post { callback(names) }
        }
    }

    fun snapshot(context: Context, name: String?, callback: (File?, String?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val source = name?.let { File(app.filesDir, it) }
            if (source == null || !source.isFile) {
                callback(null, "暂无记录，请先开始记录")
                return@execute
            }
            val copy = try {
                val folder = File(app.cacheDir, "exports/${UUID.randomUUID()}").apply { mkdirs() }
                source.copyTo(File(folder, source.name))
            } catch (e: IOException) {
                callback(null, e.localizedMessage ?: "读取失败")
                return@execute
            }
            callback(copy, null)
        }
    }
}
