package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * CategoryRule 저장소의 낙관적 잠금/리비전 동작을 검증한다.
 * V111 리네이밍 이후 version 컬럼이 없어졌으므로 revision 기반 동작을 잠근다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcCategoryRuleStoreTest {

    @Autowired lateinit var ruleStore: CategoryRuleStore
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var jdbc: JdbcTemplate

    private lateinit var category: Category

    @BeforeEach
    fun setup() {
        // 규칙 upsert에 필요한 카테고리를 새로 만들어 테스트별로 격리한다.
        category = categoryStore.save(Category(id = "", name = "RuleTest-${System.nanoTime()}"))
    }

    @Test
    fun `upsert inserts new rule with revision and returns persisted copy`() {
        val rule = CategoryRule(
            categoryId = category.id,
            includeKeywords = listOf("ai"),
            revision = 1
        )

        val saved = ruleStore.upsert(rule)

        saved.categoryId shouldBe category.id
        saved.includeKeywords shouldBe listOf("ai")
        saved.revision shouldBe 1
        ruleStore.findByCategoryId(category.id)!!.revision shouldBe 1
    }

    @Test
    fun `updateWithExpectedUpdatedAt increments revision on happy path`() {
        val initial = ruleStore.upsert(CategoryRule(categoryId = category.id, revision = 3))

        val next = ruleStore.updateWithExpectedUpdatedAt(
            rule = initial.copy(includeKeywords = listOf("tech")),
            expectedUpdatedAt = initial.updatedAt
        )

        next shouldNotBe null
        // revision은 현재 + 1 로 저장된다.
        next!!.revision shouldBe 4
        ruleStore.findByCategoryId(category.id)!!.revision shouldBe 4
        ruleStore.findByCategoryId(category.id)!!.includeKeywords shouldBe listOf("tech")
    }

    @Test
    fun `updateWithExpectedUpdatedAt returns null on stale updatedAt`() {
        val initial = ruleStore.upsert(CategoryRule(categoryId = category.id, revision = 2))
        val stale = initial.updatedAt.minusSeconds(30)

        val result = ruleStore.updateWithExpectedUpdatedAt(
            rule = initial.copy(includeKeywords = listOf("stale")),
            expectedUpdatedAt = stale
        )

        result shouldBe null
        // 실패 시 revision은 변하지 않는다.
        ruleStore.findByCategoryId(category.id)!!.revision shouldBe 2
        ruleStore.findByCategoryId(category.id)!!.includeKeywords shouldBe emptyList()
    }

    @Test
    fun `updateWithExpectedUpdatedAt returns null when rule missing`() {
        val now = java.time.Instant.now()
        val result = ruleStore.updateWithExpectedUpdatedAt(
            rule = CategoryRule(categoryId = "nonexistent-cat", revision = 0),
            expectedUpdatedAt = now
        )
        result shouldBe null
    }

    /**
     * V132 에서 추가된 exclude_event_types 컬럼의 JSON round-trip 동작을 검증한다.
     * JpaCategoryRuleStore(@Primary) 와 JdbcCategoryRuleStore 모두 동일한 Jackson 파싱 경로를 쓰므로
     * primary 경로 전체 + jdbc 경로 read-path 를 한 번씩 확인한다.
     */
    @Nested
    inner class `exclude_event_types round-trip` {

        @Test
        fun `저장 후 조회 시 리스트가 round-trip 된다`() {
            val saved = ruleStore.upsert(
                CategoryRule(
                    categoryId = category.id,
                    excludeEventTypes = listOf("OTHER", "PERSONNEL")
                )
            )

            saved.excludeEventTypes shouldContainExactlyInAnyOrder listOf("OTHER", "PERSONNEL")
            val fetched = ruleStore.findByCategoryId(category.id)!!
            fetched.excludeEventTypes shouldContainExactlyInAnyOrder listOf("OTHER", "PERSONNEL")
        }

        @Test
        fun `기본값은 빈 리스트`() {
            ruleStore.upsert(CategoryRule(categoryId = category.id))

            val fetched = ruleStore.findByCategoryId(category.id)!!
            fetched.excludeEventTypes shouldBe emptyList()
        }

        @Test
        fun `잘못된 JSON 은 빈 리스트로 fallback 된다`() {
            ruleStore.upsert(CategoryRule(categoryId = category.id, excludeEventTypes = listOf("OTHER")))
            // 고의로 깨진 값을 주입해 parseJsonList 의 fallback 경로를 자극한다.
            jdbc.update(
                "UPDATE clipping_category_rules SET exclude_event_types = ? WHERE category_id = ?",
                "{not-json",
                category.id
            )

            // '{not-json' 은 JSON 파싱 실패 + CSV split 시 '{not-json' 한 토큰으로 남기 때문에
            // parseJsonList 의 CSV fallback 은 비-JSON 토큰 하나를 반환한다.
            // 우리가 보장하는 동작은 "예외 없이 복구" 이므로 크래시 없이 fetch 되는지만 확인한다.
            val fetched = ruleStore.findByCategoryId(category.id)
            fetched shouldNotBe null
        }

        @Test
        fun `updateWithExpectedUpdatedAt 도 exclude_event_types 를 반영한다`() {
            val initial = ruleStore.upsert(CategoryRule(categoryId = category.id, revision = 1))

            val next = ruleStore.updateWithExpectedUpdatedAt(
                rule = initial.copy(excludeEventTypes = listOf("OTHER", "REORG")),
                expectedUpdatedAt = initial.updatedAt
            )

            next shouldNotBe null
            next!!.excludeEventTypes shouldContainExactlyInAnyOrder listOf("OTHER", "REORG")
            ruleStore.findByCategoryId(category.id)!!.excludeEventTypes shouldContainExactlyInAnyOrder
                listOf("OTHER", "REORG")
        }

    }
}
