package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 사용자 행동 이벤트 추적 엔티티.
 * user_events 테이블에 매핑된다.
 * PK는 BIGSERIAL(자동 증가)이다.
 */
@Entity
@Table(name = "user_events")
class UserEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", length = 36, nullable = false)
    val userId: String = "",

    @Column(name = "event_type", length = 50, nullable = false)
    val eventType: String = "",

    @Column(name = "event_data", columnDefinition = "TEXT")
    val eventData: String? = null,

    @Column(name = "page_path", length = 255)
    val pagePath: String? = null,

    @Column(name = "session_id", length = 64)
    val sessionId: String? = null,

    /**
     * 페르소나 분석 조인용 컬럼 (V75 마이그레이션). article_*, bookmark_toggle 이벤트만 채워진다.
     */
    @Column(name = "summary_id", length = 36)
    val summaryId: String? = null,

    /**
     * article_share_passive 이벤트의 공유 대상 Slack 채널 ID (V127 마이그레이션).
     * dedup key (summary_id, target_channel_id, slack_message_ts) 의 일부.
     */
    @Column(name = "target_channel_id", length = 64)
    val targetChannelId: String? = null,

    /**
     * article_share_passive 이벤트의 Slack 메시지 timestamp (V127 마이그레이션).
     * dedup key (summary_id, target_channel_id, slack_message_ts) 의 일부.
     */
    @Column(name = "slack_message_ts", length = 64)
    val slackMessageTs: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
