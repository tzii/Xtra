package com.github.andreyasadchy.xtra.ui.stats

/** Readable chart ceilings, including short sessions that disappeared at a six-hour floor. */
object StatsChartScale {
    fun ceilingSeconds(largest: Long): Long {
        val steps = longArrayOf(60, 120, 300, 600, 1800, 3600, 7200, 14400, 21600, 43200, 86400)
        return steps.firstOrNull { it >= largest } ?: largest
    }
}
