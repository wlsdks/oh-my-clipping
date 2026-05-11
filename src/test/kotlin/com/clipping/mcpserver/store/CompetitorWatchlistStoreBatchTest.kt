package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.CompetitorWatchlist
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

/**
 * CompetitorWatchlistStore.findByNamesIgnoreCase 배치 조회를 검증한다.
 * N+1 쿼리 제거를 위해 추가된 메서드로, 단일 DB 조회로 여러 이름을 처리해야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CompetitorWatchlistStoreBatchTest {

    @Autowired
    lateinit var store: CompetitorWatchlistStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    // JUnit 5 기본 lifecycle 은 PER_METHOD — 인스턴스가 메서드마다 새로 생성된다.
    // 공유 H2 컨텍스트에서 UNIQUE(name) 충돌을 피하기 위해 실행마다 고유 suffix 를 사용한다.
    private val suffix = System.nanoTime()
    private val nameTestCorp = "BatchTestCorp-$suffix"
    private val nameAlpha = "BatchLG-$suffix"
    private val nameBeta = "BatchBeta-$suffix"

    private val seededIds = mutableListOf<String>()

    @BeforeEach
    fun seedData() {
        // 테스트 격리를 위해 고유 이름으로 3개 row 를 삽입한다.
        listOf(
            newEntry(nameTestCorp, "DIRECT"),
            newEntry(nameAlpha, "ADJACENT"),
            newEntry(nameBeta, "GLOBAL"),
        ).forEach { seededIds.add(store.save(it).id) }
    }

    @AfterEach
    fun cleanup() {
        // competitor_watchlist 삭제 (FK CASCADE 로 연관 row 도 함께 제거된다).
        seededIds.forEach { id -> runCatching { store.delete(id) } }
        seededIds.clear()
        // CompetitorOrganizationSynchronizer 가 mirror 한 organizations row 도 함께 제거한다.
        // UNIQUE(tenant_id, name) 때문에 재시드 시 충돌이 발생하므로 명시 삭제가 필요하다.
        listOf(nameTestCorp, nameAlpha, nameBeta).forEach { name ->
            runCatching {
                jdbc.update(
                    "DELETE FROM organizations WHERE tenant_id = 'default' AND name = ?",
                    name
                )
            }
        }
    }

    @Nested
    inner class `findByNamesIgnoreCase` {

        @Test
        fun `존재하는 이름은 대소문자 무관하게 반환된다`() {
            // 소문자 변환 + 원본 대소문자 혼합 → 2개 매칭, "unknown" 제외
            val result = store.findByNamesIgnoreCase(
                listOf(nameTestCorp.lowercase(), nameAlpha, "unknown-that-does-not-exist")
            )

            result.map { it.name }.shouldContainExactlyInAnyOrder(nameTestCorp, nameAlpha)
        }

        @Test
        fun `빈 리스트 입력이면 DB 쿼리 없이 빈 결과를 반환한다`() {
            val result = store.findByNamesIgnoreCase(emptyList())

            result.shouldBeEmpty()
        }

        @Test
        fun `존재하지 않는 이름만 입력하면 빈 결과를 반환한다`() {
            val result = store.findByNamesIgnoreCase(listOf("never-exists-xyz", "also-not-real-abc"))

            result.shouldBeEmpty()
        }

        @Test
        fun `전체 3개 이름 입력 시 seed 한 3개 모두 반환된다`() {
            // 이 테스트가 seed 한 이름만 조회하므로 정확히 3개여야 한다.
            val result = store.findByNamesIgnoreCase(listOf(nameTestCorp, nameAlpha, nameBeta))

            result.map { it.name }.shouldContainExactlyInAnyOrder(nameTestCorp, nameAlpha, nameBeta)
        }

        @Test
        fun `대소문자 혼합 입력도 정규화하여 매칭한다`() {
            // 이름 전체를 대문자 또는 소문자로 변환해도 매칭돼야 한다.
            val result = store.findByNamesIgnoreCase(
                listOf(nameTestCorp.uppercase(), nameAlpha.lowercase())
            )

            result.map { it.name }.shouldContainExactlyInAnyOrder(nameTestCorp, nameAlpha)
        }
    }

    private fun newEntry(name: String, tier: String) = CompetitorWatchlist(
        id = "",
        name = name,
        tier = tier,
        isActive = true,
        aliases = emptyList(),
        excludeKeywords = emptyList(),
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
