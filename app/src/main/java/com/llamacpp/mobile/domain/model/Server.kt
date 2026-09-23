package com.llamacpp.mobile.domain.model

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

/** A llama.cpp `llama-server` endpoint the app can talk to. */
@Serializable
data class ServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String? = null,
    /** Model ids hidden from the model picker. */
    val hiddenModels: List<String> = emptyList(),
) {
    /** Base URL without a trailing slash. */
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')
}

/**
 * Validates a user-entered base URL. Returns `null` when the URL is usable,
 * otherwise a short, user-facing message. Uses the same parser as the HTTP
 * client, so anything accepted here builds a valid request URL.
 */
fun baseUrlError(raw: String): String? {
    val value = raw.trim()
    if (value.isBlank()) return "Enter the server URL."
    if (!value.startsWith("http://", ignoreCase = true) &&
        !value.startsWith("https://", ignoreCase = true)
    ) {
        return "URL must start with http:// or https://"
    }
    return runCatching { value.toHttpUrl() }.fold(
        onSuccess = { url -> if (url.host.isBlank()) "URL is missing a host." else null },
        onFailure = { error ->
            if (error.message.orEmpty().contains("port", ignoreCase = true)) {
                "Port must be between 1 and 65535."
            } else {
                "That doesn't look like a valid URL."
            }
        },
    )
}

data class ServerHealth(
    val ok: Boolean,
    val status: String,
    val message: String? = null,
)

/**
 * Response of `GET /props` (server- or model-scoped). In router mode the
 * model-scoped variant also carries `default_generation_settings.params`,
 * which seeds the sampler UI.
 */
data class ServerProps(
    val role: String? = null,
    val modelAlias: String? = null,
    val modelPath: String? = null,
    val buildInfo: String? = null,
    val maxInstances: Int? = null,
    val modelsAutoload: Boolean? = null,
    val corsProxyEnabled: Boolean? = null,
    val chatTemplate: String? = null,
    val samplingDefaults: Map<String, Double> = emptyMap(),
)
