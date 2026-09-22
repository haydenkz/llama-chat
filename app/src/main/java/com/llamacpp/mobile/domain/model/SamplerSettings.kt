package com.llamacpp.mobile.domain.model

import kotlinx.serialization.Serializable

/**
 * Generation parameters mirroring the llama.cpp sampler set. Defaults match
 * llama-server's built-in sampling defaults; they are replaced at runtime by
 * the model's `default_generation_settings.params` when available.
 */
@Serializable
data class SamplerSettings(
    val systemPrompt: String = "",
    val temperature: Float = 1.0f,
    val dynatempRange: Float = 0.0f,
    val dynatempExponent: Float = 1.0f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val minP: Float = 0.05f,
    val xtcProbability: Float = 0.0f,
    val xtcThreshold: Float = 0.1f,
    val typicalP: Float = 1.0f,
    val repeatLastN: Int = 64,
    val repeatPenalty: Float = 1.0f,
    val presencePenalty: Float = 0.0f,
    val frequencyPenalty: Float = 0.0f,
    val dryMultiplier: Float = 0.0f,
    val dryBase: Float = 1.75f,
    val dryAllowedLength: Int = 2,
    val mirostat: Int = 0,
    val mirostatTau: Float = 5.0f,
    val mirostatEta: Float = 0.1f,
    val maxTokens: Int = 2048,
    val seed: Int = -1,
    val stop: List<String> = emptyList(),
    val reasoning: Boolean = true,
    val cachePrompt: Boolean = true,
    val stream: Boolean = true,
    val nKeep: Int = 0,
    /** Offer the built-in web_search tool to the model. */
    val webSearch: Boolean = true,
    /** Optional raw JSON string used as `response_format.schema`. */
    val jsonSchema: String = "",
)
