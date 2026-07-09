// SPDX-License-Identifier: MIT

package dev.bluedog.garagedoor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorsTest {

    // Mirrors the real entryData from an Alta guest pass (order as delivered in the token).
    private val samplePayload = """
        {"orgId":50299,"userId":36402256,"entryData":[
          {"entryId":612514,"uiLabel":"Clubhouse South Stairwell"},
          {"entryId":612508,"uiLabel":"Garage Clubhouse"},
          {"entryId":947325,"uiLabel":"Garage North Coiling Door"},
          {"entryId":946701,"uiLabel":"Garage West Coiling door"}
        ],"exp":1795284540}
    """.trimIndent()

    @Test
    fun parsesAllDoorsInTokenOrder() {
        val doors = Doors.parseFromPayload(samplePayload)
        assertEquals(4, doors.size)
        assertEquals(Door(612514, "Clubhouse South Stairwell"), doors[0])
        assertEquals(Door(946701, "Garage West Coiling door"), doors[3])
    }

    @Test
    fun parseReturnsEmptyWhenNoEntryData() {
        assertTrue(Doors.parseFromPayload("""{"orgId":1}""").isEmpty())
    }

    @Test
    fun parseSkipsEntriesMissingLabelOrId() {
        val payload = """{"entryData":[{"entryId":1},{"uiLabel":"No Id"},{"entryId":2,"uiLabel":"Good"}]}"""
        val doors = Doors.parseFromPayload(payload)
        assertEquals(listOf(Door(2, "Good")), doors)
    }

    @Test
    fun sortPutsGarageAndParkingFirstThenAlphabetical() {
        val sorted = Doors.sortForDisplay(Doors.parseFromPayload(samplePayload)).map { it.uiLabel }
        assertEquals(
            listOf(
                "Garage Clubhouse",
                "Garage North Coiling Door",
                "Garage West Coiling door",
                "Clubhouse South Stairwell",
            ),
            sorted,
        )
    }

    @Test
    fun parkingLabelsCountAsPriorityGroup() {
        val doors = listOf(
            Door(1, "Main Lobby"),
            Door(2, "Parking Level 2"),
            Door(3, "Amenity Room"),
            Door(4, "parking level 1"),
        )
        assertEquals(
            listOf("parking level 1", "Parking Level 2", "Amenity Room", "Main Lobby"),
            Doors.sortForDisplay(doors).map { it.uiLabel },
        )
    }

    @Test
    fun jsonRoundTripPreservesDoors() {
        val doors = listOf(Door(1, "Garage A"), Door(2, "Lobby"))
        assertEquals(doors, Doors.fromJson(Doors.toJson(doors)))
    }

    @Test
    fun fromJsonToleratesNullAndGarbage() {
        assertTrue(Doors.fromJson(null).isEmpty())
        assertTrue(Doors.fromJson("").isEmpty())
        assertTrue(Doors.fromJson("not json").isEmpty())
    }
}
