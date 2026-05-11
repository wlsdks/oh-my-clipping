package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.service.source.CategorySourceBuilder
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryFeatureFlagStore
import com.ohmyclipping.store.CategoryRuleStore
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * CategoryRuleBundleService 단위 테스트.
 *
 * 협력자는 MockK 로 모킹하고, 서비스 오케스트레이션 순서 및
 * best-effort 핸들링을 검증한다.
 */
class CategoryRuleBundleServiceTest {

    private val categoryRuleStore = mockk<CategoryRuleStore>()
    private val organizationService = mockk<OrganizationService>()
    private val categoryFeatureFlagStore = mockk<CategoryFeatureFlagStore>()
    private val categorySourceBuilder = mockk<CategorySourceBuilder>()
    private val auditLogStore = mockk<AuditLogStore>()
    private val auditActorResolver = mockk<AuditActorResolver>()

    private val service = CategoryRuleBundleService(
        categoryRuleStore = categoryRuleStore,
        organizationService = organizationService,
        categoryFeatureFlagStore = categoryFeatureFlagStore,
        categorySourceBuilder = categorySourceBuilder,
        auditLogStore = auditLogStore,
        auditActorResolver = auditActorResolver,
    )

    private val resolvedActor = ResolvedActor(id = "actor-uuid", name = "테스트 관리자")

