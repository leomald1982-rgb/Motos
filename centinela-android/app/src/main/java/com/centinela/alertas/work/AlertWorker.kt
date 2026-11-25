package com.centinela.alertas.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.centinela.alertas.CentinelaApp
import com.centinela.alertas.data.AlertRepository
import com.centinela.alertas.util.LocationHelper
import java.util.concurrent.TimeUnit

/** Escaneo en segundo plano: consulta las fuentes y notifica las alertas nuevas. */
class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CentinelaApp
        return try {
            LocationHelper.lastKnown(applicationContext)?.let { loc ->
                app.store.update { it.copy(lastLat = loc.lat, lastLon = loc.lon) }
            }
            val result = app.repository.refresh()
            val settings = app.store.current()
            Notifier.notify(applicationContext, result.fresh.filter { AlertRepository.shouldNotify(it, settings) })
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "centinela-scan"
        private const val NOW = "centinela-scan-now"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context, minutes: Int, replace: Boolean = false) {
            val req = PeriodicWorkRequestBuilder<AlertWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                PERIODIC,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<AlertWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
