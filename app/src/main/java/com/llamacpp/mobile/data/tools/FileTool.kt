package com.llamacpp.mobile.data.tools

import android.content.Context
import com.llamacpp.mobile.data.remote.dto.ToolDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lets the model create a file (text, markdown, CSV, JSON, code, SVG, HTML, …).
 * The file is written to app storage and the chat shows a card the user can save.
 */
class FileTool(context: Context) : ChatTool {

    private val dir: File = File(context.applicationContext.filesDir, "generated").apply { mkdirs() }

    override val name = NAME
    override val displayName = "File creation"
    override val description =
        "Create a file for the user to download — reports, CSV, JSON, code, SVG, HTML, etc. " +
            "Provide the full content and a filename with extension."

    override fun definition(): ToolDto = functionTool(
        NAME,
        description,
        mapOf(
            "filename" to "The file name including extension, e.g. report.md.",
            "content" to "The full file content.",
        ),
    )

    override suspend fun execute(arguments: String): String {
        val rawName = toolStringArg(arguments, "filename", "name", "path")
            ?.takeIf { it.isNotBlank() } ?: "file.txt"
        val content = toolStringArg(arguments, "content", "text", "data", "body")
            ?: return "No content provided."
        val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        return withContext(Dispatchers.IO) {
            runCatching {
                File(dir, safeName).writeText(content)
                val bytes = content.toByteArray(Charsets.UTF_8).size
                "Created \"$safeName\" ($bytes bytes). The user can save it from the file card."
            }.getOrElse { "Could not create file: ${it.message}" }
        }
    }

    companion object {
        const val NAME = "create_file"
    }
}
