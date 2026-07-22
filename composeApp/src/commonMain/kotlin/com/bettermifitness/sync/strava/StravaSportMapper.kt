package com.bettermifitness.sync.strava

import com.bettermifitness.sync.health.WorkoutMetricFamilies
import com.bettermifitness.sync.health.WorkoutMetricFamily

/**
 * TCX v2's Sport attribute only defines three values, and Strava's TCX importer only
 * trusts these three — everything else collapses to "Other".
 */
object StravaSportMapper {

    fun tcxSport(activityType: String): String = when (WorkoutMetricFamilies.classify(activityType)) {
        WorkoutMetricFamily.RUNNING -> "Running"
        WorkoutMetricFamily.CYCLING -> "Biking"
        WorkoutMetricFamily.WALKING,
        WorkoutMetricFamily.SWIMMING,
        WorkoutMetricFamily.OTHER,
        -> "Other"
    }
}
