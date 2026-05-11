package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.DeliveryDaySummary
import com.clipping.mcpserver.service.dto.clipping.DigestResult
import com.clipping.mcpserver.model.DeliveryLog
import java.time.Instant
import java.time.LocalDate

/**
 * 카테고리-채널 단위 발송 중복 방지를 위한 delivery_log 저장소.
 * tryReserve()로 예약 → 발송 후 updateStatus()로 최종 상태를 기록한다.
 */
interface DeliveryLogStore {

    /**
     * 발송 슬롯을 예약한다. 같은 categoryId+channelId+deliveryDate+deliveryHour 조합이
     * 이미 존재하면 null을 반환하여 중복 발송을 방지한다.
     *
     * @return 생성된 로그 ID, 이미 예약/발송된 경우 null
     */
    fun tryReserve(
        categoryId: String,
        channelId: String,
        deliveryDate: LocalDate,
        deliveryHour: Int
    ): String?

    /**
     * 예약된 발송 로그의 상태를 갱신한다.
     *
     * @param id 로그 ID (tryReserve 반환값)
     * @param status SENT, SKIPPED, FAILED, FINALIZATION_FAILED, NOTIFIED_NO_CONTENT 중 하나
     * @param itemCount 발송된 항목 수
     * @param slackMessageTs Slack 메시지 타임스탬프 (선택)
     */
    fun updateStatus(
        id: String,
        status: String,
        itemCount: Int,
        slackMessageTs: String? = null
    )

    /**
     * 예약된 발송 로그에 준비된 다이제스트 스냅샷을 저장한다.
     * 재시도 시 동일한 payload를 다시 사용할 수 있도록 한다.
     */
    fun savePreparedDigest(id: String, preparedDigest: DigestResult)

    /**
     * Slack payload 에러로 Block Kit 렌더를 포기하고 text-only fallback 으로 전송한 경우
     * 해당 건을 표시한다. 24h 내 fallback 반복 탐지 및 품질 대시보드에 사용한다.
     *
     * @param id 발송 로그 ID
     * @param used fallback 사용 여부 (기본 true)
     */
    fun markFallbackUsed(id: String, used: Boolean = true)

    /** 오래된 발송 이력을 삭제한다. */
    fun deleteOlderThan(days: Int): Int

    /**
     * 재시도 대상 발송 실패 건을 조회한다.
     * retry_count < maxRetries이고 next_retry_at이 현재 시각 이전인 건을 반환한다.
     *
     * @param maxRetries 최대 재시도 횟수 상한
     */
    fun findPendingRetries(maxRetries: Int): List<DeliveryRetryCandidate>

    /**
     * 발송 로그를 RETRYING 상태로 원자적으로 점유(claim)한다.
     * 이미 다른 워커가 점유 중이거나 조건 불일치 시 false를 반환한다.
     *
     * @return 점유 성공 여부
     */
    fun claimForRetry(id: String): Boolean

    /**
     * 일정 시간 이상 RETRYING 상태로 머문 행(stuck claim)을 FAILED로 복구한다.
     *
     * @param timeoutMinutes RETRYING 상태 허용 최대 시간(분)
     */
    fun recoverStuckClaims(timeoutMinutes: Long = 5)

    /**
     * 재시도 결과(실패)를 기록한다.
     * retry_count, next_retry_at, status, last_error, claimed_at을 한 번에 갱신한다.
     *
     * @param id 발송 로그 ID
     * @param retryCount 갱신할 retry_count 값
     * @param nextRetryAt 다음 재시도 시각 (ABANDONED이면 null)
     * @param status 새 상태 (FAILED 또는 ABANDONED)
     * @param lastError 마지막 오류 메시지 (최대 500자로 잘림)
     */
    fun recordFailure(id: String, retryCount: Int, nextRetryAt: Instant?, status: String, lastError: String?)

    /**
     * 특정 카테고리+채널의 ABANDONED 상태 건 중 cutoffHours 이내 생성된 건을 조회한다.
     * 병합 발송(catch-up) 시 재활용할 수 있는 준비된 payload를 반환한다.
     */
    fun findAbandonedForMerge(categoryId: String, channelId: String, cutoffHours: Long = 24): List<DeliveryRetryCandidate>

