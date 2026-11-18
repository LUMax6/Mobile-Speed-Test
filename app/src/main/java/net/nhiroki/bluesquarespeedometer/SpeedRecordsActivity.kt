package net.nhiroki.bluesquarespeedometer

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors

class SpeedRecordsActivity : AppCompatActivity() {
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) SpeedLog.snapshot(this) { file, error ->
            if (file == null) message(error ?: "无记录") else {
                Executors.newSingleThreadExecutor().apply {
                    execute {
                        try {
                            val output = contentResolver.openOutputStream(uri, "wt")
                                ?: throw java.io.IOException("无法打开导出文件")
                            output.use { out -> file.inputStream().use { it.copyTo(out) } }
                            message("导出成功")
                        } catch (e: Exception) { message("导出失败：${e.localizedMessage}") }
                        finally { file.delete() }
                    }
                    shutdown()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        setContentView(layout)
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left + 24, bars.top + 24, bars.right + 24, bars.bottom + 24)
            insets
        }
        layout.addView(TextView(this).apply {
            text = "速度记录\n测速页面收到定位后自动追加到本地 CSV 文件，切换到后台后不采集。\n字段：Unix 毫秒时间、纬度、经度、速度（m/s）。缺失速度留空，记录保留原始小数。\n导出可保存到本机或系统文件选择器中的云盘；分享可选择支持文件的上传应用。"
        })
        layout.addView(Button(this).apply {
            text = "导出 CSV"
            setOnClickListener { export.launch("speed_records.csv") }
        })
        layout.addView(Button(this).apply {
            text = "分享 / 上传 CSV"
            setOnClickListener {
                SpeedLog.snapshot(this@SpeedRecordsActivity) { file, error ->
                    runOnUiThread {
                        if (file == null) message(error ?: "无记录")
                        else try {
                            val uri = FileProvider.getUriForFile(this@SpeedRecordsActivity, "${packageName}.records", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newRawUri("速度记录", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(intent, "分享 / 上传速度记录"))
                        } catch (e: Exception) { message("分享失败：${e.localizedMessage}") }
                    }
                }
            }
        })
        SpeedLog.snapshot(this) { file, error ->
            file?.delete()
            if (error != null) message(error)
        }
    }

    private fun message(text: String) {
        runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    }
}
