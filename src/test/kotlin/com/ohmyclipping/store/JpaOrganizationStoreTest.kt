package com.ohmyclipping.store

import com.ohmyclipping.model.Category
import com.ohmyclipping.model.Organization
import com.ohmyclipping.model.OrganizationType
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

/**
 * JpaOrganizationStore 저장/조회/링크 동작을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class JpaOrganizationStoreTest {

    @Autowired lateinit var store: OrganizationStore

    @Autowired lateinit var categoryStore: CategoryStore

    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun cleanup() {
        // Spring context 가 공유되므로 각 테스트 간 격리를 위해 이 테스트가 만드는 데이터를 정리한다.
        jdbc.update("DELETE FROM category_organizations")
        jdbc.update("DELETE FROM organizations")
    }

    @Test
    fun `save 후 findById 로 라운드트립 가능하다`() {
        val saved = store.save(
            Organization(
                id = "",
                name = "Acme Corp",
                type = OrganizationType.COMPETITOR,
                domain = "acme.com",
                description = "주요 경쟁사",
            )
        )

        saved.id.shouldNotBeNull()
        val fetched = store.findById(saved.id)
        fetched.shouldNotBeNull()
        fetched.name shouldBe "Acme Corp"
        fetched.type shouldBe OrganizationType.COMPETITOR
        fetched.domain shouldBe "acme.com"
        fetched.tenantId shouldBe "default"
    }

    @Test
    fun `같은 tenant + name 으로 저장 시 UNIQUE 위반 예외`() {
        store.save(Organization(id = "", name = "Dup Co", type = OrganizationType.OTHER))

        assertThrows<DataIntegrityViolationException> {
            store.save(Organization(id = "", name = "Dup Co", type = OrganizationType.CUSTOMER))
        }
    }

    @Test
    fun `findAll 은 type 필터를 지원한다`() {
        store.save(Organization(id = "", name = "A Competitor", type = OrganizationType.COMPETITOR))
        store.save(Organization(id = "", name = "B Customer", type = OrganizationType.CUSTOMER))
        store.save(Organization(id = "", name = "C Partner", type = OrganizationType.PARTNER))

        store.findAll().size shouldBe 3
        val customers = store.findAll(OrganizationType.CUSTOMER)
        customers.map { it.name } shouldBe listOf("B Customer")
    }

    @Test
    fun `update 는 name, type, domain, description 을 반영한다`() {
        val saved = store.save(Organization(id = "", name = "Orig", type = OrganizationType.OTHER))
        val updated = store.update(
            saved.copy(
                name = "Renamed",
                type = OrganizationType.PARTNER,
                domain = "renamed.com",
                description = "changed"
            )
        )

        updated.name shouldBe "Renamed"
        updated.type shouldBe OrganizationType.PARTNER
        updated.domain shouldBe "renamed.com"
        updated.description shouldBe "changed"
    }

    @Test
    fun `setCategoryOrganizations 는 링크 교체가 idempotent 하다`() {
        val category = categoryStore.save(Category(id = "", name = "CatLinkTest-${System.nanoTime()}"))
        val orgA = store.save(Organization(id = "", name = "LinkA", type = OrganizationType.COMPETITOR))
        val orgB = store.save(Organization(id = "", name = "LinkB", type = OrganizationType.COMPETITOR))
        val orgC = store.save(Organization(id = "", name = "LinkC", type = OrganizationType.PARTNER))

        // 초기 연결
        store.setCategoryOrganizations(category.id, listOf(orgA.id, orgB.id))
        store.findByCategoryId(category.id).map { it.id }
            .shouldContainExactlyInAnyOrder(orgA.id, orgB.id)

        // 같은 세트 재호출 — 결과 동일
        store.setCategoryOrganizations(category.id, listOf(orgA.id, orgB.id))
        store.findByCategoryId(category.id).map { it.id }
            .shouldContainExactlyInAnyOrder(orgA.id, orgB.id)

        // 교체 — A 제거, C 추가
        store.setCategoryOrganizations(category.id, listOf(orgB.id, orgC.id))
        store.findByCategoryId(category.id).map { it.id }
            .shouldContainExactlyInAnyOrder(orgB.id, orgC.id)

        // 빈 리스트 — 전체 해제
        store.setCategoryOrganizations(category.id, emptyList())
        store.findByCategoryId(category.id) shouldBe emptyList()
    }

    @Test
    fun `setCategoryOrganizations 는 중복 id 가 들어와도 한 번만 저장한다`() {
        val category = categoryStore.save(Category(id = "", name = "CatDupLink-${System.nanoTime()}"))
        val org = store.save(Organization(id = "", name = "DupLink", type = OrganizationType.OTHER))

        store.setCategoryOrganizations(category.id, listOf(org.id, org.id, org.id))

        store.findByCategoryId(category.id).map { it.id } shouldBe listOf(org.id)
    }

    @Test
    fun `delete 는 category 링크를 CASCADE 정리한다`() {
        val category = categoryStore.save(Category(id = "", name = "CatCascade-${System.nanoTime()}"))
        val org = store.save(Organization(id = "", name = "CascadeOrg", type = OrganizationType.OTHER))
        store.setCategoryOrganizations(category.id, listOf(org.id))

        store.delete(org.id)

        store.findById(org.id) shouldBe null
        store.findByCategoryId(category.id) shouldBe emptyList()
    }
}