    /**
     * ABANDONED 상태 건의 retry_count를 0으로 초기화하고 다시 FAILED로 되돌린다.
     * 관리자가 수동으로 재시도를 강제할 때 사용한다.
     */
    fun resetForRetry(id: String)

    /**
     * cutoffHours 이상 경과한 ABANDONED 건을 STALE로 전이한다.
     * 주기적 하우스키핑에서 호출된다.
     */
    fun transitionToStale(cutoffHours: Long = 24)

    /**
     * 특정 카테고리들의 미전달 발송 건을 조회한다.
     * ABANDONED, STALE, 또는 15분 이상 경과한 FAILED 건을 반환한다.
     *
     * @param categoryIds 조회 대상 카테고리 ID 목록
     */
    fun findUndeliveredForUser(categoryIds: List<String>): List<UndeliveredDigest>

    /**
     * 특정 날짜의 발송 요약 통계를 반환한다.
     * 총 건수, 성공/실패/스킵 건수, 성공률을 포함한다.
     */
    fun summary(date: LocalDate = LocalDate.now()): DeliveryDaySummary

    /**
     * 지정한 `date` 의 `delivery_log` 를 status 별 카운트로 집계한다 (delivery_date = date).
     * 결과 Map 의 key 는 status 문자열(SENT/FAILED/SKIPPED/RESERVED/...), value 는 count.
     * 해당 상태 row 가 없으면 key 가 포함되지 않는다. 빈 결과일 때는 빈 Map 을 반환한다.
     *
     * 타임존/달력 정책은 호출자가 소유한다. 예: 서비스에서 KST 오늘을 계산한 뒤 이 메서드에 전달한다.
     *
     * @param date 집계 대상 delivery_date (DATE 컬럼과 정확히 일치하는 값)
     */
    fun countByStatusOn(date: LocalDate): Map<String, Long>

    /**
     * 필터 조건에 따라 발송 이력을 페이지네이션으로 조회한다.
     *
     * @param categoryId 카테고리 ID 필터 (선택)
     * @param status 상태 필터 (선택)
     * @param from 시작 날짜 필터 (선택)
     * @param to 종료 날짜 필터 (선택)
     * @param since null이 아니면 createdAt이 이 시각 이후인 건만 반환한다 (within 파라미터 전달용)
     * @param offset 조회 시작 위치
     * @param limit 조회 건수
     */
    fun findAll(
        categoryId: String? = null,
        status: String? = null,
        from: LocalDate? = null,
        to: LocalDate? = null,
        since: Instant? = null,
        offset: Int = 0,
        limit: Int = 30
    ): List<DeliveryLog>

    /**
     * 필터 조건에 따라 발송 이력의 총 건수를 반환한다.
     *
     * @param since null이 아니면 createdAt이 이 시각 이후인 건만 집계한다
     */
    fun countAll(
        categoryId: String? = null,
        status: String? = null,
        from: LocalDate? = null,
        to: LocalDate? = null,
        since: Instant? = null
    ): Int

    /**
     * ID로 발송 이력 단건을 조회한다.
     *
     * @return 해당 로그, 없으면 null
     */
    fun findById(id: String): DeliveryLog?

    /**
     * 기간별 일자 단위 발송 통계를 집계한다.
     * 각 날짜의 성공/실패/스킵 건수를 반환한다.
     *
     * @param from 시작 날짜 (포함)
     * @param to 종료 날짜 (포함)
     */
    fun dailyStats(from: LocalDate, to: LocalDate): List<DailyStat>

    /**
     * 최근 N일 동안 SENT 발송이 0건인 활성 카테고리를 조회한다.
     * 발송이 예정되었으나 실제로 전달되지 않은 카테고리를 식별하는 데 사용한다.
     *
     * @param days 조회할 최근 일수
     */
    fun consecutiveZeroCategories(days: Int): List<ZeroCategoryAlert>

