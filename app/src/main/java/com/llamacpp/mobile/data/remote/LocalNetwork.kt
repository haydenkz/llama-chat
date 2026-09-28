package com.llamacpp.mobile.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * Android 17 (API 37) blocks LAN traffic for apps targeting it unless the user
 * grants ACCESS_LOCAL_NETWORK ("Nearby devices"). Blocked TCP connects don't
 * fail fast, they just time out, which looks like an unreachable server.
 */
object LocalNetwork {

    /** Whether this OS enforces the local network permission. */
    val enforced: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

    val permission: String
        get() = Manifest.permission.ACCESS_LOCAL_NETWORK

    /** True when LAN access is allowed (always true before Android 17). */
    fun hasAccess(context: Context): Boolean =
        !enforced || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** True when [baseUrl] points at the local network and access isn't granted yet. */
    fun needsPermission(context: Context, baseUrl: String): Boolean {
        val host = baseUrl.trim().toHttpUrlOrNull()?.host ?: return false
        return isLocalHost(host) && !hasAccess(context)
    }

    /**
     * Best-effort check for hosts on the local network: private and link-local
     * IP literals, plus hostnames that normally only resolve on a LAN
     * (`nas`, `pc.local`, `box.lan`, `x.home.arpa`, ...). Loopback isn't LAN
     * traffic and doesn't need the permission.
     */
    fun isLocalHost(host: String): Boolean {
        val h = host.trim().trimStart('[').trimEnd(']').trimEnd('.').lowercase()
        if (h.isEmpty() || h == "localhost") return false
        if (':' in h) return isLocalIpv6(h)
        ipv4(h)?.let { return isLocalIpv4(it) }
        if ('.' !in h) return true
        return LAN_SUFFIXES.any { h.endsWith(it) }
    }

    private val LAN_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain")

    private fun ipv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
        return octets.toIntArray()
    }

    private fun isLocalIpv4(o: IntArray): Boolean = when {
        o[0] == 10 -> true
        o[0] == 172 && o[1] in 16..31 -> true
        o[0] == 192 && o[1] == 168 -> true
        o[0] == 169 && o[1] == 254 -> true
        else -> false
    }

    private fun isLocalIpv6(host: String): Boolean {
        val first = host.substringBefore(':').toIntOrNull(16) ?: return false
        return first and 0xfe00 == 0xfc00 || // fc00::/7 unique local
            first and 0xffc0 == 0xfe80 // fe80::/10 link-local
    }
}

/**
 * Replaces the generic connect timeout with an actionable message when the
 * missing local network permission is the likely cause.
 */
class LocalNetworkErrorInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            val connectFailure = e is SocketTimeoutException || e is ConnectException
            if (connectFailure &&
                LocalNetwork.isLocalHost(request.url.host) &&
                !LocalNetwork.hasAccess(context)
            ) {
                throw IOException(
                    "Can't reach ${request.url.host}: LlamaChat needs the \"Nearby devices\" " +
                        "permission to connect to servers on your local network. Enable it in " +
                        "Settings › Apps › LlamaChat › Permissions.",
                    e,
                )
            }
            throw e
        }
    }
}
