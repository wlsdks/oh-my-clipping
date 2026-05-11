package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 파이프라인 실행 이력 엔티티.
 * pipeline_runs 테이블에 매핑된다.
 */
@Entity
@Table(name = "pipeline_runs")
class PipelineRunEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "category_id", length = 36, nullable = false)
    var categoryId: String = "",

    @Column(name = "category_name", length = 200)
    var categoryName: String? = null,

    @Column(name = "triggered_by", length = 120)
    var triggeredBy: String? = null,

    @Column(length = 20, nullable = false)
    var status: String = "RUNNING",

    @Column(name = "orchestration_mode", length = 20)
    var orchestrationMode: String? = null,

    @Column(name = "total_collected")
    var totalCollected: Int? = 0,

    @Column(name = "total_summarized")
    var totalSummarized: Int? = 0,

    @Column(name = "total_digest_selected")
    var totalDigestSelected: Int? = 0,

    @Column(name = "posted_to_slack")
    var postedToSlack: Boolean? = false,

    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "duration_ms")
    var durationMs: Long? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "slack_thread_ts", length = 64)
    var slackThreadTs: String? = null,

    @Column(name = "slack_payload_json", columnDefinition = "TEXT")
    var slackPayloadJson: String? = null
)