    @Nested
    inner class `해피패스 - 정상 업데이트` {

        @Test
        fun `모든 협력자가 정확히 한 번 호출되고 감사 로그가 기록된다`() {
            // given
            val categoryId = "cat-001"
            val actor = "admin@example.com"
            val includeKeywords = listOf("AI", "RAG")
            val excludeEventTypes = listOf("OTHER")
            val organizationIds = listOf("org-1", "org-2")

            every { categoryRuleStore.setKeywordsAndExcludeEventTypes(categoryId, includeKeywords, excludeEventTypes) } just Runs
            every { organizationService.setCategoryOrganizations(categoryId, organizationIds) } just Runs
            every { categoryFeatureFlagStore.setAccountBasedDigestEnabled(categoryId, true) } just Runs
            every { categoryFeatureFlagStore.setShadowModeEnabled(categoryId, false) } just Runs
            every { categorySourceBuilder.syncSourcesForCategory(categoryId) } just Runs
            every { auditActorResolver.resolve(actor) } returns resolvedActor
            every {
                auditLogStore.log(
                    actorId = resolvedActor.id,
                    actorName = resolvedActor.name,
                    action = "RULE_BUNDLE_UPDATE",
                    targetType = "CATEGORY",
                    targetId = categoryId,
                )
            } just Runs

            // when
            service.updateRuleBundle(
                categoryId = categoryId,
                excludeEventTypes = excludeEventTypes,
                includeKeywords = includeKeywords,
                organizationIds = organizationIds,
                accountBasedDigestEnabled = true,
                shadowModeEnabled = false,
                actor = actor,
            )

            // then — 각 협력자가 정확히 1회 호출되었는지 검증
            verify(exactly = 1) { categoryRuleStore.setKeywordsAndExcludeEventTypes(categoryId, includeKeywords, excludeEventTypes) }
            verify(exactly = 1) { organizationService.setCategoryOrganizations(categoryId, organizationIds) }
            verify(exactly = 1) { categoryFeatureFlagStore.setAccountBasedDigestEnabled(categoryId, true) }
            verify(exactly = 1) { categoryFeatureFlagStore.setShadowModeEnabled(categoryId, false) }
            verify(exactly = 1) { categorySourceBuilder.syncSourcesForCategory(categoryId) }
            verify(exactly = 1) { auditActorResolver.resolve(actor) }
            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = resolvedActor.id,
                    actorName = resolvedActor.name,
                    action = "RULE_BUNDLE_UPDATE",
                    targetType = "CATEGORY",
                    targetId = categoryId,
                )
            }
        }

        @Test
        fun `AuditActorResolver 결과가 auditLogStore 에 actorId 와 actorName 으로 전달된다`() {
            // given
            val categoryId = "cat-002"
            val actor = "another-admin"
            val resolvedWithNull = ResolvedActor(id = null, name = "another-admin")

            every { categoryRuleStore.setKeywordsAndExcludeEventTypes(categoryId, emptyList(), emptyList()) } just Runs
            every { organizationService.setCategoryOrganizations(categoryId, emptyList()) } just Runs
            every { categoryFeatureFlagStore.setAccountBasedDigestEnabled(categoryId, false) } just Runs
            every { categoryFeatureFlagStore.setShadowModeEnabled(categoryId, false) } just Runs
            every { categorySourceBuilder.syncSourcesForCategory(categoryId) } just Runs
            every { auditActorResolver.resolve(actor) } returns resolvedWithNull
            every {
                auditLogStore.log(
                    actorId = null,
                    actorName = "another-admin",
                    action = "RULE_BUNDLE_UPDATE",
                    targetType = "CATEGORY",
                    targetId = categoryId,
                )
            } just Runs

            // when
            service.updateRuleBundle(
                categoryId = categoryId,
                excludeEventTypes = emptyList(),
                includeKeywords = emptyList(),
                organizationIds = emptyList(),
                accountBasedDigestEnabled = false,
                shadowModeEnabled = false,
                actor = actor,
            )

            // then — resolver 결과(null id)가 그대로 auditLogStore 에 전달되어야 한다
            verify(exactly = 1) { auditActorResolver.resolve(actor) }
            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = null,
                    actorName = "another-admin",
                    action = "RULE_BUNDLE_UPDATE",
                    targetType = "CATEGORY",
                    targetId = categoryId,
                )
            }
        }
    }

    @Nested
    inner class `부분 실패 롤백` {

        @Test
        fun `organizationService 가 예외를 던지면 메서드가 예외를 전파한다`() {
            // given — 프로덕션에서 OrganizationService.setCategoryOrganizations 는
            // 존재하지 않는 org id 가 섞이면 InvalidInputException 을 던진다.
            val categoryId = "cat-err"
            val actor = "admin"

            every { categoryRuleStore.setKeywordsAndExcludeEventTypes(categoryId, emptyList(), emptyList()) } just Runs
            every { organizationService.setCategoryOrganizations(categoryId, listOf("bad-org")) } throws
                InvalidInputException("Organization(s) not found: bad-org")
            every { auditActorResolver.resolve(actor) } returns resolvedActor

            // when / then — 예외가 전파되어야 한다
            val ex = assertThrows<InvalidInputException> {
                service.updateRuleBundle(
                    categoryId = categoryId,
                    excludeEventTypes = emptyList(),
                    includeKeywords = emptyList(),
                    organizationIds = listOf("bad-org"),
                    accountBasedDigestEnabled = false,
                    shadowModeEnabled = false,
                    actor = actor,
                )
            }
            ex.message shouldBe "Organization(s) not found: bad-org"

            // ruleStore 는 이미 호출됐지만 audit 은 기록되지 않는다
            verify(exactly = 1) { categoryRuleStore.setKeywordsAndExcludeEventTypes(categoryId, emptyList(), emptyList()) }
            verify(exactly = 0) { auditLogStore.log(any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `shadow mode wiring` {

        @Test
        fun `shadowModeEnabled=true 이면 setShadowModeEnabled 가 true 로 호출된다`() {
            // given — D2 완료: setShadowModeEnabled 가 실제로 호출되어야 한다.
            val categoryId = "cat-shadow"
            val actor = "admin"

            every { categoryRuleStore.setKeywordsAndExcludeEventTypes(categoryId, emptyList(), emptyList()) } just Runs
            every { organizationService.setCategoryOrganizations(categoryId, emptyList()) } just Runs
            every { categoryFeatureFlagStore.setAccountBasedDigestEnabled(categoryId, false) } just Runs
            every { categoryFeatureFlagStore.setShadowModeEnabled(categoryId, true) } just Runs
            every { categorySourceBuilder.syncSourcesForCategory(categoryId) } just Runs
            every { auditActorResolver.resolve(actor) } returns resolvedActor
            every {
                auditLogStore.log(
                    actorId = resolvedActor.id,
                    actorName = resolvedActor.name,
                    action = "RULE_BUNDLE_UPDATE",
                    targetType = "CATEGORY",
                    targetId = categoryId,
                )
            } just Runs

            // when
            service.updateRuleBundle(
                categoryId = categoryId,
                excludeEventTypes = emptyList(),
                includeKeywords = emptyList(),
                organizationIds = emptyList(),
                accountBasedDigestEnabled = false,
                shadowModeEnabled = true,
                actor = actor,
            )

            // then — setShadowModeEnabled 가 true 로 정확히 1번 호출되어야 한다
            verify(exactly = 1) { categoryFeatureFlagStore.setShadowModeEnabled(categoryId, true) }
            verify(exactly = 1) { categorySourceBuilder.syncSourcesForCategory(categoryId) }
            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = resolvedActor.id,
                    actorName = resolvedActor.name,
                    action = "RULE_BUNDLE_UPDATE",
                    targetType = "CATEGORY",
                    targetId = categoryId,
                )
            }
        }

        @Test
        fun `shadowModeEnabled=false 이면 setShadowModeEnabled 가 false 로 호출된다`() {
            // given
            val categoryId = "cat-shadow-off"
            val actor = "admin"

            every { categoryRuleStore.setKeywordsAndExcludeEventTypes(categoryId, emptyList(), emptyList()) } just Runs
            every { organizationService.setCategoryOrganizations(categoryId, emptyList()) } just Runs
            every { categoryFeatureFlagStore.setAccountBasedDigestEnabled(categoryId, false) } just Runs
            every { categoryFeatureFlagStore.setShadowModeEnabled(categoryId, false) } just Runs
            every { categorySourceBuilder.syncSourcesForCategory(categoryId) } just Runs
            every { auditActorResolver.resolve(actor) } returns resolvedActor
            every {
                auditLogStore.log(
                    actorId = resolvedActor.id,
                    actorName = resolvedActor.name,
                    action = "RULE_BUNDLE_UPDATE",
                    targetType = "CATEGORY",
                    targetId = categoryId,
                )
            } just Runs

            // when
            service.updateRuleBundle(
                categoryId = categoryId,
                excludeEventTypes = emptyList(),
                includeKeywords = emptyList(),
                organizationIds = emptyList(),
                accountBasedDigestEnabled = false,
                shadowModeEnabled = false,
                actor = actor,
            )

            // then — setShadowModeEnabled 가 false 로 정확히 1번 호출되어야 한다
            verify(exactly = 1) { categoryFeatureFlagStore.setShadowModeEnabled(categoryId, false) }
        }
    }
}
