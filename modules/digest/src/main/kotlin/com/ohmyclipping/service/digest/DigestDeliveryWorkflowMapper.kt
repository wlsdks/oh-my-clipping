package com.ohmyclipping.service.digest

import com.ohmyclipping.service.dto.clipping.DigestItemResult
import com.ohmyclipping.service.dto.clipping.DigestResult
import com.ohmyclipping.service.port.PreparedDigestItemResult
import com.ohmyclipping.service.port.PreparedDigestResult

fun DigestResult.toPreparedDigestResult(): PreparedDigestResult =
    PreparedDigestResult(
        categoryId = categoryId,
        categoryName = categoryName,
        unsentOnly = unsentOnly,
        totalCandidates = totalCandidates,
        selectedCount = selectedCount,
        postedToSlack = postedToSlack,
        slackChannelId = slackChannelId,
        slackMessageTs = slackMessageTs,
        markedSentCount = markedSentCount,
        digestText = digestText,
        items = items.map {
            PreparedDigestItemResult(
                summaryId = it.summaryId,
                title = it.title,
                summary = it.summary,
                keywords = it.keywords,
                importanceScore = it.importanceScore,
                whyImportant = it.whyImportant,
                sourceLink = it.sourceLink,
                createdAt = it.createdAt,
                isFallback = it.isFallback,
            )
        },
        fallbackUsed = fallbackUsed,
    )

fun PreparedDigestResult.toDigestResult(): DigestResult =
    DigestResult(
        categoryId = categoryId,
        categoryName = categoryName,
        unsentOnly = unsentOnly,
        totalCandidates = totalCandidates,
        selectedCount = selectedCount,
        postedToSlack = postedToSlack,
        slackChannelId = slackChannelId,
        slackMessageTs = slackMessageTs,
        markedSentCount = markedSentCount,
        digestText = digestText,
        items = items.map {
            DigestItemResult(
                summaryId = it.summaryId,
                title = it.title,
                summary = it.summary,
                keywords = it.keywords,
                importanceScore = it.importanceScore,
                whyImportant = it.whyImportant,
                sourceLink = it.sourceLink,
                createdAt = it.createdAt,
                isFallback = it.isFallback,
            )
        },
        fallbackUsed = fallbackUsed,
    )
