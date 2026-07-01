package dev.bluedog.garagedoor

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.OnClickListener
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import java.util.concurrent.Executors

class GarageScreen(carContext: CarContext) : Screen(carContext) {

    private val unlockClient = AltaUnlockClient()
    private val executor = Executors.newSingleThreadExecutor()
    private var statusMessage = "Ready"
    private var isUnlocking = false

    override fun onGetTemplate(): Template {
        val garageIcon = IconCompat.createWithResource(carContext, R.drawable.ic_garage)
        val title = if (isUnlocking) "Unlocking..." else AltaConfig.DOOR_LABEL

        val gridItem = GridItem.Builder()
            .setTitle(title)
            .setText(statusMessage)
            .setImage(CarIcon.Builder(garageIcon).build(), GridItem.IMAGE_TYPE_ICON)
            .setLoading(isUnlocking)
            .setOnClickListener(
                OnClickListener {
                    if (!isUnlocking) {
                        unlockGarageDoor()
                    }
                },
            )
            .build()

        return GridTemplate.Builder()
            .setTitle("Garage Unlock")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(
                ItemList.Builder()
                    .addItem(gridItem)
                    .build(),
            )
            .build()
    }

    private fun unlockGarageDoor() {
        isUnlocking = true
        statusMessage = "Sending unlock request..."
        invalidate()

        executor.execute {
            val configStore = UnlockConfigStore(carContext)
            val shortCode = configStore.getShortCode()
            if (shortCode == null) {
                isUnlocking = false
                statusMessage = carContext.getString(R.string.pass_not_configured)
                invalidate()
                return@execute
            }

            val result = unlockClient.unlockDoor(
                shortCode = shortCode,
                doorLabel = AltaConfig.DOOR_LABEL,
            )
            isUnlocking = false
            statusMessage = formatUnlockResult(carContext, result.message)
            invalidate()
        }
    }

    private fun formatUnlockResult(carContext: CarContext, message: String): String {
        if (message.contains("HTTP 4", ignoreCase = true)) {
            return carContext.getString(R.string.pass_update_on_phone)
        }
        return message
    }
}
