package com.ohmyclipping.service

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.service.dto.admin.AiQaRequest
import com.ohmyclipping.store.BatchSummaryStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import java.time.Instant

class AiQaServiceTest {

    private val chatClient = mockk<ChatClient>()
    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val service = AiQaService(chatClient, batchSummaryStore)

    private val promptSpec = mockk<ChatClient.ChatClientRequestSpec>()
    private val callSpec = mockk<ChatClient.CallResponseSpec>()

    private val mockArticle = BatchSummary(
        id = "sum-1",
        originalTitle = "테스트 기사",
        translatedTitle = "테스트 기사 번역",
        summary = "요약 내용",
        keywords = listOf("AI", "반도체"),
        importanceScore = 0.8f,
        sourceLink = "https://example.com/1",
        categoryId = "cat-1",
        rssItemId = "rss-1",
        sentiment = "POSITIVE",
        createdAt = Instant.now(),
    )

    private fun stubChatClient(content: String?) {
        every { chatClient.prompt() } returns promptSpec
        every { promptSpec.system(any<String>()) } returns promptSpec
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callSpec
        every { callSpec.content() } returns content
    }

    private fun stubArticles(articles: List<BatchSummary> = listOf(mockArticle)) {
        every {
            batchSummaryStore.findByDateRange(any(), any(), any())
        } returns articles
    }

    @Nested
    inner class `AI Q&A 질문 처리` {

        @Test
        fun `기사 컨텍스트를 기반으로 AI 답변을 생성한다`() {
            stubArticles()
            stubChatClient(
                """{"answer": "테스트 답변입니다.", "relatedArticleIndices": [1], "relevanceReasons": ["관련됨"]}"""
            )

            val result = service.ask(AiQaRequest(question = "AI 트렌드는?"))

            result.question shouldBe "AI 트렌드는?"
            result.answer shouldBe "테스트 답변입니다."
            result.relatedArticles shouldHaveSize 1
            result.relatedArticles[0].summaryId shouldBe "sum-1"
            result.relatedArticles[0].relevanceReason shouldBe "관련됨"
            result.contextArticleCount shouldBe 1
        }

        @Test
        fun `기사가 없으면 기본 메시지를 반환한다`() {
            stubArticles(emptyList())

            val result = service.ask(AiQaRequest(question = "AI 트렌드는?"))

            result.answer shouldContain "수집된 기사가 없어"
            result.relatedArticles.shouldBeEmpty()
            result.contextArticleCount shouldBe 0
        }

        @Test
        fun `빈 질문은 예외를 발생시킨다`() {
            val exception = shouldThrow<com.ohmyclipping.error.InvalidInputException> {
                service.ask(AiQaRequest(question = "   "))
            }
            exception.message shouldContain "질문을 입력해 주세요"
        }

        @Test
        fun `조회 기간이 90일을 초과하면 예외를 발생시킨다`() {
            val exception = shouldThrow<com.ohmyclipping.error.InvalidInputException> {
                service.ask(AiQaRequest(question = "테스트", days = 91))
            }
            exception.message shouldContain "1~90일"
        }

        @Test
        fun `AI 응답 파싱 실패 시 원본 텍스트를 반환한다`() {
            stubArticles()
            stubChatClient("이것은 JSON이 아닌 일반 텍스트 응답입니다.")

            val result = service.ask(AiQaRequest(question = "AI 트렌드는?"))

            result.answer shouldBe "이것은 JSON이 아닌 일반 텍스트 응답입니다."
            result.relatedArticles.shouldBeEmpty()
            result.contextArticleCount shouldBe 1
        }

        @Test
        fun `AI 응답이 null이면 폴백 메시지를 반환한다`() {
            stubArticles()
            stubChatClient(null)

            val result = service.ask(AiQaRequest(question = "AI 트렌드는?"))

            result.answer shouldContain "AI 응답을 생성하지 못했습니다"
            result.relatedArticles.shouldBeEmpty()
            result.contextArticleCount shouldBe 1
        }
    }
}
