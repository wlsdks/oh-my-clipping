package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 파이프라인 단계별 추적 이력 엔티티.
 * pipeline_step_traces 테이블에 매핑된다.
 */
@Entity
@Table(name = "pipeline_step_traces")
class PipelineStepTraceEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "run_id", length = 36, nullable = false)
    val runId: String = "",

    @Column(length = 80, nullable = false)
    var step: String = "",

    @Column(length = 32, nullable = false)
    var status: String = "",

    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "duration_ms")
    var durationMs: Long? = null,

    @Column(columnDefinition = "TEXT")
    var detail: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
