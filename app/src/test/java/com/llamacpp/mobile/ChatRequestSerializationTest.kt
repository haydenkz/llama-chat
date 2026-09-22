package com.llamacpp.mobile

import com.llamacpp.mobile.data.remote.dto.ChatCompletionRequestDto
import com.llamacpp.mobile.data.remote.dto.ChatMessageDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the request serialization contract with llama-server:
 * `encodeDefaults = true` is required so that `stream = true` (which equals the
 * DTO default) is not silently dropped and the server does not downgrade the
 * request to a non-streaming response.
 */
class ChatRequestSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun streamIsAlwaysSerialized() {
        val request = ChatCompletionRequestDto(
            model = "test-model",
            messages = listOf(ChatMessageDto("user", JsonPrimitive("hi"))),
            stream = true,
        )
        val encoded = json.encodeToString(ChatCompletionRequestDto.serializer(), request)
        assertTrue("stream must be present: $encoded", encoded.contains("\"stream\":true"))
    }

    @Test
    fun nullOptionalFieldsAreOmitted() {
        val request = ChatCompletionRequestDto(
            messages = listOf(ChatMessageDto("user", JsonPrimitive("hi"))),
        )
        val encoded = json.encodeToString(ChatCompletionRequestDto.serializer(), request)
        assertFalse("model must be omitted when null: $encoded", encoded.contains("\"model\""))
    }

    @Test
    fun samplerParametersAreSerialized() {
        val request = ChatCompletionRequestDto(
            model = "m",
            messages = listOf(ChatMessageDto("user", JsonPrimitive("hi"))),
            temperature = 0.2f,
            topK = 40,
            maxTokens = 128,
            reasoning = true,
        )
        val encoded = json.encodeToString(ChatCompletionRequestDto.serializer(), request)
        assertTrue(encoded.contains("\"temperature\":0.2"))
        assertTrue(encoded.contains("\"top_k\":40"))
        assertTrue(encoded.contains("\"max_tokens\":128"))
        assertTrue(encoded.contains("\"reasoning\":true"))
    }
}
