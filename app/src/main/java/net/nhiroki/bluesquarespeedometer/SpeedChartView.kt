package net.nhiroki.bluesquarespeedometer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpeedChartView(context: Context) : View(context) {
    var points: List<SpeedPoint> = emptyList()
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        val scale = resources.displayMetrics.density
        paint.textSize = 12 * scale
        paint.color = Color.DKGRAY
        if (points.isEmpty()) {
            canvas.drawText("选择 CSV 查看时间—速度曲线", 12 * scale, 28 * scale, paint)
            return
        }
        val left = 48 * scale
        val top = 28 * scale
        val right = width - 12 * scale
        val bottom = height - 48 * scale
        if (right <= left || bottom <= top) return
        val start = points.first().time
        val duration = (points.last().time.toDouble() - start).coerceAtLeast(1.0)
        val maximum = points.maxOf { it.speed * 3.6 }.coerceAtLeast(1.0)
        canvas.drawText("速度 km/h", left, 18 * scale, paint)
        for (i in 0..4) {
            val y = bottom - (bottom - top) * i / 4
            paint.color = Color.LTGRAY
            canvas.drawLine(left, y, right, y, paint)
            paint.color = Color.DKGRAY
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", maximum * i / 4), 0f, y, paint)
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = left + ((point.time.toDouble() - start) / duration * (right - left)).toFloat()
            val y = bottom - (point.speed * 3.6 / maximum * (bottom - top)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = Color.BLUE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * scale
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        if (points.size == 1) canvas.drawCircle(left, bottom - (points[0].speed * 3.6 / maximum * (bottom - top)).toFloat(), 4 * scale, paint)
        paint.color = Color.DKGRAY
        val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        canvas.drawText(format.format(Date(start)), left, bottom + 18 * scale, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(format.format(Date(points.last().time)), right, bottom + 18 * scale, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("本地时间 · ${points.size} 个有效样本", left, bottom + 36 * scale, paint)
    }
}
