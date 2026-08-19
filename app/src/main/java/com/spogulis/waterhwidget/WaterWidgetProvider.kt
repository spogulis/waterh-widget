package com.spogulis.waterhwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

class WaterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderAll(ctx)
        WidgetUpdateWorker.enqueueOnce(ctx, sync = false)
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context, mgr: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        mgr.updateAppWidget(appWidgetId, build(ctx, null, layoutFor(mgr, appWidgetId)))
    }

    override fun onEnabled(ctx: Context) {
        WidgetUpdateWorker.schedulePeriodic(ctx)
    }

    override fun onDisabled(ctx: Context) {
        WidgetUpdateWorker.cancelPeriodic(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                renderAll(ctx, note = ctx.getString(R.string.note_refreshing))
                WidgetUpdateWorker.enqueueOnce(ctx, sync = false)
            }
            ACTION_SYNC -> {
                renderAll(ctx, note = ctx.getString(R.string.note_syncing))
                WidgetUpdateWorker.enqueueOnce(ctx, sync = true)
            }
            ACTION_ADD_BLACK, ACTION_ADD_WHITE, ACTION_ADD_CAPP -> {
                val coffee = when (intent.action) {
                    ACTION_ADD_BLACK -> Config.Coffee.BLACK
                    ACTION_ADD_WHITE -> Config.Coffee.WHITE
                    else -> Config.Coffee.CAPPUCCINO
                }
                val ml = Config.coffeeMl(ctx, coffee)
                renderAll(ctx, note = ctx.getString(R.string.note_adding, ml))
                WidgetUpdateWorker.enqueueAdd(ctx, ml)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.spogulis.waterhwidget.REFRESH"
        const val ACTION_SYNC = "com.spogulis.waterhwidget.SYNC"
        const val ACTION_ADD_BLACK = "com.spogulis.waterhwidget.ADD_BLACK"
        const val ACTION_ADD_WHITE = "com.spogulis.waterhwidget.ADD_WHITE"
        const val ACTION_ADD_CAPP = "com.spogulis.waterhwidget.ADD_CAPP"

        // Launchers report ~70dp per grid cell minus margins; two cells start
        // safely above this.
        private const val LARGE_MIN_HEIGHT_DP = 105

        /** Repaint every widget instance from the cached status. */
        fun renderAll(ctx: Context, note: String? = null) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WaterWidgetProvider::class.java))
            for (id in ids) {
                mgr.updateAppWidget(id, build(ctx, note, layoutFor(mgr, id)))
            }
        }

        private fun layoutFor(mgr: AppWidgetManager, id: Int): Int {
            val minH = mgr.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            return if (minH >= LARGE_MIN_HEIGHT_DP) R.layout.widget_large else R.layout.widget
        }

        private fun build(ctx: Context, note: String?, layoutId: Int): RemoteViews {
            val views = RemoteViews(ctx.packageName, layoutId)
            val st = Config.loadStatus(ctx)
            val err = Config.lastError(ctx)

            if (st != null) {
                views.setTextViewText(
                    R.id.txt_main,
                    ctx.getString(R.string.main_line, st.intakeMl, st.goalMl)
                )
                views.setProgressBar(R.id.progress, st.goalMl.coerceAtLeast(1), st.intakeMl, false)
                views.setTextViewText(R.id.txt_sub, note ?: subLine(ctx, st, err))
            } else {
                views.setTextViewText(R.id.txt_main, ctx.getString(R.string.main_placeholder))
                views.setProgressBar(R.id.progress, 1, 0, false)
                views.setTextViewText(
                    R.id.txt_sub,
                    note ?: err ?: ctx.getString(R.string.error_not_configured)
                )
            }

            views.setOnClickPendingIntent(R.id.widget_root, broadcast(ctx, ACTION_REFRESH, 1))
            views.setOnClickPendingIntent(R.id.btn_sync, syncIntent(ctx))

            val coffees = listOf(
                Triple(R.id.btn_black, Config.Coffee.BLACK, ACTION_ADD_BLACK to 3),
                Triple(R.id.btn_white, Config.Coffee.WHITE, ACTION_ADD_WHITE to 4),
                Triple(R.id.btn_capp, Config.Coffee.CAPPUCCINO, ACTION_ADD_CAPP to 5),
            )
            for ((viewId, coffee, actionAndCode) in coffees) {
                val (action, requestCode) = actionAndCode
                if (Config.coffeeEnabled(ctx, coffee)) {
                    views.setViewVisibility(viewId, android.view.View.VISIBLE)
                    views.setOnClickPendingIntent(viewId, broadcast(ctx, action, requestCode))
                } else {
                    views.setViewVisibility(viewId, android.view.View.GONE)
                }
            }
            return views
        }

        /** Sync taps either broadcast directly or detour through the WaterH app. */
        private fun syncIntent(ctx: Context): PendingIntent =
            if (Config.openWaterhEnabled(ctx)) {
                PendingIntent.getActivity(
                    ctx, 2,
                    Intent(ctx, SyncActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                broadcast(ctx, ACTION_SYNC, 2)
            }

        private fun subLine(ctx: Context, st: Config.Status, err: String?): String {
            val parts = mutableListOf("${st.percent}%")
            if (st.sweatLossMl > 0) {
                parts += ctx.getString(R.string.sweat_bump, st.sweatLossMl)
            }
            // The server reports "today" in its own time zone; flag a stale day.
            if (st.date.isNotEmpty() && st.date != LocalDate.now().toString()) {
                parts += st.date
            }
            parts += SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(st.fetchedAt))
            var line = parts.joinToString(" · ")
            if (err != null) line = "⚠ $err"
            return line
        }

        private fun broadcast(ctx: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(ctx, WaterWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                ctx, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
