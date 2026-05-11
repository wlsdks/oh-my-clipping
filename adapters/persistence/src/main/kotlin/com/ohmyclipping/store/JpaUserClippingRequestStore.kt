package com.ohmyclipping.store

import com.ohmyclipping.entity.UserClippingRequestEntity
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.repository.UserClippingRequestRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * 사용자 클리핑 구독 요청 JPA 구현. JdbcUserClippingRequestStore를 대체한다.
 */
@Repository
@Primary
class JpaUserClippingRequestStore(
    private val repository: UserClippingRequestRepository,
    private val jdbc: JdbcTemplate
) : UserClippingRequestStore {

    companion object {
        /** 구독 요청 목록 조회 시 안전 상한. 이 수에 도달하면 경고를 남긴다. */
        private const val MAX_LIST_ALL_RESULTS = 10000

        /** 통계 화면의 TOP N 항목 수. */
        private const val STATS_TOP_LIMIT = 5
    }

    override fun listByRequesterUserId(requesterUserId: String): List<UserClippingRequest> =
        repository.findByRequesterUserId(requesterUserId)
            .map { it.toModel() }
            .sortedByDescending { it.createdAt }

    override fun countActiveSubscriptionsByRequesterUserId(requesterUserId: String): Int {
        // 구독 한도 검증은 PENDING/APPROVED 카운트만 필요하므로 엔티티 목록 로드를 피한다.
        return repository.countByRequesterUserIdAndStatusIn(
            requesterUserId,
            listOf(UserClippingRequestStatus.PENDING.name, UserClippingRequestStatus.APPROVED.name)
        )
    }

    override fun countCreatedSinceByRequesterUserId(requesterUserId: String, since: Instant): Int {
        // 월 생성 제한은 유효 상태만 DB에서 집계해 장기 사용자 요청 목록 전체 로드를 방지한다.
        return repository.countByRequesterUserIdAndCreatedAtAfterAndStatusIn(
            requesterUserId,
            since,
            listOf(UserClippingRequestStatus.PENDING.name, UserClippingRequestStatus.APPROVED.name)
        )
    }

    override fun existsApprovedByRequesterUserIdAndCategoryId(requesterUserId: String, categoryId: String): Boolean =
        repository.existsByRequesterUserIdAndApprovedCategoryIdAndStatus(
            requesterUserId,
            categoryId,
            UserClippingRequestStatus.APPROVED.name
        )

    /**
     * 전체/상태별 구독 요청 목록을 최신순으로 제한 조회한다.
     * 안전 상한은 DB Pageable에 적용해 JVM에 전체 row를 올리지 않는다.
     */
    override fun listAll(status: UserClippingRequestStatus?): List<UserClippingRequest> {
        val pageable = PageRequest.of(0, MAX_LIST_ALL_RESULTS)
        // 목록성 조회는 DB에서 정렬과 상한을 처리해 메모리 사용량을 제한한다.
        val entities = if (status == null) {
            repository.findAllByOrderByCreatedAtDesc(pageable)
        } else {
            repository.findByStatusOrderByCreatedAtDesc(status.name, pageable)
        }
        val results = entities.map { it.toModel() }
        // 안전 상한에 도달하면 경고를 남겨 운영자가 페이지네이션 도입을 검토할 수 있게 한다.
        if (results.size >= MAX_LIST_ALL_RESULTS) {
            log.warn { "listAll hit safety limit=$MAX_LIST_ALL_RESULTS (status=$status). Consider adding pagination." }
        }
        return results
    }

    override fun listRecent(status: UserClippingRequestStatus?, limit: Int): List<UserClippingRequest> {
        val safeLimit = limit.coerceIn(1, MAX_LIST_ALL_RESULTS)
        val pageable = PageRequest.of(0, safeLimit)
        // 최신 목록 API는 DB에서 정렬과 limit을 처리해 전체 요청 로드를 피한다.
        val entities = if (status == null) {
            repository.findAllByOrderByCreatedAtDesc(pageable)
        } else {
            repository.findByStatusOrderByCreatedAtDesc(status.name, pageable)
        }
        return entities.map { it.toModel() }
    }

    override fun findById(id: String): UserClippingRequest? =
        repository.findById(id).orElse(null)?.toModel()

    override fun findPendingCreatedBefore(cutoff: Instant): List<UserClippingRequest> =
        repository.findByStatusAndCreatedAtBefore(UserClippingRequestStatus.PENDING.name, cutoff)
            .map { it.toModel() }
            .sortedBy { it.createdAt }

    override fun save(request: UserClippingRequest): UserClippingRequest {
        val now = Instant.now()
        val saved = request.copy(
            id = request.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = now,
            updatedAt = now
        )
        repository.save(saved.toEntity())
        return saved
    }

    override fun update(request: UserClippingRequest): UserClippingRequest {
        val entity = repository.findById(request.id).orElseThrow {
            NotFoundException("UserClippingRequest not found: ${request.id}")
        }
        val now = Instant.now()
        entity.requestName = request.requestName
        entity.sourceName = request.sourceName
        entity.sourceUrl = request.sourceUrl
        entity.slackChannelId = request.slackChannelId
        entity.personaName = request.personaName
        entity.personaPrompt = request.personaPrompt
        entity.summaryStyle = request.summaryStyle
        entity.targetAudience = request.targetAudience
        entity.selectedPresetId = request.selectedPresetId
        entity.requestNote = request.requestNote
        entity.status = request.status.name
        entity.reviewNote = request.reviewNote
        entity.reviewedByUserId = request.reviewedByUserId
        entity.reviewedAt = request.reviewedAt
        entity.approvedCategoryId = request.approvedCategoryId
        entity.approvedPersonaId = request.approvedPersonaId
        entity.approvedSourceId = request.approvedSourceId
        entity.updatedAt = now
        repository.save(entity)
        return request.copy(updatedAt = now)
    }

    override fun delete(id: String) {
        repository.deleteById(id)
    }

    override fun updateStatusBulk(
        ids: List<String>,
        status: UserClippingRequestStatus,
        reviewNote: String?,
        reviewedByUserId: String?
    ): Int {
        if (ids.isEmpty()) return 0
        // 일괄 UPDATE로 N+1 개별 update 호출을 방지한다.
        return repository.updateStatusBulk(
            ids, status.name, reviewNote, reviewedByUserId, java.time.Instant.now()
        )
    }

    override fun countApprovedGroupByCategoryId(): Map<String, Int> {
        val rows = repository.countApprovedGroupByCategoryId()
        return rows.mapNotNull { row ->
            val categoryId = row[0] as? String ?: return@mapNotNull null
            val count = (row[1] as? Number)?.toInt() ?: return@mapNotNull null
            categoryId to count
        }.toMap()
    }

    override fun countApprovedGroupByRequester(): Map<String, Int> {
        val rows = repository.countApprovedGroupByRequester()
        return rows.mapNotNull { row ->
            val requesterUserId = row[0] as? String ?: return@mapNotNull null
            val count = (row[1] as? Number)?.toInt() ?: return@mapNotNull null
            requesterUserId to count
        }.toMap()
    }

    override fun findApprovedRequesterIdsByCategoryIds(categoryIds: Set<String>): Set<String> {
        if (categoryIds.isEmpty()) return emptySet()
        // 페르소나 필터에 필요한 사용자 ID만 DB에서 DISTINCT 조회해 대량 구독 row 로드를 피한다.
        return repository.findApprovedRequesterIdsByCategoryIds(categoryIds).toSet()
    }

    override fun findApprovedCategoryIdsByRequesterIds(requesterUserIds: Set<String>): Set<String> {
        if (requesterUserIds.isEmpty()) return emptySet()
        // 개인 스케줄 판단에 필요한 카테고리 ID만 DB에서 DISTINCT 조회해 전체 승인 구독 로드를 피한다.
        return repository.findApprovedCategoryIdsByRequesterIds(requesterUserIds).toSet()
    }

    override fun listApprovedByCategoryId(categoryId: String): List<UserClippingRequest> {
        if (categoryId.isBlank()) return emptyList()
        // Slack fan-out은 실제 발송 카테고리의 승인 구독만 조회해 listAll 안전 상한 누락을 피한다.
        return repository.findByStatusAndApprovedCategoryIdOrderByCreatedAtDesc(
            UserClippingRequestStatus.APPROVED.name,
            categoryId
        ).map { it.toModel() }
    }

    override fun existsBySlackChannelIdAndStatusIn(
        slackChannelId: String,
        statuses: List<UserClippingRequestStatus>
    ): Boolean {
        if (statuses.isEmpty()) return false
        // 상태 문자열 리스트로 변환하여 JPA 파생 쿼리에 위임한다.
        return repository.existsBySlackChannelIdAndStatusIn(slackChannelId, statuses.map { it.name })
    }

    override fun saveWithFormEntries(request: UserClippingRequest, formEntries: String?): UserClippingRequest {
        val now = Instant.now()
        val saved = request.copy(
            id = request.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = now,
            updatedAt = now
        )
        // form_entries 는 entity 에 직접 설정한 뒤 저장한다.
        val entity = saved.toEntity()
        entity.formEntries = formEntries
        repository.save(entity)
        return saved
    }

    override fun findFormEntries(id: String): String? =
        repository.findById(id).orElse(null)?.formEntries

    override fun getStatsSnapshot(weeklyCutoff: Instant): UserClippingRequestStatsSnapshot {
        // 상태별 건수와 TOP N은 DB 집계로 계산해 listAll 안전 상한에 통계가 잘리지 않도록 한다.
        val statusCounts = countRequestsByStatus()
        return UserClippingRequestStatsSnapshot(
            pendingCount = statusCounts[UserClippingRequestStatus.PENDING.name] ?: 0,
            approvedCount = statusCounts[UserClippingRequestStatus.APPROVED.name] ?: 0,
            rejectedCount = statusCounts[UserClippingRequestStatus.REJECTED.name] ?: 0,
            totalCount = statusCounts.values.sum(),
            avgApprovalHours = averageApprovalLeadTimeHours(),
            topTopics = topCountRows(
                """
                    SELECT TRIM(request_name) AS name, COUNT(*) AS count
                    FROM clipping_user_requests
                    WHERE request_name IS NOT NULL AND TRIM(request_name) <> ''
                    GROUP BY TRIM(request_name)
                    ORDER BY count DESC, name ASC
                    LIMIT ?
                """.trimIndent()
            ),
            rejectionReasons = topCountRows(
                """
                    SELECT TRIM(review_note) AS name, COUNT(*) AS count
                    FROM clipping_user_requests
                    WHERE status = ? AND review_note IS NOT NULL AND TRIM(review_note) <> ''
                    GROUP BY TRIM(review_note)
                    ORDER BY count DESC, name ASC
                    LIMIT ?
                """.trimIndent(),
                UserClippingRequestStatus.REJECTED.name
            ),
            weeklyProcessedCount = countWeeklyProcessed(weeklyCutoff)
        )
    }

    // ── private helpers ──

    private fun countRequestsByStatus(): Map<String, Int> =
        jdbc.query(
            "SELECT status, COUNT(*) AS count FROM clipping_user_requests GROUP BY status"
        ) { rs, _ ->
            rs.getString("status") to rs.getInt("count")
        }.toMap()

    private fun averageApprovalLeadTimeHours(): Double? {
        val rows = jdbc.query(
            """
                SELECT created_at, reviewed_at
                FROM clipping_user_requests
                WHERE status = ? AND reviewed_at IS NOT NULL
            """.trimIndent(),
            { rs, _ ->
                val createdAt = rs.getTimestamp("created_at")?.toInstant() ?: return@query null
                val reviewedAt = rs.getTimestamp("reviewed_at")?.toInstant() ?: return@query null
                val leadTime = Duration.between(createdAt, reviewedAt)
                if (leadTime.isNegative) return@query null
                leadTime.toHours().toDouble()
            },
            UserClippingRequestStatus.APPROVED.name
        )
        val validRows = rows.mapNotNull { it }
        return validRows.takeIf { it.isNotEmpty() }
            ?.average()
            ?.let { "%.1f".format(it).toDouble() }
    }

    private fun topCountRows(sql: String, vararg params: Any): List<UserClippingRequestCountRow> =
        jdbc.query(sql, { rs, _ ->
            UserClippingRequestCountRow(
                name = rs.getString("name"),
                count = rs.getInt("count")
            )
        }, *params, STATS_TOP_LIMIT)

    private fun countWeeklyProcessed(weeklyCutoff: Instant): Int =
        jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM clipping_user_requests
                WHERE status IN (?, ?) AND reviewed_at > ?
            """.trimIndent(),
            Int::class.java,
            UserClippingRequestStatus.APPROVED.name,
            UserClippingRequestStatus.REJECTED.name,
            Timestamp.from(weeklyCutoff)
        ) ?: 0

    private fun UserClippingRequestEntity.toModel() = UserClippingRequest(
        id = id,
        requesterUserId = requesterUserId,
        requestName = requestName,
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        slackChannelId = slackChannelId,
        personaName = personaName,
        personaPrompt = personaPrompt,
        summaryStyle = summaryStyle,
        targetAudience = targetAudience,
        selectedPresetId = selectedPresetId,
        requestNote = requestNote,
        status = UserClippingRequestStatus.valueOf(status),
        reviewNote = reviewNote,
        reviewedByUserId = reviewedByUserId,
        reviewedAt = reviewedAt,
        approvedCategoryId = approvedCategoryId,
        approvedPersonaId = approvedPersonaId,
        approvedSourceId = approvedSourceId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun UserClippingRequest.toEntity() = UserClippingRequestEntity(
        id = id,
        requesterUserId = requesterUserId,
        requestName = requestName,
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        slackChannelId = slackChannelId,
        personaName = personaName,
        personaPrompt = personaPrompt,
        summaryStyle = summaryStyle,
        targetAudience = targetAudience,
        selectedPresetId = selectedPresetId,
        requestNote = requestNote,
        status = status.name,
        reviewNote = reviewNote,
        reviewedByUserId = reviewedByUserId,
        reviewedAt = reviewedAt,
        approvedCategoryId = approvedCategoryId,
        approvedPersonaId = approvedPersonaId,
        approvedSourceId = approvedSourceId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
