package com.llamacpp.mobile.data.tools

import com.llamacpp.mobile.data.remote.dto.ToolDto
import net.objecthunter.exp4j.ExpressionBuilder

/** Deterministic arithmetic via exp4j. */
class CalculatorTool : ChatTool {
    override val name = NAME
    override val displayName = "Calculator"
    override val description =
        "Evaluate a mathematical expression exactly. Use this for arithmetic instead of guessing."

    override fun definition(): ToolDto =
        functionTool(NAME, description, mapOf("expression" to "The expression, e.g. (2 + 3) * 4^2."))

    override suspend fun execute(arguments: String): String {
        val expression = toolStringArg(arguments, "expression", "input", "query")
            ?: return "No expression provided."
        return runCatching {
            val value = ExpressionBuilder(expression).build().evaluate()
            val formatted = if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                value.toString()
            }
            "$expression = $formatted"
        }.getOrElse {
            "Could not evaluate \"$expression\": ${it.message}"
        }
    }

    companion object {
        const val NAME = "calculate"
    }
}
