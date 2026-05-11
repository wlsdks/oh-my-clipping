package com.ohmyclipping.store

import com.ohmyclipping.model.Persona
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcPersonaStoreTest {

    @Autowired lateinit var personaStore: PersonaStore
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setup() {
        // @Transactional ensures rollback after each test
    }

    @Test
    fun `should save and findById`() {
        val persona = personaStore.save(
            Persona(id = "", name = "Tech Expert", systemPrompt = "You are a tech expert")
        )
        persona.id shouldNotBe ""

        val found = personaStore.findById(persona.id)!!
        found.name shouldBe "Tech Expert"
        found.systemPrompt shouldBe "You are a tech expert"
        found.maxItems shouldBe 5
        found.language shouldBe "ko"
        found.isActive shouldBe true
    }

    @Test
    fun `should list all personas`() {
        val initialCount = personaStore.list().size
        personaStore.save(Persona(id = "", name = "P1", systemPrompt = "prompt1"))
        personaStore.save(Persona(id = "", name = "P2", systemPrompt = "prompt2"))
        personaStore.list().size shouldBe initialCount + 2
    }

    @Test
    fun `should list only active personas`() {
        val initialActive = personaStore.listActive().size
        personaStore.save(Persona(id = "", name = "Active", systemPrompt = "p"))
        val inactive = personaStore.save(Persona(id = "", name = "Inactive", systemPrompt = "p"))
        personaStore.update(inactive.copy(isActive = false))

        personaStore.listActive().size shouldBe initialActive + 1
    }

    @Test
    fun `should update persona`() {
        val persona = personaStore.save(
            Persona(id = "", name = "Original", systemPrompt = "original prompt")
        )
        personaStore.update(persona.copy(name = "Updated", systemPrompt = "new prompt"))

        val updated = personaStore.findById(persona.id)!!
        updated.name shouldBe "Updated"
        updated.systemPrompt shouldBe "new prompt"
    }

    @Test
    fun `should delete persona`() {
        val persona = personaStore.save(Persona(id = "", name = "ToDelete", systemPrompt = "p"))
        personaStore.delete(persona.id)
        personaStore.findById(persona.id) shouldBe null
    }

    @Test
    fun `updateWithExpectedUpdatedAt returns saved persona when updatedAt matches`() {
        val original = personaStore.save(Persona(id = "", name = "Original", systemPrompt = "p"))
        val saved = personaStore.updateWithExpectedUpdatedAt(
            original.copy(name = "Renamed"),
            expectedUpdatedAt = original.updatedAt
        )
        saved shouldNotBe null
        saved!!.name shouldBe "Renamed"
        personaStore.findById(original.id)!!.name shouldBe "Renamed"
    }

    @Test
    fun `updateWithExpectedUpdatedAt returns null on stale updatedAt`() {
        val original = personaStore.save(Persona(id = "", name = "Original", systemPrompt = "p"))
        val stale = original.updatedAt.minusSeconds(60)
        val result = personaStore.updateWithExpectedUpdatedAt(
            original.copy(name = "Stale"),
            expectedUpdatedAt = stale
        )
        result shouldBe null
        personaStore.findById(original.id)!!.name shouldBe "Original"
    }

    @Test
    fun `updateWithExpectedUpdatedAt returns null when persona missing`() {
        val now = java.time.Instant.now()
        val result = personaStore.updateWithExpectedUpdatedAt(
            Persona(id = "nonexistent-id", name = "X", systemPrompt = "p"),
            expectedUpdatedAt = now
        )
        result shouldBe null
    }

    @Test
    fun `구독 사용자 집계는 사용자 소유 카테고리 테이블을 기준으로 계산한다`() {
        val preset = personaStore.save(
            Persona(id = "", name = "Preset-${UUID.randomUUID()}", systemPrompt = "preset", isPreset = true)
        )
        val custom = personaStore.save(
            Persona(id = "", name = "Custom-${UUID.randomUUID()}", systemPrompt = "custom", isPreset = false)
        )
        val userA = insertAdminUser()
        val userB = insertAdminUser()
        val userC = insertAdminUser()
        val presetCategory = insertCategory(personaId = preset.id, isActive = true)
        val customCategory = insertCategory(personaId = custom.id, isActive = true)
        val inactivePresetCategory = insertCategory(personaId = preset.id, isActive = false)

        insertOwnedCategory(userA, presetCategory)
        insertOwnedCategory(userA, customCategory)
        insertOwnedCategory(userB, presetCategory)
        insertOwnedCategory(userC, inactivePresetCategory)

        personaStore.countPresetSubscriptionUsers() shouldBe 2L
        personaStore.countTotalSubscriptionUsers() shouldBe 2L
    }

    private fun insertAdminUser(): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO admin_users (
                id, username, password_hash, display_name, is_active, role, approval_status,
                created_at, updated_at
            ) VALUES (
                ?, ?, '{noop}pw', '테스트 사용자', TRUE, 'USER', 'APPROVED',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            id,
            "persona-store-${id.take(8)}"
        )
        return id
    }

    private fun insertCategory(personaId: String, isActive: Boolean): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO batch_categories (
                id, name, is_active, is_public, max_items, persona_id, status,
                created_at, updated_at, system_updated_at
            ) VALUES (?, ?, ?, FALSE, 10, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            id,
            "PersonaStoreCat-${id.take(8)}",
            isActive,
            personaId
        )
        return id
    }

    private fun insertOwnedCategory(userId: String, categoryId: String) {
        jdbc.update(
            """
            INSERT INTO clipping_user_owned_categories (user_id, category_id, created_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            userId,
            categoryId
        )
    }
}
