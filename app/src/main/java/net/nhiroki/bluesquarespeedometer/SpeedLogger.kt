package net.nhiroki.bluesquarespeedometer

import android.content.Context
import android.location.Location
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends every location callback to a CSV file so that the speed reported by
 * the location provider can be examined later.
 *
 * Values are stored as delivered by the provider, without unit conversion or
 * filtering. The columns are:
 *
 *   elapsedRealtimeNanos, utcTimeMillis, hasSpeed, speedMetersPerSecond
 *
 * elapsedRealtimeNanos is the monotonic clock and should be used to compute
 * intervals, while utcTimeMillis is the wall clock shown to users.
 */
object SpeedLogger {
    private const val LOG_DIRECTORY_NAME: String = "speedlog"
    private const val CSV_HEADER: String = "elapsedRealtimeNanos,utcTimeMillis,hasSpeed,speedMetersPerSecond"

    private var _writer: BufferedWriter? = null
    private var _currentFile: File? = null
    private var _recordedCount: Long = 0

    val isRecording: Boolean
        get() = this._writer != null

    val currentFile: File?
        get() = this._currentFile

    val recordedCount: Long
        get() = this._recordedCount

    fun getLogDirectory(context: Context): File {
        // Files under getExternalFilesDir() are per-app and need no permission.
        // They can be exported with SAF from the activity.
        val baseDirectory: File = context.getExternalFilesDir(null) ?: context.filesDir
        return File(baseDirectory, LOG_DIRECTORY_NAME)
    }

    /**
     * Starts a new session. Returns the created file, or null when the output
     * directory cannot be prepared.
     */
    fun start(context: Context): File? {
        this.stop()

        val directory: File = this.getLogDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            return null
        }

        val timestamp: String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file: File = File(directory, "speed-log-" + timestamp + ".csv")
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.UTF_8))
        writer.write(CSV_HEADER)
        writer.newLine()
        writer.flush()

        this._writer = writer
        this._currentFile = file
        this._recordedCount = 0
        return file
    }

    fun stop() {
        try {
            this._writer?.flush()
            this._writer?.close()
        } catch (e: IOException) {
            // Data already flushed to the file is still usable.
        }
        this._writer = null
    }

    /**
     * Called for each location callback. Does nothing when logging is stopped.
     * Flushing every line keeps the file readable even if the process is
     * killed; at the provider rate (usually 1 Hz) the cost is negligible.
     */
    fun record(location: Location) {
        val writer: BufferedWriter = this._writer ?: return

        val hasSpeed: Boolean = location.hasSpeed()
        val speedText: String = if (hasSpeed) String.format(Locale.US, "%.6f", location.speed) else ""
        val line: String = location.elapsedRealtimeNanos.toString() + "," +
                location.time.toString() + "," +
                (if (hasSpeed) "1" else "0") + "," +
                speedText

        try {
            writer.write(line)
            writer.newLine()
            writer.flush()
            this._recordedCount++
        } catch (e: IOException) {
            this.stop()
        }
    }

    fun latestFile(context: Context): File? {
        val files: Array<File> = this.getLogDirectory(context)
            .listFiles { file -> file.isFile && file.name.endsWith(".csv") } ?: return null
        if (files.isEmpty()) {
            return null
        }
        return files.maxByOrNull { file -> file.lastModified() }
    }

    /**
     * The file the export button should hand to the user: the active session
     * when recording, otherwise the most recently written one.
     */
    fun fileToExport(context: Context): File? {
        val active: File? = this._currentFile
        if (active != null && active.exists()) {
            return active
        }
        return this.latestFile(context)
    }
}
