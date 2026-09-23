package com.llamacpp.mobile

import com.llamacpp.mobile.data.repo.buildRequestMessages
import com.llamacpp.mobile.domain.model.ChatMessage
import com.llamacpp.mobile.domain.model.ChatRole
import com.llamacpp.mobile.domain.model.ToolCall
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Stored history → wire messages. Some chat templates reject an assistant row
 * with empty content, so a failed turn must never reach the request.
 */
class ChatHistoryTest {

    private val user = ChatMessage(role = ChatRole.User, content = "hi")

    @Test
    fun blankAssistantWithoutToolCallsIsExcluded() {
        val history = listOf(user, ChatMessage(role = ChatRole.Assistant, content = "", error = "boom"))
        val messages = buildRequestMessages(history, system = "")
        assertEquals(listOf("user"), messages.map { it.role })
    }

    @Test
    fun blankAssistantWithToolCallsIsKeptWithToolCalls() {
        val call = ToolCall(id = "call-1", name = "calculate", arguments = """{"expression":"2+2"}""")
        val history = listOf(user, ChatMessage(role = ChatRole.Assistant, content = "", toolCalls = listOf(call)))
        val assistant = buildRequestMessages(history, system = "").last()
        assertEquals("assistant", assistant.role)
        assertNull(assistant.content)
        val dto = assistant.toolCalls!!.single()
        assertEquals("call-1", dto.id)
        assertEquals("calculate", dto.function?.name)
        assertEquals("""{"expression":"2+2"}""", dto.function?.arguments)
    }

    @Test
    fun toolMessageKeepsCallIdAndName() {
        val history = listOf(
            user,
            ChatMessage(role = ChatRole.Tool, content = "4", toolCallId = "call-1", toolName = "calculate"),
        )
        val tool = buildRequestMessages(history, system = "").last()
        assertEquals("tool", tool.role)
        assertEquals("call-1", tool.toolCallId)
        assertEquals("calculate", tool.name)
        assertEquals(JsonPrimitive("4"), tool.content)
    }

    @Test
    fun storedSystemMessageDoesNotDuplicateSystemPrompt() {
        val history = listOf(ChatMessage(role = ChatRole.System, content = "old prompt"), user)
        val messages = buildRequestMessages(history, system = "Be brief.")
        assertEquals(listOf("system", "user"), messages.map { it.role })
        assertEquals(JsonPrimitive("Be brief."), messages.first().content)
    }

    @Test
    fun userImagesBecomeContentParts() {
        val history = listOf(
            ChatMessage(role = ChatRole.User, content = "what is this?", images = listOf("data:image/png;base64,AAAA")),
        )
        val parts = buildRequestMessages(history, system = "").single().content!!.jsonArray
        assertEquals(2, parts.size)
        assertEquals("text", parts[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("what is this?", parts[0].jsonObject["text"]?.jsonPrimitive?.content)
        assertEquals("image_url", parts[1].jsonObject["type"]?.jsonPrimitive?.content)
        val url = parts[1].jsonObject["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content
        assertNotNull(url)
        assertEquals("data:image/png;base64,AAAA", url)
    }
}
