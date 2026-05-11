package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.UserClippingRequest
import com.clipping.mcpserver.model.UserClippingRequestStatus
import java.time.Instant

interface UserClippingRequestStore {
    fun listByRequesterUserId(requesterUserId: String): List<UserClippingRequest>
    fun listAll(status: UserClippingRequestStatus? = null): List<UserClippingRequest>

    /**
     * 구독 한도에 포함되는 요청 수를 DB에서 직접 계산한다.
     * PENDING/APPROVED만 세어 전체 요청 row 로드를 피한다.
     */
    fun countActiveSubscriptionsByRequesterUserId(requesterUserId: String): Int

    /**
     * 지정 시각 이후 생성된 유효 요청 수를 DB에서 직접 계산한다.
     * 월별 생성 제한 검증에서 전체 사용자 요청을 메모리에 올리지 않도록 사용한다.
     */
    fun countCreatedSinceByRequesterUserId(requesterUserId: String, since: Instant): Int

    /**
     * 지정 사용자가 특정 카테고리를 이미 APPROVED 상태로 구독 중인지 DB에서 확인한다.
     * 즉시 구독 경로에서 전체 사용자 요청 목록 로드를 피한다.
     */
    fun existsApprovedByRequesterUserIdAndCategoryId(requesterUserId: String, categoryId: String): Boolean

    /**
     * 관리자 목록·MCP 응답용 최근 요청을 제한 조회한다.
     * 전체 row 로드 후 자르지 않고 DB limit을 사용한다.
     */
    fun listRecent(status: UserClippingRequestStatus? = null, limit: Int): List<UserClippingRequest>

    fun findById(id: String): UserClippingRequest?
    fun findPendingCreatedBefore(cutoff: java.time.Instant): List<UserClippingRequest>
    fun save(request: UserClippingRequest): UserClippingRequest
    fun update(request: UserClippingRequest): UserClippingRequest
    fun delete(id: String)

    /** 여러 구독 요청의 상태를 일괄 변경한다. 변경된 건수를 반환한다. */
    fun updateStatusBulk(
        ids: List<String>,
        status: UserClippingRequestStatus,
        reviewNote: String?,
        reviewedByUserId: String?
    ): Int

    /** APPROVED 상태의 구독자 수를 카테고리별로 그룹 카운트한다. */
    fun countApprovedGroupByCategoryId(): Map<String, Int>

    /** APPROVED 상태의 구독 수를 요청자(userId)별로 그룹 카운트한다. */
    fun countApprovedGroupByRequester(): Map<String, Int>

    /**
     * 지정 카테고리 중 하나를 APPROVED 상태로 구독 중인 요청자 ID를 중복 없이 조회한다.
     * 페르소나별 사용자 필터에서 전체 구독 row를 메모리에 올리지 않도록 사용한다.
     */
    fun findApprovedRequesterIdsByCategoryIds(categoryIds: Set<String>): Set<String>

    /**
     * 지정 사용자들이 APPROVED 상태로 구독 중인 카테고리 ID를 중복 없이 조회한다.
     * 다이제스트 스케줄 판단에서 전체 승인 구독 row를 로드하지 않도록 사용한다.
     */
    fun findApprovedCategoryIdsByRequesterIds(requesterUserIds: Set<String>): Set<String>

    /**
     * 지정 카테고리의 APPROVED 구독 요청만 조회한다.
     * Slack fan-out 대상 계산에서 전체 승인 구독 목록의 안전 상한에 잘리지 않도록 사용한다.
     */
    fun listApprovedByCategoryId(categoryId: String): List<UserClippingRequest>

    /** 지정 채널 ID 에 해당 상태 중 하나인 요청이 존재하면 true를 반환한다. */
    fun existsBySlackChannelIdAndStatusIn(
        slackChannelId: String,
        statuses: List<UserClippingRequestStatus>
    ): Boolean

    /**
     * form_entries JSON 을 포함한 신규 구독 요청을 저장한다.
     * V135 컬럼이 존재해야 한다.
     */
    fun saveWithFormEntries(request: UserClippingRequest, formEntries: String?): UserClippingRequest

    /**
     * 요청 ID 에 저장된 form_entries JSON 문자열을 반환한다.
     * 요청이 없거나 form_entries 가 NULL 이면 null 반환.
     */
    fun findFormEntries(id: String): String?

    /**
     * 관리자 요청 통계 화면에 필요한 집계 스냅샷을 조회한다.
     * 전체 요청 row를 메모리에 올리지 않고 상태별 건수와 TOP N을 DB에서 계산한다.
     */
    fun getStatsSnapshot(weeklyCutoff: Instant): UserClippingRequestStatsSnapshot
}
