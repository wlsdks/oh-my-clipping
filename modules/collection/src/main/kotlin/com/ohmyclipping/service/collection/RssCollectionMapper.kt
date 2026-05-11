package com.ohmyclipping.service.collection

import com.ohmyclipping.model.Language
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.model.SourceLegalBasis
import com.ohmyclipping.model.SourceRegionType
import com.ohmyclipping.service.port.RssCollectedItem
import com.ohmyclipping.service.port.RssCollectionSource

fun RssSource.toRssCollectionSource(): RssCollectionSource =
    RssCollectionSource(
        id = id,
        name = name,
        url = url,
        emoji = emoji,
        isActive = isActive,
        crawlApproved = crawlApproved,
        approvedBy = approvedBy,
        approvedAt = approvedAt,
        legalBasis = legalBasis.name,
        summaryAllowed = summaryAllowed,
        fulltextAllowed = fulltextAllowed,
        termsReviewedAt = termsReviewedAt,
        expectedReviewAt = expectedReviewAt,
        reviewNotes = reviewNotes,
        verificationStatus = verificationStatus,
        reliabilityScore = reliabilityScore,
        lastCrawlError = lastCrawlError,
        crawlFailCount = crawlFailCount,
        lastSuccessAt = lastSuccessAt,
        sourceRegion = sourceRegion.name,
        categoryId = categoryId,
        curated = curated,
        responsibilityAcknowledgedAt = responsibilityAcknowledgedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        systemUpdatedAt = systemUpdatedAt,
        origin = origin
    )

fun RssCollectionSource.toRssSource(): RssSource =
    RssSource(
        id = id,
        name = name,
        url = url,
        emoji = emoji,
        isActive = isActive,
        crawlApproved = crawlApproved,
        approvedBy = approvedBy,
        approvedAt = approvedAt,
        legalBasis = enumValueOrDefault(legalBasis, SourceLegalBasis.QUOTATION_ONLY),
        summaryAllowed = summaryAllowed,
        fulltextAllowed = fulltextAllowed,
        termsReviewedAt = termsReviewedAt,
        expectedReviewAt = expectedReviewAt,
        reviewNotes = reviewNotes,
        verificationStatus = verificationStatus,
        reliabilityScore = reliabilityScore,
        lastCrawlError = lastCrawlError,
        crawlFailCount = crawlFailCount,
        lastSuccessAt = lastSuccessAt,
        sourceRegion = enumValueOrDefault(sourceRegion, SourceRegionType.UNKNOWN),
        categoryId = categoryId,
        curated = curated,
        responsibilityAcknowledgedAt = responsibilityAcknowledgedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        systemUpdatedAt = systemUpdatedAt,
        origin = origin
    )

fun RssItem.toRssCollectedItem(): RssCollectedItem =
    RssCollectedItem(
        id = id,
        title = title,
        content = content,
        link = link,
        publishedAt = publishedAt,
        language = language.name,
        isProcessed = isProcessed,
        categoryId = categoryId,
        rssSourceId = rssSourceId,
        screenedScore = screenedScore,
        createdAt = createdAt
    )

fun RssCollectedItem.toRssItem(): RssItem =
    RssItem(
        id = id,
        title = title,
        content = content,
        link = link,
        publishedAt = publishedAt,
        language = enumValueOrDefault(language, Language.FOREIGN),
        isProcessed = isProcessed,
        categoryId = categoryId,
        rssSourceId = rssSourceId,
        screenedScore = screenedScore,
        createdAt = createdAt
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(default)
