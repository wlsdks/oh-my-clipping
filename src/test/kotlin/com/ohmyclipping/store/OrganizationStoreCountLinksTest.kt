package com.ohmyclipping.store

import com.ohmyclipping.model.Category
import com.ohmyclipping.model.Organization
import com.ohmyclipping.model.OrganizationType
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

/**
 * OrganizationStore.countCategoryLinksByOrganizationIds 배치 조회를 검증한다.
 *
 * Spring context 가 공유되므로 @BeforeEach 에서 관련 테이블을 정리해 테스트 간 격리를 보장한다.
 * @Transactional 은 사용하지 않는다 (§5.1 참고).
 */
@SpringBootTest
@ActiveProfiles("test")
class OrganizationStoreCountLinksTest {

    @Autowired lateinit var store: OrganizationStore

    @Autowired lateinit var categoryStore: CategoryStore

    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun cleanup() {
        // 공유 H2 컨텍스트 오염 방지 — 링크 먼저 삭제 후 조직 삭제(FK 순서).
        jdbc.update("DELETE FROM category_organizations")
        jdbc.update("DELETE FROM organizations")
    }

    @Nested
    inner class `countCategoryLinksByOrganizationIds 배치 조회` {

        @Test
        fun `2개 조직 각각 1개 링크 시 두 조직 모두 count 1 반환`() {
            // 카테고리 1개, 조직 2개, 링크 각 1개 생성
            val category = categoryStore.save(Category(id = "", name = "BatchCountCat-${System.nanoTime()}"))
            val orgA = store.save(Organization(id = "", name = "BatchOrgA-${System.nanoTime()}", type = OrganizationType.COMPETITOR))
            val orgB = store.save(Organization(id = "", name = "BatchOrgB-${System.nanoTime()}", type = OrganizationType.CUSTOMER))

            store.setCategoryOrganizations(category.id, listOf(orgA.id, orgB.id))

            // 배치 조회 실행
            val result = store.countCategoryLinksByOrganizationIds(listOf(orgA.id, orgB.id))

            result shouldHaveSize 2
            result shouldContainKey orgA.id
            result shouldContainKey orgB.id
            result[orgA.id] shouldBe 1
            result[orgB.id] shouldBe 1
        }

        @Test
        fun `한 조직이 여러 카테고리와 링크될 때 count 가 올바르게 집계된다`() {
            val catX = categoryStore.save(Category(id = "", name = "BatchCatX-${System.nanoTime()}"))
            val catY = categoryStore.save(Category(id = "", name = "BatchCatY-${System.nanoTime()}"))
            val org = store.save(Organization(id = "", name = "MultiLinkOrg-${System.nanoTime()}", type = OrganizationType.PARTNER))

            store.setCategoryOrganizations(catX.id, listOf(org.id))
            store.setCategoryOrganizations(catY.id, listOf(org.id))

            val result = store.countCategoryLinksByOrganizationIds(listOf(org.id))

            result shouldHaveSize 1
            result shouldContainKey org.id
            result[org.id] shouldBe 2
        }

        @Test
        fun `링크가 없는 조직 ID 는 결과 map 에 포함되지 않는다`() {
            val org = store.save(Organization(id = "", name = "NoLinkOrg-${System.nanoTime()}", type = OrganizationType.OTHER))

            val result = store.countCategoryLinksByOrganizationIds(listOf(org.id))

            // 링크가 없으면 GROUP BY 결과에 포함되지 않음
            result shouldHaveSize 0
        }
    }

    @Nested
    inner class `빈 리스트 입력` {

        @Test
        fun `빈 리스트 입력 시 empty map 반환`() {
            val result = store.countCategoryLinksByOrganizationIds(emptyList())

            result shouldBe emptyMap()
        }
    }
}
