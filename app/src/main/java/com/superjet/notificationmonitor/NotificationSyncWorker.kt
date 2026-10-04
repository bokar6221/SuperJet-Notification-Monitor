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
        if (SecureConfig.getToken(applicationContext).isBlank()) return Result.success()

        var retry = false
        for (item in pending) {
            val result = NotificationApi.syncOne(applicationContext, item)
            if (result.ok) {
                NotificationStore.markSynced(applicationContext, item.eventId)
            } else {
                NotificationStore.markSynced(applicationContext, item.eventId, result.error)
                if (!result.error.startsWith("HTTP_401") &&
                    !result.error.startsWith("HTTP_403") &&
                    !result.error.contains("LOGIN_REQUIRED")) {
                    retry = true
                }
            }
        }
        return if (retry) Result.retry() else Result.success()
    }
}

object NotificationSyncScheduler {
    const val UNIQUE_WORK = "superjet_payment_notification_sync"

    fun enqueue(context: android.content.Context) {
        if (SecureConfig.getToken(context).isBlank()) return
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