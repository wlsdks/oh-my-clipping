package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.UserEvent
import com.ohmyclipping.service.dto.user.UserEventBatchResponse
import com.ohmyclipping.service.dto.user.UserEventRequest
import com.ohmyclipping.store.ArticleEventRow
import com.ohmyclipping.store.ArticleMetadataRow
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.DailyCount
import com.ohmyclipping.store.UserEventStore
import com.ohmyclipping.store.WizardStepRow
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

private val log = KotlinLogging.logger {}

/**
 * 사용자 행동 이벤트 서비스.
 * 이벤트 유효성 검증, 저장, 통계 집계를 담당한다.
 */
@Service
class UserEventService(
    private val userEventStore: UserEventStore,
    private val adminUserStore: AdminUserStore,
    private val objectMapper: ObjectMapper
) {

    companion object {
        /** 허용된 이벤트 타입 목록. */
        val ALLOWED_EVENT_TYPES = setOf(
            "page_view",
            "article_impression",
            "article_click",
            "wizard_step",
            "bookmark_toggle",
            // Phase 3 PR3b: Slack link_shared 이벤트로 감지된 passive share.
            "article_share_passive"
        )

        private val ALLOWED_WIZARD_ACTIONS = setOf("enter", "complete", "abandon")
        private val ALLOWED_BOOKMARK_ACTIONS = setOf("add", "remove")
        // 현재는 slack 만 지원. 추후 email/web 등 추가 시 확장.
        private val ALLOWED_CLICK_SOURCES = setOf("slack")
        private const val MAX_SESSION_ID_LENGTH = 64
        private const val MAX_PAGE_PATH_LENGTH = 255
    }

    /**
     * Slack 다이제스트의 "원문 보기" 버튼 클릭을 기록한다.
     * 저장 실패 시 warn 로그만 남기고 예외를 던지지 않는다 — 리다이렉트 흐름을 끊지 않기 위함.
     *
     * @param userId 사용자 ID (미인증이면 "anonymous")
     * @param summaryId 클릭한 기사의 요약 ID
     * @param url 원본 기사 URL
     * @param source 클릭 출처 (예: "slack"). null 이면 event_data 에 source 키 미포함 (backward compat).
     */
    fun saveClick(userId: String, summaryId: String, url: String, source: String? = null) {
        // Source 정규화: trim + lowercase, 빈 문자열 / 허용 목록 밖 값은 null 로 coerce
        val normalizedSource = source?.trim()?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() && it in ALLOWED_CLICK_SOURCES }
        try {
            // normalizedSource 가 있을 때만 payload 에 포함해 backward compat 를 유지한다
            val payload: Map<String, Any> = buildMap {
                put("summaryId", summaryId)
                put("url", url)
                if (normalizedSource != null) put("source", normalizedSource)
            }
            val event = UserEvent(
                userId = userId,
                eventType = "article_click",
                eventData = objectMapper.writeValueAsString(payload),
                pagePath = null,
                sessionId = "track-redirect",
                summaryId = summaryId,
                createdAt = Instant.now()
            )
            userEventStore.saveBatch(listOf(event))
        } catch (e: JsonProcessingException) {
            log.warn(e) { "Failed to serialize click event for summary=$summaryId" }
        } catch (e: RuntimeException) {
            log.warn(e) { "Failed to save click event for summary=$summaryId" }
        }
    }

    /**
     * Slack `link_shared` 이벤트로 감지된 패시브 공유를 저장한다.
     *
     * dedup: (summaryId, targetChannelId, slackMessageTs) UNIQUE (V127).
     * 중복 키가 발생하면 상위 호출자([SlackLinkSharedHandler])가 [org.springframework.dao.DuplicateKeyException] 을 처리한다.
     *
     * @param userId 공유한 사용자의 Slack user ID
     * @param summaryId 공유 대상 요약 ID (tracking URL 에서 추출됨)
     * @param targetChannelId 공유된 Slack 채널 ID
     * @param slackMessageTs 공유 메시지의 timestamp
     */
    fun savePassiveShare(
        userId: String,
        summaryId: String,
        targetChannelId: String,
        slackMessageTs: String
    ) {
        // 공유 메타를 JSON payload 에도 중복 기록해 downstream 분석 쿼리 호환성을 유지한다.
        val payload = mapOf(
            "summaryId" to summaryId,
            "targetChannelId" to targetChannelId,
            "messageTs" to slackMessageTs
        )
        val event = UserEvent(
            userId = userId,
            eventType = "article_share_passive",
            eventData = objectMapper.writeValueAsString(payload),
            pagePath = null,
            sessionId = "slack-link-shared",
            summaryId = summaryId,
            targetChannelId = targetChannelId,
            slackMessageTs = slackMessageTs,
            createdAt = Instant.now()
        )
        userEventStore.saveBatch(listOf(event))
    }

    /**
     * 이벤트 목록을 검증하고 유효한 이벤트만 저장한다.
     * @param userId 이벤트를 발생시킨 사용자 ID
     * @param requests 클라이언트에서 전송한 이벤트 목록
     * @return 수락/거부 건수를 포함한 응답
     * @throws InvalidInputException 이벤트 목록이 비어있는 경우
     */
    fun saveBatch(
        userId: String,
        requests: List<UserEventRequest>
    ): UserEventBatchResponse {
        // 빈 이벤트 목록은 거부한다.
        if (requests.isEmpty()) {
            throw InvalidInputException("이벤트 목록이 비어있습니다.")
        }

        val rejectedTypes = mutableSetOf<String>()
        val rejectedContracts = mutableSetOf<String>()

        // 허용 타입과 payload 계약을 모두 통과한 이벤트만 저장한다.
        val events = requests.mapNotNull { req ->
            when {
                req.eventType !in ALLOWED_EVENT_TYPES -> {
                    rejectedTypes += req.eventType
                    null
                }
                else -> toUserEvent(userId, req) ?: run {
                    rejectedContracts += req.eventType
                    null
                }
            }
        }

        if (rejectedTypes.isNotEmpty()) {
            log.debug { "거부된 이벤트 타입: ${rejectedTypes.toList().sorted()}" }
        }
        if (rejectedContracts.isNotEmpty()) {
            log.debug { "payload 계약 위반으로 거부된 이벤트 타입: ${rejectedContracts.toList().sorted()}" }
        }

        if (events.isNotEmpty()) {
            // 유효한 이벤트만 정규화된 payload로 일괄 저장한다.
            userEventStore.saveBatch(events)
        }

        return UserEventBatchResponse(
            accepted = events.size,
            rejected = requests.size - events.size
        )
    }

    /**
     * 인증 주체 사용자명으로 내부 사용자 ID를 해석한 뒤 이벤트를 저장한다.
     * 사용자가 조회되지 않으면 운영 추적을 위해 사용자명 자체를 fallback ID로 사용한다.
     *
     * @param username 인증 컨텍스트의 사용자명
     * @param requests 저장할 이벤트 목록
     */
    fun saveBatchForUsername(username: String, requests: List<UserEventRequest>): UserEventBatchResponse {
        val user = adminUserStore.findByUsername(username)
        if (user == null) {
            log.warn { "이벤트 수집 시 사용자를 찾을 수 없음: $username" }
        }
        return saveBatch(user?.id ?: username, requests)
    }

    /**
     * 기간 내 특정 이벤트 타입의 발생 횟수를 조회한다.
     */
    fun countByEventType(
        eventType: String,
        from: Instant,
        to: Instant
    ): Long = userEventStore.countByEventType(eventType, from, to)

    /**
     * 여러 KST 날짜의 특정 이벤트 타입 발생 횟수를 한 번에 조회한다.
     *
     * 홈 대시보드처럼 고정 기간 일별 추이가 필요한 경로에서 날짜별 count 쿼리 반복을 피한다.
     */
    fun countByEventTypeForDays(
        eventType: String,
        days: List<LocalDate>
    ): Map<LocalDate, Long> =
        userEventStore.countByEventTypeForDays(eventType, days)

    /**
     * 기간 내 고유 사용자 수를 조회한다.
     */
    fun countDistinctUsers(from: Instant, to: Instant): Long =
        userEventStore.countDistinctUsers(from, to)

    /**
     * 기간 내 일별 활성 사용자 수를 집계한다.
     * 이벤트가 없는 날짜도 count=0으로 채워 완전한 날짜 범위를 반환한다.
     */
    fun dailyActiveUsers(
        from: Instant,
        to: Instant
    ): List<DailyCount> {
        val rawCounts = userEventStore.dailyActiveUsers(from, to)
        // DB 결과를 날짜 키 맵으로 변환한다.
        val countMap = rawCounts.associate { it.date to it.count }
        // from~to 범위의 모든 날짜를 생성하고, 이벤트 없는 날은 0으로 채운다.
        // to는 반개구간 상한(다음 날 00:00)이므로 1일 빼서 포함 범위 끝을 구한다.
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val startDate = from.atZone(zone).toLocalDate()
        val endDate = to.atZone(zone).toLocalDate().minusDays(1)
        val result = mutableListOf<DailyCount>()
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            val dateStr = cursor.toString()
            result.add(DailyCount(date = dateStr, count = countMap[dateStr] ?: 0))
            cursor = cursor.plusDays(1)
        }
        return result
    }

    /**
     * 기간 내 wizard_step 이벤트 raw 데이터를 조회한다.
     */
    fun findWizardStepEvents(
        from: Instant,
        to: Instant
    ): List<WizardStepRow> = userEventStore.findWizardStepEvents(from, to)

    /**
     * 기간 내 기사 노출/클릭 이벤트 raw 데이터를 조회한다.
     */
    fun findArticleEvents(
        from: Instant,
        to: Instant
    ): List<ArticleEventRow> = userEventStore.findArticleEvents(from, to)

    /**
     * summaryId 목록에 해당하는 기사 메타데이터를 조회한다.
     */
    fun findArticleMetadata(
        summaryIds: List<String>
    ): List<ArticleMetadataRow> =
        userEventStore.findArticleMetadata(summaryIds)

    /**
     * summaryId 목록에 해당하는 기사별 북마크 수를 조회한다.
     */
    fun countBookmarksBySummaryIds(
        summaryIds: List<String>
    ): Map<String, Long> =
        userEventStore.countBookmarksBySummaryIds(summaryIds)

    /**
     * 특정 사용자의 기간 내 이벤트를 조회한다.
     * AdminUserAccountController에서 사용 중이므로 유지한다.
     */
    fun findByUserAndDateRange(
        userId: String,
        from: Instant,
        to: Instant,
        limit: Int = 100
    ): List<com.ohmyclipping.model.UserEvent> =
        userEventStore.findByUserAndDateRange(userId, from, to, limit)

    /**
     * 최근 7일간 사용자 활동을 한국어 요약 문자열로 반환한다.
     * 활동이 없으면 null을 반환한다.
     * AdminUserAccountController에서 회원 관리 페이지에 활용 중이므로 유지한다.
     *
     * @param userId 대상 사용자 ID
     * @return 예: "최근 7일: 기사 12건 열람, 북마크 3건"
     */
    fun buildRecentActivitySummary(userId: String): String? {
        val now = Instant.now()
        val sevenDaysAgo = now.minus(7, java.time.temporal.ChronoUnit.DAYS)
        // 최근 7일간 해당 사용자의 전체 이벤트를 조회한다.
        val events = userEventStore.findByUserAndDateRange(userId, sevenDaysAgo, now, 1000)
        if (events.isEmpty()) return null

        // 이벤트 타입별 건수를 집계한다.
        val countByType = events.groupBy { it.eventType }
            .mapValues { (_, v) -> v.size }

        val parts = mutableListOf<String>()
        // 기사 열람 건수 (article_click + article_impression)
        val articleViews = (countByType["article_click"] ?: 0) +
            (countByType["article_impression"] ?: 0)
        if (articleViews > 0) parts.add("기사 ${articleViews}건 열람")
        // 북마크 토글 건수
        val bookmarks = countByType["bookmark_toggle"] ?: 0
        if (bookmarks > 0) parts.add("북마크 ${bookmarks}건")
        // 페이지 조회 건수
        val pageViews = countByType["page_view"] ?: 0
        if (pageViews > 0) parts.add("페이지 ${pageViews}회 조회")

        if (parts.isEmpty()) return null
        return "최근 7일: ${parts.joinToString(", ")}"
    }

    /**
     * 여러 사용자의 최근 7일 활동을 배치로 요약한다.
     * 단일 SQL 집계 쿼리로 N+1 문제를 방지한다.
     */
    fun buildRecentActivitySummaryBatch(userIds: List<String>): Map<String, String?> {
        if (userIds.isEmpty()) return emptyMap()
        val now = Instant.now()
        val sevenDaysAgo = now.minus(7, java.time.temporal.ChronoUnit.DAYS)
        val countsByUser = userEventStore.countEventsByTypeForUsers(userIds, sevenDaysAgo, now)
        return userIds.associateWith { userId ->
            val counts = countsByUser[userId] ?: return@associateWith null
            formatActivitySummary(counts)
        }
    }

    /** 이벤트 타입별 건수를 한국어 요약 문자열로 변환한다. */
    private fun formatActivitySummary(countByType: Map<String, Int>): String? {
        val parts = mutableListOf<String>()
        val articleViews = (countByType["article_click"] ?: 0) +
            (countByType["article_impression"] ?: 0)
        if (articleViews > 0) parts.add("기사 ${articleViews}건 열람")
        val bookmarks = countByType["bookmark_toggle"] ?: 0
        if (bookmarks > 0) parts.add("북마크 ${bookmarks}건")
        val pageViews = countByType["page_view"] ?: 0
        if (pageViews > 0) parts.add("페이지 ${pageViews}회 조회")
        if (parts.isEmpty()) return null
        return "최근 7일: ${parts.joinToString(", ")}"
    }

    /** eventData Map을 JSON 문자열로 직렬화한다. */
    private fun serializeEventData(data: Map<String, Any?>?): String? {
        if (data.isNullOrEmpty()) return null
        return objectMapper.writeValueAsString(data)
    }

    /** HTTP 요청 DTO를 정책 검증 후 도메인 이벤트로 변환한다. */
    private fun toUserEvent(
        userId: String,
        req: UserEventRequest
    ): UserEvent? {
        // sessionId는 랜덤 식별자만 허용하고 비정상 길이는 거부한다.
        val sessionId = normalizeSessionId(req.sessionId) ?: return null
        // page_view는 pagePath가 없으면 분석 가치가 없어 저장하지 않는다.
        val pagePath = normalizePagePath(req)
        if (req.eventType == "page_view" && pagePath == null) return null

        // eventType별 최소 필드만 남기고 자유 입력/PII 가능 필드는 제거한다.
        val sanitizedData = sanitizeEventData(req.eventType, req.eventData) ?: return null
        // article_*, bookmark_toggle 이벤트의 summaryId 는 V75 이후 별도 컬럼에도 저장한다.
        // (페르소나 분석 Slice 2 에서 인덱스 기반 조인에 사용된다.)
        val summaryId = sanitizedData["summaryId"]?.toString()
        return UserEvent(
            userId = userId,
            eventType = req.eventType,
            eventData = serializeEventData(sanitizedData),
            pagePath = pagePath,
            sessionId = sessionId,
            summaryId = summaryId,
            createdAt = Instant.ofEpochMilli(req.timestamp)
        )
    }

    /** eventType별 허용 필드만 남기고 계약을 벗어난 payload는 거부한다. */
    private fun sanitizeEventData(
        eventType: String,
        eventData: Map<String, Any?>?
    ): Map<String, Any?>? = when (eventType) {
        "page_view" -> emptyMap()
        "article_impression", "article_click" -> sanitizeArticleEventData(eventData)
        "wizard_step" -> sanitizeWizardStepEventData(eventData)
        "bookmark_toggle" -> sanitizeBookmarkEventData(eventData)
        // article_share_passive 는 서버 내부 경로 (Slack link_shared) 로만 저장되지만,
        // 혹시 HTTP saveBatch 로 요청이 오더라도 summaryId 만 남기고 그대로 반려하지는 않는다.
        "article_share_passive" -> sanitizeArticleEventData(eventData)
        else -> null
    }

    /** 기사 이벤트는 summaryId만 저장하고 title 같은 중복/자유입력 필드는 버린다. */
    private fun sanitizeArticleEventData(eventData: Map<String, Any?>?): Map<String, Any?>? {
        val summaryId = extractString(eventData?.get("summaryId")) ?: return null
        return mapOf("summaryId" to summaryId)
    }

    /** 위자드 이벤트는 step과 허용된 action만 저장한다. */
    private fun sanitizeWizardStepEventData(eventData: Map<String, Any?>?): Map<String, Any?>? {
        val step = extractString(eventData?.get("step")) ?: return null
        val action = extractString(eventData?.get("action"))
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it in ALLOWED_WIZARD_ACTIONS }
            ?: "enter"
        return mapOf(
            "step" to step,
            "action" to action
        )
    }

    /** 북마크 이벤트는 summaryId와 add/remove action만 저장한다. */
    private fun sanitizeBookmarkEventData(eventData: Map<String, Any?>?): Map<String, Any?>? {
        val summaryId = extractString(eventData?.get("summaryId")) ?: return null
        val action = extractString(eventData?.get("action"))
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it in ALLOWED_BOOKMARK_ACTIONS }

        val sanitized = linkedMapOf<String, Any?>("summaryId" to summaryId)
        if (action != null) {
            sanitized["action"] = action
        }
        return sanitized
    }

    /** page_view는 body.path보다 pagePath 컬럼을 우선하고, 없으면 body.path를 보조로 사용한다. */
    private fun normalizePagePath(req: UserEventRequest): String? {
        val rawPath = req.pagePath ?: extractString(req.eventData?.get("path"))
        val normalized = rawPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return normalized.takeIf {
            it.startsWith("/") && it.length <= MAX_PAGE_PATH_LENGTH
        }
    }

    /** sessionId는 비어 있지 않고 저장 컬럼 길이 이하여야 한다. */
    private fun normalizeSessionId(sessionId: String): String? {
        val normalized = sessionId.trim()
        return normalized.takeIf {
            it.isNotEmpty() && it.length <= MAX_SESSION_ID_LENGTH
        }
    }

    /** eventData에서 문자열로 해석 가능한 값만 추출한다. */
    private fun extractString(value: Any?): String? {
        val normalized = when (value) {
            is String -> value.trim()
            is Number, is Boolean -> value.toString()
            else -> null
        }
        return normalized?.takeIf { it.isNotBlank() }
    }
}
