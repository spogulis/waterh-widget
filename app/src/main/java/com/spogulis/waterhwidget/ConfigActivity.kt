package com.spogulis.waterhwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/** Settings screen: server URL + sync key. Also the widget's configure activity. */
class ConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val urlField = findViewById<EditText>(R.id.field_url)
        val keyField = findViewById<EditText>(R.id.field_key)
        val openWaterh = findViewById<CheckBox>(R.id.check_open_waterh)
        val pkgField = findViewById<EditText>(R.id.field_waterh_pkg)
        val delayField = findViewById<EditText>(R.id.field_delay)
        val result = findViewById<TextView>(R.id.txt_result)

        Config.loadServer(this)?.let {
            urlField.setText(it.baseUrl)
            keyField.setText(it.key)
        }
        openWaterh.isChecked = Config.openWaterhEnabled(this)
        pkgField.setText(Config.waterhPackage(this))
        delayField.setText(Config.syncDelaySec(this).toString())

        val coffeeRows = listOf(
            Triple(Config.Coffee.BLACK, R.id.check_black, R.id.field_black_ml),
            Triple(Config.Coffee.WHITE, R.id.check_white, R.id.field_white_ml),
            Triple(Config.Coffee.CAPPUCCINO, R.id.check_capp, R.id.field_capp_ml),
        )
        for ((coffee, checkId, mlId) in coffeeRows) {
            findViewById<CheckBox>(checkId).isChecked = Config.coffeeEnabled(this, coffee)
            findViewById<EditText>(mlId).setText(Config.coffeeMl(this, coffee).toString())
        }

        val manualField = findViewById<EditText>(R.id.field_manual_ml)
        Config.loadStatus(this)?.let {
            manualField.setText(Config.coffeeToday(this, it.date).toString())
        }
        findViewById<Button>(R.id.btn_set_manual).setOnClickListener {
            val server = Config.loadServer(this)
            val ml = manualField.text.toString().toIntOrNull()
            if (server == null || ml == null || ml < 0) {
                result.text = getString(
                    if (server == null) R.string.error_not_configured else R.string.hint_ml
                )
                return@setOnClickListener
            }
            result.text = getString(R.string.testing)
            thread {
                val msg = try {
                    val st = Api.setManualToday(server, ml)
                    Config.saveStatus(this, st)
                    WaterWidgetProvider.renderAll(this)
                    getString(R.string.set_ok, ml)
                } catch (e: Exception) {
                    getString(R.string.test_fail, e.message ?: e.javaClass.simpleName)
                }
                runOnUiThread { result.text = msg }
            }
        }

        findViewById<Button>(R.id.btn_test).setOnClickListener {
            val url = Config.normalizeUrl(urlField.text.toString())
            if (url.isEmpty()) {
                result.text = getString(R.string.error_url_missing)
                return@setOnClickListener
            }
            urlField.setText(url)
            val server = Config.Server(url, keyField.text.toString().trim())
            result.text = getString(R.string.testing)
            thread {
                val msg = try {
                    val st = Api.fetchStatus(server)
                    getString(R.string.test_ok, st.intakeMl, st.goalMl, st.sweatLossMl)
                } catch (e: Exception) {
                    getString(R.string.test_fail, e.message ?: e.javaClass.simpleName)
                }
                runOnUiThread { result.text = msg }
            }
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val url = Config.normalizeUrl(urlField.text.toString())
            if (url.isEmpty()) {
                result.text = getString(R.string.error_url_missing)
                return@setOnClickListener
            }
            urlField.setText(url)
            Config.saveServer(this, url, keyField.text.toString())
            Config.saveOpenWaterh(
                this,
                openWaterh.isChecked,
                pkgField.text.toString(),
                delayField.text.toString().toIntOrNull() ?: Config.DEFAULT_SYNC_DELAY_SEC
            )
            for ((coffee, checkId, mlId) in coffeeRows) {
                Config.saveCoffee(
                    this, coffee,
                    findViewById<CheckBox>(checkId).isChecked,
                    findViewById<EditText>(mlId).text.toString().toIntOrNull()
                        ?: coffee.defaultMl
                )
            }
            // Repaint immediately so button visibility changes apply even if
            // the network fetch can't run right now.
            WaterWidgetProvider.renderAll(this)
            WidgetUpdateWorker.schedulePeriodic(this)
            WidgetUpdateWorker.enqueueOnce(this, sync = false)
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                )
                finish()
            }
        }
    }
}
