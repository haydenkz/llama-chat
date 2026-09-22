package com.llamacpp.mobile.data.tools

import com.llamacpp.mobile.data.remote.dto.ToolDto

/** A client-side tool the model can call. */
interface ChatTool {
    /** Function name advertised to the model (must be a valid identifier). */
    val name: String
    val displayName: String
    val description: String

    /** OpenAI-style definition sent in the request. */
    fun definition(): ToolDto

    /** Executes the tool from the model's raw JSON arguments. */
    suspend fun execute(arguments: String): String
}
