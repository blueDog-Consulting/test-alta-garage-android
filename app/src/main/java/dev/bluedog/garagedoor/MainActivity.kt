package dev.bluedog.garagedoor

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val unlockClient = AltaUnlockClient()
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var configStore: UnlockConfigStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        configStore = UnlockConfigStore(this)

        val passInput = findViewById<EditText>(R.id.passInput)
        val passStatusView = findViewById<TextView>(R.id.passStatusText)
        val savePassButton = findViewById<Button>(R.id.savePassButton)
        val statusView = findViewById<TextView>(R.id.statusText)
        val unlockButton = findViewById<Button>(R.id.unlockButton)

        refreshPassStatus(passStatusView)

        savePassButton.setOnClickListener {
            savePass(passInput.text.toString(), passInput, passStatusView, statusView)
        }

        unlockButton.setOnClickListener {
            unlockDoor(statusView, unlockButton)
        }

        handleShareIntent(intent, passInput, passStatusView, statusView)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(
            intent,
            findViewById(R.id.passInput),
            findViewById(R.id.passStatusText),
            findViewById(R.id.statusText),
        )
    }

    private fun handleShareIntent(
        intent: Intent?,
        passInput: EditText,
        passStatusView: TextView,
        statusView: TextView,
    ) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val passLink = UnlockConfigStore.normalizePassInput(sharedText)
        passInput.setText(passLink)
        savePass(passLink, passInput, passStatusView, statusView)
    }

    private fun savePass(
        input: String,
        passInput: EditText,
        passStatusView: TextView,
        statusView: TextView,
    ) {
        val normalized = UnlockConfigStore.normalizePassInput(input)
        if (normalized != input.trim()) {
            passInput.setText(normalized)
        }

        configStore.savePassInput(normalized)
            .onSuccess { shortCode ->
                passStatusView.setTextColor(getColor(android.R.color.holo_green_light))
                passStatusView.text = getString(R.string.pass_saved, shortCode)
                statusView.text = getString(R.string.ready)
            }
            .onFailure {
                passStatusView.setTextColor(getColor(android.R.color.holo_red_light))
                passStatusView.text = getString(R.string.pass_save_failed)
            }
    }

    private fun refreshPassStatus(passStatusView: TextView) {
        if (configStore.hasSavedPass()) {
            passStatusView.setTextColor(getColor(android.R.color.holo_green_light))
            passStatusView.text = getString(R.string.pass_saved, configStore.getShortCode())
        } else {
            passStatusView.setTextColor(getColor(android.R.color.holo_orange_light))
            passStatusView.text = getString(R.string.pass_not_configured)
        }
    }

    private fun unlockDoor(statusView: TextView, unlockButton: Button) {
        val shortCode = configStore.getShortCode()
        if (shortCode == null) {
            statusView.text = getString(R.string.pass_not_configured)
            return
        }

        unlockButton.isEnabled = false
        statusView.text = getString(R.string.unlocking)

        executor.execute {
            val result = unlockClient.unlockDoor(
                shortCode = shortCode,
                doorLabel = AltaConfig.DOOR_LABEL,
            )
            runOnUiThread {
                unlockButton.isEnabled = true
                statusView.text = formatUnlockResult(result.message)
            }
        }
    }

    private fun formatUnlockResult(message: String): String {
        if (message.contains("HTTP 4", ignoreCase = true) ||
            message.contains("failed", ignoreCase = true) &&
            message.contains("HTTP", ignoreCase = true)
        ) {
            return "$message\n${getString(R.string.pass_update_on_phone)}"
        }
        return message
    }
}
