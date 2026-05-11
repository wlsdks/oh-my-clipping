package com.ohmyclipping.service

import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.model.Organization
import com.ohmyclipping.model.OrganizationType
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.OrganizationStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

class OrganizationServiceUpsertTest {
    private val store = mockk<OrganizationStore>(relaxed = true)
    private val service = OrganizationService(store, mockk<CategoryStore>(relaxed = true))

    private fun org(
        id: String = "o1", name: String = "MegaCorp", stockCode: String? = null,
        origin: String? = null
    ) = Organization(
        id = id, tenantId = "default", name = name, type = OrganizationType.CUSTOMER,
        domain = null, description = null, stockCode = stockCode,
        aliases = emptyList(), origin = origin,
        createdAt = Instant.now(), updatedAt = Instant.now()
    )

    @Nested
    inner class `upsertByStockCodeOrName 동작` {
        @Test
        fun `stockCode 매치 우선 반환 — name lookup 없음`() {
            every { store.findByTenantAndStockCode("default", "999930") } returns org(stockCode = "999930")
            val result = service.upsertByStockCodeOrName("default", "MegaCorp", "999930")
            result.stockCode shouldBe "999930"
            verify(exactly = 0) { store.findByTenantAndName(any(), any()) }
        }

        @Test
        fun `stockCode 없으면 name 으로 매치`() {
            every { store.findByTenantAndStockCode(any(), any()) } returns null
            every { store.findByTenantAndName("default", "MegaCorp") } returns org()
            val result = service.upsertByStockCodeOrName("default", "MegaCorp", null)
            result.name shouldBe "MegaCorp"
        }

        @Test
        fun `기존 row stockCode NULL 이고 새 stockCode 있으면 UPDATE`() {
            val existing = org(stockCode = null)
            every { store.findByTenantAndStockCode(any(), any()) } returns null
            every { store.findByTenantAndName("default", "MegaCorp") } returns existing
            every { store.updateStockCode("o1", "999930") } returns org(stockCode = "999930")
            val result = service.upsertByStockCodeOrName("default", "MegaCorp", "999930")
            result.stockCode shouldBe "999930"
            verify { store.updateStockCode("o1", "999930") }
        }

        @Test
        fun `기존 stockCode 와 새 stockCode 다르면 Conflict`() {
            every { store.findByTenantAndStockCode(any(), any()) } returns null
            every { store.findByTenantAndName("default", "MegaCorp") } returns org(stockCode = "999930")
            shouldThrow<ConflictException> {
                service.upsertByStockCodeOrName("default", "MegaCorp", "066570")
            }
        }

        @Test
        fun `신규 INSERT 성공 시 insert 호출 + origin 전달`() {
            every { store.findByTenantAndStockCode(any(), any()) } returns null
            every { store.findByTenantAndName("default", "새회사") } returnsMany
                listOf(null, org(name = "새회사"))
            val result = service.upsertByStockCodeOrName("default", "새회사", "111111", origin = "user_wizard")
            result.name shouldBe "새회사"
            verify {
                store.insert(
                    id = any(), tenantId = "default", name = "새회사",
                    type = any(), domain = any(),
                    stockCode = "111111", aliases = any(), origin = "user_wizard"
                )
            }
        }

        @Test
        fun `신규 INSERT 중 race 발생 시 re-select 로 복구`() {
            every { store.findByTenantAndStockCode(any(), any()) } returns null
            every { store.findByTenantAndName("default", "MegaCorp") } returnsMany
                listOf(null, org())
            every { store.insert(any(), any(), any(), any(), any(), any(), any(), any()) } throws
                DataIntegrityViolationException("duplicate key")
            val result = service.upsertByStockCodeOrName("default", "MegaCorp", null)
            result.name shouldBe "MegaCorp"
        }

        @Test
        fun `race 후에도 찾지 못하면 Conflict`() {
            every { store.findByTenantAndStockCode(any(), any()) } returns null
            every { store.findByTenantAndName(any(), any()) } returns null  // race 후도 없음
            every { store.insert(any(), any(), any(), any(), any(), any(), any(), any()) } throws
                DataIntegrityViolationException("duplicate key")
            shouldThrow<ConflictException> {
                service.upsertByStockCodeOrName("default", "유령", null)
            }
        }
    }
}
