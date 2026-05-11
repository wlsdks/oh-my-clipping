package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 사용자 클리핑 구독 요청 엔티티.
 * clipping_user_requests 테이블에 매핑된다.
 */
@Entity
@Table(name = "clipping_user_requests")
class UserClippingRequestEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "requester_user_id", length = 36, nullable = false)
    val requesterUserId: String = "",

    @Column(name = "request_name", length = 120, nullable = false)
    var requestName: String = "",

    @Column(name = "source_name", length = 120, nullable = false)
    var sourceName: String = "",

    @Column(name = "source_url", length = 2000, nullable = false)
    var sourceUrl: String = "",

    @Column(name = "slack_channel_id", length = 80, nullable = false)
    var slackChannelId: String = "",

    @Column(name = "persona_name", length = 120, nullable = false)
    var personaName: String = "",

    @Column(name = "persona_prompt", columnDefinition = "TEXT", nullable = false)
    var personaPrompt: String = "",

    @Column(name = "summary_style", length = 120)
    var summaryStyle: String? = null,

    @Column(name = "target_audience", length = 120)
    var targetAudience: String? = null,

    @Column(name = "selected_preset_id", length = 36)
    var selectedPresetId: String? = null,

    @Column(name = "request_note", columnDefinition = "TEXT")
    var requestNote: String? = null,

    @Column(length = 20, nullable = false)
    var status: String = "PENDING",

    @Column(name = "review_note", columnDefinition = "TEXT")
    var reviewNote: String? = null,

    @Column(name = "reviewed_by_user_id", length = 36)
    var reviewedByUserId: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(name = "approved_category_id", length = 36)
    var approvedCategoryId: String? = null,

    @Column(name = "approved_persona_id", length = 36)
    var approvedPersonaId: String? = null,

    @Column(name = "approved_source_id", length = 36)
    var approvedSourceId: String? = null,

    @Column(name = "form_entries", columnDefinition = "TEXT")
    var formEntries: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
