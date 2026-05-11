package com.clipping.mcpserver.store

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * OrganizationStore V134 확장 검증.
 *
 * stockCode / aliases / origin 필드 라운드트립 및 신규 메서드(findByTenantAndStockCode, updateStockCode)를 검증한다.
 */
@SpringBootTest
@Transactional
class JdbcOrganizationStoreExtTest(
    @Autowired private val store: OrganizationStore,
) {

    @Test
    fun `findByTenantAndStockCode returns inserted org with stockCode + aliases + origin`() {
        val id = java.util.UUID.randomUUID().toString()
        store.insert(
            id = id, tenantId = "default", name = "MegaCorp",
            type = "CUSTOMER", domain = null, stockCode = "999930",
            aliases = "[\"SEC\",\"samsung\"]", origin = "user_wizard",
        )
        val found = store.findByTenantAndStockCode("default", "999930")
        found.shouldNotBeNull()
        found.name shouldBe "MegaCorp"
        found.aliases shouldBe listOf("SEC", "samsung")
        found.origin shouldBe "user_wizard"
        found.stockCode shouldBe "999930"
    }

    @Test
    fun `updateStockCode sets code for existing org with null`() {
        val id = java.util.UUID.randomUUID().toString()
        store.insert(
            id = id, tenantId = "default", name = "ConglomerateCo",
            type = "CUSTOMER", domain = null, stockCode = null,
            aliases = null, origin = null,
        )
        val updated = store.updateStockCode(id, "066570")
        updated.stockCode shouldBe "066570"
    }

    @Test
    fun `findByTenantAndStockCode returns null when absent`() {
        store.findByTenantAndStockCode("default", "999999").shouldBeNull()
    }

    @Test
    fun `aliases null in DB is read as empty list`() {
        val id = java.util.UUID.randomUUID().toString()
        store.insert(
            id = id, tenantId = "default", name = "NoAliasCo",
            type = "CUSTOMER", domain = null, stockCode = "111111",
            aliases = null, origin = "admin_created",
        )
        val found = store.findByTenantAndStockCode("default", "111111")
        found.shouldNotBeNull()
        found.aliases shouldBe emptyList()
    }
}
