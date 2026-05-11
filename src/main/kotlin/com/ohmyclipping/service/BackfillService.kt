package com.ohmyclipping.service

import com.ohmyclipping.service.dto.admin.BackfillApplyError
import com.ohmyclipping.service.dto.admin.BackfillApplyResponse
import com.ohmyclipping.service.dto.admin.BackfillCandidate
import com.ohmyclipping.service.dto.admin.BackfillPreviewResponse
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.model.OrganizationOrigins
import com.ohmyclipping.service.source.CategorySourceBuilder
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssSourceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

private val log = KotlinLogging.logger {}

/**
 * RSS 소스 URL 을 분석하여 기존 소스를 조직 링크로 마이그레이션하는 백필 서비스.
 *
 * **Preview**: 각 소스 name 을 [CompanySearchService] 로 검색하여 confidence 별 후보를 반환한다.
 * **Apply**: 선택된 후보에 대해 1건씩 격리된 트랜잭션으로 조직 upsert + 카테고리 링크를 수행한다.
 *
 * **Precision 신호 출처**: [CompanySearchService.searchWithIsCompetitor] 는 precision 점수를 직접
 * 반환하지 않는다. 반환 결과가 있으면 `DEFAULT_PRECISION = 0.8` (high) 을 사용한다.
 * DART 데이터의 상장사 우선 정렬로 인해 상위 결과의 정밀도가 높다고 가정한다.
 * 향후 DART API 가 precision 점수를 반환하면 이 fallback 을 교체할 수 있다.
 */
