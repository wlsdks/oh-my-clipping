package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.digest.*

import com.clipping.mcpserver.config.AppProperties
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Organization
import com.clipping.mcpserver.model.OrganizationType
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryRuleStore
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * DigestPreviewService 의 sectionState 스냅샷 계산을 mock 환경에서 검증한다.
 */
class DigestPreviewServiceSectionStateTest {

    private val categoryRuleStore = mockk<CategoryRuleStore>()
    private val organizationService = mockk<OrganizationService>()
    private val summaryStore = mockk<BatchSummaryStore>()
    private val appProperties = mockk<AppProperties>(relaxed = true)

    private val service = DigestPreviewService(
        categoryRuleStore = categoryRuleStore,
        organizationService = organizationService,
        summaryStore = summaryStore,
        appProperties = appProperties,
    )

    @Test
    fun `0 keyword + 0 org 이면 mode EMPTY + sectionState 빈 리스트`() {
        val cat = "c-empty-${System.nanoTime()}"
        every { categoryRuleStore.findIncludeKeywords(cat) } returns emptyList()
        every { organizationService.findByCategoryId(cat) } returns emptyList()

        val result = service.dryRunForCategory(cat)

        result.mode shouldBe "EMPTY"
        result.sectionState shouldBe emptyList()
    }

    @Test
    fun `TOPIC_ONLY 모드는 sectionState 에 topic 섹션 포함`() {
        val cat = "c-topic-${System.nanoTime()}"
        every { categoryRuleStore.findIncludeKeywords(cat) } returns listOf("AI")
        every { organizationService.findByCategoryId(cat) } returns emptyList()
        every {
            summaryStore.findByDateRange(any(), any(), cat, any())
        } returns listOf(
            summary("s1", "AI 관련 기사", "본문 AI"),
        )

        val result = service.dryRunForCategory(cat)

        result.mode shouldBe "TOPIC_ONLY"
        // composeSections 결과 순서와 동일 — 최소한 topic 섹션이 포함되어야 한다
        result.sectionState.map { it.kind } shouldContain "topic"
    }

    @Test
    fun `articlesCount 와 isEmpty 가 정확히 계산된다`() {
        val cat = "c-count-${System.nanoTime()}"
        every { categoryRuleStore.findIncludeKeywords(cat) } returns listOf("AI")
        every { organizationService.findByCategoryId(cat) } returns emptyList()
        every {
            summaryStore.findByDateRange(any(), any(), cat, any())
        } returns listOf(
            summary("s1", "AI 뉴스 A", "AI 내용"),
            summary("s2", "AI 뉴스 B", "AI 내용"),
        )

        val result = service.dryRunForCategory(cat)

        val topic = result.sectionState.find { it.kind == "topic" }!!
        topic.articlesCount shouldBe 2
        topic.isEmpty shouldBe false
    }

    private fun summary(id: String, title: String, body: String): BatchSummary {
        return BatchSummary(
            id = id,
            originalTitle = title,
            summary = body,
            sourceLink = "https://example.com/$id",
            categoryId = "cat",
            rssItemId = "rss-$id",
            createdAt = Instant.now(),
        )
    }
}
