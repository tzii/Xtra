package com.github.andreyasadchy.xtra.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsChartScaleTest {
    @Test fun `short sessions use minute scales instead of six hours`() {
        assertEquals(60L, StatsChartScale.ceilingSeconds(0))
        assertEquals(300L, StatsChartScale.ceilingSeconds(180))
        assertEquals(600L, StatsChartScale.ceilingSeconds(600))
    }
    @Test fun `scale never clips the largest bar`() {
        for (seconds in listOf(1L, 61L, 601L, 3601L, 22000L, 100000L, Long.MAX_VALUE)) {
            assertTrue(StatsChartScale.ceilingSeconds(seconds) >= seconds)
        }
    }
}
