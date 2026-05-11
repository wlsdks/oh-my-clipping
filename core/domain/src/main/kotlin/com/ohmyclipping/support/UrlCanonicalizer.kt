package com.ohmyclipping.support

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Article URL canonicalization for deduplication.
 *
 * Keep content-identifying query params, but remove common tracking params and fragments.
 */
object UrlCanonicalizer {
    private val exactTrackingParams = setOf(
        "fbclid",
        "gclid",
        "gbraid",
        "wbraid",
        "msclkid",
        "igshid",
        "mc_cid",
        "mc_eid",
        "spm",
        "ref",
        "ref_src"
    )

    fun canonicalizeToString(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return trimmed
        return runCatching { canonicalize(URI(trimmed)).toString() }
            .getOrDefault(trimmed)
    }

    fun canonicalize(uri: URI): URI {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return uri.normalize()
        val host = uri.host?.lowercase(Locale.ROOT) ?: return uri.normalize()
        val port = when {
            scheme == "http" && uri.port == 80 -> -1
            scheme == "https" && uri.port == 443 -> -1
            else -> uri.port
        }
        val path = uri.rawPath?.ifBlank { "/" } ?: "/"
        val query = canonicalQuery(uri.rawQuery)
        return buildHierarchicalUri(scheme, host, port, path, query).normalize()
    }

    private fun canonicalQuery(rawQuery: String?): String? {
        if (rawQuery.isNullOrBlank()) return null
        val params = rawQuery
            .split("&")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isTrackingParam(it.substringBefore("=")) }
            .sorted()
            .toList()
        return params.joinToString("&").ifBlank { null }
    }

    private fun isTrackingParam(rawName: String): Boolean {
        val name = decodeQueryName(rawName).lowercase(Locale.ROOT)
        return name.startsWith("utm_") || name in exactTrackingParams
    }

    private fun decodeQueryName(rawName: String): String =
        runCatching {
            URLDecoder.decode(rawName, StandardCharsets.UTF_8)
        }.getOrDefault(rawName)

    private fun buildHierarchicalUri(
        scheme: String,
        host: String,
        port: Int,
        rawPath: String,
        rawQuery: String?
    ): URI {
        val authorityHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
        val authority = if (port >= 0) "$authorityHost:$port" else authorityHost
        val raw = buildString {
            append(scheme)
            append("://")
            append(authority)
            append(rawPath)
            if (!rawQuery.isNullOrBlank()) {
                append('?')
                append(rawQuery)
            }
        }
        return URI(raw)
    }
}
