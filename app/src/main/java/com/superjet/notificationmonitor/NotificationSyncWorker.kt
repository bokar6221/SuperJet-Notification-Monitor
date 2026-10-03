package com.superjet.notificationmonitor

import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class NotificationSyncWorker(
    appContext: android.content.Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pending = NotificationStore.pending(applicationContext)
        if (pending.isEmpty()) return Result.success()

        var retry = false

        for (item in pending) {
            val result = NotificationApi.syncOne(applicationContext, item)

            if (result.ok) {
                NotificationStore.markSynced(applicationContext, item.eventId)
            } else {
                NotificationStore.markSynced(applicationContext, item.eventId, result.error)

                val permanent = result.error.startsWith("HTTP_403") ||
                    result.error.contains("SERVER_URL_NOT_CONFIGURED") ||
                    result.error.contains("ANDROID_TOKEN_NOT_CONFIGURED")

                if (!permanent) retry = true
            }
        }

        return if (retry) Result.retry() else Result.success()
    }
}

object NotificationSyncScheduler {
    const val UNIQUE_WORK = "superjet_payment_notification_sync"

    fun enqueue(context: android.content.Context) {
        val request = OneTimeWorkRequestBuilder<NotificationSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: android.content.Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }
}
