package com.llamacpp.mobile.data.remote

import com.llamacpp.mobile.data.remote.dto.ChatCompletionChunkDto
import com.llamacpp.mobile.data.remote.dto.ChatCompletionRequestDto
import com.llamacpp.mobile.data.remote.dto.CompletionChunkDto
import com.llamacpp.mobile.data.remote.dto.CompletionRequestDto
import com.llamacpp.mobile.data.remote.dto.ControlRequestDto
import com.llamacpp.mobile.data.remote.dto.DetokenizeRequestDto
import com.llamacpp.mobile.data.remote.dto.DetokenizeResponseDto
import com.llamacpp.mobile.data.remote.dto.ErrorEnvelope
import com.llamacpp.mobile.data.remote.dto.HealthDto
import com.llamacpp.mobile.data.remote.dto.ModelListDto
import com.llamacpp.mobile.data.remote.dto.ModelNameRequestDto
import com.llamacpp.mobile.data.remote.dto.PropsDto
import com.llamacpp.mobile.data.remote.dto.SlotDto
import com.llamacpp.mobile.data.remote.dto.TokenizeRequestDto
import com.llamacpp.mobile.data.remote.dto.TokenizeResponseDto
import com.llamacpp.mobile.data.remote.dto.toDomain
import com.llamacpp.mobile.domain.model.LlamaModel
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.domain.model.ServerHealth
import com.llamacpp.mobile.domain.model.ServerProps
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException

class ApiException(val statusCode: Int, override val message: String) : Exception(message)

/** Parsed events emitted while streaming a chat completion. */
sealed interface ChatStreamEvent {
    data class Chunk(val chunk: ChatCompletionChunkDto) : ChatStreamEvent
    data class Failed(val message: String) : ChatStreamEvent
}

/** Parsed events emitted while streaming a raw text completion. */
sealed interface CompletionStreamEvent {
    data class Delta(val text: String, val finishReason: String? = null) : CompletionStreamEvent
    data class Failed(val message: String) : CompletionStreamEvent
}

