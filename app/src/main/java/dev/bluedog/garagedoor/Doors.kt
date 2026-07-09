// SPDX-License-Identifier: MIT

package dev.bluedog.garagedoor

import org.json.JSONArray
import org.json.JSONObject

/** A single door/entry the guest pass grants, as listed in the token's `entryData`. */
data class Door(val entryId: Int, val uiLabel: String)

/**
 * Door discovery and ordering. A pass can grant several doors (the JWT `entryData` array); the app
 * lists all of them rather than a hard-coded one. Pure Kotlin + org.json so the parsing and sort
 * order are unit-testable on the host JVM.
 */
object Doors {

    /** Reads the door list from a decoded JWT payload. Returns empty if `entryData` is absent. */
    fun parseFromPayload(payloadJson: String): List<Door> {
        val entryData = JSONObject(payloadJson).optJSONArray("entryData") ?: return emptyList()
        return readDoors(entryData)
    }

    /**
     * Display order: doors whose label mentions "garage" or "parking" come first, then everything
     * else — each group sorted alphabetically (case-insensitive).
     */
    fun sortForDisplay(doors: List<Door>): List<Door> =
        doors.sortedWith(
            compareByDescending<Door> { isGarageOrParking(it.uiLabel) }
                .thenBy { it.uiLabel.lowercase() },
        )

    fun isGarageOrParking(label: String): Boolean {
        val lower = label.lowercase()
        return lower.contains("garage") || lower.contains("parking")
    }

    /** Serializes the door list for caching in SharedPreferences. */
    fun toJson(doors: List<Door>): String {
        val array = JSONArray()
        for (door in doors) {
            array.put(JSONObject().put("entryId", door.entryId).put("uiLabel", door.uiLabel))
        }
        return array.toString()
    }

    /** Reads a cached door list; tolerant of null/blank/garbage (returns empty). */
    fun fromJson(json: String?): List<Door> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            readDoors(JSONArray(json))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readDoors(array: JSONArray): List<Door> {
        val doors = ArrayList<Door>(array.length())
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            if (!entry.has("entryId")) continue
            val label = entry.optString("uiLabel").takeIf { it.isNotBlank() } ?: continue
            doors.add(Door(entry.getInt("entryId"), label))
        }
        return doors
    }
}
