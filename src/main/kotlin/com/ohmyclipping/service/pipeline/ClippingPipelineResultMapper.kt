package com.ohmyclipping.service.pipeline

import com.ohmyclipping.service.dto.clipping.CollectCategoryResult
import com.ohmyclipping.service.dto.clipping.CollectResult
import com.ohmyclipping.service.dto.clipping.DigestItemResult
import com.ohmyclipping.service.dto.clipping.DigestResult
import com.ohmyclipping.service.dto.clipping.SummarizeCategoryResult
import com.ohmyclipping.service.dto.clipping.SummarizeResult
import com.ohmyclipping.service.port.PipelineCollectCategoryResult
import com.ohmyclipping.service.port.PipelineCollectResult
import com.ohmyclipping.service.port.PipelineDigestItemResult
import com.ohmyclipping.service.port.PipelineDigestResult
import com.ohmyclipping.service.port.PipelineSummarizeCategoryResult
import com.ohmyclipping.service.port.PipelineSummarizeResult

fun CollectResult.toPipelineCollectResult(): PipelineCollectResult =
    PipelineCollectResult(
        totalCollected = totalCollected,
        newItems = newItems,
        duplicateSkipped = duplicateSkipped,
        categories = categories.map {
            PipelineCollectCategoryResult(
                categoryId = it.categoryId,
                categoryName = it.categoryName,
                collected = it.collected,
                newItems = it.newItems
            )
        }
    )

fun PipelineCollectResult.toCollectResult(): CollectResult =
    CollectResult(
        totalCollected = totalCollected,
        newItems = newItems,
        duplicateSkipped = duplicateSkipped,
        categories = categories.map {
            CollectCategoryResult(
                categoryId = it.categoryId,
                categoryName = it.categoryName,
                collected = it.collected,
                newItems = it.newItems
            )
        }
    )

fun SummarizeResult.toPipelineSummarizeResult(): PipelineSummarizeResult =
    PipelineSummarizeResult(
        totalSummarized = totalSummarized,
        categories = categories.map {
            PipelineSummarizeCategoryResult(
                categoryId = it.categoryId,
                categoryName = it.categoryName,
                summarized = it.summarized
            )
        }
    )

fun PipelineSummarizeResult.toSummarizeResult(): SummarizeResult =
    SummarizeResult(
        totalSummarized = totalSummarized,
        categories = categories.map {
            SummarizeCategoryResult(
                categoryId = it.categoryId,
                categoryName = it.categoryName,
                summarized = it.summarized
            )
        }
    )

fun DigestResult.toPipelineDigestResult(): PipelineDigestResult =
    PipelineDigestResult(
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
            PipelineDigestItemResult(
                summaryId = it.summaryId,
                title = it.title,
                summary = it.summary,
                keywords = it.keywords,
                importanceScore = it.importanceScore,
                whyImportant = it.whyImportant,
                sourceLink = it.sourceLink,
                createdAt = it.createdAt,
                isFallback = it.isFallback
            )
        },
        fallbackUsed = fallbackUsed
    )

fun PipelineDigestResult.toDigestResult(): DigestResult =
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
                isFallback = it.isFallback
            )
        },
        fallbackUsed = fallbackUsed
    )
