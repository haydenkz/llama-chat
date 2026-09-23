package com.llamacpp.mobile.data.tools

import com.llamacpp.mobile.data.remote.dto.ToolDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/** Article summaries from Wikipedia (no API key). */
class WikipediaTool(
    private val client: OkHttpClient,
    private val json: Json,
) : ChatTool {
    override val name = NAME
    override val displayName = "Wikipedia"
    override val description =
        "Look up a factual summary from Wikipedia. Use this for encyclopedic facts about people, places and things."

    override fun definition(): ToolDto =
        functionTool(NAME, description, mapOf("query" to "The topic to look up."))

    override suspend fun execute(arguments: String): String {
        val query = toolStringArg(arguments, "query", "topic", "title")
            ?: return "No query provided."

        val search = client.getText(
            "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=1" +
                "&srsearch=${urlEncode(query)}",
        )
        val title = runCatching {
            json.parseToJsonElement(search).jsonObject["query"]?.jsonObject
                ?.get("search")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("title")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: return "No Wikipedia article found for \"$query\"."

        val summary = client.getText("https://en.wikipedia.org/api/rest_v1/page/summary/${urlEncodePath(title)}")
        val body = runCatching { json.parseToJsonElement(summary).jsonObject }.getOrNull()
            ?: return "Could not load the Wikipedia article \"$title\"."

        val extract = body["extract"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (extract.isBlank()) return "The Wikipedia article \"$title\" has no summary."
        val url = body["content_urls"]?.jsonObject
            ?.get("desktop")?.jsonObject
            ?.get("page")?.jsonPrimitive?.contentOrNull

        return buildString {
            append(title).append("\n\n").append(extract)
            if (!url.isNullOrBlank()) append("\n\nSource: ").append(url)
        }.trim()
    }

    companion object {
        const val NAME = "wikipedia_summary"
    }
}