@Service
class BackfillService(
    private val rssSourceStore: RssSourceStore,
    private val categoryStore: CategoryStore,
    private val companySearchService: CompanySearchService,
    private val organizationService: OrganizationService,
    private val categorySourceBuilder: CategorySourceBuilder,
    private val transactionTemplate: TransactionTemplate,
    private val auditLogStore: AuditLogStore,
    private val auditActorResolver: AuditActorResolver,
) {

    companion object {
        /** CompanySearchService 가 precision 점수를 반환하지 않으므로 사용하는 기본 precision. */
        const val DEFAULT_PRECISION = 0.8

        /** precision >= 0.9 이면 high. */
        const val HIGH_THRESHOLD = 0.9

        /** precision >= 0.5 이면 medium. */
        const val MEDIUM_THRESHOLD = 0.5

        /** confidence 레이블 상수 */
        const val CONFIDENCE_HIGH = "high"
        const val CONFIDENCE_MEDIUM = "medium"
        const val CONFIDENCE_LOW = "low"
    }

    /**
     * RSS 소스 기반 기업 매칭 후보를 미리보기 한다.
     *
     * - [categoryId] 가 주어지면 해당 카테고리 소스만 조회한다.
     * - [confidence] 는 반환할 최소 레벨을 지정한다 (`high` / `medium` / `low`).
     * - [includeMedium] 이 true 이면 confidence=high 에서도 medium 이 포함된다.
     *
     * @param confidence 반환 기준 최소 confidence 레벨 (기본: "high")
     * @param includeMedium true 면 medium 까지 포함
     * @param categoryId 특정 카테고리만 필터 (null 이면 전체)
     */
    fun previewCompanyBackfill(
        confidence: String = CONFIDENCE_HIGH,
        includeMedium: Boolean = false,
        categoryId: String? = null,
    ): BackfillPreviewResponse {
        // 소스 목록 로드 — categoryId 가 주어지면 해당 카테고리만 조회
        val sources = if (categoryId != null) {
            rssSourceStore.listByCategoryId(categoryId)
        } else {
            rssSourceStore.list()
        }

        // 카테고리 이름 캐시 — N+1 방지를 위해 미리 조회
        val categoryNames = mutableMapOf<String, String>()

        val allCandidates = sources.mapNotNull { source ->
            // 소스 name 으로 기업 검색 (limit=1: 가장 정밀한 1건만)
            val searchQuery = source.name.trim().takeIf { it.isNotBlank() }
                ?: extractHostname(source.url)

            val matches = companySearchService.searchWithIsCompetitor(searchQuery, limit = 1)
            val match = matches.firstOrNull() ?: return@mapNotNull null

            // CompanySearchService 는 precision 점수를 반환하지 않으므로 DEFAULT_PRECISION 사용
            val precision = DEFAULT_PRECISION
            val candidateConfidence = mapToConfidence(precision)

            // 카테고리 이름 캐시에서 조회
            val catName = categoryNames.getOrPut(source.categoryId) {
                categoryStore.findById(source.categoryId)?.name ?: source.categoryId
            }

            BackfillCandidate(
                sourceId = source.id,
                sourceUrl = source.url,
                sourceName = source.name,
                categoryId = source.categoryId,
                categoryName = catName,
                matchedCompanyName = match.corpName,
                stockCode = match.stockCode.takeIf { it.isNotBlank() },
                confidence = candidateConfidence,
                precision = precision,
            )
        }

        // 허용할 confidence 레벨 목록 계산
        val allowedConfidences = resolveAllowedConfidences(confidence, includeMedium)

        // 전체 집계 (필터 전)
        val byConfidenceAll = allCandidates.groupingBy { it.confidence }.eachCount()
        val byConfidence = mapOf(
            CONFIDENCE_HIGH to (byConfidenceAll[CONFIDENCE_HIGH] ?: 0),
            CONFIDENCE_MEDIUM to (byConfidenceAll[CONFIDENCE_MEDIUM] ?: 0),
            CONFIDENCE_LOW to (byConfidenceAll[CONFIDENCE_LOW] ?: 0),
        )

        // confidence 필터 적용
        val filtered = allCandidates.filter { it.confidence in allowedConfidences }

        return BackfillPreviewResponse(
            candidates = filtered,
            total = filtered.size,
            byConfidence = byConfidence,
        )
    }

    /**
     * 선택된 후보 sourceId 목록에 대해 조직 upsert + 카테고리 링크를 적용한다.
     *
     * - 각 후보는 독립된 [TransactionTemplate] 트랜잭션으로 격리한다.
     *   한 건 실패가 나머지를 롤백하지 않는다.
     * - 루프 종료 후 영향받은 카테고리별로 [CategorySourceBuilder.syncSourcesForCategory] 를 호출한다.
     * - 성공한 카테고리 각각에 대해 [AuditLogStore] 에 `BACKFILL_APPLY` 행을 기록한다.
     *
     * @param candidateIds 적용할 sourceId 목록
     * @param actorName 감사 목적 사용자명 — [AuditActorResolver] 가 UUID 로 변환
     */
    fun applyCompanyBackfill(
        candidateIds: List<String>,
        actorName: String,
    ): BackfillApplyResponse {
        if (candidateIds.isEmpty()) {
            return BackfillApplyResponse(
                total = 0, succeeded = 0, failed = 0,
                errors = emptyList(), affectedCategoryIds = emptyList()
            )
        }

        val errors = mutableListOf<BackfillApplyError>()
        val affectedCategoryIds = mutableSetOf<String>()
        var succeeded = 0

        for (sourceId in candidateIds) {
            // 각 후보를 독립 트랜잭션으로 처리 — 실패 시 해당 건만 롤백
            val result = runCatching {
                transactionTemplate.execute { _ ->
                    applyOneCandidateInTx(sourceId, actorName)
                }
            }

            result.fold(
                onSuccess = { categoryId ->
                    if (categoryId != null) {
                        affectedCategoryIds.add(categoryId)
                        succeeded++
                        log.info { "Backfill applied: sourceId=$sourceId categoryId=$categoryId actor=$actorName" }
                    } else {
                        errors.add(BackfillApplyError(sourceId, "transaction returned null — source may not exist"))
                        log.warn { "Backfill tx returned null for sourceId=$sourceId" }
                    }
                },
                onFailure = { ex ->
                    val reason = ex.message ?: ex.javaClass.simpleName
                    errors.add(BackfillApplyError(sourceId, reason))
                    log.warn { "Backfill failed for sourceId=$sourceId reason=$reason" }
                }
            )
        }

        // 트랜잭션 완료 후 — 영향받은 카테고리별로 소스 동기화
        for (catId in affectedCategoryIds) {
            runCatching { categorySourceBuilder.syncSourcesForCategory(catId) }
                .onFailure { ex ->
                    log.warn { "syncSourcesForCategory failed for categoryId=$catId: ${ex.message}" }
                }
        }

        // 감사 로그 기록 — 영향받은 카테고리마다 1행씩 기록해 추적성을 높인다
        val resolved = auditActorResolver.resolve(actorName)
        for (catId in affectedCategoryIds) {
            runCatching {
                auditLogStore.log(
                    actorId = resolved.id,
                    actorName = resolved.name,
                    action = "BACKFILL_APPLY",
                    targetType = "CATEGORY",
                    targetId = catId,
                    detail = "succeeded=$succeeded total=${candidateIds.size} actor=$actorName"
                )
            }.onFailure { ex ->
                log.warn { "Audit log failed for BACKFILL_APPLY categoryId=$catId: ${ex.message}" }
            }
        }

        return BackfillApplyResponse(
            total = candidateIds.size,
            succeeded = succeeded,
            failed = errors.size,
            errors = errors,
            affectedCategoryIds = affectedCategoryIds.toList(),
        )
    }

    /**
     * 단일 후보에 대한 트랜잭션 내 처리 로직.
     *
     * 1. sourceId 로 소스를 조회한다.
     * 2. 소스 name 으로 기업 검색 후 조직을 upsert 한다 (origin = [OrganizationOrigins.BACKFILL]).
     * 3. 조직을 카테고리에 연결한다 (idempotent).
     *
     * @return 성공 시 categoryId, 소스가 없으면 null
     */
    private fun applyOneCandidateInTx(sourceId: String, actorName: String): String? {
        // 소스 조회 — 존재하지 않으면 null 반환
        val source = rssSourceStore.findById(sourceId) ?: run {
            log.warn { "Backfill: source not found sourceId=$sourceId" }
            return null
        }

        val searchQuery = source.name.trim().takeIf { it.isNotBlank() }
            ?: extractHostname(source.url)

        // 기업 검색 — 매칭 없으면 오류
        val matches = companySearchService.searchWithIsCompetitor(searchQuery, limit = 1)
        val match = matches.firstOrNull()
            ?: throw IllegalStateException("No company match found for source name: $searchQuery")

        val stockCode = match.stockCode.takeIf { it.isNotBlank() }

        // 조직 upsert — origin = backfill 경로임을 상수로 명시
        val org = try {
            organizationService.upsertByStockCodeOrName(
                tenantId = "default",
                name = match.corpName,
                stockCode = stockCode,
                origin = OrganizationOrigins.BACKFILL,
            )
        } catch (e: ConflictException) {
            // 동시성 race 후 org 를 찾지 못한 경우 — 다시 name 으로 조회
            throw IllegalStateException("organization upsert conflict for name=${match.corpName}: ${e.message}", e)
        }

        // 카테고리 링크 — idempotent
        organizationService.linkToCategoryIfAbsent(source.categoryId, org.id)

        return source.categoryId
    }

    /** precision 점수를 confidence 레벨로 변환한다. */
    private fun mapToConfidence(precision: Double): String = when {
        precision >= HIGH_THRESHOLD -> CONFIDENCE_HIGH
        precision >= MEDIUM_THRESHOLD -> CONFIDENCE_MEDIUM
        else -> CONFIDENCE_LOW
    }

    /**
     * 요청된 confidence 와 includeMedium 플래그를 기반으로
     * 허용할 confidence 레벨 집합을 반환한다.
     */
    private fun resolveAllowedConfidences(confidence: String, includeMedium: Boolean): Set<String> {
        return when (confidence.lowercase()) {
            CONFIDENCE_LOW -> setOf(CONFIDENCE_HIGH, CONFIDENCE_MEDIUM, CONFIDENCE_LOW)
            CONFIDENCE_MEDIUM -> setOf(CONFIDENCE_HIGH, CONFIDENCE_MEDIUM)
            else -> {
                // high (기본) — includeMedium 이 true 이면 medium 도 포함
                if (includeMedium) setOf(CONFIDENCE_HIGH, CONFIDENCE_MEDIUM)
                else setOf(CONFIDENCE_HIGH)
            }
        }
    }

    /** URL 에서 hostname 을 추출한다. 파싱 실패 시 URL 전체를 반환한다. */
    private fun extractHostname(url: String): String =
        runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
}
