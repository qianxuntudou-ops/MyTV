package com.mytv0

import android.net.Uri
import android.util.Log
import com.mytv0.requests.HttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.util.Locale
import kotlin.random.Random

object CctvLiveResolver {
    private const val TAG = "CctvLiveResolver"
    private const val API = "https://vdn.live.cntv.cn/api2/liveHtml5.do"
    private const val VIDEO_PLAYER = "1"
    private const val VN = "2049"
    private const val CLIENT = "html5"

    data class Result(
        val url: String,
        val backups: List<String>,
        val headers: Map<String, String>,
    )

    fun resolve(channelId: String, quality: Quality): Result {
        val slug = channelId.lowercase(Locale.ROOT)
        val query = buildApiUrl(slug)

        val request = Request.Builder()
            .url(query)
            .header("Referer", "https://tv.cctv.com/live/$slug/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 7.0; MyTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
            .build()

        HttpClient.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("resolver status=${response.codeAlias()}")
            }

            val body = response.bodyAlias()?.string().orEmpty()
            val json = extractJson(body)
            val ack = json.optString("ack")
            if (ack != "yes") {
                throw IllegalStateException("resolver ack=$ack tip=${json.optString("tip_msg")}")
            }

            val hls = json.optJSONObject("hls_url")
                ?: throw IllegalStateException("resolver missing hls_url")

            val candidates = linkedSetOf<String>()
            selectByQuality(hls, quality)?.let { candidates.add(it) }

            for (key in listOf("hls1", "hls2", "hls4", "hls6")) {
                val value = hls.optString(key)
                if (value.startsWith("http")) {
                    candidates.add(value)
                }
            }

            val urls = candidates.filter { it.startsWith("http") }
            if (urls.isEmpty()) {
                throw IllegalStateException("resolver no playable hls for $slug")
            }

            Log.i(TAG, "resolve $slug quality=${quality.key} primary=${urls.first()} api=$query")
            return Result(
                url = urls.first(),
                backups = urls.drop(1),
                headers = mapOf(
                    "Referer" to "https://tv.cctv.com/live/$slug/",
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 7.0; MyTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
                )
            )
        }
    }

    private fun buildApiUrl(slug: String): String {
        val query = listOf(
            "channel=pw://cctv_p2p_hd$slug",
            "channel_id=$slug",
            "client=$CLIENT",
            "im=1",
            "tsp=${System.currentTimeMillis() / 1000}",
            "uid=",
            "vc=${randomVc()}",
            "video_player=$VIDEO_PLAYER",
            "vn=$VN",
            "wlan="
        ).joinToString("&")

        val url = "$API?$query"
        requireNotNull(url.toHttpUrlOrNull()) { "resolver api url invalid: $url" }
        return url
    }

    private fun extractJson(body: String): JSONObject {
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw IllegalStateException("resolver body invalid")
        }
        return JSONObject(body.substring(start, end + 1))
    }

    private fun selectByQuality(hls: JSONObject, quality: Quality): String? {
        return when (quality) {
            Quality.AUTO -> firstPlayable(hls, listOf("hls1", "hls2", "hls4"))
            Quality.BLUE -> hls.optString("hls1").takeIf { it.startsWith("http") }
            Quality.SUPER,
            Quality.HIGH,
            Quality.STANDARD,
            Quality.SMOOTH -> hls.optString("hls2").takeIf { it.startsWith("http") }
        }
    }

    private fun firstPlayable(hls: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            val value = hls.optString(key)
            if (value.startsWith("http")) {
                return value
            }
        }
        return null
    }

    private fun randomVc(): String {
        val chars = "0123456789ABCDEF"
        return buildString(32) {
            repeat(32) {
                append(chars[Random.nextInt(chars.length)])
            }
        }
    }

    enum class Quality(val key: String, val label: String) {
        AUTO("", "自动"),
        BLUE("p1080", "蓝光"),
        SUPER("p720", "超清"),
        HIGH("p540", "高清"),
        STANDARD("p480", "标清"),
        SMOOTH("p360", "流畅");

        companion object {
            fun fromStoredValue(value: String?): Quality {
                return entries.find { it.key == value } ?: AUTO
            }
        }
    }
}

