package com.clipping.mcpserver.service.collection

import com.clipping.mcpserver.model.OriginalContent
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.model.SourceLegalBasis
import com.clipping.mcpserver.store.OriginalContentStore
import com.clipping.mcpserver.store.RssSourceStore
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import java.util.Locale

/**
 * RSS 아이템의 원본 본문 보존 정책을 판단하고 아카이빙한다.
 */
@Component
class OriginalContentArchiver(
    private val sourceStore: RssSourceStore,
    private val originalContentStore: OriginalContentStore
) {

    fun archive(item: RssItem) {
        if (!isFulltextAllowed(item.categoryId, item.link)) {
            return
        }
        val markdown = buildMarkdown(item.title, item.content, item.link)
        val hash = sha256Hex(markdown)
        originalContentStore.save(
            OriginalContent(
                id = "",
                rssItemId = item.id,
                sourceLink = item.link,
                title = item.title,
                markdown = markdown,
                contentHash = hash
            )
        )
    }

    internal fun isFulltextAllowed(categoryId: String, sourceLink: String): Boolean {
        val host = parseHost(sourceLink) ?: return false
        return sourceStore.listApproved(categoryId)
            .asSequence()
            .filter { source ->
                val sourceHost = parseHost(source.url) ?: return@filter false
                hostMatches(host, sourceHost)
            }
            .any { source ->
                source.fulltextAllowed &&
                    source.legalBasis in setOf(SourceLegalBasis.LICENSED, SourceLegalBasis.OPEN_LICENSE)
            }
    }

    private fun buildMarkdown(title: String, content: String?, sourceLink: String): String {
        val normalizedContent = (content ?: "")
            .trim()
            .replace(Regex("\\r\\n?"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .ifBlank { "(no content extracted)" }
        return """
            # $title

            $normalizedContent

            ---
            Source: $sourceLink
        """.trimIndent()
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parseHost(url: String): String? = try {
        URI(url).host?.trim()?.lowercase(Locale.ROOT)
    } catch (_: URISyntaxException) {
        null
    }

    private fun hostMatches(targetHost: String, allowedHost: String): Boolean {
        if (targetHost == allowedHost) return true
        return targetHost.endsWith(".$allowedHost")
    }
}
