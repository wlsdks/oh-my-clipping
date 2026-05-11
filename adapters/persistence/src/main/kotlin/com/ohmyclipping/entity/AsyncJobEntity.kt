package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 비동기 작업 큐 엔티티.
 * clipping_jobs 테이블에 매핑된다.
 */
@Entity
@Table(name = "clipping_jobs")
class AsyncJobEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "job_type", length = 30, nullable = false)
    var jobType: String = "",

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    var payloadJson: String = "",

    @Column(length = 20, nullable = false)
    var status: String = "",

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "max_attempts", nullable = false)
    var maxAttempts: Int = 3,

    @Column(name = "next_run_at", nullable = false)
    var nextRunAt: Instant = Instant.now(),

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "result_json", columnDefinition = "TEXT")
    var resultJson: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
