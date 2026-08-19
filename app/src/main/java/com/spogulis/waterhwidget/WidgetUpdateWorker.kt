package com.spogulis.waterhwidget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Fetches /status (optionally running /sync first) and repaints the widget. */
class WidgetUpdateWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val server = Config.loadServer(ctx)
        if (server == null) {
            Config.saveError(ctx, ctx.getString(R.string.error_not_configured))
            WaterWidgetProvider.renderAll(ctx)
            return@withContext Result.success()
        }
        try {
            if (inputData.getBoolean(KEY_SYNC, false)) Api.runSync(server)
            Config.saveStatus(ctx, Api.fetchStatus(server))
        } catch (e: Exception) {
            Config.saveError(ctx, e.message ?: e.javaClass.simpleName)
        }
        WaterWidgetProvider.renderAll(ctx)
        Result.success()
    }

    companion object {
        private const val KEY_SYNC = "sync"
        private const val PERIODIC_WORK = "waterh-refresh"
        private const val ONCE_WORK = "waterh-refresh-now"

        fun enqueueOnce(ctx: Context, sync: Boolean) {
            val req = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(Data.Builder().putBoolean(KEY_SYNC, sync).build())
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork(ONCE_WORK, ExistingWorkPolicy.REPLACE, req)
        }

        fun schedulePeriodic(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancelPeriodic(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC_WORK)
        }
    }
}
