package com.llamacpp.mobile.data.tools

import com.llamacpp.mobile.data.remote.dto.ToolDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Runs Python code in a self-hosted [Piston](https://github.com/engineer-man/piston)
 * sandbox (server-side, container-isolated, with timeouts).
 */
class PythonTool(
    private val client: OkHttpClient,
    private val json: Json,
    private val pistonUrlProvider: suspend () -> String,
) : ChatTool {
    override val name = NAME
    override val displayName = "Python"
    override val description =
        "Run Python code in a sandbox and return its output. Use this for calculations, data " +
            "processing, string manipulation, or anything better done in code than in your head."

    override fun definition(): ToolDto =
        functionTool(NAME, description, mapOf("code" to "The Python code to run."))

    override suspend fun execute(arguments: String): String {
        val code = toolStringArg(arguments, "code", "input", "source")
            ?: return "No code provided."
        val base = pistonUrlProvider().trim()
        if (base.isBlank()) {
            return "Python isn't configured. Set a Piston URL in Settings → Tools."
        }
        return runViaPiston(base, code)
    }

    private suspend fun runViaPiston(baseUrl: String, code: String): String = withContext(Dispatchers.IO) {
        try {
            val endpoint = baseUrl.trimEnd('/') + "/api/v2/execute"
            val payload = buildJsonObject {
                put("language", "python")
                put("version", "*")
                put("run_timeout", RUN_TIMEOUT_MS)
                put("compile_timeout", COMPILE_TIMEOUT_MS)
                putJsonArray("files") {
                    add(
                        buildJsonObject {
                            put("name", "main.py")
                            put("content", code)
                        },
                    )
                }
            }.toString()

            val request = Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val bodyText = client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    return@withContext "Python sandbox error: HTTP ${response.code} — ${text.take(300)}"
                }
                text
            }

            val run = runCatching { json.parseToJsonElement(bodyText).jsonObject["run"]?.jsonObject }
                .getOrNull() ?: return@withContext "Python sandbox returned an unexpected response."
            val stdout = run["stdout"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val stderr = run["stderr"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val exitCode = run["code"]?.jsonPrimitive?.intOrNull

            buildString {
                if (exitCode == null || exitCode == 0) {
                    if (stdout.isNotBlank()) {
                        append(stdout.trimEnd())
                    } else if (stderr.isNotBlank()) {
                        append(stderr.trimEnd())
                    } else {
                        append("(ran with no output)")
                    }
                } else {
                    // Non-zero exit: show the traceback/error the sandbox returned.
                    if (stderr.isNotBlank()) append(stderr.trimEnd())
                    if (stdout.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(stdout.trimEnd())
                    }
                    if (isNotEmpty()) append('\n')
                    append("exit code: ").append(exitCode)
                }
            }.trim()
        } catch (t: Throwable) {
            "Python sandbox failed: ${t.message}"
        }
    }

    companion object {
        const val NAME = "run_python"
        // Piston caps these at 3000 ms by default (PISTON_RUN_TIMEOUT).
        private const val RUN_TIMEOUT_MS = 3_000
        private const val COMPILE_TIMEOUT_MS = 3_000
    }
}
