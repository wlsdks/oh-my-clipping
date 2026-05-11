package com.clipping.mcpserver.service.collection

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.InvalidStateException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.service.dto.clipping.AddUrlResult
import com.clipping.mcpserver.model.Language
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.service.port.CollectionArticleExtractorPort
import com.clipping.mcpserver.service.port.CollectionMetricsPort
import com.clipping.mcpserver.service.port.CollectionRuntimeSettingsPort
import com.clipping.mcpserver.service.port.CollectionStatsPort
import com.clipping.mcpserver.service.port.CollectionUrlSafetyPort
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.RssSourceStore
import com.clipping.mcpserver.support.UrlCanonicalizer
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * 관리자가 개별 기사 URL을 수동으로 수집하는 유스케이스를 담당한다.
 */
@Service
class ManualUrlCollectionService(
    private val categoryStore: CategoryStore,
    private val sourceStore: RssSourceStore,
    private val itemStore: RssItemStore,
    private val originalContentArchiver: OriginalContentArchiver,
    private val statsService: CollectionStatsPort,
    private val metrics: CollectionMetricsPort,
    private val runtimeSettingsPort: CollectionRuntimeSettingsPort,
    private val urlSafetyValidator: CollectionUrlSafetyPort,
    private val robotsPolicyClient: RobotsPolicyClient,
    private val articleContentExtractor: CollectionArticleExtractorPort,
) {

    fun addUrl(categoryId: String, rawUrl: String): AddUrlResult {
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")
        if (!category.status.isOperational) {
            throw InvalidInputException("Category is inactive: $categoryId")
        }

        val safeUri = urlSafetyValidator.validatePublicHttpUrl(rawUrl)
        val canonicalUri = UrlCanonicalizer.canonicalize(safeUri)
        enforceManualUrlPolicy(category.id, canonicalUri)
        val safeUrl = canonicalUri.toString()
        if (itemStore.findByLink(safeUrl, categoryId) != null) {
            return AddUrlResult(
                added = false,
                duplicate = true,
                itemId = null,
                categoryId = categoryId,
                sourceLink = safeUrl
            )
        }

        val extractionStartedAt = Instant.now()
        val extracted = try {
            articleContentExtractor.extract(safeUrl)
        } catch (e: Exception) {
            metrics.recordExtraction(
                "clip_add_url", false,
                Duration.between(extractionStartedAt, Instant.now()).toMillis()
            )
            throw InvalidStateException("Failed to extract article from URL")
        }
        metrics.recordExtraction(
            context = "clip_add_url",
            success = extracted != null,
            durationMs = Duration.between(extractionStartedAt, Instant.now()).toMillis()
        )
        if (extracted == null) {
            throw InvalidStateException("Failed to extract article from URL")
        }

        val item = try {
            itemStore.save(
                RssItem(
                    id = "",
                    title = extracted.title.ifBlank { safeUrl },
                    content = extracted.content.take(runtimeSettingsPort.currentCollectionSettings().maxContentLength * 2),
                    link = safeUrl,
                    language = enumValueOrDefault(extracted.language, Language.FOREIGN),
                    categoryId = categoryId,
                    rssSourceId = null
                )
            )
        } catch (_: DuplicateKeyException) {
            return AddUrlResult(
                added = false,
                duplicate = true,
                itemId = null,
                categoryId = categoryId,
                sourceLink = safeUrl
            )
        }
        originalContentArchiver.archive(item)
        statsService.recordCollection(categoryId, 1)

        return AddUrlResult(
            added = true,
            duplicate = false,
            itemId = item.id,
            categoryId = categoryId,
            sourceLink = item.link
        )
    }

    private fun enforceManualUrlPolicy(categoryId: String, safeUri: URI) {
        val targetHost = safeUri.host?.trim()?.lowercase(Locale.ROOT)
            ?: throw InvalidInputException("URL host is required")
        val allowedSources = sourceStore.listApproved(categoryId)
            .asSequence()
            .filter { it.verificationStatus.equals(VERIFIED_STATUS, ignoreCase = true) }
            .toList()

        ensureValid(allowedSources.isNotEmpty()) {
            "No approved and verified source domains configured for category"
        }
        val inAllowlist = allowedSources.any { source ->
            val sourceHost = parseHost(source.url) ?: return@any false
            hostMatches(targetHost, sourceHost)
        }
        ensureValid(inAllowlist) {
            "URL host is not in category allowlist: $targetHost"
        }
        ensureValid(robotsPolicyClient.isAllowed(safeUri)) {
            "URL is blocked by robots.txt policy"
        }
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

    private fun enumValueOrDefault(value: String, default: Language): Language =
        runCatching { enumValueOf<Language>(value) }.getOrDefault(default)

    companion object {
        private const val VERIFIED_STATUS = "VERIFIED"
    }
}
