package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssSourceStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.net.URI

class CategoryServiceTest {

    @Test
    fun `listCategories는 소스 수를 카테고리별 반복 조회하지 않고 일괄 집계한다`() {
        val categoryStore = mockk<CategoryStore>()
        val sourceStore = mockk<RssSourceStore>(relaxed = true)
        val urlSafetyValidator = mockk<UrlSafetyValidator>(relaxed = true)
        val service = CategoryService(categoryStore, sourceStore, urlSafetyValidator)
        val categories = listOf(
            Category(id = "cat-1", name = "AI"),
            Category(id = "cat-2", name = "HR")
        )
        every { categoryStore.list() } returns categories
        every { categoryStore.countSourcesByCategoryIds(listOf("cat-1", "cat-2")) } returns mapOf(
            "cat-1" to 3,
            "cat-2" to 0
        )

        val result = service.listCategories()

        result shouldHaveSize 2
        result[0].sourceCount shouldBe 3
        result[1].sourceCount shouldBe 0
        verify(exactly = 1) { categoryStore.countSourcesByCategoryIds(listOf("cat-1", "cat-2")) }
        verify(exactly = 0) { categoryStore.countSources(any()) }
    }

    @Test
    fun `addSource should use UrlSafetyValidator normalized URL`() {
        val categoryStore = mockk<CategoryStore>()
        val sourceStore = mockk<RssSourceStore>()
        val urlSafetyValidator = mockk<UrlSafetyValidator>()
        val sourceSlot = slot<RssSource>()

        every { categoryStore.findById("cat-1") } returns Category(id = "cat-1", name = "AI")
        every {
            urlSafetyValidator.validatePublicHttpUrl("https://example.com/rss")
        } returns URI.create("https://example.com/rss")
        every { sourceStore.save(capture(sourceSlot)) } answers { sourceSlot.captured.copy(id = "src-1") }

        val service = CategoryService(categoryStore, sourceStore, urlSafetyValidator)
        val saved = service.addSource(
            name = "Example RSS",
            url = "https://example.com/rss",
            emoji = null,
            categoryId = "cat-1"
        )

        saved.id shouldBe "src-1"
        sourceSlot.captured.url shouldBe "https://example.com/rss"
        verify(exactly = 1) { urlSafetyValidator.validatePublicHttpUrl("https://example.com/rss") }
    }

    @Test
    fun `addSource should reject missing category`() {
        val categoryStore = mockk<CategoryStore>()
        val sourceStore = mockk<RssSourceStore>()
        val urlSafetyValidator = mockk<UrlSafetyValidator>()

        every { categoryStore.findById("missing") } returns null

        val service = CategoryService(categoryStore, sourceStore, urlSafetyValidator)
        val exception = shouldThrow<InvalidInputException> {
            service.addSource(
                name = "Example RSS",
                url = "https://example.com/rss",
                emoji = null,
                categoryId = "missing"
            )
        }

        exception.message shouldBe "Category not found: missing"
        verify(exactly = 0) { urlSafetyValidator.validatePublicHttpUrl(any()) }
        verify(exactly = 0) { sourceStore.save(any()) }
    }

    @Test
    fun `createCategory should reject blank category name`() {
        val categoryStore = mockk<CategoryStore>()
        val sourceStore = mockk<RssSourceStore>()
        val urlSafetyValidator = mockk<UrlSafetyValidator>()

        val service = CategoryService(categoryStore, sourceStore, urlSafetyValidator)
        val exception = shouldThrow<InvalidInputException> {
            service.createCategory(
                name = "   ",
                description = null,
                slackChannelId = null
            )
        }

        exception.message shouldBe "Category name is required"
        verify(exactly = 0) { categoryStore.findByName(any()) }
        verify(exactly = 0) { categoryStore.save(any()) }
    }
}
