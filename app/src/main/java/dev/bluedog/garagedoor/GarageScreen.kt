// SPDX-License-Identifier: MIT

package dev.bluedog.garagedoor

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import java.util.concurrent.Executors

class GarageScreen(carContext: CarContext) : Screen(carContext) {

    private val unlockClient = AltaUnlockClient()
    private val executor = Executors.newSingleThreadExecutor()
    private val configStore = UnlockConfigStore(carContext)

    private var loadingDoors = false
    private var fetchAttempted = false
    private var loadError = false
    private var unlockingEntryId: Int? = null
    private val statusByEntry = mutableMapOf<Int, String>()

    override fun onGetTemplate(): Template {
        if (!configStore.hasSavedPass()) {
            return message(carContext.getString(R.string.car_no_pass))
        }
        if (loadingDoors) {
            return gridLoading()
        }

        val doors = configStore.getDoors()
        if (doors.isEmpty()) {
            return when {
                loadError -> message(carContext.getString(R.string.doors_load_failed), withRetry = true)
                !fetchAttempted -> {
                    fetchDoors()
                    gridLoading()
                }
                else -> message(carContext.getString(R.string.doors_none))
            }
        }

        return doorGrid(Doors.sortForDisplay(doors))
    }

    private fun doorGrid(doors: List<Door>): Template {
        val garageIcon = IconCompat.createWithResource(carContext, R.drawable.ic_garage)
        val list = ItemList.Builder()
        for (door in doors) {
            val item = GridItem.Builder().setTitle(door.uiLabel)
            if (unlockingEntryId == door.entryId) {
                item.setText(carContext.getString(R.string.car_unlocking)).setLoading(true)
            } else {
                item
                    .setText(statusByEntry[door.entryId] ?: carContext.getString(R.string.car_ready))
                    .setImage(CarIcon.Builder(garageIcon).build(), GridItem.IMAGE_TYPE_ICON)
                    .setOnClickListener { if (unlockingEntryId == null) unlock(door) }
            }
            list.addItem(item.build())
        }
        return GridTemplate.Builder()
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(list.build())
            .build()
    }

    private fun gridLoading(): Template =
        GridTemplate.Builder()
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .setLoading(true)
            .build()

    private fun message(text: String, withRetry: Boolean = false): Template {
        val builder = MessageTemplate.Builder(text)
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
        if (withRetry) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Retry")
                    .setOnClickListener {
                        loadError = false
                        fetchAttempted = false
                        invalidate()
                    }
                    .build(),
            )
        }
        return builder.build()
    }

    private fun fetchDoors() {
        val shortCode = configStore.getShortCode() ?: return
        fetchAttempted = true
        loadingDoors = true
        invalidate()

        executor.execute {
            val result = unlockClient.fetchPassInfo(shortCode)
            loadingDoors = false
            result
                .onSuccess { info ->
                    configStore.saveDoors(info.doors)
                    if (info.expiresAt != null && configStore.setExpiryFromToken(info.expiresAt)) {
                        PassExpiryWorker.schedule(carContext)
                    }
                    loadError = false
                }
                .onFailure { loadError = true }
            invalidate()
        }
    }

    private fun unlock(door: Door) {
        val shortCode = configStore.getShortCode() ?: return
        unlockingEntryId = door.entryId
        statusByEntry.remove(door.entryId)
        invalidate()

        executor.execute {
            val result = unlockClient.unlockDoor(shortCode, door.entryId, door.uiLabel)
            unlockingEntryId = null
            statusByEntry[door.entryId] = formatUnlockResult(result.message)
            invalidate()
        }
    }

    private fun formatUnlockResult(message: String): String {
        if (message.contains("HTTP 4", ignoreCase = true)) {
            return carContext.getString(R.string.pass_update_on_phone)
        }
        return message
    }
}
