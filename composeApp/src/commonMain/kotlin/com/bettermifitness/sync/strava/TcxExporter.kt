package com.bettermifitness.sync.strava

import com.bettermifitness.sync.data.api.WorkoutSession
import kotlinx.datetime.Instant

/**
 * Pure [WorkoutSession] → TCX v2 XML conversion for Strava's `/uploads` endpoint.
 *
 * TCX (not GPX, not a manual Strava activity) because it's the only format that lets a
 * `Trackpoint` carry `HeartRateBpm` with no `Position` — required so indoor / no-GPS
 * workouts (treadmill, indoor trainer) still upload heart rate, not just outdoor GPS ones.
 */
object TcxExporter {

    fun export(session: WorkoutSession): String {
        val routeByTime = session.route.associateBy { it.timeSec }
        val hrByTime = session.heartRateSeries.associate { it.timeSec to it.value }
        val times = (routeByTime.keys + hrByTime.keys).distinct().sorted()
            .ifEmpty { listOf(session.startTime, session.endTime).distinct().sorted() }

        val startIso = isoInstant(session.startTime)
        val sport = StravaSportMapper.tcxSport(session.activityType)
        val calories = session.caloriesKcal?.toInt()?.coerceAtLeast(0) ?: 0
        val durationSec = (session.endTime - session.startTime).coerceAtLeast(0)

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append(
            "<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\">\n",
        )
        sb.append("  <Activities>\n")
        sb.append("    <Activity Sport=\"$sport\">\n")
        sb.append("      <Id>$startIso</Id>\n")
        sb.append("      <Lap StartTime=\"$startIso\">\n")
        sb.append("        <TotalTimeSeconds>$durationSec</TotalTimeSeconds>\n")
        sb.append("        <DistanceMeters>${formatDouble(session.distanceMeters ?: 0.0)}</DistanceMeters>\n")
        sb.append("        <Calories>$calories</Calories>\n")
        session.avgHeartRateBpm?.let {
            sb.append("        <AverageHeartRateBpm><Value>$it</Value></AverageHeartRateBpm>\n")
        }
        session.maxHeartRateBpm?.let {
            sb.append("        <MaximumHeartRateBpm><Value>$it</Value></MaximumHeartRateBpm>\n")
        }
        sb.append("        <Intensity>Active</Intensity>\n")
        sb.append("        <TriggerMethod>Manual</TriggerMethod>\n")
        sb.append("        <Track>\n")
        for (t in times) {
            sb.append("          <Trackpoint>\n")
            sb.append("            <Time>${isoInstant(t)}</Time>\n")
            routeByTime[t]?.let { p ->
                sb.append("            <Position>\n")
                sb.append("              <LatitudeDegrees>${formatDouble(p.latitude)}</LatitudeDegrees>\n")
                sb.append("              <LongitudeDegrees>${formatDouble(p.longitude)}</LongitudeDegrees>\n")
                sb.append("            </Position>\n")
                p.altitudeMeters?.let { alt ->
                    sb.append("            <AltitudeMeters>${formatDouble(alt)}</AltitudeMeters>\n")
                }
            }
            hrByTime[t]?.let { hr ->
                val bpm = hr.toInt().coerceIn(30, 250)
                sb.append("            <HeartRateBpm><Value>$bpm</Value></HeartRateBpm>\n")
            }
            sb.append("          </Trackpoint>\n")
        }
        sb.append("        </Track>\n")
        sb.append("      </Lap>\n")
        sb.append("    </Activity>\n")
        sb.append("  </Activities>\n")
        sb.append("</TrainingCenterDatabase>\n")
        return sb.toString()
    }

    private fun isoInstant(epochSec: Long): String = Instant.fromEpochSeconds(epochSec).toString()

    /** Fixed-precision (6 decimals) without locale-dependent number formatting. */
    private fun formatDouble(value: Double): String {
        val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
        return rounded.toString()
    }
}
