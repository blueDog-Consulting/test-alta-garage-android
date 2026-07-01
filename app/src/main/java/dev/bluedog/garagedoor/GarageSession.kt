package dev.bluedog.garagedoor

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class GarageSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return GarageScreen(carContext)
    }
}
