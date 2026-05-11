package com.ohmyclipping.integration

import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.service.OrganizationService
import com.ohmyclipping.service.UserClippingRequestService
import com.ohmyclipping.service.dto.EntryDto
import com.ohmyclipping.service.dto.SubmitWithEntriesRequest
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.CategoryRuleStore
import com.ohmyclipping.store.RssSourceStore
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * 위자드 submit → approve 전체 흐름 통합 테스트.
 *
 * 검증 범위:
 * - submit 시 form_entries 가 DB 에 저장된다.
 * - approve 시 category + include_keywords + organizations + auto_generated sources 가 모두 생성된다.
 * - legacy(form_entries NULL) 경로는 기존 approveRequest(cmd) 로 여전히 처리된다 (별도 단위 테스트에서 커버).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubmitApproveEndToEndTest(
    @Autowired private val service: UserClippingRequestService,
    @Autowired private val orgService: OrganizationService,
    @Autowired private val ruleStore: CategoryRuleStore,
    @Autowired private val rssSourceStore: RssSourceStore,
    @Autowired private val adminUserStore: AdminUserStore
) {

    /**
     * 테스트용 사용자를 생성하여 FK 제약을 만족한다.
     * @Transactional 로 롤백되므로 cleanup 불필요.
     */
    private fun createTestUser(suffix: Long): AdminUser =
        adminUserStore.save(
            AdminUser(
                id = "",
                username = "wizard-test-$suffix@test.io",
                passwordHash = "hashed",
                role = AccountRole.USER
            )
        )

    @Test
    fun `submit 후 approve 하면 Category, Rule include_keywords, Organizations, AutoSources 가 모두 생성된다`() {
        val uniqueSuffix = System.currentTimeMillis()
        val categoryName = "E2E-AB-테스트-$uniqueSuffix"
        // requester_user_id FK 제약을 만족하기 위해 테스트 사용자를 미리 생성한다.
        val testUser = createTestUser(uniqueSuffix)

        // 1. 위자드 submit — keyword 1개 + company 2개
        val submitResponse = service.submitRequestWithEntries(
            SubmitWithEntriesRequest(
                categoryName = categoryName,
                entries = listOf(
                    EntryDto("리스킬링", "keyword"),
                    EntryDto("MegaCorp-$uniqueSuffix", "company", null),
                    EntryDto("ConglomerateCo-$uniqueSuffix", "company", null)
                )
            ),
            username = testUser.username
        )
        submitResponse.status shouldBe "submitted"
        val requestId = submitResponse.requestId
        requestId.shouldNotBeNull()

        // 2. approve — entries 를 파싱해서 category, rule, orgs, sources 를 생성한다.
        val approval = service.approveRequestWithEntries(
            requestId = requestId,
            approverUsername = "admin"
        )
        val categoryId = approval.createdCategoryId
        categoryId.shouldNotBeNull()

        // 3. include_keywords 가 저장됐는지 확인한다.
        val keywords = ruleStore.findIncludeKeywords(categoryId)
        keywords shouldContain "리스킬링"

        // 4. organizations 가 upsert 되고 category 에 링크됐는지 확인한다.
        val orgs = orgService.findByCategoryId(categoryId)
        orgs shouldHaveSize 2
        orgs.map { it.name }.toSet() shouldBe setOf(
            "MegaCorp-$uniqueSuffix",
            "ConglomerateCo-$uniqueSuffix"
        )

        // 5. CategorySourceBuilder 가 auto_generated sources 를 생성했는지 확인한다.
        // CROSSFILTER(org × kw) = 2 combos → 2 auto_generated sources.
        val autoSources = rssSourceStore.findByCategoryIdAndOrigin(categoryId, "auto_generated")
        autoSources.size shouldBe 2

        // 6. 위자드 승인은 소스 승인까지 포함 — 생성된 sources 는 crawl_approved + VERIFIED 로 표시돼
        //    CollectionService 다음 사이클에 바로 수집 대상이 되어야 한다.
        autoSources.forEach { src ->
            src.crawlApproved shouldBe true
            src.verificationStatus shouldBe "VERIFIED"
        }
    }

    @Test
    fun `form_entries 가 없는 요청에 approveRequestWithEntries 호출 시 NotFoundException 발생`() {
        // form_entries NULL 요청은 레거시 approveRequest(cmd) 경로로 처리해야 한다.
        // 이 경로에서 approveRequestWithEntries 를 호출하면 명확한 오류를 반환해야 한다.
        io.kotest.assertions.throwables.shouldThrow<com.ohmyclipping.error.NotFoundException> {
            service.approveRequestWithEntries(
                requestId = "non-existent-id",
                approverUsername = "admin"
            )
        }
    }
}
