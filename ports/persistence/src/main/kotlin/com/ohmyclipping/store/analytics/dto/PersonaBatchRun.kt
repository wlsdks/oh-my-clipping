package com.ohmyclipping.store.analytics.dto

import java.time.Instant
import java.time.LocalDate

/** persona_batch_run 테이블 매핑 DTO. 배치 실행 이력과 단계별 상태를 포함한다. */
data class PersonaBatchRun(
    val id: String,
    val runId: String,
    val triggerType: TriggerType,
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
    val triggeredBy: String?
)
