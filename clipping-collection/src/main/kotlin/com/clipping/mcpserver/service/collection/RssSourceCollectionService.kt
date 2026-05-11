package com.clipping.mcpserver.service.collection

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.service.port.CollectionBackgroundErrorNotifierPort
import com.clipping.mcpserver.service.port.CollectionMetricsPort
import com.clipping.mcpserver.service.port.RssCollectionPort
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.RssSourceStore
import com.clipping.mcpserver.util.TitleSimilarity
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * 한 RSS source의 수집, 중복 필터링, 저장, 관측 기록을 처리한다.
 */
@Service
class RssSourceCollectionService(
    private val sourceStore: RssSourceStore,
    private val itemStore: RssItemStore,
    private val collector: RssCollectionPort,
    private val originalContentArchiver: OriginalContentArchiver,
    private val crawlLogRecorder: SourceCrawlLogRecorder,
    private val metrics: CollectionMetricsPort,
    @param:Lazy
    private val schedulerErrorNotifier: CollectionBackgroundErrorNotifierPort?
) {

    data class SourceCollectionResult(
        val collected: Int,
        val newItems: Int,
        val duplicates: Int
    )

    fun collectSource(
        category: Category,
        source: RssSource,
        hours: Int,
        existingTitles: MutableList<String>,
        seenLinks: MutableSet<String>
    ): SourceCollectionResult {
        val sourceStartedAt = Instant.now()
        var sourceCollected = 0
        var sourceNew = 0
        var sourceDuplicates = 0
        try {
            val items = collector.collect(source.toRssCollectionSource(), hours, enrichShortContent = false)
                .map { it.toRssItem() }
            sourceCollected = items.size

            val existingLinks = if (items.isEmpty()) {
                emptySet()
            } else {
                itemStore.findExistingLinks(items.map { it.link }, category.id)
            }

            for (item in items) {
                if (item.link in existingLinks) {
                    sourceDuplicates++
                    continue
                }
                if (!seenLinks.add(item.link)) {
                    sourceDuplicates++
                    continue
                }
                if (existingTitles.any { TitleSimilarity.isDuplicate(it, item.title) }) {
                    sourceDuplicates++
                    log.debug { "Similar title skipped: ${item.title}" }
                    continue
                }

                try {
                    val enrichedItem = collector.enrichShortContent(item.toRssCollectedItem()).toRssItem()
                    val savedItem = itemStore.save(enrichedItem)
                    runCatching {
                        originalContentArchiver.archive(savedItem)
                    }.onFailure { archiveError ->
                        log.warn(archiveError) {
                            "Failed to archive original content for item '${savedItem.id}' (${savedItem.link})"
                        }
                    }
                    existingTitles.add(savedItem.title)
                    sourceNew++
                } catch (_: DuplicateKeyException) {
                    log.debug { "Duplicate link skipped: ${item.link}" }
                    sourceDuplicates++
                }
            }
            sourceStore.resetFailCount(source.id)
            val durationMs = Duration.between(sourceStartedAt, Instant.now()).toMillis()
            metrics.recordCollectionSource(
                sourceId = source.id,
                categoryId = category.id,
                success = true,
                durationMs = durationMs,
                collected = sourceCollected,
                newItems = sourceNew,
                duplicates = sourceDuplicates
            )
            crawlLogRecorder.recordSuccess(
                sourceId = source.id,
                durationMs = durationMs,
                articlesFound = sourceCollected
            )
        } catch (e: Exception) {
            log.warn(e) { "Failed to collect from source '${source.name}': ${e.message}" }
            sourceStore.incrementFailCount(source.id, e.message ?: "Unknown error")
            schedulerErrorNotifier?.notifyCollectionError("RSS 수집", e, "source=${source.name}")
            val durationMs = Duration.between(sourceStartedAt, Instant.now()).toMillis()
            metrics.recordCollectionSource(
                sourceId = source.id,
                categoryId = category.id,
                success = false,
                durationMs = durationMs,
                collected = 0,
                newItems = 0,
                duplicates = 0
            )
            crawlLogRecorder.recordFailure(
                sourceId = source.id,
                durationMs = durationMs,
                error = e
            )
        }

        return SourceCollectionResult(
            collected = sourceCollected,
            newItems = sourceNew,
            duplicates = sourceDuplicates
        )
    }
}
