package com.ohmyclipping.model

import java.time.Instant

/**
 * 사용자 행동 이벤트 도메인 모델.
 * 페이지 조회, 기사 클릭, 기능 사용 등 프론트엔드에서 수집한 사용자 행동을 기록한다.
 */
data class UserEvent(
    val id: Long? = null,
    val userId: String,
    val eventType: String,
    val eventData: String? = null,
    val pagePath: String? = null,
    val sessionId: String? = null,
    /**
     * 페르소나 분석에서 기사 클릭/노출/북마크 이벤트를 persona 로 역추적할 때 사용한다.
     * V75 마이그레이션으로 추가된 user_events.summary_id 컬럼과 매핑된다.
     */
    val summaryId: String? = null,
    /**
     * `article_share_passive` 이벤트에서 공유 대상 Slack 채널 ID.
     * V127 마이그레이션으로 추가된 user_events.target_channel_id 컬럼과 매핑된다.
     * dedup(summaryId, targetChannelId, slackMessageTs) 의 키로 사용된다.
     */
    val targetChannelId: String? = null,
    /**
     * `article_share_passive` 이벤트에서 Slack 메시지의 timestamp.
     * V127 마이그레이션으로 추가된 user_events.slack_message_ts 컬럼과 매핑된다.
     * dedup key 로 사용된다.
     */
    val slackMessageTs: String? = null,
    val createdAt: Instant = Instant.now()
)
