package com.llamacpp.mobile.domain.model

/** A model exposed by `GET /v1/models` (router mode). */
data class LlamaModel(
    val id: String,
    val aliases: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val ownedBy: String? = null,
    val created: Long? = null,
    val status: String? = null,
    val statusArgs: List<String> = emptyList(),
    val presetText: String? = null,
    val source: String? = null,
    val canRemove: Boolean = false,
    val meta: ModelMeta? = null,
    val inputModalities: List<String> = emptyList(),
    val outputModalities: List<String> = emptyList(),
) {
    val isLoaded: Boolean get() = status?.equals("loaded", ignoreCase = true) == true
    val isLoading: Boolean get() = status?.equals("loading", ignoreCase = true) == true
    val supportsVision: Boolean
        get() = inputModalities.any { it.equals("image", ignoreCase = true) }
    val displayName: String get() = aliases.firstOrNull() ?: id
}

data class ModelMeta(
    val nVocab: Long? = null,
    val nCtx: Long? = null,
    val nCtxTrain: Long? = null,
    val nEmbd: Long? = null,
    val nParams: Long? = null,
    val sizeBytes: Long? = null,
    val ftype: String? = null,
)
