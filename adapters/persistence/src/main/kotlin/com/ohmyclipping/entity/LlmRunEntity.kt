package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * LLM 실행 이력 엔티티.
 * llm_runs 테이블에 매핑된다.
 */
@Entity
@Table(name = "llm_runs")
class LlmRunEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "category_id", length = 36, nullable = false)
    val categoryId: String = "",

    @Column(name = "rss_item_id", length = 36)
    val rssItemId: String? = null,

    @Column(length = 120, nullable = false)
    val model: String = "",

    @Column(name = "prompt_version", length = 80, nullable = false)
    val promptVersion: String = "",

    @Column(name = "input_hash", length = 64, nullable = false)
    val inputHash: String = "",

    @Column(name = "input_chars", nullable = false)
    val inputChars: Int = 0,

    @Column(name = "output_chars", nullable = false)
    var outputChars: Int = 0,

    @Column(length = 20, nullable = false)
    val status: String = "",

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(name = "duration_ms", nullable = false)
    val durationMs: Long = 0,

    @Column(name = "tokens_in")
    val tokensIn: Int? = null,

    @Column(name = "tokens_out")
    val tokensOut: Int? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
