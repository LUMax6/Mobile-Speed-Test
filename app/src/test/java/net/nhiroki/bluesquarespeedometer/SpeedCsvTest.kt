package net.nhiroki.bluesquarespeedometer

import org.junit.Assert.*
import org.junit.Test

class SpeedCsvTest {
    @Test fun readsOldAndNewFormatsAndSorts() {
        val old = SpeedCsv.parse("time_unix_ms,latitude_deg,longitude_deg,speed_m_s\n2000,1,2,1.25\n1000,1,2,0.5".reader().buffered())
        assertEquals(1000L, old.first().time)
        assertEquals(1.25, old.last().speed, 0.0)
        val current = SpeedCsv.parse("\uFEFFtime_unix_ms,local_time,latitude_deg,longitude_deg,speed_m_s\n1000,08:00:01,1,2,2.75\n2000,08:00:02,1,2,\n3000,08:00:03,1,2,NaN".reader().buffered())
        assertEquals(1, current.size)
        assertEquals(2.75, current.single().speed, 0.0)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownColumns() {
        SpeedCsv.parse("time,speed\n1,2".reader().buffered())
    }
}
