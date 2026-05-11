package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 뉴스 클리핑 카테고리 엔티티.
 * batch_categories 테이블에 매핑된다.
 */
@Entity
@Table(name = "batch_categories")
class CategoryEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(length = 200, nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "slack_channel_id", length = 100)
    var slackChannelId: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "is_public", nullable = false)
    var isPublic: Boolean = false,

    @Column(name = "max_items", nullable = false)
    var maxItems: Int = 5,

    @Column(name = "persona_id", length = 36)
    var personaId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "status", nullable = false, length = 20)
    var status: String = "ACTIVE",

    @Column(name = "paused_at")
    var pausedAt: Instant? = null,

    /**
     * 스케줄러/크롤러가 마지막으로 건드린 시각. 사용자 편집 시각과 분리한다.
     */
    @Column(name = "system_updated_at", nullable = false)
    var systemUpdatedAt: Instant = Instant.now(),

    /**
     * 구독 목적 분류. SALES / RESEARCH / COMPETITIVE / CUSTOMER_CARE / OTHER.
     * V123 에서 추가됨. null 허용. DB 에서 CHECK 제약으로 enum 값 강제.
     */
    @Column(name = "purpose", length = 32)
    var purpose: String? = null,

    /** 구독을 만든 배경/맥락 (자유 텍스트). V123 에서 추가됨. */
    @Column(name = "background", columnDefinition = "TEXT")
    var background: String? = null,

    /** 구독이 해결하려는 문제 (자유 텍스트). V123 에서 추가됨. */
    @Column(name = "problem_statement", columnDefinition = "TEXT")
    var problemStatement: String? = null
)
