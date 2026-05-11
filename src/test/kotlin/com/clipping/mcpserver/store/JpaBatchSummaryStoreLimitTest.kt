package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.BatchSummaryEntity
import com.clipping.mcpserver.repository.BatchSummaryRepository
import com.clipping.mcpserver.repository.RssItemRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.time.Instant

/**
 * BatchSummaryStore의 limit 적용 조회 테스트.
 * 카테고리 조건이 있어도 DB에서 limit과 정렬을 처리하는지 검증한다.
 */
class JpaBatchSummaryStoreLimitTest {

    private val repository = mockk<BatchSummaryRepository>()
    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val rssItemRepository = mockk<RssItemRepository>(relaxed = true)
    private val store = JpaBatchSummaryStore(repository, jdbc, rssItemRepository)

    @Test
    fun `카테고리 요약 조회는 최신순 Pageable로 제한 조회한다`() {
        val pageable = PageRequest.of(0, 5)
        every {
            repository.findByCategoryIdOrderByCreatedAtDesc("cat-1", pageable)
        } returns listOf(summaryEntity("summary-latest"))

        val result = store.findByCategoryId("cat-1", 5)

        result.map { it.id } shouldBe listOf("summary-latest")
        verify(exactly = 1) { repository.findByCategoryIdOrderByCreatedAtDesc("cat-1", pageable) }
        verify(exactly = 0) { repository.findByCategoryId("cat-1", pageable) }
    }

    @Test
    fun `카테고리 미발송 요약은 Pageable로 제한 조회한다`() {
        val pageable = PageRequest.of(0, 7)
        every {
            repository.findByCategoryIdAndIsSentToSlackFalseOrderByCreatedAtAsc("cat-1", pageable)
        } returns listOf(summaryEntity("summary-1"))

        val result = store.findUnsent("cat-1", 7)

        result.map { it.id } shouldBe listOf("summary-1")
        verify(exactly = 1) {
            repository.findByCategoryIdAndIsSentToSlackFalseOrderByCreatedAtAsc("cat-1", pageable)
        }
        verify(exactly = 0) { repository.findByCategoryIdAndIsSentToSlackFalse("cat-1") }
    }

    @Test
    fun `카테고리 날짜 범위 limit 조회는 Pageable로 최신순 제한 조회한다`() {
        val from = Instant.parse("2026-04-20T00:00:00Z")
        val to = Instant.parse("2026-04-26T00:00:00Z")
        val pageable = PageRequest.of(0, 3)
        every {
            repository.findByCategoryIdAndCreatedAtBetweenOrderByCreatedAtDesc("cat-1", from, to, pageable)
        } returns listOf(summaryEntity("summary-2"))

        val result = store.findByDateRange(from, to, "cat-1", limit = 3)

        result.map { it.id } shouldBe listOf("summary-2")
        verify(exactly = 1) {
            repository.findByCategoryIdAndCreatedAtBetweenOrderByCreatedAtDesc("cat-1", from, to, pageable)
        }
        verify(exactly = 0) { repository.findByCategoryIdAndCreatedAtBetween("cat-1", from, to) }
    }

    @Test
    fun `발행 기사 조회는 접근 가능한 카테고리가 없으면 항상 빈 결과 조건을 사용한다`() {
        val sqlSlot = slot<String>()
        every {
            jdbc.query(capture(sqlSlot), any<RowMapper<BatchSummaryEntity>>(), *anyVararg())
        } returns emptyList()

        val result = store.findSentArticles(categoryIds = emptyList(), limit = 20)

        result shouldBe emptyList()
        sqlSlot.captured.contains("1 = 0") shouldBe true
        sqlSlot.captured.contains("category_id IN ()") shouldBe false
    }

    @Test
    fun `발행 기사 카운트는 접근 가능한 카테고리가 없으면 항상 0 조건을 사용한다`() {
        val sqlSlot = slot<String>()
        every {
            jdbc.queryForObject(capture(sqlSlot), eq(Int::class.java), *anyVararg())
        } returns 0

        val result = store.countSentArticles(categoryIds = emptyList())

        result shouldBe 0
        sqlSlot.captured.contains("1 = 0") shouldBe true
        sqlSlot.captured.contains("category_id IN ()") shouldBe false
    }

    /**
     * 요약 조회 테스트용 최소 entity를 만든다.
     */
    private fun summaryEntity(id: String): BatchSummaryEntity =
        BatchSummaryEntity(
            id = id,
            originalTitle = "원문 제목",
            translatedTitle = "번역 제목",
            summary = "요약",
            keywords = "AI,뉴스",
            sourceLink = "https://example.com/$id",
            isSentToSlack = false,
            importanceScore = 0.8f,
            categoryId = "cat-1",
            createdAt = Instant.parse("2026-04-25T00:00:00Z"),
        )
}
