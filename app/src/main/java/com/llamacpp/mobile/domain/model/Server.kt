package com.llamacpp.mobile.domain.model

import kotlinx.serialization.Serializable

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
