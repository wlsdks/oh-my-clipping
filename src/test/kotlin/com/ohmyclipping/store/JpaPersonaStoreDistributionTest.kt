package com.ohmyclipping.store

import com.ohmyclipping.repository.PersonaRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.sql.Timestamp
import java.time.Instant

/**
 * Persona distribution native/projection result coercion tests.
 */
class JpaPersonaStoreDistributionTest {

    private val repository = mockk<PersonaRepository>()
    private val entityManager = mockk<EntityManager>(relaxed = true)
    private val query = mockk<Query>(relaxed = true)
    private val store = JpaPersonaStore(repository, entityManager)

    @Test
    fun `findToneDistribution은 COUNT 타입이 Long이 아니어도 Number로 변환한다`() {
        every { repository.findToneDistribution() } returns listOf(
            arrayOf("formal", 2),
            arrayOf("casual", BigInteger.valueOf(3)),
        )

        store.findToneDistribution() shouldBe mapOf(
            "formal" to 2L,
            "casual" to 3L,
        )
    }

    @Test
    fun `findLengthDistribution은 null label 또는 null count row를 제외한다`() {
        @Suppress("UNCHECKED_CAST")
        val rows = listOf(
            arrayOf<Any?>("short", 4L) as Array<Any>,
            arrayOf<Any?>(null, 9L) as Array<Any>,
            arrayOf<Any?>("long", null) as Array<Any>,
        )
        every { repository.findLengthDistribution() } returns listOf(
            *rows.toTypedArray()
        )

        store.findLengthDistribution() shouldBe mapOf("short" to 4L)
    }

    @Test
    fun `findPresetUsage는 null preset 또는 null count row를 제외한다`() {
        every { entityManager.createNativeQuery(any<String>()) } returns query
        every { query.resultList } returns listOf(
            arrayOf<Any?>("preset-1", "기본", 2),
            arrayOf<Any?>(null, "깨진 프리셋", 3L),
            arrayOf<Any?>("preset-2", "카운트 누락", null),
        )

        store.findPresetUsage() shouldBe listOf(
            PresetUsageRow(
                presetId = "preset-1",
                presetName = "기본",
                activeSubscriptions = 2L,
            )
        )
    }

    @Test
    fun `findRecentCustomPersonas는 Timestamp와 Instant created_at을 모두 처리한다`() {
        val firstCreatedAt = Instant.parse("2026-04-26T10:00:00Z")
        val secondCreatedAt = Instant.parse("2026-04-25T10:00:00Z")
        every { entityManager.createNativeQuery(any<String>()) } returns query
        every { query.resultList } returns listOf(
            arrayOf<Any?>("persona-1", "첫번째", "prompt", "formal", "short", Timestamp.from(firstCreatedAt), "홍길동"),
            arrayOf<Any?>("persona-2", "두번째", null, null, null, secondCreatedAt, null),
            arrayOf<Any?>(null, "깨진 row", "prompt", null, null, firstCreatedAt, "홍길동"),
        )

        store.findRecentCustomPersonas(10) shouldBe listOf(
            RecentCustomPersonaRow(
                id = "persona-1",
                userName = "홍길동",
                personaName = "첫번째",
                systemPrompt = "prompt",
                tone = "formal",
                lengthPref = "short",
                createdAt = firstCreatedAt,
            ),
            RecentCustomPersonaRow(
                id = "persona-2",
                userName = "알 수 없음",
                personaName = "두번째",
                systemPrompt = "",
                tone = null,
                lengthPref = null,
                createdAt = secondCreatedAt,
            ),
        )
    }
}
