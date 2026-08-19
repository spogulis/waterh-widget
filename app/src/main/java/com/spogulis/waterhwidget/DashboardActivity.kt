package com.spogulis.waterhwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * Glanceable full-screen dashboard: the widget's content as a normal app
 * screen. Exists mainly for cover/external displays (e.g. Motorola Razr),
 * whose widget pickers are filtered but which can run any allowed app.
 */
class DashboardActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }
        wireCoffee(R.id.btn_black, Config.Coffee.BLACK)
        wireCoffee(R.id.btn_white, Config.Coffee.WHITE)
        wireCoffee(R.id.btn_capp, Config.Coffee.CAPPUCCINO)
        findViewById<ImageButton>(R.id.btn_undo).setOnClickListener {
            val last = Config.loadLastAdd(this) ?: return@setOnClickListener
            runAction(getString(R.string.note_removing, last.ml, last.label)) { server ->
                Api.addIntake(server, -last.ml).also { Config.clearLastAdd(this) }
            }
        }
        findViewById<ImageButton>(R.id.btn_sync).setOnClickListener {
            if (Config.openWaterhEnabled(this)) {
                // Same detour as the widget: open WaterH, delayed server sync.
                startActivity(Intent(this, SyncActivity::class.java))
            } else {
                runAction(getString(R.string.note_syncing)) { server ->
                    Api.runSync(server)
                    Api.fetchStatus(server)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        render(null)
        if (Config.loadServer(this) != null) {
            runAction(null) { server -> Api.fetchStatus(server) }
        }
    }

    private fun wireCoffee(viewId: Int, coffee: Config.Coffee) {
        findViewById<ImageButton>(viewId).setOnClickListener {
            val ml = Config.coffeeMl(this, coffee)
            val label = getString(coffee.labelRes)
            runAction(getString(R.string.note_adding, ml, label)) { server ->
                Api.addIntake(server, ml).also {
                    Config.saveLastAdd(this, ml, label, LocalDate.now().toString())
                }
            }
        }
    }

    /** Run a server call off the main thread, then repaint screen + widgets. */
    private fun runAction(note: String?, call: (Config.Server) -> Config.Status) {
        val server = Config.loadServer(this)
        if (server == null) {
            render(getString(R.string.error_not_configured))
            return
        }
        if (note != null) render(note, animating = true)
        thread {
            val err = try {
                Config.saveStatus(this, call(server))
                null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            if (err != null) Config.saveError(this, err)
            WaterWidgetProvider.renderAll(this)
            runOnUiThread { render(null) }
        }
    }

    private fun render(note: String?, animating: Boolean = false) {
        val txtMain = findViewById<TextView>(R.id.txt_main)
        val txtSub = findViewById<TextView>(R.id.txt_sub)
        val bar = findViewById<ProgressBar>(R.id.progress)
        val coffeeBar = findViewById<ProgressBar>(R.id.progress_coffee)
        val undo = findViewById<ImageButton>(R.id.btn_undo)

        val st = Config.loadStatus(this)
        val err = Config.lastError(this)
        val today = LocalDate.now().toString()
        val coffeeToday = Config.coffeeToday(this, st?.date ?: today)

        bar.isIndeterminate = animating
        if (st != null) {
            txtMain.text = getString(R.string.main_line, st.intakeMl, st.goalMl)
            bar.max = st.goalMl.coerceAtLeast(1)
            bar.progress = st.intakeMl
            txtSub.text = note ?: WaterWidgetProvider.statusLine(this, st, err, coffeeToday)
            coffeeBar.visibility = if (coffeeToday > 0) View.VISIBLE else View.GONE
            coffeeBar.max = st.goalMl.coerceAtLeast(1)
            coffeeBar.progress = coffeeToday
        } else {
            txtMain.text = getString(R.string.main_placeholder)
            txtSub.text = note ?: err ?: getString(R.string.error_not_configured)
            coffeeBar.visibility = View.GONE
        }

        val last = Config.loadLastAdd(this)
        undo.visibility = if (last != null && last.date == today) View.VISIBLE else View.GONE

        for ((id, coffee) in listOf(
            R.id.btn_black to Config.Coffee.BLACK,
            R.id.btn_white to Config.Coffee.WHITE,
            R.id.btn_capp to Config.Coffee.CAPPUCCINO,
        )) {
            findViewById<ImageButton>(id).visibility =
                if (Config.coffeeEnabled(this, coffee)) View.VISIBLE else View.GONE
        }
    }
}
