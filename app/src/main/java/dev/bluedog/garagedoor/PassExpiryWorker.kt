// SPDX-License-Identifier: MIT

package dev.bluedog.garagedoor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Daily background check that reminds the user before their guest pass expires, so a lapse is never
 * a surprise. Notifies once per crossed threshold (7 days, then 1 day) using dedup state in
 * [UnlockConfigStore]; saving a new pass or changing the expiry resets that state.
 */
class PassExpiryWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val store = UnlockConfigStore(applicationContext)
        val expiresAt = store.getExpiresAt() ?: return Result.success()
        val now = System.currentTimeMillis()

        val threshold = PassExpiry.activeReminderThreshold(expiresAt, now) ?: return Result.success()
        if (threshold < store.getLastNotifiedThreshold()) {
            if (threshold == PassExpiry.EXPIRED_THRESHOLD) {
                notify(
                    applicationContext.getString(R.string.pass_expired_notification_title),
                    applicationContext.getString(R.string.pass_expired_notification),
                )
            } else {
                val days = PassExpiry.daysRemaining(expiresAt, now)
                notify(
                    applicationContext.getString(R.string.pass_expiry_notification_title),
                    applicationContext.getString(R.string.pass_expiry_notification, days),
                )
            }
            store.setLastNotifiedThreshold(threshold)
        }
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return

        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_garage)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "pass_expiry"
        private const val WORK_NAME = "pass_expiry_check"
        private const val ONE_TIME_WORK_NAME = "pass_expiry_check_now"
        private const val NOTIFICATION_ID = 1001

        /** (Re)schedules the daily check. Safe to call on every save. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PassExpiryWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Evaluates the pass immediately — used right after a save or expiry change so the reminder
         * (or expired notice) fires without waiting for the next daily run. The periodic check
         * alone would not re-run within its current period.
         */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<PassExpiryWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.pass_expiry_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.pass_expiry_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
