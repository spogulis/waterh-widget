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
            val addMl = inputData.getInt(KEY_ADD_ML, 0)
            if (addMl != 0) {
                // /add replies with fresh status, so no second round-trip.
                val st = Api.addIntake(server, addMl)
                Config.saveStatus(ctx, st)
                val today = java.time.LocalDate.now().toString()
                Config.bumpCoffeeToday(ctx, today, addMl)
                if (addMl > 0) {
                    Config.saveLastAdd(
                        ctx, addMl, inputData.getString(KEY_ADD_LABEL) ?: "", today
                    )
                } else {
                    Config.clearLastAdd(ctx)   // an undo consumed the last add
                }
            } else {
                if (inputData.getBoolean(KEY_SYNC, false)) Api.runSync(server)
                Config.saveStatus(ctx, Api.fetchStatus(server))
            }
        } catch (e: Exception) {
            Config.saveError(ctx, e.message ?: e.javaClass.simpleName)
        }
        WaterWidgetProvider.renderAll(ctx)
        Result.success()
    }

    companion object {
        private const val KEY_SYNC = "sync"
        private const val KEY_ADD_ML = "addMl"
        private const val KEY_ADD_LABEL = "addLabel"
        private const val PERIODIC_WORK = "waterh-refresh"
        private const val REFRESH_WORK = "waterh-refresh-now"
        // Separate names so a refresh tap can't cancel a pending delayed sync,
        // and coffee adds queue up instead of clobbering either.
        private const val SYNC_WORK = "waterh-sync-now"
        private const val ADD_WORK = "waterh-add"

        fun enqueueOnce(ctx: Context, sync: Boolean, delaySec: Long = 0) {
            val req = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(Data.Builder().putBoolean(KEY_SYNC, sync).build())
                .setInitialDelay(delaySec, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                if (sync) SYNC_WORK else REFRESH_WORK, ExistingWorkPolicy.REPLACE, req
            )
        }

        fun enqueueAdd(ctx: Context, ml: Int, label: String) {
            // Deliberately no network constraint: offline, the attempt fails
            // fast and the widget shows an error instead of the tap sitting
            // in a queue for hours and logging a long-forgotten coffee later.
            val req = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(
                    Data.Builder().putInt(KEY_ADD_ML, ml).putString(KEY_ADD_LABEL, label).build()
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                ADD_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, req
            )
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
