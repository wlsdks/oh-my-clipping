package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.Organization
import com.clipping.mcpserver.model.OrganizationType
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.OrganizationStore
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * OrganizationService 정책 단위 테스트.
 */
class OrganizationServiceTest {

    private val store = mockk<OrganizationStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val service = OrganizationService(store, categoryStore)

    private fun sampleOrg(
        id: String = "org-1",
        name: String = "Acme",
        type: OrganizationType = OrganizationType.COMPETITOR,
    ) = Organization(
        id = id,
        name = name,
        type = type,
        domain = null,
        description = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Nested
    inner class Create {
        @Test
        fun `정상 입력이면 저장된 organization 을 반환한다`() {
            every { store.findByName("Acme") } returns null
            val saved = sampleOrg()
            val captured = slot<Organization>()
            every { store.save(capture(captured)) } returns saved

            val result = service.create(name = "  Acme ", type = "COMPETITOR", domain = "acme.com")

            result.id shouldBe "org-1"
            captured.captured.name shouldBe "Acme"
            captured.captured.type shouldBe OrganizationType.COMPETITOR
            captured.captured.domain shouldBe "acme.com"
        }

        @Test
        fun `이름이 빈 문자열이면 InvalidInputException`() {
            assertThrows<InvalidInputException> {
                service.create(name = "   ", type = "OTHER")
            }
        }

        @Test
        fun `type 이 enum 범위 밖이면 InvalidInputException`() {
            assertThrows<InvalidInputException> {
                service.create(name = "X", type = "INVESTOR")
            }
        }

        @Test
        fun `동일 이름이 이미 존재하면 ConflictException`() {
            every { store.findByName("Dup") } returns sampleOrg(name = "Dup")
            assertThrows<ConflictException> {
                service.create(name = "Dup", type = "OTHER")
            }
        }

        @Test
        fun `name 이 200자 초과면 InvalidInputException`() {
            val longName = "a".repeat(201)
            assertThrows<InvalidInputException> {
                service.create(name = longName, type = "OTHER")
            }
        }
    }

    @Nested
    inner class Update {
        @Test
        fun `존재하지 않는 id 면 NotFoundException`() {
            every { store.findById("missing") } returns null
            assertThrows<NotFoundException> {
                service.update(id = "missing", name = "new", type = null, domain = null, description = null)
            }
        }

        @Test
        fun `빈 문자열 domain 은 null 로 초기화된다`() {
            val current = sampleOrg().copy(domain = "old.com")
            every { store.findById("org-1") } returns current
            val updated = slot<Organization>()
            every { store.update(capture(updated)) } answers { updated.captured }

            service.update(id = "org-1", name = null, type = null, domain = "", description = null)

            updated.captured.domain shouldBe null
        }

        @Test
        fun `name 이 다른 조직과 중복되면 ConflictException`() {
            val current = sampleOrg()
            every { store.findById("org-1") } returns current
            every { store.findByName("Other") } returns sampleOrg(id = "org-2", name = "Other")

            assertThrows<ConflictException> {
                service.update(id = "org-1", name = "Other", type = null, domain = null, description = null)
            }
        }

        @Test
        fun `동일 id 의 같은 이름은 충돌로 간주하지 않는다`() {
            val current = sampleOrg()
            every { store.findById("org-1") } returns current
            every { store.findByName("Acme") } returns current
            every { store.update(any()) } answers { firstArg() }

            val result = service.update(id = "org-1", name = "Acme", type = null, domain = null, description = null)

            result.name shouldBe "Acme"
        }
    }

    @Nested
    inner class UpdateAliases {
        @Test
        fun `aliases 가 null 이면 기존 aliases 를 유지한다`() {
            val current = sampleOrg().copy(aliases = listOf("SEC", "samsung"))
            every { store.findById("org-1") } returns current
            val captured = slot<Organization>()
            every { store.update(capture(captured)) } answers { captured.captured }

            service.update(
                id = "org-1",
                name = null,
                type = null,
                domain = null,
                description = null,
                aliases = null,
            )

            captured.captured.aliases shouldContainExactly listOf("SEC", "samsung")
        }

        @Test
        fun `aliases non-null 이면 trim + dedup + 빈값 제거 후 저장한다`() {
            val current = sampleOrg().copy(aliases = listOf("old"))
            every { store.findById("org-1") } returns current
            val captured = slot<Organization>()
            every { store.update(capture(captured)) } answers { captured.captured }

            service.update(
                id = "org-1",
                name = null,
                type = null,
                domain = null,
                description = null,
                aliases = listOf("SEC", " samsung ", "", "SEC"),
            )

            captured.captured.aliases shouldContainExactly listOf("SEC", "samsung")
        }

        @Test
        fun `aliases 가 21개 이상이면 InvalidInputException`() {
            val current = sampleOrg()
            every { store.findById("org-1") } returns current
            // 21개의 고유 별칭 — 정규화 후에도 초과로 판정돼야 한다.
            val tooMany = (1..21).map { "alias$it" }

            assertThrows<InvalidInputException> {
                service.update(
                    id = "org-1",
                    name = null,
                    type = null,
                    domain = null,
                    description = null,
                    aliases = tooMany,
                )
            }
        }

        @Test
        fun `단일 alias 가 51자 이상이면 InvalidInputException`() {
            val current = sampleOrg()
            every { store.findById("org-1") } returns current
            val tooLong = "a".repeat(51)

            assertThrows<InvalidInputException> {
                service.update(
                    id = "org-1",
                    name = null,
                    type = null,
                    domain = null,
                    description = null,
                    aliases = listOf("ok", tooLong),
                )
            }
        }

        @Test
        fun `빈 리스트 aliases 는 모두 제거된 상태로 저장한다`() {
            val current = sampleOrg().copy(aliases = listOf("SEC"))
            every { store.findById("org-1") } returns current
            val captured = slot<Organization>()
            every { store.update(capture(captured)) } answers { captured.captured }

            service.update(
                id = "org-1",
                name = null,
                type = null,
                domain = null,
                description = null,
                aliases = emptyList(),
            )

            captured.captured.aliases shouldContainExactly emptyList()
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `존재하지 않는 id 면 NotFoundException`() {
            every { store.findById("nope") } returns null
            assertThrows<NotFoundException> { service.delete("nope") }
        }

        @Test
        fun `존재하면 store delete 호출`() {
            every { store.findById("org-1") } returns sampleOrg()
            every { store.delete("org-1") } just Runs
            service.delete("org-1")
            verify(exactly = 1) { store.delete("org-1") }
        }
    }

    @Nested
    inner class CategoryLinking {
        private val category = Category(id = "cat-1", name = "Cat1")

        @Test
        fun `categoryId 가 존재하지 않으면 NotFoundException`() {
            every { categoryStore.findById("missing") } returns null
            assertThrows<NotFoundException> {
                service.setCategoryOrganizations("missing", listOf("org-1"))
            }
        }

        @Test
        fun `organizationIds 에 존재하지 않는 id 가 있으면 InvalidInputException`() {
            every { categoryStore.findById("cat-1") } returns category
            every { store.findById("org-1") } returns sampleOrg()
            every { store.findById("org-missing") } returns null

            assertThrows<InvalidInputException> {
                service.setCategoryOrganizations("cat-1", listOf("org-1", "org-missing"))
            }
        }

        @Test
        fun `정상 호출 시 중복 제거 후 store 에 전달`() {
            every { categoryStore.findById("cat-1") } returns category
            every { store.findById("org-1") } returns sampleOrg()
            every { store.findById("org-2") } returns sampleOrg(id = "org-2", name = "Org2")
            val captured = slot<List<String>>()
            every { store.setCategoryOrganizations("cat-1", capture(captured)) } just Runs

            service.setCategoryOrganizations(
                "cat-1",
                listOf("org-1", "org-1", "org-2", "")
            )

            captured.captured shouldContainExactly listOf("org-1", "org-2")
        }

        @Test
        fun `빈 리스트 전달 시 조직 존재 체크 없이 store 호출`() {
            every { categoryStore.findById("cat-1") } returns category
            every { store.setCategoryOrganizations("cat-1", emptyList()) } just Runs

            service.setCategoryOrganizations("cat-1", emptyList())

            verify(exactly = 1) { store.setCategoryOrganizations("cat-1", emptyList()) }
        }
    }

    @Nested
    inner class FindByCategoryId {
        @Test
        fun `카테고리가 없으면 NotFoundException`() {
            every { categoryStore.findById("cat-missing") } returns null
            assertThrows<NotFoundException> {
                service.findByCategoryId("cat-missing")
            }
        }

        @Test
        fun `카테고리가 있으면 store 결과 반환`() {
            every { categoryStore.findById("cat-1") } returns Category(id = "cat-1", name = "Cat1")
            every { store.findByCategoryId("cat-1") } returns listOf(sampleOrg())

            val result = service.findByCategoryId("cat-1")
            result.size shouldBe 1
        }
    }
}
