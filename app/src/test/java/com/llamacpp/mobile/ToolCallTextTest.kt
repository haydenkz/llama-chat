package com.llamacpp.mobile

import com.llamacpp.mobile.data.tools.ToolCallText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textual tool calls must become real calls, and their markup must never be shown as an answer. */
class ToolCallTextTest {

    @Test
    fun extractsHermesStyleBlockWithObjectArguments() {
        val text = """<tool_call> {"name": "calculate", "arguments": {"expression": "17*23+5"}} </tool_call>"""
        val result = ToolCallText.extract(text)
        assertEquals("", result.content)
        val call = result.toolCalls.single()
        assertEquals("calculate", call.name)
        assertEquals("""{"expression":"17*23+5"}""", call.arguments)
    }

    @Test
    fun keepsSurroundingProseAndParsesArrayOfCalls() {
        val text = "Let me check.\n<|tool_call|>[{\"name\":\"get_time\",\"arguments\":{}},{\"name\":\"weather\",\"arguments\":\"{\\\"city\\\":\\\"Toronto\\\"}\"}]<|/tool_call|>"
        val result = ToolCallText.extract(text)
        assertEquals("Let me check.", result.content)
        assertEquals(listOf("get_time", "weather"), result.toolCalls.map { it.name })
        assertEquals("""{"city":"Toronto"}""", result.toolCalls[1].arguments)
    }

    @Test
    fun stripHidesUnterminatedBlockWhileStreaming() {
        assertEquals("Sure.", ToolCallText.strip("Sure.\n<tool_call> {\"name\": \"calc"))
        assertEquals("Sure.", ToolCallText.strip("Sure.\n<tool_call> {\"name\": \"calc\"} </tool_call>"))
    }

    @Test
    fun plainTextIsUntouched() {
        val text = "The answer is **4**."
        assertEquals(text, ToolCallText.strip(text))
        assertTrue(ToolCallText.extract(text).toolCalls.isEmpty())
    }
}
