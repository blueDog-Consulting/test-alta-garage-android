// SPDX-License-Identifier: MIT

package dev.bluedog.garagedoor

object AltaConfig {
    private const val SHORT_URL_BASE = "https://helium.prod.openpath.com/shortUrl/"
    private const val UNLOCK_URL_BASE = "https://api.openpath.com/tokens/cloudKeyUnlockTokens/"

    fun shortUrl(shortCode: String): String = SHORT_URL_BASE + shortCode

    fun unlockUrl(token: String): String = UNLOCK_URL_BASE + token + "/use"
}
