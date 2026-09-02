package com.yungmoolah.converter.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yungmoolah.converter.data.RatesRepository
import com.yungmoolah.converter.data.RatesStore
import com.yungmoolah.converter.data.RefreshResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Keeps the cached rates warm in the background so the app opens on current
 * numbers even after days offline.
 *
 * The provider republishes once a day; running every six hours picks that up
 * soon after it lands without hammering a free endpoint. WorkManager holds the
 * schedule across reboots and only runs us when there is a network.
 */
class RefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = RatesRepository(RatesStore.create(applicationContext))
        val cached = repository.snapshot.first()
        return when (repository.refresh(cached = cached, force = false)) {
            is RefreshResult.Updated, RefreshResult.AlreadyFresh -> Result.success()
            // Let WorkManager back off and retry rather than waiting for the next
            // six-hour slot; the cached snapshot stays usable meanwhile.
            is RefreshResult.Failed -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "rates-refresh"

        /** Idempotent: safe to call on every app start. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
