package com.spogulis.waterhwidget

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible trampoline for the "open WaterH app when syncing" option.
 *
 * A widget tap can't launch another app directly, so the sync button opens
 * this no-UI activity, which brings the WaterH app to the foreground (that
 * triggers its BLE sync with the bottle) and schedules the server sync a few
 * seconds later, giving the app time to upload. Android offers no way to
 * close another app afterwards — the user swipes back home.
 */
class SyncActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = Config.waterhPackage(this)
        val delay = Config.syncDelaySec(this)
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            WaterWidgetProvider.renderAll(this, getString(R.string.note_waiting_waterh, delay))
            WidgetUpdateWorker.enqueueOnce(this, sync = true, delaySec = delay.toLong())
            startActivity(launch)
        } else {
            Toast.makeText(this, getString(R.string.waterh_not_found, pkg), Toast.LENGTH_LONG)
                .show()
            WaterWidgetProvider.renderAll(this, getString(R.string.note_syncing))
            WidgetUpdateWorker.enqueueOnce(this, sync = true)
        }
        finish()
    }
}
