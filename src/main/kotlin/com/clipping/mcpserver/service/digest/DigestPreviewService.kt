package com.clipping.mcpserver.service.digest

import com.clipping.mcpserver.config.AppProperties
import com.clipping.mcpserver.service.OrganizationService
import com.clipping.mcpserver.service.digest.BadgedArticle
import com.clipping.mcpserver.service.digest.DigestArticle
import com.clipping.mcpserver.service.digest.DigestMode
import com.clipping.mcpserver.service.digest.DigestSectionResult
import com.clipping.mcpserver.service.digest.EscalationCopy
import com.clipping.mcpserver.service.digest.composeSections
import com.clipping.mcpserver.service.digest.matchesKeyword
import com.clipping.mcpserver.service.digest.matchesOrganization
import com.clipping.mcpserver.service.digest.resolveDigestMode
import com.clipping.mcpserver.service.digest.toDigestOrganizations
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryRuleStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 다이제스트 모드 미리보기 및 dry-run 렌더링을 담당하는 서비스.
 *
 * FE 위자드의 모드 전환 토스트(Task 23)와 E2E 다이제스트 검증(Task 25)에서 사용한다.
 * Slack 전송 없이 Block Kit JSON 을 반환하므로 사이드이펙트가 없다.
 */
@Service
class DigestPreviewService(
    private val categoryRuleStore: CategoryRuleStore,
    private val organizationService: OrganizationService,
    private val summaryStore: BatchSummaryStore,
    private val appProperties: AppProperties,
) {

    companion object {
        /** 미리보기 dry-run 기본 예산 (나중에 userDeliverySchedule 조회로 확장 가능) */
        private const val DEFAULT_PREVIEW_BUDGET = 5

        /** dry-run 에서 로드할 최근 기사 룩백 시간 (최근 7일) */
        private const val PREVIEW_LOOKBACK_HOURS = 168L

        /** dry-run 에서 로드할 최대 기사 수 */
        private const val PREVIEW_ARTICLE_LIMIT = 50
    }

    /**
     * 카테고리의 현재 다이제스트 모드 스냅샷을 반환한다.
     *
     * @param currentMode resolveDigestMode 결과 이름. (0,0) 이면 null.
     * @param keywordCount 카테고리에 설정된 포함 키워드 수.
     * @param orgCount 카테고리에 연결된 조직 수.
     */
    data class DigestModeSnapshot(
        val currentMode: String?,
        val keywordCount: Int,
        val orgCount: Int,
    )

    /**
     * 한 섹션의 상태 스냅샷. shadow-mode 비교용 진단 데이터 (admin diff 페이지에서 사용).
     *
     * @param kind 섹션 종류 (topic / account / cross …).
     * @param articlesCount 해당 섹션에 배치된 기사 수.
     * @param badgedCount crossMatch ⭐ 배지가 붙은 기사 수 (DUAL_SECTION topic 섹션에서만 의미).
     * @param isEmpty 기사 없음 (escalator 가 empty copy 로 대체된 상태).
     */
    data class SectionStateSnapshot(
        val kind: String,
        val articlesCount: Int,
        val badgedCount: Int,
        val isEmpty: Boolean,
    )

    /**
     * dry-run 결과. mode, Block Kit JSON, 섹션 상태 스냅샷.
     *
     * Controller 응답 DTO 는 `mode + blocks` 만 노출해 기존 API 를 건드리지 않는다 (하위 호환);
     * `sectionState` 는 shadow-mode 비교/진단을 위해 내부 호출자(DigestService 등)에서만 사용한다.
     *
     * @param mode 다이제스트 모드 이름 (TOPIC_ONLY / ACCOUNT_ONLY / CROSSFILTER / DUAL_SECTION / EMPTY).
     * @param blocks Block Kit JSON 배열 문자열. FE 가 parse 해서 Block Kit 으로 사용.
     * @param sectionState 각 섹션별 상태 스냅샷 — 순서는 composeSections 결과 순서와 동일.
     */
    data class DigestDryRunResult(
        val mode: String,
        val blocks: String,
        val sectionState: List<SectionStateSnapshot> = emptyList(),
    )

    /**
     * 카테고리의 현재 키워드/조직 수와 산출되는 digest mode 를 반환한다.
     *
     * (0, 0) 인 경우 currentMode = null 로 반환하며 예외를 던지지 않는다.
     *
     * @param categoryId 대상 카테고리 ID
     */
    fun previewModeForCategory(categoryId: String): DigestModeSnapshot {
        // 카테고리별 포함 키워드 목록을 조회한다
        val keywords = categoryRuleStore.findIncludeKeywords(categoryId)

        // 카테고리에 연결된 조직 목록을 조회한다
        val orgs = runCatching { organizationService.findByCategoryId(categoryId) }.getOrElse { emptyList() }

        // (0,0) 케이스는 IllegalStateException 을 null mode 로 흡수해 안전하게 반환한다
        val mode = runCatching { resolveDigestMode(keywords.size, orgs.size).name }.getOrNull()

        return DigestModeSnapshot(
            currentMode = mode,
            keywordCount = keywords.size,
            orgCount = orgs.size,
        )
    }

    /**
     * 최근 기사를 로드해 AccountBased Block Kit 렌더링 결과를 반환한다. Slack 미발송.
     *
     * 키워드/조직이 모두 0 인 경우 mode="EMPTY", blocks="[]" 를 반환한다.
     *
     * @param categoryId 대상 카테고리 ID
     */
    fun dryRunForCategory(categoryId: String): DigestDryRunResult {
        val keywords = categoryRuleStore.findIncludeKeywords(categoryId)
        val orgs = runCatching { organizationService.findByCategoryId(categoryId) }.getOrElse { emptyList() }

        // 설정 미완료 — (0,0) 인 경우 빈 결과를 반환한다
        if (keywords.isEmpty() && orgs.isEmpty()) {
            return DigestDryRunResult(mode = "EMPTY", blocks = "[]")
        }

        val mode = resolveDigestMode(keywords.size, orgs.size)
        val digestOrgs = orgs.toDigestOrganizations()

        // 최근 기사를 조회해 매칭 여부를 계산한다
        val articles = loadRecentArticlesForPreview(categoryId)
        val digestArticles = articles.map { summary ->
            val text = "${summary.originalTitle} ${summary.summary}"
            DigestArticle(
                id = summary.id,
                title = summary.originalTitle,
                summary = summary.summary,
                matchesKeyword = keywords.any { kw -> matchesKeyword(text, kw) },
                matchesOrganization = digestOrgs.any { org -> matchesOrganization(text, org) },
            )
        }

        // 섹션 구성은 top-level composeSections 를 직접 호출한다
        val sections = composeSections(mode, digestArticles, DEFAULT_PREVIEW_BUDGET)

        val renderer = DigestRenderer(appProperties)
        val blocks = renderer.renderAccountBasedDigest(
            sections = sections,
            mode = mode,
            keywords = keywords,
            orgs = orgs,
            dualLegendShown = false,
            emptyCopies = defaultEmptyCopies(),
        )

        // 섹션별 상태 스냅샷 — shadow-mode 비교 및 admin diff 페이지에서 활용
        val sectionState = sections.map { section ->
            SectionStateSnapshot(
                kind = section.kind,
                articlesCount = section.articles.size,
                badgedCount = section.articles.count { it.badged },
                isEmpty = section.articles.isEmpty(),
            )
        }

        return DigestDryRunResult(mode = mode.name, blocks = blocks, sectionState = sectionState)
    }

    /**
     * 최근 PREVIEW_LOOKBACK_HOURS 시간 내 기사를 PREVIEW_ARTICLE_LIMIT 건 조회한다.
     * 없거나 오류 시 빈 리스트를 반환한다.
     */
    private fun loadRecentArticlesForPreview(categoryId: String) =
        runCatching {
            val since = Instant.now().minus(PREVIEW_LOOKBACK_HOURS, ChronoUnit.HOURS)
            summaryStore.findByDateRange(
                from = since,
                to = Instant.now(),
                categoryId = categoryId,
                limit = PREVIEW_ARTICLE_LIMIT,
            )
        }.getOrElse { emptyList() }

    private fun defaultEmptyCopies(): Map<String, EscalationCopy> = mapOf(
        "topic" to EscalationCopy(text = "오늘 주제 관련 뉴스는 없었어요"),
        "account" to EscalationCopy(text = "오늘 기업 관련 뉴스는 없었어요"),
        "cross" to EscalationCopy(text = "오늘 관련 뉴스는 없었어요"),
    )
}
