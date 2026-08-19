package com.spogulis.waterhwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
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
        mgr.updateAppWidget(appWidgetId, build(ctx, null, false, layoutFor(mgr, appWidgetId)))
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
                renderAll(ctx, note = ctx.getString(R.string.note_refreshing), animating = true)
                WidgetUpdateWorker.enqueueOnce(ctx, sync = false)
            }
            ACTION_SYNC -> {
                renderAll(ctx, note = ctx.getString(R.string.note_syncing), animating = true)
                WidgetUpdateWorker.enqueueOnce(ctx, sync = true)
            }
            ACTION_ADD_BLACK, ACTION_ADD_WHITE, ACTION_ADD_CAPP -> {
                val coffee = when (intent.action) {
                    ACTION_ADD_BLACK -> Config.Coffee.BLACK
                    ACTION_ADD_WHITE -> Config.Coffee.WHITE
                    else -> Config.Coffee.CAPPUCCINO
                }
                val ml = Config.coffeeMl(ctx, coffee)
                val label = ctx.getString(coffee.labelRes)
                renderAll(
                    ctx,
                    note = ctx.getString(R.string.note_adding, ml, label),
                    animating = true,
                )
                WidgetUpdateWorker.enqueueAdd(ctx, ml, label)
            }
            ACTION_UNDO -> {
                val last = Config.loadLastAdd(ctx) ?: return
                renderAll(
                    ctx,
                    note = ctx.getString(R.string.note_removing, last.ml, last.label),
                    animating = true,
                )
                WidgetUpdateWorker.enqueueAdd(ctx, -last.ml, last.label)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.spogulis.waterhwidget.REFRESH"
        const val ACTION_SYNC = "com.spogulis.waterhwidget.SYNC"
        const val ACTION_ADD_BLACK = "com.spogulis.waterhwidget.ADD_BLACK"
        const val ACTION_ADD_WHITE = "com.spogulis.waterhwidget.ADD_WHITE"
        const val ACTION_ADD_CAPP = "com.spogulis.waterhwidget.ADD_CAPP"
        const val ACTION_UNDO = "com.spogulis.waterhwidget.UNDO"

        // Launchers report ~70dp per grid cell minus margins; two cells start
        // safely above this.
        private const val LARGE_MIN_HEIGHT_DP = 105

        /** Repaint every widget instance from the cached status. */
        fun renderAll(ctx: Context, note: String? = null, animating: Boolean = false) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WaterWidgetProvider::class.java))
            for (id in ids) {
                mgr.updateAppWidget(id, build(ctx, note, animating, layoutFor(mgr, id)))
            }
        }

        private fun layoutFor(mgr: AppWidgetManager, id: Int): Int {
            val minH = mgr.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            return if (minH >= LARGE_MIN_HEIGHT_DP) R.layout.widget_large else R.layout.widget
        }

        private fun build(ctx: Context, note: String?, animating: Boolean, layoutId: Int): RemoteViews {
            val views = RemoteViews(ctx.packageName, layoutId)
            val st = Config.loadStatus(ctx)
            val err = Config.lastError(ctx)
            val today = LocalDate.now().toString()
            // Tally is keyed by the server's day (mirrored from its ledger).
            val coffeeToday = Config.coffeeToday(ctx, st?.date ?: today)

            if (st != null) {
                views.setTextViewText(
                    R.id.txt_main,
                    ctx.getString(R.string.main_line, st.intakeMl, st.goalMl)
                )
                val max = st.goalMl.coerceAtLeast(1)
                views.setProgressBar(R.id.progress, max, st.intakeMl, animating)
                views.setTextViewText(R.id.txt_sub, note ?: statusLine(ctx, st, err, coffeeToday))
                if (layoutId == R.layout.widget_large) {
                    // Second bar on the same scale: the coffee share of today.
                    if (coffeeToday > 0) {
                        views.setViewVisibility(R.id.progress_coffee, View.VISIBLE)
                        views.setProgressBar(R.id.progress_coffee, max, coffeeToday, false)
                    } else {
                        views.setViewVisibility(R.id.progress_coffee, View.GONE)
                    }
                }
            } else {
                views.setTextViewText(R.id.txt_main, ctx.getString(R.string.main_placeholder))
                views.setProgressBar(R.id.progress, 1, 0, animating)
                views.setTextViewText(
                    R.id.txt_sub,
                    note ?: err ?: ctx.getString(R.string.error_not_configured)
                )
                if (layoutId == R.layout.widget_large) {
                    views.setViewVisibility(R.id.progress_coffee, View.GONE)
                }
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
                    views.setViewVisibility(viewId, View.VISIBLE)
                    views.setOnClickPendingIntent(viewId, broadcast(ctx, action, requestCode))
                } else {
                    views.setViewVisibility(viewId, View.GONE)
                }
            }

            // Undo appears only while there is a same-day addition to revert.
            val last = Config.loadLastAdd(ctx)
            if (last != null && last.date == today) {
                views.setViewVisibility(R.id.btn_undo, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.btn_undo, broadcast(ctx, ACTION_UNDO, 6))
            } else {
                views.setViewVisibility(R.id.btn_undo, View.GONE)
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

        /** Shared with DashboardActivity — the "42% · goal +550 sweat · ☕ 350 ml · 16:25" line. */
        fun statusLine(ctx: Context, st: Config.Status, err: String?, coffeeToday: Int): String {
            val parts = mutableListOf("${st.percent}%")
            if (st.sweatLossMl > 0) {
                parts += ctx.getString(R.string.sweat_bump, st.sweatLossMl)
            }
            if (coffeeToday > 0) {
                parts += ctx.getString(R.string.coffee_tally, coffeeToday)
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
