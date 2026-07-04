package com.primeharshh.usagetracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(UsageBridge(), "Android")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class UsageBridge {

        @JavascriptInterface
        fun hasPermission(): Boolean {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }

        @JavascriptInterface
        fun requestPermission() {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
        }

        @JavascriptInterface
        fun getUsageData(hoursBack: Int): String {
            val result = JSONObject()
            val appEventsArr = JSONArray()
            val screenEventsArr = JSONArray()

            try {
                val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val endTime = System.currentTimeMillis()
                val cal = Calendar.getInstance()
                cal.timeInMillis = endTime
                cal.add(Calendar.HOUR_OF_DAY, -hoursBack)
                val startTime = cal.timeInMillis

                val events = usm.queryEvents(startTime, endTime)
                val event = UsageEvents.Event()
                val pm = packageManager

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)

                    when (event.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND,
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            val pkg = event.packageName
                            val label = try {
                                val ai = pm.getApplicationInfo(pkg, 0)
                                pm.getApplicationLabel(ai).toString()
                            } catch (e: PackageManager.NameNotFoundException) {
                                pkg
                            }
                            val obj = JSONObject()
                            obj.put("app", label)
                            obj.put("pkg", pkg)
                            obj.put(
                                "type",
                                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) "OPEN" else "CLOSE"
                            )
                            obj.put("time", event.timeStamp)
                            appEventsArr.put(obj)
                        }

                        UsageEvents.Event.SCREEN_INTERACTIVE -> {
                            val obj = JSONObject()
                            obj.put("type", "ON")
                            obj.put("time", event.timeStamp)
                            screenEventsArr.put(obj)
                        }

                        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                            val obj = JSONObject()
                            obj.put("type", "OFF")
                            obj.put("time", event.timeStamp)
                            screenEventsArr.put(obj)
                        }
                    }
                }

                result.put("appEvents", appEventsArr)
                result.put("screenEvents", screenEventsArr)
                result.put("startTime", startTime)
                result.put("endTime", endTime)
                result.put("ok", true)
            } catch (e: Exception) {
                result.put("ok", false)
                result.put("error", e.message)
            }

            return result.toString()
        }
    }
}
