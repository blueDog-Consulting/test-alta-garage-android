// SPDX-License-Identifier: MIT

package dev.bluedog.garagedoor

import android.Manifest
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val unlockClient = AltaUnlockClient()
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var configStore: UnlockConfigStore

    private lateinit var passInput: EditText
    private lateinit var passStatusView: TextView
    private lateinit var statusView: TextView
    private lateinit var unlockButton: Button
    private lateinit var passActionsRow: View
    private lateinit var setExpiryButton: Button
    private lateinit var exportPassButton: Button
    private lateinit var removePassButton: Button

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        configStore = UnlockConfigStore(this)
        PassExpiryWorker.ensureChannel(this)

        passInput = findViewById(R.id.passInput)
        passStatusView = findViewById(R.id.passStatusText)
        statusView = findViewById(R.id.statusText)
        unlockButton = findViewById(R.id.unlockButton)
        passActionsRow = findViewById(R.id.passActionsRow)
        setExpiryButton = findViewById(R.id.setExpiryButton)
        exportPassButton = findViewById(R.id.exportPassButton)
        removePassButton = findViewById(R.id.removePassButton)

        renderPassStatus()

        findViewById<Button>(R.id.savePassButton).setOnClickListener {
            val text = passInput.text.toString()
            savePass(text, text)
        }
        unlockButton.setOnClickListener { unlockDoor() }
        setExpiryButton.setOnClickListener { showSetExpiryDialog() }
        exportPassButton.setOnClickListener { exportPass() }
        removePassButton.setOnClickListener { confirmRemovePass() }

        // If a pass with a known expiry is already saved, make sure the reminder check is scheduled.
        if (configStore.getExpiresAt() != null) {
            PassExpiryWorker.schedule(this)
        }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val passLink = UnlockConfigStore.normalizePassInput(sharedText)
        passInput.setText(passLink)
        // Pass the full shared message as rawText so any "valid until" dates can be parsed.
        savePass(passLink, sharedText)
    }

    private fun savePass(input: String, rawText: String) {
        val normalized = UnlockConfigStore.normalizePassInput(input)
        if (normalized != input.trim()) {
            passInput.setText(normalized)
        }

        when (val result = configStore.savePassInput(normalized, rawText)) {
            is UnlockConfigStore.SaveResult.Saved -> {
                statusView.text = getString(R.string.ready)
                renderPassStatus()
                if (result.expiresAt != null) {
                    ensureNotificationPermission()
                    PassExpiryWorker.schedule(this)
                }
                if (result.alreadyExpired) {
                    Toast.makeText(this, R.string.pass_already_expired_warning, Toast.LENGTH_LONG).show()
                }
            }
            is UnlockConfigStore.SaveResult.Invalid -> {
                passStatusView.setTextColor(getColor(android.R.color.holo_red_light))
                passStatusView.text = getString(R.string.pass_save_failed)
            }
        }
    }

    private fun renderPassStatus() {
        val status = configStore.getPassStatus()
        if (status == null) {
            passActionsRow.visibility = View.GONE
            passStatusView.setTextColor(getColor(android.R.color.holo_orange_light))
            passStatusView.text = getString(R.string.pass_not_configured)
            return
        }

        passActionsRow.visibility = View.VISIBLE
        val base = getString(R.string.pass_saved, status.shortCode)
        val expiresAt = status.expiresAt

        if (expiresAt == null) {
            passStatusView.setTextColor(getColor(android.R.color.holo_orange_light))
            passStatusView.text = "$base\n${getString(R.string.pass_expiry_unknown)}"
            return
        }

        val days = PassExpiry.daysRemaining(expiresAt, System.currentTimeMillis())
        val dateText = formatDate(expiresAt)
        val (colorRes, expiryLine) = when {
            days < 0 -> android.R.color.holo_red_light to getString(R.string.pass_expired_saved)
            days <= 7 -> android.R.color.holo_orange_light to
                getString(R.string.pass_expiring_soon, dateText, days)
            else -> android.R.color.holo_green_light to
                getString(R.string.pass_valid_until, dateText, days)
        }
        passStatusView.setTextColor(getColor(colorRes))
        passStatusView.text = "$base\n$expiryLine"
    }

    private fun showSetExpiryDialog() {
        val calendar = Calendar.getInstance()
        configStore.getExpiresAt()?.let { calendar.timeInMillis = it }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    clear()
                    set(year, month, day)
                }
                configStore.setExpiryManually(picked.timeInMillis)
                ensureNotificationPermission()
                PassExpiryWorker.schedule(this)
                renderPassStatus()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun exportPass() {
        val link = configStore.exportableGuestPassLink() ?: return

        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Alta guest pass", link))
        Toast.makeText(this, R.string.pass_copied, Toast.LENGTH_SHORT).show()

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
        }
        startActivity(Intent.createChooser(share, getString(R.string.export_chooser_title)))
    }

    private fun confirmRemovePass() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_remove_title)
            .setMessage(R.string.confirm_remove_pass)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_remove) { _, _ ->
                configStore.clearSavedPass()
                PassExpiryWorker.cancel(this)
                renderPassStatus()
                statusView.text = getString(R.string.pass_not_configured)
            }
            .show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun formatDate(epochMs: Long): String =
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))

    private fun unlockDoor() {
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
