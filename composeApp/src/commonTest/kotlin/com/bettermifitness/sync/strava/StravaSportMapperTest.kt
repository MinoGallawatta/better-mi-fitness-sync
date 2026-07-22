package com.bettermifitness.sync.strava

import kotlin.test.Test
import kotlin.test.assertEquals

class StravaSportMapperTest {

    @Test
    fun tcxSport_mapsKnownActivityTypes() {
        val cases = listOf(
            "running" to "Running",
            "treadmill" to "Running",
            "trail_run" to "Running",
            "cycling" to "Biking",
            "riding" to "Biking",
            "spinning" to "Biking",
            "walking" to "Other",
            "hiking" to "Other",
            "swimming" to "Other",
            "yoga" to "Other",
            "unknown_activity" to "Other",
        )
        for ((activityType, expected) in cases) {
            assertEquals(expected, StravaSportMapper.tcxSport(activityType), "activityType=$activityType")
        }
    }
}
