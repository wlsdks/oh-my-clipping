package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.security.UrlSafetyValidator
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RssSourceStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ResolveCategoryTest {

    private val categoryStore = mockk<CategoryStore>()
    private val sourceStore = mockk<RssSourceStore>()
    private val urlSafetyValidator = mockk<UrlSafetyValidator>()

    private val service = CategoryService(
        categoryStore = categoryStore,
        sourceStore = sourceStore,
        urlSafetyValidator = urlSafetyValidator,
    )

    private val sampleCategory = Category(id = "cat-1", name = "AI News")

    @Test
    fun `ID로 찾기`() {
        every { categoryStore.findById("cat-1") } returns sampleCategory

        service.resolveCategory("cat-1") shouldBe sampleCategory
    }

    @Test
    fun `이름으로 찾기 (대소문자 무시)`() {
        every { categoryStore.findById("ai news") } returns null
        every { categoryStore.list() } returns listOf(sampleCategory)

        service.resolveCategory("ai news") shouldBe sampleCategory
    }

    @Test
    fun `미존재 시 NotFoundException`() {
        every { categoryStore.findById("unknown") } returns null
        every { categoryStore.list() } returns listOf(sampleCategory)

        shouldThrow<NotFoundException> {
            service.resolveCategory("unknown")
        }
    }

    @Test fun `빈 문자열 입력 시 NotFoundException`() {
        every { categoryStore.findById("") } returns null
        every { categoryStore.list() } returns listOf(sampleCategory)
        shouldThrow<NotFoundException> { service.resolveCategory("") }
    }

    @Test fun `공백 문자열 입력 시 NotFoundException`() {
        every { categoryStore.findById("  ") } returns null
        every { categoryStore.list() } returns listOf(sampleCategory)
        shouldThrow<NotFoundException> { service.resolveCategory("  ") }
    }
}
