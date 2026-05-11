package com.ohmyclipping.service

import com.ohmyclipping.service.dto.clipping.AiDailySummaryResponse
import com.ohmyclipping.service.dto.clipping.AiSummaryResponse
import com.ohmyclipping.model.Language
import com.ohmyclipping.model.Persona
import com.ohmyclipping.service.dto.CompetitorTimelineItem
import com.ohmyclipping.service.port.LlmArticleLanguage
import com.ohmyclipping.service.port.LlmArticleSummaryResult
import com.ohmyclipping.service.port.LlmCompetitorTimelineItem
import com.ohmyclipping.service.port.LlmDailySummaryResult
import com.ohmyclipping.service.port.LlmPersona

fun Language.toLlmArticleLanguage(): LlmArticleLanguage =
    when (this) {
        Language.KOREAN -> LlmArticleLanguage.KOREAN
        Language.FOREIGN -> LlmArticleLanguage.FOREIGN
    }

fun LlmArticleLanguage.toLanguage(): Language =
    when (this) {
        LlmArticleLanguage.KOREAN -> Language.KOREAN
        LlmArticleLanguage.FOREIGN -> Language.FOREIGN
    }

fun Persona.toLlmPersona(): LlmPersona =
    LlmPersona(
        id = id,
        name = name,
        description = description,
        systemPrompt = systemPrompt,
        summaryStyle = summaryStyle,
        targetAudience = targetAudience,
        maxItems = maxItems,
        language = language,
        isActive = isActive,
        isPreset = isPreset,
        currentVersion = currentVersion,
        tone = tone,
        lengthPref = lengthPref
    )

fun LlmPersona.toPersona(): Persona =
    Persona(
        id = id,
        name = name,
        description = description,
        systemPrompt = systemPrompt,
        summaryStyle = summaryStyle,
        targetAudience = targetAudience,
        maxItems = maxItems,
        language = language,
        isActive = isActive,
        isPreset = isPreset,
        currentVersion = currentVersion,
        tone = tone,
        lengthPref = lengthPref
    )

fun AiSummaryResponse.toLlmArticleSummaryResult(): LlmArticleSummaryResult =
    LlmArticleSummaryResult(
        translatedTitle = translatedTitle,
        summary = summary,
        keywords = keywords,
        importanceScore = importanceScore,
        sentiment = sentiment,
        eventType = eventType
    )

fun LlmArticleSummaryResult.toAiSummaryResponse(): AiSummaryResponse =
    AiSummaryResponse(
        translatedTitle = translatedTitle,
        summary = summary,
        keywords = keywords,
        importanceScore = importanceScore,
        sentiment = sentiment,
        eventType = eventType
    )

fun AiDailySummaryResponse.toLlmDailySummaryResult(): LlmDailySummaryResult =
    LlmDailySummaryResult(
        title = title,
        topicKeywords = topicKeywords
    )

fun LlmDailySummaryResult.toAiDailySummaryResponse(): AiDailySummaryResponse =
    AiDailySummaryResponse(
        title = title,
        topicKeywords = topicKeywords
    )

fun CompetitorTimelineItem.toLlmCompetitorTimelineItem(): LlmCompetitorTimelineItem =
    LlmCompetitorTimelineItem(
        summaryId = summaryId,
        competitorId = competitorId,
        competitorName = competitorName,
        title = title,
        summary = summary,
        keywords = keywords,
        sourceLink = sourceLink,
        importanceScore = importanceScore,
        eventType = eventType,
        sentiment = sentiment,
        createdAt = createdAt
    )

fun LlmCompetitorTimelineItem.toCompetitorTimelineItem(): CompetitorTimelineItem =
    CompetitorTimelineItem(
        summaryId = summaryId,
        competitorId = competitorId,
        competitorName = competitorName,
        title = title,
        summary = summary,
        keywords = keywords,
        sourceLink = sourceLink,
        importanceScore = importanceScore,
        eventType = eventType,
        sentiment = sentiment,
        createdAt = createdAt
    )
