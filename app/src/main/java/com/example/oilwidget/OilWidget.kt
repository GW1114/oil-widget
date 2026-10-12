package com.example.oilwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "油价小组件已安装。\n请回到桌面，长按空白处添加小部件。"
        tv.textSize = 18f
        tv.setPadding(60, 200, 60, 60)
        setContentView(tv)
    }
}

class OilPriceWidgetProvider : AppWidgetProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            showLoading(context, manager, id)
            scope.launch {
                try {
                    val data = OilApi.fetch("北京")
                    val v = RemoteViews(context.packageName, R.layout.widget_oil_price).apply {
                        setTextViewText(R.id.tv_province, data.province)
                        setTextViewText(R.id.tv_92, "92#: ${data.p92}")
                        setTextViewText(R.id.tv_95, "95#: ${data.p95}")
                        setTextViewText(R.id.tv_98, "98#: ${data.p98}")
                    }
                    manager.updateAppWidget(id, v)
                } catch (e: Exception) {
                    val v = RemoteViews(context.packageName, R.layout.widget_oil_price).apply {
                        setTextViewText(R.id.tv_province, "获取失败，请检查网络")
                        setTextViewText(R.id.tv_92, "92#: --")
                        setTextViewText(R.id.tv_95, "95#: --")
                        setTextViewText(R.id.tv_98, "98#: --")
                    }
                    manager.updateAppWidget(id, v)
                }
            }
        }
    }

    private fun showLoading(context: Context, manager: AppWidgetManager, id: Int) {
        val v = RemoteViews(context.packageName, R.layout.widget_oil_price).apply {
            setTextViewText(R.id.tv_province, "加载中...")
            setTextViewText(R.id.tv_92, "92#: --")
            setTextViewText(R.id.tv_95, "95#: --")
            setTextViewText(R.id.tv_98, "98#: --")
        }
        manager.updateAppWidget(id, v)
    }
}

data class OilData(val province: String, val p92: String, val p95: String, val p98: String)

object OilApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetch(province: String): OilData {
        val url = "https://60s.viki.moe/v2/fuel-price?province=$province"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: throw Exception("empty")
        val root = Gson().fromJson(body, JsonObject::class.java)
        val data = root.getAsJsonObject("data") ?: throw Exception("no data")

        var prov = province
        var p92 = "--"
        var p95 = "--"
        var p98 = "--"
        for (key in data.keySet()) {
            val value = data.get(key)?.asString ?: ""
            when {
                key.contains("92") -> p92 = value
                key.contains("95") -> p95 = value
                key.contains("98") -> p98 = value
                key.contains("province") || key.contains("省份") -> prov = value
            }
        }
        return OilData(prov, p92, p95, p98)
    }
}