    /**
     * 사용자 구독 카테고리 기반 발송 이력을 조회한다.
     * delivery_log와 batch_categories를 조인하여 카테고리명을 함께 반환한다.
     *
     * @param categoryIds 조회 대상 카테고리 ID 목록
     * @param from 시작 날짜 (포함)
     * @param to 종료 날짜 (포함)
     */
    fun findByCategoryIds(
        categoryIds: List<String>,
        from: LocalDate,
        to: LocalDate
    ): List<UserDeliveryLogEntry>

    /**
     * 특정 카테고리의 특정 날짜/시간에 SENT 발송이 존재하는지 확인한다.
     * 분석 배치의 활성 판정에서 사용한다.
     *
     * @param categoryId 카테고리 ID
     * @param date       발송 날짜
     * @param hour       발송 시각 (KST)
     */
    fun existsSent(categoryId: String, date: LocalDate, hour: Int): Boolean

    /**
     * 기간 내 카테고리별·일자별 SENT 발송된 item_count 합계를 집계한다.
     * 사용자 포털 "받은 뉴스" 지표처럼 실제 전달 성공 건수를 계산할 때 사용한다.
     *
     * @param categoryIds 조회 대상 카테고리 ID 목록 (비어 있으면 빈 맵 반환)
     * @param from        시작 날짜 (포함, delivery_date 기준)
     * @param to          종료 날짜 (포함, delivery_date 기준)
     * @return `(categoryId, deliveryDate)` 키에 대한 item_count 합
     */
    fun sumDeliveredItemsByCategoryDate(
        categoryIds: List<String>,
        from: LocalDate,
        to: LocalDate
    ): Map<Pair<String, LocalDate>, Int>

    /**
     * 특정 채널+카테고리의 마지막 SENT 발송 시각을 조회한다.
     * 연속 무발송 기간 계산에 사용한다.
     *
     * @return 마지막 SENT 발송의 created_at, 없으면 null
     */
    fun findLastSentDate(channelId: String, categoryId: String): Instant?

    /**
     * 특정 채널+카테고리에 마지막 SENT 이후 NOTIFIED_NO_CONTENT 기록이 존재하는지 확인한다.
     * 연속 무발송 안내 DM의 중복 발송 방지에 사용한다.
     *
     * @return 마지막 SENT 이후에 NOTIFIED_NO_CONTENT가 존재하면 true
     */
    fun hasNotifiedSinceLastSent(channelId: String, categoryId: String): Boolean

    /**
     * 채널 ID 목록에 해당하는 발송 이력을 최신순으로 조회한다.
     * 개인정보 export 등 사용자별 발송 이력 열람에서 사용한다.
     *
     * @param channelIds 조회할 슬랙 채널 ID 목록 (비어 있으면 빈 리스트 반환)
     */
    fun findByChannelIds(channelIds: Collection<String>): List<DeliveryLog>

    /**
     * 발송 재시도 후보 정보.
     */
    data class DeliveryRetryCandidate(
        val id: String,
        val categoryId: String,
        val channelId: String,
        val status: String,
        val slackMessageTs: String?,
        val preparedDigest: DigestResult?,
        val retryCount: Int,
        val createdAt: Instant
    )

    /**
     * 미전달 다이제스트 정보.
     */
    data class UndeliveredDigest(
        val deliveryLogId: String,
        val categoryId: String,
        val deliveryDate: LocalDate,
        val deliveryHour: Int,
        val status: String,
        val retryCount: Int,
        val preparedDigest: DigestResult?
    )

    /**
     * 일자별 발송 통계.
     */
    data class DailyStat(
        val date: LocalDate,
        val sent: Int,
        val failed: Int,
        val skipped: Int
    )

    /**
     * SENT 발송이 0건인 활성 카테고리 알림 정보.
     */
    data class ZeroCategoryAlert(
        val categoryId: String,
        val categoryName: String,
        val missedSlots: Int
    )

    /**
     * 사용자 발송 이력 항목. delivery_log와 batch_categories 조인 결과.
     */
    data class UserDeliveryLogEntry(
        val date: LocalDate,
        val categoryId: String,
        val categoryName: String,
        val itemCount: Int,
        val status: String,
        val deliveredAt: Instant?
    )
}
