package com.llamacpp.mobile.data.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client-side `web_search` tool. Queries DuckDuckGo's HTML endpoint (no API key)
 * and returns a compact, model-readable list of results.
 */
class WebSearchTool(
    private val client: OkHttpClient,
) {
    suspend fun search(query: String, maxResults: Int = 6): String = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext "No query provided."
        try {
            val form = "q=" + java.net.URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html")
                .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "Search failed: HTTP ${response.code}"
                response.body.string()
            }
            val results = parseResults(html, maxResults)
            if (results.isEmpty()) {
                "No web results found for \"$query\"."
            } else {
                buildString {
                    append("Web results for \"").append(query).append("\":\n\n")
                    results.forEachIndexed { i, r ->
                        append(i + 1).append(". ").append(r.title).append('\n')
                        append(r.url).append('\n')
                        if (r.snippet.isNotBlank()) append(r.snippet).append('\n')
                        append('\n')
                    }
                }.trim()
            }
        } catch (t: Throwable) {
            "Search error: ${t.message}"
        }
    }

    private data class Result(val title: String, val url: String, val snippet: String)

    private fun parseResults(html: String, max: Int): List<Result> {
        val linkRegex = Regex(
            """<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val snippetRegex = Regex(
            """class="result__snippet"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val links = linkRegex.findAll(html).toList()
        val snippets = snippetRegex.findAll(html).map { clean(it.groupValues[1]) }.toList()
        return links.take(max).mapIndexed { index, match ->
            Result(
                title = clean(match.groupValues[2]).ifBlank { "Result ${index + 1}" },
                url = resolveUrl(match.groupValues[1]),
                snippet = snippets.getOrNull(index).orEmpty(),
            )
        }
    }

    private fun resolveUrl(href: String): String {
        val decoded = clean(href)
        val uddg = Regex("""[?&]uddg=([^&]+)""").find(decoded)?.groupValues?.get(1)
        if (uddg != null) {
            return runCatching { java.net.URLDecoder.decode(uddg, "UTF-8") }.getOrDefault(uddg)
        }
        return if (decoded.startsWith("//")) "https:$decoded" else decoded
    }

    private fun clean(raw: String): String = raw
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        const val NAME = "web_search"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        /** OpenAI-style tool definition advertised to the model. */
        fun definition(): com.llamacpp.mobile.data.remote.dto.ToolDto =
            com.llamacpp.mobile.data.remote.dto.ToolDto(
                type = "function",
                function = com.llamacpp.mobile.data.remote.dto.ToolDefinitionDto(
                    name = NAME,
                    description = "Search the public web for up-to-date information. " +
                        "Use this whenever the answer depends on recent events, facts you are unsure about, " +
                        "or anything that happened after your training data.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("query") {
                                put("type", "string")
                                put("description", "The search query.")
                            }
                        }
                        put("required", JsonArray(listOf(JsonPrimitive("query"))))
                    },
                ),
            )
    }
}
