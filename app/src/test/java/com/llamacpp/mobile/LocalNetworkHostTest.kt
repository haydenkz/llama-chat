package com.llamacpp.mobile

import com.llamacpp.mobile.data.remote.LocalNetwork
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkHostTest {

    @Test
    fun privateIpv4IsLocal() {
        listOf("192.168.1.199", "10.0.0.5", "172.16.0.1", "172.31.255.254", "169.254.10.2")
            .forEach { assertTrue(it, LocalNetwork.isLocalHost(it)) }
    }

    @Test
    fun publicAndLoopbackIpv4AreNotLocal() {
        listOf("8.8.8.8", "172.32.0.1", "172.15.0.1", "127.0.0.1", "localhost")
            .forEach { assertFalse(it, LocalNetwork.isLocalHost(it)) }
    }

    @Test
    fun lanHostnamesAreLocal() {
        listOf("nas", "gpu-box.local", "server.lan", "llama.home.arpa")
            .forEach { assertTrue(it, LocalNetwork.isLocalHost(it)) }
    }

    @Test
    fun publicHostnamesAreNotLocal() {
        assertFalse(LocalNetwork.isLocalHost("llama.example.com"))
    }

    @Test
    fun ipv6UniqueAndLinkLocalAreLocal() {
        assertTrue(LocalNetwork.isLocalHost("fd12:3456::1"))
        assertTrue(LocalNetwork.isLocalHost("[fe80::1]"))
        assertFalse(LocalNetwork.isLocalHost("2001:db8::1"))
        assertFalse(LocalNetwork.isLocalHost("::1"))
    }
}