class LlamaApi(
    private val plainClient: OkHttpClient,
    private val streamClient: OkHttpClient,
    private val json: Json,
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ---- helpers ----------------------------------------------------------

    private fun endpoint(server: ServerConfig, path: String): HttpUrl =
        (server.normalizedBaseUrl + path).toHttpUrl()

    private fun Request.Builder.auth(apiKey: String?): Request.Builder = apply {
        if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer ${apiKey.trim()}")
    }

    private inline fun <reified T> bodyJson(value: T): okhttp3.RequestBody =
        json.encodeToString(value).toRequestBody(jsonMedia)

    /** Enqueues the call so coroutine cancellation cancels the request instead of blocking an IO thread. */
    private suspend fun executeRaw(
        request: Request,
        client: OkHttpClient = plainClient,
    ): Pair<Int, String> = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    cont.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    val result = runCatching { response.use { it.code to it.body.string() } }
                    cont.resumeWith(result)
                }
            },
        )
    }

    private suspend fun executeText(request: Request): String {
        val (code, text) = executeRaw(request)
        if (code !in 200..299) throw ApiException(code, errorMessage(code, text))
        return text
    }

    private suspend inline fun <reified T> executeJson(request: Request): T {
        val text = executeText(request)
        return json.decodeFromString(text)
    }

    private fun errorMessage(code: Int, body: String): String {
        val parsed = runCatching { json.decodeFromString<ErrorEnvelope>(body) }.getOrNull()
        return parsed?.error?.message?.takeIf { it.isNotBlank() } ?: "HTTP $code"
    }

    // ---- endpoints --------------------------------------------------------

    suspend fun health(server: ServerConfig, model: String? = null): ServerHealth {
        val builder = endpoint(server, "/health").newBuilder()
        if (!model.isNullOrBlank()) builder.addQueryParameter("model", model)
        val request = Request.Builder().url(builder.build()).get().auth(server.apiKey).build()
        return try {
            val (code, text) = executeRaw(request)
            val status = runCatching { json.decodeFromString<HealthDto>(text) }.getOrNull()?.status
            ServerHealth(
                ok = code in 200..299 && status.equals("ok", ignoreCase = true),
                status = status ?: "error",
                message = if (code in 200..299) null else text.take(200),
            )
        } catch (t: Throwable) {
            ServerHealth(ok = false, status = "unreachable", message = t.message)
        }
    }

    suspend fun props(server: ServerConfig, model: String? = null): ServerProps {
        val builder = endpoint(server, "/props").newBuilder()
        if (!model.isNullOrBlank()) builder.addQueryParameter("model", model)
        val request = Request.Builder().url(builder.build()).get().auth(server.apiKey).build()
        val dto: PropsDto = executeJson(request)
        return dto.toDomain()
    }

    suspend fun models(server: ServerConfig): List<LlamaModel> {
        val request = Request.Builder().url(endpoint(server, "/v1/models")).get().auth(server.apiKey).build()
        val dto: ModelListDto = executeJson(request)
        return dto.data.map { it.toDomain() }
    }

    fun streamChat(server: ServerConfig, request: ChatCompletionRequestDto): Flow<ChatStreamEvent> =
        callbackFlow {
            val httpRequest = Request.Builder()
                .url(endpoint(server, "/v1/chat/completions"))
                .header("Accept", "text/event-stream")
                .post(bodyJson(request))
                .auth(server.apiKey)
                .build()

            val listener = object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == "[DONE]") {
                        close()
                        return
                    }
                    val chunk = runCatching {
                        json.decodeFromString<ChatCompletionChunkDto>(data)
                    }.getOrNull()
                    if (chunk != null) trySend(ChatStreamEvent.Chunk(chunk))
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val message = if (response != null) {
                        val text = runCatching { response.body.string() }.getOrDefault("")
                        runCatching { errorMessage(response.code, text) }.getOrDefault("HTTP ${response.code}")
                    } else {
                        t?.message ?: "Connection failed"
                    }
                    trySend(ChatStreamEvent.Failed(message))
                    close()
                }
            }

            val source = EventSources.createFactory(streamClient).newEventSource(httpRequest, listener)
            awaitClose { source.cancel() }
        }

    fun streamCompletion(
        server: ServerConfig,
        request: CompletionRequestDto,
    ): Flow<CompletionStreamEvent> = callbackFlow {
        val httpRequest = Request.Builder()
            .url(endpoint(server, "/v1/completions"))
            .header("Accept", "text/event-stream")
            .post(bodyJson(request))
            .auth(server.apiKey)
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    close()
                    return
                }
                val chunk = runCatching { json.decodeFromString<CompletionChunkDto>(data) }.getOrNull()
                if (chunk != null) {
                    chunk.choices.firstOrNull()?.let { choice ->
                        trySend(CompletionStreamEvent.Delta(choice.text.orEmpty(), choice.finishReason))
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val message = if (response != null) {
                    val text = runCatching { response.body.string() }.getOrDefault("")
                    runCatching { errorMessage(response.code, text) }.getOrDefault("HTTP ${response.code}")
                } else {
                    t?.message ?: "Connection failed"
                }
                trySend(CompletionStreamEvent.Failed(message))
                close()
            }
        }

        val source = EventSources.createFactory(streamClient).newEventSource(httpRequest, listener)
        awaitClose { source.cancel() }
    }

    /** Non-streaming chat completion (used e.g. for generating conversation titles). */
    suspend fun chatCompletion(
        server: ServerConfig,
        request: ChatCompletionRequestDto,
    ): ChatCompletionChunkDto {
        val httpRequest = Request.Builder()
            .url(endpoint(server, "/v1/chat/completions"))
            .post(bodyJson(request))
            .auth(server.apiKey)
            .build()
        return executeJson(httpRequest)
    }

    suspend fun abort(server: ServerConfig, model: String, completionId: String) {        val request = Request.Builder()
            .url(endpoint(server, "/v1/chat/completions/control"))
            .post(bodyJson(ControlRequestDto(model = model, id = completionId)))
            .auth(server.apiKey)
            .build()
        runCatching { executeText(request) }
    }

    suspend fun loadModel(server: ServerConfig, model: String): String {
        val request = Request.Builder()
            .url(endpoint(server, "/models/load"))
            .post(bodyJson(ModelNameRequestDto(model)))
            .auth(server.apiKey)
            .build()
        return executeText(request)
    }

    suspend fun unloadModel(server: ServerConfig, model: String): String {
        val request = Request.Builder()
            .url(endpoint(server, "/models/unload"))
            .post(bodyJson(ModelNameRequestDto(model)))
            .auth(server.apiKey)
            .build()
        return executeText(request)
    }

    suspend fun tokenize(server: ServerConfig, model: String, content: String): List<Int> {
        val request = Request.Builder()
            .url(endpoint(server, "/tokenize"))
            .post(bodyJson(TokenizeRequestDto(model = model, content = content)))
            .auth(server.apiKey)
            .build()
        return executeJson<TokenizeResponseDto>(request).tokens
    }

    suspend fun detokenize(server: ServerConfig, model: String, tokens: List<Int>): String {
        val request = Request.Builder()
            .url(endpoint(server, "/detokenize"))
            .post(bodyJson(DetokenizeRequestDto(model = model, tokens = tokens)))
            .auth(server.apiKey)
            .build()
        return executeJson<DetokenizeResponseDto>(request).content
    }

    suspend fun slots(server: ServerConfig, model: String): String {
        val url = endpoint(server, "/slots").newBuilder().addQueryParameter("model", model).build()
        val request = Request.Builder().url(url).get().auth(server.apiKey).build()
        return executeText(request)
    }

    suspend fun rawJson(server: ServerConfig, path: String, model: String? = null): JsonElement {
        val builder = endpoint(server, path).newBuilder()
        if (!model.isNullOrBlank()) builder.addQueryParameter("model", model)
        val request = Request.Builder().url(builder.build()).get().auth(server.apiKey).build()
        return json.parseToJsonElement(executeText(request))
    }

    /** GET a path and return the body as-is (e.g. `/metrics` is Prometheus text). */
    suspend fun rawText(server: ServerConfig, path: String, model: String? = null): String {
        val builder = endpoint(server, path).newBuilder()
        if (!model.isNullOrBlank()) builder.addQueryParameter("model", model)
        val request = Request.Builder().url(builder.build()).get().auth(server.apiKey).build()
        return executeText(request)
    }
}

// ---- DTO -> domain mappers -------------------------------------------------

private fun PropsDto.toDomain(): ServerProps {
    val numeric = defaultGenerationSettings?.params
        ?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.doubleOrNull?.let { key to it } }
        ?.toMap()
        .orEmpty()
    return ServerProps(
        role = role,
        modelAlias = modelAlias,
        modelPath = modelPath,
        buildInfo = buildInfo,
        maxInstances = maxInstances,
        modelsAutoload = modelsAutoload,
        corsProxyEnabled = corsProxyEnabled,
        chatTemplate = chatTemplate,
        samplingDefaults = numeric,
    )
}
