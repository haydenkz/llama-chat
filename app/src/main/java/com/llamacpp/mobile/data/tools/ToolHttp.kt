package com.llamacpp.mobile.data.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val TOOL_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/** GET a URL and return the body as text. */
internal suspend fun OkHttpClient.getText(url: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", TOOL_USER_AGENT)
        .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
        .build()
    newCall(request).execute().use { response ->
        val body = response.body.string()
        if (!response.isSuccessful) error("HTTP ${response.code}")
        body
    }
}

internal fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
