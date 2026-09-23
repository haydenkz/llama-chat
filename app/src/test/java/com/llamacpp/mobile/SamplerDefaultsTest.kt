package com.llamacpp.mobile

import com.llamacpp.mobile.domain.model.samplerSettingsFromParams
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sampler defaults shown for a model must come from the server's
 * `default_generation_settings.params`, not a hardcoded set.
 */
class SamplerDefaultsTest {

    @Test
    fun mapsServerReportedParams() {
        val settings = samplerSettingsFromParams(
            mapOf(
                "temperature" to 0.2,
                "top_k" to 40.0,
                "top_p" to 0.9,
                "min_p" to 0.05,
                "repeat_penalty" to 1.1,
                "seed" to 4_294_967_295.0,
            ),
        )
        assertEquals(0.2f, settings.temperature, 1e-4f)
        assertEquals(40, settings.topK)
        assertEquals(0.9f, settings.topP, 1e-4f)
        assertEquals(0.05f, settings.minP, 1e-4f)
        assertEquals(1.1f, settings.repeatPenalty, 1e-4f)
        assertEquals(-1, settings.seed)
    }

    @Test
    fun fallsBackToBuiltInDefaultsForMissingParams() {
        val settings = samplerSettingsFromParams(emptyMap())
        assertEquals(1.0f, settings.temperature, 1e-4f)
        assertEquals(40, settings.topK)
        assertEquals(0.95f, settings.topP, 1e-4f)
    }
}
