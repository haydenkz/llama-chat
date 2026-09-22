package com.llamacpp.mobile.data.tools

import com.llamacpp.mobile.data.remote.dto.ToolDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client-side `web_search` tool. Queries DuckDuckGo's HTML endpoint (no API key)
 * and returns a compact, model-readable list of results.
 */
class WebSearchTool(private val client: OkHttpClient) : ChatTool {
    override val name = NAME
    override val displayName = "Web search"
    override val description =
        "Search the public web for up-to-date information. Use this whenever the answer depends on " +
            "recent events, facts you are unsure about, or anything that happened after your training data."

    override fun definition(): ToolDto =
        functionTool(NAME, description, mapOf("query" to "The search query."))

    override suspend fun execute(arguments: String): String =
        search(toolStringArg(arguments, "query") ?: arguments.trim().trim('{', '}', ' '))

    suspend fun search(query: String, maxResults: Int = 6): String = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext "No query provided."
        try {
            val form = "q=" + urlEncode(query)
            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/")
                .header("User-Agent", TOOL_USER_AGENT)
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
                    results.forEachIndexed { index, result ->
                        append(index + 1).append(". ").append(result.title).append('\n')
                        append(result.url).append('\n')
                        if (result.snippet.isNotBlank()) append(result.snippet).append('\n')
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
    }
}
