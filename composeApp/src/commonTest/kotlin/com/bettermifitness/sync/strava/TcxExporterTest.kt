package com.bettermifitness.sync.strava

import com.bettermifitness.sync.data.api.WorkoutRoutePoint
import com.bettermifitness.sync.data.api.WorkoutSession
import com.bettermifitness.sync.data.api.WorkoutTimedSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TcxExporterTest {

    @Test
    fun export_outdoorWorkout_includesPositionAndHeartRate() {
        val session = WorkoutSession(
            startTime = 1_700_000_000L,
            endTime = 1_700_000_020L,
            activityType = "running",
            route = listOf(
                WorkoutRoutePoint(1_700_000_000L, -6.20, 106.80),
                WorkoutRoutePoint(1_700_000_010L, -6.2001, 106.8001),
            ),
            heartRateSeries = listOf(
                WorkoutTimedSample(1_700_000_000L, 140.0),
                WorkoutTimedSample(1_700_000_010L, 150.0),
            ),
        )
        val xml = TcxExporter.export(session)
        assertTrue(xml.contains("<Position>"), "expected GPS positions in output")
        assertTrue(xml.contains("<HeartRateBpm><Value>140</Value></HeartRateBpm>"))
        assertTrue(xml.contains("Sport=\"Running\""))
        assertEquals(2, xml.split("<Trackpoint>").size - 1)
    }

    @Test
    fun export_indoorWorkout_hasHeartRateButNoPosition() {
        val session = WorkoutSession(
            startTime = 1_700_000_000L,
            endTime = 1_700_000_020L,
            activityType = "treadmill",
            route = emptyList(),
            heartRateSeries = listOf(
                WorkoutTimedSample(1_700_000_000L, 130.0),
                WorkoutTimedSample(1_700_000_010L, 145.0),
            ),
        )
        val xml = TcxExporter.export(session)
        assertFalse(xml.contains("<Position>"), "indoor workout must not emit a GPS position")
        assertTrue(xml.contains("<HeartRateBpm>"), "indoor workout must still carry heart rate")
        assertEquals(2, xml.split("<Trackpoint>").size - 1)
    }

    @Test
    fun export_noRouteOrHeartRate_stillProducesValidMinimalTrack() {
        val session = WorkoutSession(
            startTime = 1_700_000_000L,
            endTime = 1_700_000_600L,
            activityType = "yoga",
        )
        val xml = TcxExporter.export(session)
        assertFalse(xml.contains("<Position>"))
        assertFalse(xml.contains("<HeartRateBpm>"))
        assertTrue(xml.contains("Sport=\"Other\""))
        assertEquals(2, xml.split("<Trackpoint>").size - 1, "start/end synthetic trackpoints")
    }

    @Test
    fun export_isWellFormed_balancedTags() {
        val session = WorkoutSession(
            startTime = 1_700_000_000L,
            endTime = 1_700_000_010L,
            activityType = "cycling",
            route = listOf(WorkoutRoutePoint(1_700_000_000L, 1.0, 2.0)),
        )
        val xml = TcxExporter.export(session)
        assertTrue(xml.startsWith("<?xml"))
        assertTrue(xml.trimEnd().endsWith("</TrainingCenterDatabase>"))
        assertEquals(
            xml.split("<Trackpoint>").size,
            xml.split("</Trackpoint>").size,
            "Trackpoint open/close tags must balance",
        )
        assertEquals(
            xml.split("<Activity ").size,
            xml.split("</Activity>").size,
            "Activity open/close tags must balance",
        )
    }

    @Test
    fun export_idIsValidIso8601AtStartTime() {
        val session = WorkoutSession(
            startTime = 1_700_000_000L,
            endTime = 1_700_000_010L,
            activityType = "running",
        )
        val xml = TcxExporter.export(session)
        val id = xml.substringAfter("<Id>").substringBefore("</Id>")
        assertTrue(id.endsWith("Z"), "expected UTC ISO-8601 timestamp, got $id")
        assertTrue(id.contains("T"))
    }
}
