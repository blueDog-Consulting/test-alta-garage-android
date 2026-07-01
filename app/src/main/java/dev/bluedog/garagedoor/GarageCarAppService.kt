package dev.bluedog.garagedoor

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class GarageCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Required for sideloaded Android Auto apps during personal use.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return GarageSession()
    }
}
