package com.llamacpp.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---- /v1/models -----------------------------------------------------------

@Serializable
data class ModelListDto(val data: List<ModelDto> = emptyList())

@Serializable
data class ModelDto(
    val id: String,
    val aliases: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("object") val objectType: String? = null,
    @SerialName("owned_by") val ownedBy: String? = null,
    val created: Long? = null,
    val status: ModelStatusDto? = null,
    val architecture: ArchitectureDto? = null,
    val source: String? = null,
    @SerialName("can_remove") val canRemove: Boolean = false,
    val meta: ModelMetaDto? = null,
)

@Serializable
data class ModelStatusDto(
    val value: String? = null,
    val args: List<String> = emptyList(),
    val preset: String? = null,
)

@Serializable
data class ArchitectureDto(
    @SerialName("input_modalities") val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities") val outputModalities: List<String> = emptyList(),
)

@Serializable
data class ModelMetaDto(
    @SerialName("n_vocab") val nVocab: Long? = null,
    @SerialName("n_ctx") val nCtx: Long? = null,
    @SerialName("n_ctx_train") val nCtxTrain: Long? = null,
    @SerialName("n_embd") val nEmbd: Long? = null,
    @SerialName("n_params") val nParams: Long? = null,
    val size: Long? = null,
    val ftype: String? = null,
)

// ---- /props ---------------------------------------------------------------

@Serializable
data class PropsDto(
    val role: String? = null,
    @SerialName("model_alias") val modelAlias: String? = null,
    @SerialName("model_path") val modelPath: String? = null,
    @SerialName("build_info") val buildInfo: String? = null,
    @SerialName("max_instances") val maxInstances: Int? = null,
    @SerialName("models_autoload") val modelsAutoload: Boolean? = null,
    @SerialName("cors_proxy_enabled") val corsProxyEnabled: Boolean? = null,
    @SerialName("chat_template") val chatTemplate: String? = null,
    @SerialName("default_generation_settings") val defaultGenerationSettings: DefaultGenerationDto? = null,
)

@Serializable
data class DefaultGenerationDto(
    val params: Map<String, JsonElement> = emptyMap(),
    @SerialName("n_ctx") val nCtx: Int? = null,
)

@Serializable
data class HealthDto(val status: String? = null)

// ---- /v1/chat/completions -------------------------------------------------

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
data class ChatCompletionRequestDto(
    val model: String? = null,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("dynatemp_range") val dynatempRange: Float? = null,
    @SerialName("dynatemp_exponent") val dynatempExponent: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("min_p") val minP: Float? = null,
    @SerialName("xtc_probability") val xtcProbability: Float? = null,
    @SerialName("xtc_threshold") val xtcThreshold: Float? = null,
    @SerialName("typical_p") val typicalP: Float? = null,
    @SerialName("repeat_last_n") val repeatLastN: Int? = null,
    @SerialName("repeat_penalty") val repeatPenalty: Float? = null,
    @SerialName("presence_penalty") val presencePenalty: Float? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Float? = null,
    @SerialName("dry_multiplier") val dryMultiplier: Float? = null,
    @SerialName("dry_base") val dryBase: Float? = null,
    @SerialName("dry_allowed_length") val dryAllowedLength: Int? = null,
    val mirostat: Int? = null,
    @SerialName("mirostat_tau") val mirostatTau: Float? = null,
    @SerialName("mirostat_eta") val mirostatEta: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val seed: Int? = null,
    val stop: List<String>? = null,
    val reasoning: Boolean? = null,
    @SerialName("cache_prompt") val cachePrompt: Boolean? = null,
    @SerialName("n_keep") val nKeep: Int? = null,
    val tools: List<ToolDto>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
)

@Serializable
data class ChatCompletionChunkDto(
    val id: String? = null,
    val model: String? = null,
    val created: Long? = null,
    @SerialName("system_fingerprint") val systemFingerprint: String? = null,
    val choices: List<ChunkChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
    val timings: TimingsDto? = null,
)

@Serializable
data class ChunkChoiceDto(
    val index: Int = 0,
    val delta: DeltaDto? = null,
    val message: MessageDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class DeltaDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
)

// ---- tool calling ---------------------------------------------------------

@Serializable
data class ToolCallDto(
    val index: Int? = null,
    val id: String? = null,
    val type: String? = null,
    val function: ToolFunctionCallDto? = null,
)

@Serializable
data class ToolFunctionCallDto(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class ToolDto(
    val type: String = "function",
    val function: ToolDefinitionDto,
)

@Serializable
data class ToolDefinitionDto(
    val name: String,
    val description: String,
    val parameters: JsonElement,
)

@Serializable
data class MessageDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
)

@Serializable
data class TimingsDto(
    @SerialName("cache_n") val cacheN: Int? = null,
    @SerialName("prompt_n") val promptN: Int? = null,
    @SerialName("prompt_ms") val promptMs: Double? = null,
    @SerialName("prompt_per_second") val promptPerSecond: Double? = null,
    @SerialName("predicted_n") val predictedN: Int? = null,
    @SerialName("predicted_ms") val predictedMs: Double? = null,
    @SerialName("predicted_per_second") val predictedPerSecond: Double? = null,
)

// ---- /v1/completions (raw text completion) --------------------------------

@Serializable
data class CompletionRequestDto(
    val model: String? = null,
    val prompt: String,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("min_p") val minP: Float? = null,
    @SerialName("repeat_penalty") val repeatPenalty: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("n_predict") val nPredict: Int? = null,
    val seed: Int? = null,
    val stop: List<String>? = null,
    @SerialName("cache_prompt") val cachePrompt: Boolean? = null,
)

@Serializable
data class CompletionChunkDto(
    val id: String? = null,
    val choices: List<CompletionChoiceDto> = emptyList(),
    val timings: TimingsDto? = null,
)

@Serializable
data class CompletionChoiceDto(
    val text: String? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

// ---- control / models / tokenizer / slots ---------------------------------
@Serializable
data class ControlRequestDto(val model: String, val id: String)

@Serializable
data class ModelNameRequestDto(val model: String)

@Serializable
data class TokenizeRequestDto(val model: String, val content: String)

@Serializable
data class TokenizeResponseDto(val tokens: List<Int> = emptyList())

@Serializable
data class DetokenizeRequestDto(val model: String, val tokens: List<Int>)

@Serializable
data class DetokenizeResponseDto(val content: String = "")

@Serializable
data class SlotDto(
    val id: Int,
    @SerialName("n_ctx") val nCtx: Int? = null,
    val speculative: Boolean = false,
    @SerialName("is_processing") val isProcessing: Boolean = false,
)

// ---- errors ---------------------------------------------------------------

@Serializable
data class ErrorEnvelope(val error: ErrorDto? = null)

@Serializable
data class ErrorDto(val code: Int? = null, val message: String? = null, val type: String? = null)
