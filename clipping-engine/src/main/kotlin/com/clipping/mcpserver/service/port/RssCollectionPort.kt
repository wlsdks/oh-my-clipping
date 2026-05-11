package com.clipping.mcpserver.service.port

import java.time.Instant

interface RssCollectionPort {
    fun collect(
        source: RssCollectionSource,
        hoursBack: Int = 24,
        enrichShortContent: Boolean = true
    ): List<RssCollectedItem>

    fun collectByUrl(url: String, hoursBack: Int = 24): List<RssCollectedItem>

    fun enrichShortContent(item: RssCollectedItem): RssCollectedItem
}

data class RssCollectionSource(
    val id: String,
    val name: String,
    val url: String,
    val emoji: String? = null,
    val isActive: Boolean = true,
    val crawlApproved: Boolean = false,
    val approvedBy: String? = null,
    val approvedAt: Instant? = null,
    val legalBasis: String = "QUOTATION_ONLY",
    val summaryAllowed: Boolean = true,
    val fulltextAllowed: Boolean = false,
    val termsReviewedAt: Instant? = null,
    val expectedReviewAt: Instant? = null,
    val reviewNotes: String? = null,
    val verificationStatus: String = "PENDING",
    val reliabilityScore: Int = 50,
    val lastCrawlError: String? = null,
    val crawlFailCount: Int = 0,
    val lastSuccessAt: Instant? = null,
    val sourceRegion: String = "UNKNOWN",
    val categoryId: String,
    val curated: Boolean = false,
    val responsibilityAcknowledgedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val systemUpdatedAt: Instant,
    val origin: String = "manual"
)

data class RssCollectedItem(
    val id: String,
    val title: String,
    val content: String? = null,
    val link: String,
    val publishedAt: Instant? = null,
    val language: String = "FOREIGN",
    val isProcessed: Boolean = false,
    val categoryId: String,
    val rssSourceId: String? = null,
    val screenedScore: Float? = null,
    val createdAt: Instant
)
