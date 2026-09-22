package com.llamacpp.mobile

import com.llamacpp.mobile.data.remote.dto.PropsDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Server-level `GET /props` (router mode) returns `default_generation_settings`
 * with `"params": null`. Without `coerceInputValues`, kotlinx-serialization
 * throws for a null against a non-nullable field that has a default, which made
 * the Server info screen fail entirely.
 */
class PropsDecodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun serverLevelPropsWithNullParamsDecodes() {
        val payload = """
            {"role":"router","max_instances":1,"models_autoload":true,
             "model_alias":"llama-server","model_path":"none",
             "default_generation_settings":{"params":null,"n_ctx":0},
             "ui_settings":{},"build_info":"b10964-b29c606e28","cors_proxy_enabled":false}
        """.trimIndent()

        val dto = json.decodeFromString(PropsDto.serializer(), payload)

        assertEquals("router", dto.role)
        assertEquals("b10964-b29c606e28", dto.buildInfo)
        assertTrue(dto.defaultGenerationSettings?.params?.isEmpty() == true)
    }
}
