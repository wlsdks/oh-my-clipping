package com.ohmyclipping.service

import com.ohmyclipping.service.source.CategorySourceBuilder
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryFeatureFlagStore
import com.ohmyclipping.store.CategoryRuleStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

/**
 * 카테고리 룰 + 조직 링크 + feature flag 를 단일 트랜잭션으로 원자적으로 저장한다.
 *
 * PUT /api/admin/categories/{categoryId}/rule-bundle 의 오케스트레이터.
 * 실패 시 전체 롤백되어 카테고리가 불일치 상태에 빠지지 않는다.
 *
 * 설계 배경: docs/ADR.md ADR-032 참고.
 */
@Service
class CategoryRuleBundleService(
    private val categoryRuleStore: CategoryRuleStore,
    private val organizationService: OrganizationService,
    private val categoryFeatureFlagStore: CategoryFeatureFlagStore,
    private val categorySourceBuilder: CategorySourceBuilder,
    private val auditLogStore: AuditLogStore,
    private val auditActorResolver: AuditActorResolver,
) {

    /**
     * 아래 6개 부수효과를 단일 @Transactional 에서 순서대로 실행한다:
     * 1. 카테고리 룰의 includeKeywords + excludeEventTypes 업데이트
     * 2. 카테고리 ↔ 조직 링크 교체 (setCategoryOrganizations)
     * 3. accountBasedDigestEnabled 플래그 upsert
     * 4. shadowModeEnabled 플래그 upsert (CategoryFeatureFlagStore.setShadowModeEnabled)
     * 5. 소스 목록 재동기화 (syncSourcesForCategory)
     * 6. 감사 로그 기록
     *
     * 실패 시 전체 롤백 — 4개 플래그/룰 + 소스 동기화가 모두 성공해야 커밋된다.
     *
     * @param categoryId 대상 카테고리 ID
     * @param excludeEventTypes 자동 제외 event_type 블랙리스트
     * @param includeKeywords 포함 키워드 목록
     * @param organizationIds 연결할 조직 ID 목록 (빈 리스트면 전체 링크 해제)
     * @param accountBasedDigestEnabled 계정 기반 다이제스트 활성화 여부
     * @param shadowModeEnabled 섀도 모드 활성화 여부 — true 이면 Slack 미발송 diff 기록 경로로 분기
     * @param actor Spring Security principal — AuditActorResolver 가 UUID 로 변환
     */
    @Transactional
    fun updateRuleBundle(
        categoryId: String,
        excludeEventTypes: List<String>,
        includeKeywords: List<String>,
        organizationIds: List<String>,
        accountBasedDigestEnabled: Boolean,
        shadowModeEnabled: Boolean,
        actor: String,
    ) {
        // 1) 카테고리 룰: includeKeywords + excludeEventTypes 동시 교체
        categoryRuleStore.setKeywordsAndExcludeEventTypes(categoryId, includeKeywords, excludeEventTypes)

        // 2) 카테고리 ↔ 조직 링크 완전 교체
        organizationService.setCategoryOrganizations(categoryId, organizationIds)

        // 3) account-based digest feature flag upsert
        categoryFeatureFlagStore.setAccountBasedDigestEnabled(categoryId, accountBasedDigestEnabled)

        // 4) shadow mode 플래그 upsert — setShadowModeEnabled 는 V137 에서 추가됨 (D2 완료)
        categoryFeatureFlagStore.setShadowModeEnabled(categoryId, shadowModeEnabled)

        // 5) 소스 목록 재동기화 — self-proxy 경유로 REQUIRED 전파(outer TX 합류).
        // 주의: lock 은 inner TX 완료 시 즉시 해제되므로, 다른 스레드의 sync 호출은
        // 이 TX 의 커밋 완전 전에 시작될 수 있다. 이는 CategorySourceBuilder 의 설계 제약.
        categorySourceBuilder.syncSourcesForCategory(categoryId)

        // 6) 감사 로그 — AuditActorResolver 를 통해 UUID(admin_users.id) 로 정규화
        val resolved = auditActorResolver.resolve(actor)
        auditLogStore.log(
            actorId = resolved.id,
            actorName = resolved.name,
            action = "RULE_BUNDLE_UPDATE",
            targetType = "CATEGORY",
            targetId = categoryId,
        )
    }
}
