package com.spogulis.waterhwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
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
        val result = findViewById<TextView>(R.id.txt_result)

        Config.loadServer(this)?.let {
            urlField.setText(it.baseUrl)
            keyField.setText(it.key)
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
