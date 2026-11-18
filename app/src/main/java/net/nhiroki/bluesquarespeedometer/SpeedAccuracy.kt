package net.nhiroki.bluesquarespeedometer

import android.content.Context
import android.location.Location
import android.os.Build

internal fun Location.speedAccuracyOrNull(): Float? {
    if (Build.VERSION.SDK_INT < 26 || !hasSpeed() || !hasSpeedAccuracy()) return null
    return speedAccuracyMetersPerSecond.takeIf { it.isFinite() && it >= 0f }
}

internal fun Context.speedAccuracyLabel(location: Location, unit: Int, unitLabel: CharSequence): String {
    val accuracy = location.speedAccuracyOrNull()?.toDouble()
        ?: return getString(R.string.speed_accuracy_unavailable)
    val converted = when (unit) {
        MainActivity.PREFERENCE_VAL_SPEED_UNIT_KM_H -> accuracy * 3.6
        MainActivity.PREFERENCE_VAL_SPEED_UNIT_KNOT -> accuracy * 3.6 / 1.852
        MainActivity.PREFERENCE_VAL_SPEED_UNIT_MPH -> accuracy * 3.6 / 1.609344
        else -> accuracy
    }
    return getString(R.string.speed_accuracy_estimate, converted, unitLabel)
}
