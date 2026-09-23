package com.llamacpp.mobile

import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.domain.model.baseUrlError
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlValidationTest {

    @Test
    fun acceptsLanHttpUrl() {
        assertNull(baseUrlError("http://10.0.0.5:8080"))
    }

    @Test
    fun acceptsHttpsUrlWithoutPort() {
        assertNull(baseUrlError("https://llama.example.com"))
    }

    @Test
    fun rejectsOutOfRangePort() {
        assertNotNull(baseUrlError("http://my.phone.ip:500034"))
    }

    @Test
    fun rejectsMissingScheme() {
        assertNotNull(baseUrlError("my.phone.ip:8080"))
    }

    @Test
    fun rejectsBlank() {
        assertNotNull(baseUrlError("   "))
    }

    @Test
    fun rejectsSchemeOnly() {
        assertNotNull(baseUrlError("http://"))
    }

    @Test
    fun defaultServerHasNoHardcodedAddress() {
        val server = ServerRepository.defaultServer()
        assertTrue(server.baseUrl.isBlank())
        assertTrue(!server.toString().contains("192.168."))
    }
}
