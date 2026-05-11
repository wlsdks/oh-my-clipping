package com.clipping.mcpserver.store

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * 엔티티 변경 이력 저장소 통합 테스트.
 *
 * JPA 구현체가 실제 DB(H2)에 대해 append/listRecent/findById를 정상 동작하는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityRevisionStoreTest {

    @Autowired
    lateinit var store: EntityRevisionStore

    @Test
    fun `append는 revision_number를 1부터 단조 증가시킨다`() {
        val type = "persona"
        val resourceId = "persona-append-1"

        val first = store.append(
            resourceType = type,
            resourceId = resourceId,
            editorId = "admin",
            editorDisplayName = "관리자",
            changedFields = listOf("name"),
            snapshot = "{\"name\":\"v1\"}"
        )
        val second = store.append(
            resourceType = type,
            resourceId = resourceId,
            editorId = "admin",
            editorDisplayName = null,
            changedFields = emptyList(),
            snapshot = "{\"name\":\"v2\"}"
        )

        first.revisionNumber shouldBe 1L
        second.revisionNumber shouldBe 2L
        second.changedFields shouldBe emptyList()
    }

    @Test
    fun `listRecent는 최신 revision부터 limit 건수까지만 반환한다`() {
        val type = "category"
        val resourceId = "cat-listrecent"

        repeat(5) { index ->
            store.append(
                resourceType = type,
                resourceId = resourceId,
                editorId = "admin",
                editorDisplayName = null,
                changedFields = listOf("field-$index"),
                snapshot = "{\"idx\":$index}"
            )
        }

        val top3 = store.listRecent(type, resourceId, limit = 3)

        top3 shouldHaveSize 3
        top3.map { it.revisionNumber } shouldBe listOf(5L, 4L, 3L)
    }

    @Test
    fun `findById는 저장된 revision을 반환하고 없으면 null을 반환한다`() {
        val appended = store.append(
            resourceType = "rss_source",
            resourceId = "src-findbyid",
            editorId = "admin",
            editorDisplayName = null,
            changedFields = listOf("url"),
            snapshot = "{\"url\":\"https://example.com\"}"
        )

        val found = store.findById(appended.id)
        found?.resourceId shouldBe "src-findbyid"
        found?.changedFields shouldBe listOf("url")

        store.findById("non-existent-uuid").shouldBeNull()
    }

    @Test
    fun `서로 다른 resourceType_resourceId는 독립적으로 revision_number를 센다`() {
        val a = store.append("persona", "p-independent", "admin", null, emptyList(), "{}")
        val b = store.append("category", "p-independent", "admin", null, emptyList(), "{}")

        a.revisionNumber shouldBe 1L
        b.revisionNumber shouldBe 1L
    }
}
