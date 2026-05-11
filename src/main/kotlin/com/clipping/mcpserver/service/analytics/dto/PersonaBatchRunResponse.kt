package com.clipping.mcpserver.service.analytics.dto

import java.time.Instant
import java.time.LocalDate

data class PersonaBatchRunResponse(
    val id: String,
    val runId: String,
    val triggerType: String,
    val weekStart: LocalDate,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val overallStatus: String,
    val snapshotStatus: String?,
    val anomalyStatus: String?,
    val clusteringStatus: String?,
    val reportStatus: String?,
    val personasScanned: Int,
    val anomaliesCreated: Int,
    val anomaliesResolved: Int,
    val embeddingCalls: Int,
    val llmCalls: Int,
    val llmTokensUsed: Int,
    val errorMessage: String?,
    val errorStep: String?,
    val triggeredBy: String?,
)
