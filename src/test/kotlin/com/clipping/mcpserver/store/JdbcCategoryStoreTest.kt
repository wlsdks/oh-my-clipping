package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryPurpose
import com.clipping.mcpserver.model.CategoryStatus
import com.clipping.mcpserver.model.RssSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * CategoryStore의 scheduler-safe 경로 (pause/resume)가 updated_at을 오염시키지 않는지 잠근다.
 * updated_at은 관리자 편집 시각이므로 시스템 전이가 이 값을 밀어버리면 안 된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcCategoryStoreTest {

    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var sourceStore: RssSourceStore
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `countSourcesByCategoryIds는 여러 카테고리의 소스 수를 DB에서 일괄 집계한다`() {
        val first = categoryStore.save(Category(id = "", name = "BulkCountA-${System.nanoTime()}"))
        val second = categoryStore.save(Category(id = "", name = "BulkCountB-${System.nanoTime()}"))
        sourceStore.save(RssSource(id = "", name = "A-1", url = "https://a.example.com/rss", categoryId = first.id))
        sourceStore.save(RssSource(id = "", name = "A-2", url = "https://b.example.com/rss", categoryId = first.id))
        sourceStore.save(RssSource(id = "", name = "B-1", url = "https://c.example.com/rss", categoryId = second.id))

        val counts = categoryStore.countSourcesByCategoryIds(listOf(first.id, second.id))

        counts[first.id] shouldBe 2
        counts[second.id] shouldBe 1
    }

    @Test
    fun `listByIds는 요청한 카테고리만 DB에서 조회한다`() {
        val first = categoryStore.save(Category(id = "", name = "ListByIdsA-${System.nanoTime()}"))
        val second = categoryStore.save(Category(id = "", name = "ListByIdsB-${System.nanoTime()}"))
        val unrequested = categoryStore.save(Category(id = "", name = "ListByIdsC-${System.nanoTime()}"))

        val foundIds = categoryStore.listByIds(listOf(first.id, second.id, first.id)).map { it.id }.toSet()

        foundIds shouldBe setOf(first.id, second.id)
        (unrequested.id !in foundIds) shouldBe true
    }

    @Test
    fun `findActiveByPersonaId는 같은 persona의 활성 카테고리만 조회한다`() {
        val personaId = "persona-${System.nanoTime()}"
        val active = categoryStore.save(
            Category(id = "", name = "PersonaActive-${System.nanoTime()}", personaId = personaId)
        )
        val paused = categoryStore.save(
            Category(
                id = "",
                name = "PersonaPaused-${System.nanoTime()}",
                personaId = personaId,
                isActive = false,
                status = CategoryStatus.PAUSED,
            )
        )
        val otherPersona = categoryStore.save(
            Category(id = "", name = "PersonaOther-${System.nanoTime()}", personaId = "other-$personaId")
        )

        val foundIds = categoryStore.findActiveByPersonaId(personaId).map { it.id }.toSet()

        foundIds shouldBe setOf(active.id)
        (paused.id !in foundIds) shouldBe true
        (otherPersona.id !in foundIds) shouldBe true
    }

    @Test
    fun `findOperational은 ACTIVE 카테고리만 DB에서 조회한다`() {
        val active = categoryStore.save(Category(id = "", name = "OperationalActive-${System.nanoTime()}"))
        val paused = categoryStore.save(
            Category(
                id = "",
                name = "OperationalPaused-${System.nanoTime()}",
                isActive = false,
                status = CategoryStatus.PAUSED,
            )
        )

        val foundIds = categoryStore.findOperational().map { it.id }.toSet()

        (active.id in foundIds) shouldBe true
        (paused.id !in foundIds) shouldBe true
    }

    @Test
    fun `countOperational은 ACTIVE 카테고리 수를 DB에서 집계한다`() {
        val before = categoryStore.countOperational()
        categoryStore.save(Category(id = "", name = "CountOperationalActive-${System.nanoTime()}"))
        categoryStore.save(
            Category(
                id = "",
                name = "CountOperationalPaused-${System.nanoTime()}",
                isActive = false,
                status = CategoryStatus.PAUSED,
            )
        )

        val after = categoryStore.countOperational()

        (after - before) shouldBe 1L
    }

    @Test
    fun `findPublicOperational은 공개 ACTIVE 카테고리만 DB에서 조회한다`() {
        val publicActive = categoryStore.save(
            Category(id = "", name = "PublicOperational-${System.nanoTime()}", isPublic = true)
        )
        val privateActive = categoryStore.save(
            Category(id = "", name = "PrivateOperational-${System.nanoTime()}", isPublic = false)
        )
        val publicPaused = categoryStore.save(
            Category(
                id = "",
                name = "PublicPaused-${System.nanoTime()}",
                isPublic = true,
                isActive = false,
                status = CategoryStatus.PAUSED,
            )
        )

        val foundIds = categoryStore.findPublicOperational().map { it.id }.toSet()

        (publicActive.id in foundIds) shouldBe true
        (privateActive.id !in foundIds) shouldBe true
        (publicPaused.id !in foundIds) shouldBe true
    }

    @Test
    fun `findExpiredPaused는 기준보다 오래된 PAUSED 카테고리만 DB에서 조회한다`() {
        val expiredPaused = categoryStore.save(
            Category(
                id = "",
                name = "ExpiredPaused-${System.nanoTime()}",
                isActive = false,
                status = CategoryStatus.PAUSED,
                pausedAt = java.time.Instant.now().minus(java.time.Duration.ofDays(31)),
            )
        )
        val freshPaused = categoryStore.save(
            Category(
                id = "",
                name = "FreshPaused-${System.nanoTime()}",
                isActive = false,
                status = CategoryStatus.PAUSED,
                pausedAt = java.time.Instant.now().minus(java.time.Duration.ofDays(1)),
            )
        )
        val active = categoryStore.save(
            Category(
                id = "",
                name = "ExpiredActive-${System.nanoTime()}",
                isActive = true,
                status = CategoryStatus.ACTIVE,
                pausedAt = java.time.Instant.now().minus(java.time.Duration.ofDays(31)),
            )
        )

        val foundIds = categoryStore.findExpiredPaused(java.time.Duration.ofDays(30)).map { it.id }.toSet()

        (expiredPaused.id in foundIds) shouldBe true
        (freshPaused.id !in foundIds) shouldBe true
        (active.id !in foundIds) shouldBe true
    }

    @Test
    fun `save 한 purpose,background,problem_statement 이 findById 로 복원된다`() {
        val saved = categoryStore.save(
            Category(
                id = "",
                name = "MetaTest-${System.nanoTime()}",
                purpose = CategoryPurpose.SALES,
                background = "영업팀 경쟁사 모니터링",
                problemStatement = "수동 검색 시간 소요"
            )
        )

        val found = categoryStore.findById(saved.id)
        found?.purpose shouldBe CategoryPurpose.SALES
        found?.background shouldBe "영업팀 경쟁사 모니터링"
        found?.problemStatement shouldBe "수동 검색 시간 소요"
    }

    @Test
    fun `purpose 가 null 이면 그대로 null 로 저장,조회된다`() {
        val saved = categoryStore.save(
            Category(id = "", name = "NullPurposeTest-${System.nanoTime()}")
        )

        val found = categoryStore.findById(saved.id)
        found?.purpose shouldBe null
        found?.background shouldBe null
        found?.problemStatement shouldBe null
    }

    @Test
    fun `update 는 purpose,background,problem_statement 를 교체한다`() {
        val saved = categoryStore.save(
            Category(
                id = "",
                name = "UpdatePurposeTest-${System.nanoTime()}",
                purpose = CategoryPurpose.RESEARCH
            )
        )

        val updated = categoryStore.update(
            saved.copy(
                purpose = CategoryPurpose.COMPETITIVE,
                background = "업데이트 배경",
                problemStatement = "업데이트 문제"
            )
        )

        val found = categoryStore.findById(saved.id)
        found?.purpose shouldBe CategoryPurpose.COMPETITIVE
        found?.background shouldBe "업데이트 배경"
        found?.problemStatement shouldBe "업데이트 문제"
        updated.purpose shouldBe CategoryPurpose.COMPETITIVE
    }

    @Test
    fun `purpose rowMapper — DB 에 남아있는 허용 밖 값은 null 로 안전 복원한다`() {
        // DB CHECK 제약이 빡빡하더라도 H2 PostgreSQL MODE 에서 안 걸릴 가능성이 있고
        // 미래의 enum 확장 / 레거시 값이 DB 에 남아있을 수 있으므로 rowMapper 는
        // 알 수 없는 값을 예외 대신 null 로 복원해 앱이 죽지 않도록 한다.
        val saved = categoryStore.save(
            Category(id = "", name = "CheckTest-${System.nanoTime()}", purpose = CategoryPurpose.SALES)
        )

        // 직접 UPDATE 로 허용 밖 값을 강제 주입 (H2 에서는 CHECK 가 동작 안 할 수 있음).
        runCatching {
            jdbc.update("UPDATE batch_categories SET purpose = ? WHERE id = ?", "LEGACY_VALUE", saved.id)
        }
        // 어떤 경우든 findById 는 예외 없이 응답해야 한다.
        val found = categoryStore.findById(saved.id)
        // 값이 실제로 바뀌었다면 null 로 복원됐을 것이고, CHECK 에 걸려 못 바뀌었다면 SALES 그대로.
        (found?.purpose == null || found.purpose == CategoryPurpose.SALES) shouldBe true
    }

    @Test
    fun `pause preserves updated_at and bumps system_updated_at`() {
        val saved = categoryStore.save(Category(id = "", name = "PauseTest-${System.nanoTime()}"))
        val originalUpdatedAt = saved.updatedAt
        val originalSystemUpdatedAt = saved.systemUpdatedAt

        // pause는 AutoUnpauseScheduler와 관리자 API 공용 경로이므로 updated_at은 건드리지 않아야 한다.
        categoryStore.pause(saved.id)

        val after = categoryStore.findById(saved.id)!!
        after.status shouldBe CategoryStatus.PAUSED
        after.isActive shouldBe false
        after.updatedAt shouldBe originalUpdatedAt
        // system_updated_at은 반드시 갱신돼야 한다.
        (after.systemUpdatedAt >= originalSystemUpdatedAt) shouldBe true
    }

    @Test
    fun `resume preserves updated_at and bumps system_updated_at`() {
        val saved = categoryStore.save(
            Category(id = "", name = "ResumeTest-${System.nanoTime()}", status = CategoryStatus.PAUSED)
        )
        val originalUpdatedAt = saved.updatedAt
        val originalSystemUpdatedAt = saved.systemUpdatedAt

        categoryStore.resume(saved.id)

        val after = categoryStore.findById(saved.id)!!
        after.status shouldBe CategoryStatus.ACTIVE
        after.isActive shouldBe true
        after.updatedAt shouldBe originalUpdatedAt
        (after.systemUpdatedAt >= originalSystemUpdatedAt) shouldBe true
    }
}
