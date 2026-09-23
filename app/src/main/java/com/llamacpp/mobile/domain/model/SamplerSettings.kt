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
    /** Optional raw JSON string used as `response_format.schema`. */
    val jsonSchema: String = "",
)

/**
 * Builds settings from the server's `default_generation_settings.params`
 * (`GET /props?model=…`), falling back to the built-in defaults for any param
 * the server doesn't report.
 */
fun samplerSettingsFromParams(params: Map<String, Double>): SamplerSettings {
    fun f(key: String): Float? = params[key]?.toFloat()
    fun i(key: String): Int? = params[key]
        ?.takeIf { it >= Int.MIN_VALUE.toDouble() && it <= Int.MAX_VALUE.toDouble() }
        ?.toInt()

    return SamplerSettings(
        temperature = f("temperature") ?: 1.0f,
        dynatempRange = f("dynatemp_range") ?: 0.0f,
        dynatempExponent = f("dynatemp_exponent") ?: 1.0f,
        topK = i("top_k") ?: 40,
        topP = f("top_p") ?: 0.95f,
        minP = f("min_p") ?: 0.05f,
        xtcProbability = f("xtc_probability") ?: 0.0f,
        xtcThreshold = f("xtc_threshold") ?: 0.1f,
        typicalP = f("typical_p") ?: 1.0f,
        repeatLastN = i("repeat_last_n") ?: 64,
        repeatPenalty = f("repeat_penalty") ?: 1.0f,
        presencePenalty = f("presence_penalty") ?: 0.0f,
        frequencyPenalty = f("frequency_penalty") ?: 0.0f,
        dryMultiplier = f("dry_multiplier") ?: 0.0f,
        dryBase = f("dry_base") ?: 1.75f,
        dryAllowedLength = i("dry_allowed_length") ?: 2,
        mirostat = i("mirostat") ?: 0,
        mirostatTau = f("mirostat_tau") ?: 5.0f,
        mirostatEta = f("mirostat_eta") ?: 0.1f,
        seed = seedOrRandom(params["seed"]),
    )
}

/** llama.cpp reports "random" as 4294967295 (= -1); normalize it. */
private fun seedOrRandom(value: Double?): Int =
    if (value == null || value >= 4_294_967_295.0 || value > Int.MAX_VALUE) -1 else value.toInt()
