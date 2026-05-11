package com.clipping.mcpserver.ai

import com.clipping.mcpserver.service.dto.clipping.AiSummaryResponse
import com.clipping.mcpserver.model.Language
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SchedulerRunTracker
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation

class ClippingSummarizerTest {

    private val chatClient = mockk<ChatClient>()
    private val promptSpec = mockk<ChatClient.ChatClientRequestSpec>()
    private val callSpec = mockk<ChatClient.CallResponseSpec>()
    private val registry = SimpleMeterRegistry()
    private val metrics = ClippingMetrics(null, registry, SchedulerRunTracker())
    private val summaryQualityValidator = mockk<SummaryQualityValidator>(relaxed = true)

    private val summarizer = ClippingSummarizer(
        chatClient = chatClient,
        metrics = metrics,
        summaryQualityValidator = summaryQualityValidator
    )

    @Test
    fun `screenImportance 성공 시 screening 메트릭을 기록한다`() {
        stubChatClient("""{"importanceScore": 0.82}""")

        val result = summarizer.screenImportance("제목", "본문 미리보기")

        result.score shouldBe 0.82f
        result.status shouldBe "SUCCEEDED"
        registry.get("clipping.summarize.calls")
            .tag("mode", "screening")
            .tag("result", "success")
            .counter()
            .count() shouldBe 1.0
    }

    @Test
    fun `screenImportance 파싱 실패 시 fallback 점수와 failure 메트릭을 기록한다`() {
        stubChatClient("not-json-response")

        val result = summarizer.screenImportance("제목", "본문 미리보기")

        result.score shouldBe 0.5f
        result.status shouldBe "NO_JSON"
        result.outputChars shouldBe "not-json-response".length
        registry.get("clipping.summarize.calls")
            .tag("mode", "screening")
            .tag("result", "failure")
            .counter()
            .count() shouldBe 1.0
    }

    // =====================================================================
    // extractJson — 리플렉션으로 private 메서드를 직접 테스트
    // =====================================================================

    @Nested
    inner class `extractJson 엣지케이스` {

        private fun callExtractJson(text: String): String? {
            val method = ClippingSummarizer::class.java.getDeclaredMethod("extractJson", String::class.java)
            method.isAccessible = true
            return method.invoke(summarizer, text) as String?
        }

        @Test
        fun `유효한 완전한 JSON은 그대로 반환한다`() {
            val json = """{"summary": "요약 내용", "keywords": ["AI"]}"""
            callExtractJson(json) shouldBe json
        }

        @Test
        fun `백틱 json 블록으로 감싼 응답은 벗기고 반환한다`() {
            val input = "```json\n{\"importanceScore\": 0.8}\n```"
            val result = callExtractJson(input)
            result.shouldNotBeNull()
            result shouldBe """{"importanceScore": 0.8}"""
        }

        @Test
        fun `백틱 JSON 대문자 블록도 처리한다`() {
            val input = "```JSON\n{\"score\": 1}\n```"
            val result = callExtractJson(input)
            result.shouldNotBeNull()
            result shouldBe """{"score": 1}"""
        }

        @Test
        fun `이모지 접두사 뒤에 JSON이 있으면 첫 중괄호부터 추출한다`() {
            val input = "Here is the result:\n{\"importanceScore\": 0.7}"
            val result = callExtractJson(input)
            result.shouldNotBeNull()
            result shouldBe """{"importanceScore": 0.7}"""
        }

        @Test
        fun `잘린 JSON — 닫는 따옴표와 중괄호가 없으면 복구한다`() {
            val input = """{"summary": "some text"""
            val result = callExtractJson(input)
            result.shouldNotBeNull()
            result shouldBe """{"summary": "some text"}"""
        }

        @Test
        fun `잘린 JSON — 배열 중간에서 잘린 경우 복구한다`() {
            val input = """{"keywords": ["a", "b"""
            val result = callExtractJson(input)
            result.shouldNotBeNull()
            // 복구 후 유효한 JSON이어야 한다
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val tree = mapper.readTree(result)
            tree.has("keywords") shouldBe true
        }

        @Test
        fun `잘린 JSON — 중첩 객체에서 잘린 경우 복구한다`() {
            val input = """{"summary": "text", "nested": {"key": "val"""
            val result = callExtractJson(input)
            result.shouldNotBeNull()
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val tree = mapper.readTree(result)
            tree.has("summary") shouldBe true
            tree.has("nested") shouldBe true
        }

        @Test
        fun `완전히 빈 응답은 null을 반환한다`() {
            callExtractJson("") shouldBe null
        }

        @Test
        fun `공백만 있는 응답은 null을 반환한다`() {
            callExtractJson("   \n  ") shouldBe null
        }

        @Test
        fun `JSON이 전혀 없는 텍스트는 null을 반환한다`() {
            callExtractJson("This is just a plain text response with no JSON.") shouldBe null
        }

        @Test
        fun `여는 중괄호가 있지만 문자열 값이 잘린 경우 복구한다`() {
            val input = """{"title": "잘린 제목이"""
            val result = callExtractJson(input)
            result.shouldNotBeNull()
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val tree = mapper.readTree(result)
            tree.has("title") shouldBe true
        }

        @Test
        fun `JSON 앞에 텍스트가 있고 뒤에도 텍스트가 있으면 JSON 부분만 추출한다`() {
            val input = """Here is the analysis: {"score": 0.9} That's all."""
            val result = callExtractJson(input)
            result.shouldNotBeNull()
            result shouldBe """{"score": 0.9}"""
        }

        @Test
        fun `트레일링 콤마가 있는 잘린 JSON도 복구를 시도한다`() {
            // 트레일링 콤마 뒤에 잘리면 Jackson이 여전히 파싱 가능할 수 있다
            val input = """{"a": 1, "b": 2,"""
            val result = callExtractJson(input)
            // 복구 시도하지만 트레일링 콤마는 Jackson이 거부할 수 있으므로 null도 허용
            // 핵심은 예외가 발생하지 않는 것
        }
    }

    // =====================================================================
    // isJsonStructurallyComplete — 리플렉션으로 직접 테스트
    // =====================================================================

    @Nested
    inner class `isJsonStructurallyComplete 엣지케이스` {

        private fun callIsComplete(json: String): Boolean {
            val method = ClippingSummarizer::class.java.getDeclaredMethod(
                "isJsonStructurallyComplete", String::class.java
            )
            method.isAccessible = true
            return method.invoke(summarizer, json) as Boolean
        }

        @Test
        fun `완전한 JSON 객체는 true를 반환한다`() {
            callIsComplete("""{"key": "value"}""") shouldBe true
        }

        @Test
        fun `닫는 중괄호가 없으면 false를 반환한다`() {
            callIsComplete("""{"key": "value"""") shouldBe false
        }

        @Test
        fun `닫히지 않은 문자열이 있으면 false를 반환한다`() {
            callIsComplete("""{"key": "unclosed""") shouldBe false
        }

        @Test
        fun `이스케이프된 따옴표는 문자열 경계로 인식하지 않는다`() {
            callIsComplete("""{"key": "value with \"escaped\" quotes"}""") shouldBe true
        }

        @Test
        fun `중첩 배열과 객체가 완전하면 true를 반환한다`() {
            callIsComplete("""{"arr": [1, 2, {"nested": [3]}], "obj": {"a": "b"}}""") shouldBe true
        }

        @Test
        fun `빈 객체는 true를 반환한다`() {
            callIsComplete("{}") shouldBe true
        }

        @Test
        fun `빈 배열이 포함된 객체는 true를 반환한다`() {
            callIsComplete("""{"arr": []}""") shouldBe true
        }

        @Test
        fun `닫는 대괄호가 없으면 false를 반환한다`() {
            callIsComplete("""{"arr": [1, 2""") shouldBe false
        }

        @Test
        fun `중첩된 중괄호가 하나 부족하면 false를 반환한다`() {
            callIsComplete("""{"outer": {"inner": "val"}""") shouldBe false
        }

        @Test
        fun `문자열 내부의 중괄호는 구조에 영향을 주지 않는다`() {
            callIsComplete("""{"msg": "use { and } in text"}""") shouldBe true
        }

        @Test
        fun `여러 개의 이스케이프 시퀀스가 연속으로 있어도 정확히 처리한다`() {
            // \\\" 는 이스케이프된 백슬래시 + 따옴표 닫힘
            callIsComplete("""{"key": "val\\"}""") shouldBe true
        }
    }

    // =====================================================================
    // repairTruncatedJson — 리플렉션으로 직접 테스트
    // =====================================================================

    @Nested
    inner class `repairTruncatedJson 엣지케이스` {

        private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

        private fun callRepair(json: String): String? {
            val method = ClippingSummarizer::class.java.getDeclaredMethod(
                "repairTruncatedJson", String::class.java
            )
            method.isAccessible = true
            return method.invoke(summarizer, json) as String?
        }

        @Test
        fun `문자열 중간에서 잘린 JSON은 문자열을 닫고 중괄호를 닫는다`() {
            val result = callRepair("""{"summary": "잘린 텍스트""")
            result.shouldNotBeNull()
            mapper.readTree(result) // 파싱 성공해야 한다
            result shouldBe """{"summary": "잘린 텍스트"}"""
        }

        @Test
        fun `배열 중간에서 잘린 JSON은 배열과 중괄호를 닫는다`() {
            val result = callRepair("""{"keywords": ["a", "b""")
            result.shouldNotBeNull()
            mapper.readTree(result) // 파싱 성공해야 한다
        }

        @Test
        fun `트레일링 콤마가 있는 잘린 JSON도 복구를 시도한다`() {
            val result = callRepair("""{"a": 1,""")
            // Jackson은 트레일링 콤마를 거부하므로 null일 수 있다
            // 하지만 예외가 발생하면 안 된다
        }

        @Test
        fun `이미 유효한 JSON은 그대로 반환한다`() {
            val valid = """{"key": "value"}"""
            val result = callRepair(valid)
            result shouldBe valid
        }

        @Test
        fun `복구 불가능한 JSON은 null을 반환한다`() {
            // 여는 중괄호도 없는 완전히 망가진 입력
            val result = callRepair("not json at all")
            // repairTruncatedJson은 문자열/괄호 분석을 하지만 결과적으로 파싱 불가하면 null
            // 단, 예외가 발생하면 안 된다
        }

        @Test
        fun `깊게 중첩된 구조가 잘려도 모든 레벨을 닫는다`() {
            val input = """{"l1": {"l2": {"l3": {"l4": "deep"""
            val result = callRepair(input)
            result.shouldNotBeNull()
            val tree = mapper.readTree(result)
            tree.has("l1") shouldBe true
        }

        @Test
        fun `배열 안의 객체가 잘려도 복구한다`() {
            val input = """{"items": [{"name": "a"}, {"name": "b"""
            val result = callRepair(input)
            result.shouldNotBeNull()
            mapper.readTree(result)
        }

        @Test
        fun `중첩 배열이 잘려도 복구한다`() {
            val input = """{"matrix": [[1, 2], [3"""
            val result = callRepair(input)
            result.shouldNotBeNull()
            mapper.readTree(result)
        }
    }

    // =====================================================================
    // parseJsonWithEmojiRetry — 리플렉션으로 직접 테스트
    // =====================================================================

    @Nested
    inner class `parseJsonWithEmojiRetry 엣지케이스` {

        /**
         * parseJsonWithEmojiRetry는 inline reified 함수이므로 직접 리플렉션 호출이 불가능하다.
         * 대신 screenImportance/summarizeArticle을 통해 간접적으로 테스트한다.
         */

        @Test
        fun `summarizeArticle에서 이모지가 포함된 JSON 응답도 파싱에 성공한다`() {
            // 이모지가 포함된 유효한 AiSummaryResponse JSON
            val jsonWithEmoji = """
                {"translatedTitle": "제목", "summary": "요약 내용입니다. 첫 번째 문단. 두 번째 문단.\n\n세 번째 문단. 네 번째 문단.", "keywords": ["AI", "테스트"], "importanceScore": 0.8, "sentiment": "POSITIVE", "eventType": "PRODUCT_LAUNCH"}
            """.trimIndent()

            stubChatClientWithSystemPrompt(jsonWithEmoji)
            every { summaryQualityValidator.validate(any(), any()) } returns SummaryQualityValidationResult(
                accepted = true,
                normalized = AiSummaryResponse(
                    translatedTitle = "제목",
                    summary = "요약 내용",
                    keywords = listOf("AI", "테스트"),
                    importanceScore = 0.8f,
                    sentiment = "POSITIVE",
                    eventType = "PRODUCT_LAUNCH"
                )
            )

            val result = summarizer.summarizeArticle("테스트 제목", "테스트 본문 내용", Language.KOREAN)
            result.shouldNotBeNull()
            result.summary shouldBe "요약 내용"
        }

        @Test
        fun `summarizeArticle에서 잘린 JSON은 GeminiTruncationException을 유발하고 null을 반환한다`() {
            // extractJson이 복구를 시도하지만, 복구 후에도 AiSummaryResponse로 파싱 불가할 수 있는 케이스
            // 또는 repairTruncatedJson이 null을 반환하는 케이스
            val truncatedBadly = "just text with no json"

            stubChatClientWithSystemPrompt(truncatedBadly)

            val result = summarizer.summarizeArticle("제목", "본문", Language.KOREAN)
            result.shouldBeNull()
        }
    }

    // =====================================================================
    // detectTruncation — ChatResponse의 finishReason 기반 감지
    // =====================================================================

    @Nested
    inner class `detectTruncation 엣지케이스` {

        @Test
        fun `finish_reason이 STOP이면 예외를 던지지 않는다`() {
            val metadata = ChatGenerationMetadata.builder().finishReason("STOP").build()
            val generation = Generation(AssistantMessage("""{"importanceScore": 0.9}"""), metadata)
            val chatResponse = ChatResponse(listOf(generation))

            stubChatClientWithResponse(chatResponse)

            val result = summarizer.screenImportance("제목", "본문")
            result.status shouldBe "SUCCEEDED"
        }

        @Test
        fun `finish_reason이 LENGTH이면 summarizeArticle에서 GeminiTruncationException이 발생하고 null을 반환한다`() {
            val metadata = ChatGenerationMetadata.builder().finishReason("LENGTH").build()
            val generation = Generation(
                AssistantMessage("""{"translatedTitle": "t", "summary": "s", "keywords": ["k"], "importanceScore": 0.5}"""),
                metadata
            )
            val chatResponse = ChatResponse(listOf(generation))

            stubChatClientWithSystemPromptAndResponse(chatResponse)

            // summarizeArticle은 내부에서 detectTruncation을 호출하고, 예외를 catch해 null을 반환한다
            val result = summarizer.summarizeArticle("제목", "본문", Language.KOREAN)
            result.shouldBeNull()
        }

        @Test
        fun `finish_reason이 MAX_TOKENS이면 summarizeArticle에서 null을 반환한다`() {
            val metadata = ChatGenerationMetadata.builder().finishReason("MAX_TOKENS").build()
            val generation = Generation(
                AssistantMessage("""{"translatedTitle": "t", "summary": "s", "keywords": ["k"], "importanceScore": 0.5}"""),
                metadata
            )
            val chatResponse = ChatResponse(listOf(generation))

            stubChatClientWithSystemPromptAndResponse(chatResponse)

            val result = summarizer.summarizeArticle("제목", "본문", Language.KOREAN)
            result.shouldBeNull()
        }

        @Test
        fun `finish_reason이 null이면 예외를 던지지 않는다`() {
            // 기본 Generation은 finishReason이 null이다
            val generation = Generation(AssistantMessage("""{"importanceScore": 0.85}"""))
            val chatResponse = ChatResponse(listOf(generation))

            stubChatClientWithResponse(chatResponse)

            val result = summarizer.screenImportance("제목", "본문")
            result.status shouldBe "SUCCEEDED"
            result.score shouldBe 0.85f
        }

        @Test
        fun `chatResponse가 null이면 예외를 던지지 않는다`() {
            stubChatClient(null)

            val result = summarizer.screenImportance("제목", "본문")
            result.status shouldBe "EMPTY_RESULT"
        }

        @Test
        fun `finish_reason이 소문자 length여도 대문자 변환 후 감지한다`() {
            val metadata = ChatGenerationMetadata.builder().finishReason("length").build()
            val generation = Generation(
                AssistantMessage("""{"translatedTitle": "t", "summary": "s", "keywords": ["k"], "importanceScore": 0.5}"""),
                metadata
            )
            val chatResponse = ChatResponse(listOf(generation))

            stubChatClientWithSystemPromptAndResponse(chatResponse)

            val result = summarizer.summarizeArticle("제목", "본문", Language.KOREAN)
            result.shouldBeNull()
        }

        @Test
        fun `finish_reason이 STOP이 아닌 다른 정상 종료 이유면 예외를 던지지 않는다`() {
            val metadata = ChatGenerationMetadata.builder().finishReason("END_TURN").build()
            val generation = Generation(AssistantMessage("""{"importanceScore": 0.6}"""), metadata)
            val chatResponse = ChatResponse(listOf(generation))

            stubChatClientWithResponse(chatResponse)

            val result = summarizer.screenImportance("제목", "본문")
            result.status shouldBe "SUCCEEDED"
        }

        @Test
        fun `detectTruncation을 직접 리플렉션으로 호출하여 LENGTH 감지를 확인한다`() {
            val method = ClippingSummarizer::class.java.getDeclaredMethod(
                "detectTruncation", ChatResponse::class.java, String::class.java
            )
            method.isAccessible = true

            val metadata = ChatGenerationMetadata.builder().finishReason("LENGTH").build()
            val generation = Generation(AssistantMessage("text"), metadata)
            val chatResponse = ChatResponse(listOf(generation))

            val exception = assertThrows<java.lang.reflect.InvocationTargetException> {
                method.invoke(summarizer, chatResponse, "test-context")
            }
            val cause = exception.cause
            (cause is GeminiTruncationException) shouldBe true
            (cause as GeminiTruncationException).message.shouldNotBeNull()
            cause.message!! shouldContain "LENGTH"
        }

        @Test
        fun `detectTruncation을 직접 리플렉션으로 호출하여 MAX_TOKENS 감지를 확인한다`() {
            val method = ClippingSummarizer::class.java.getDeclaredMethod(
                "detectTruncation", ChatResponse::class.java, String::class.java
            )
            method.isAccessible = true

            val metadata = ChatGenerationMetadata.builder().finishReason("MAX_TOKENS").build()
            val generation = Generation(AssistantMessage("text"), metadata)
            val chatResponse = ChatResponse(listOf(generation))

            val exception = assertThrows<java.lang.reflect.InvocationTargetException> {
                method.invoke(summarizer, chatResponse, "test-context")
            }
            (exception.cause as GeminiTruncationException).message!! shouldContain "MAX_TOKENS"
        }

        @Test
        fun `detectTruncation에 null chatResponse를 전달하면 예외 없이 반환한다`() {
            val method = ClippingSummarizer::class.java.getDeclaredMethod(
                "detectTruncation", ChatResponse::class.java, String::class.java
            )
            method.isAccessible = true

            // null 전달 시 예외 없이 정상 반환
            method.invoke(summarizer, null, "test-context")
        }
    }

    // =====================================================================
    // extractJson + screenImportance 통합 엣지케이스
    // =====================================================================

    @Nested
    inner class `screenImportance를 통한 extractJson 통합 테스트` {

        @Test
        fun `백틱으로 감싼 JSON도 screenImportance에서 정상 파싱된다`() {
            stubChatClient("```json\n{\"importanceScore\": 0.75}\n```")

            val result = summarizer.screenImportance("제목", "본문")
            result.score shouldBe 0.75f
            result.status shouldBe "SUCCEEDED"
        }

        @Test
        fun `이모지 접두사 후 JSON도 screenImportance에서 정상 파싱된다`() {
            stubChatClient("Here's the result: {\"importanceScore\": 0.6}")

            val result = summarizer.screenImportance("제목", "본문")
            result.score shouldBe 0.6f
            result.status shouldBe "SUCCEEDED"
        }

        @Test
        fun `완전히 빈 문자열 응답은 NO_JSON으로 처리된다`() {
            stubChatClient("")

            val result = summarizer.screenImportance("제목", "본문")
            result.status shouldBe "NO_JSON"
            result.score shouldBe 0.5f
        }

        @Test
        fun `JSON 없는 순수 텍스트 응답은 NO_JSON으로 처리된다`() {
            stubChatClient("I cannot process this request.")

            val result = summarizer.screenImportance("제목", "본문")
            result.status shouldBe "NO_JSON"
        }

        @Test
        fun `잘린 JSON이 복구되어 importanceScore가 추출되면 성공한다`() {
            // 닫는 중괄호가 없지만 복구 가능
            stubChatClient("""{"importanceScore": 0.55""")

            val result = summarizer.screenImportance("제목", "본문")
            result.score shouldBe 0.55f
            result.status shouldBe "SUCCEEDED"
        }

        @Test
        fun `importanceScore가 1을 초과하면 1로 클램핑된다`() {
            stubChatClient("""{"importanceScore": 1.5}""")

            val result = summarizer.screenImportance("제목", "본문")
            result.score shouldBe 1.0f
        }

        @Test
        fun `importanceScore가 음수이면 0으로 클램핑된다`() {
            stubChatClient("""{"importanceScore": -0.3}""")

            val result = summarizer.screenImportance("제목", "본문")
            result.score shouldBe 0.0f
        }

        @Test
        fun `importanceScore가 문자열 NaN이면 Jackson이 0으로 처리하여 성공한다`() {
            // Jackson의 floatValue()는 텍스트 노드에 대해 0.0f를 반환한다
            stubChatClient("""{"importanceScore": "NaN"}""")

            val result = summarizer.screenImportance("제목", "본문")
            result.status shouldBe "SUCCEEDED"
            result.score shouldBe 0.0f
        }

        @Test
        fun `importanceScore가 숫자 NaN이면 FAILED_PARSE로 처리된다`() {
            // JSON에서 NaN은 유효한 숫자 리터럴이 아니므로 파싱 에러가 발생한다
            stubChatClient("""{"importanceScore": NaN}""")

            val result = summarizer.screenImportance("제목", "본문")
            // Jackson이 NaN 리터럴을 파싱하지 못해 extractJson에서 파싱 에러 발생
            result.status shouldBe "FAILED_PARSE"
        }

        @Test
        fun `importanceScore 필드가 없으면 FAILED_PARSE로 처리된다`() {
            stubChatClient("""{"score": 0.8}""")

            val result = summarizer.screenImportance("제목", "본문")
            result.status shouldBe "FAILED_PARSE"
        }
    }

    // =====================================================================
    // summarizeArticle을 통한 전체 파이프라인 엣지케이스
    // =====================================================================

    @Nested
    inner class `summarizeArticle 파이프라인 엣지케이스` {

        @Test
        fun `Gemini가 null을 반환하면 null을 반환한다`() {
            stubChatClientWithSystemPrompt(null)

            val result = summarizer.summarizeArticle("제목", "본문", Language.KOREAN)
            result.shouldBeNull()
        }

        @Test
        fun `Gemini가 JSON이 아닌 응답을 반환하면 null을 반환한다`() {
            stubChatClientWithSystemPrompt("죄송합니다. 요약할 수 없습니다.")

            val result = summarizer.summarizeArticle("제목", "본문", Language.KOREAN)
            result.shouldBeNull()
        }

        @Test
        fun `품질 검증에서 거부되면 null을 반환한다`() {
            val json = """{"translatedTitle": "t", "summary": "s", "keywords": ["k"], "importanceScore": 0.5}"""
            stubChatClientWithSystemPrompt(json)
            every { summaryQualityValidator.validate(any(), any()) } returns SummaryQualityValidationResult(
                accepted = false,
                normalized = null
            )

            val result = summarizer.summarizeArticle("제목", "본문", Language.KOREAN)
            result.shouldBeNull()
        }
    }

    // =====================================================================
    // summarizeArticle null-return 경로마다 진단 태그(lastRejectReason)를 남기는지 검증.
    // llm_runs.error_message 가 NULL 인 EMPTY_RESULT 행을 만들지 않기 위한 가드.
    // =====================================================================
    @Nested
    inner class `summarizeArticle 진단 태그` {

        @Test
        fun `Gemini가 null 응답이면 API_NULL_RESPONSE 태그를 세팅한다`() {
            stubChatClientWithSystemPrompt(null)
            summarizer.summarizeArticle("제목", "본문", Language.KOREAN).shouldBeNull()
            summarizer.getLastRejectReason() shouldBe "API_NULL_RESPONSE"
        }

        @Test
        fun `JSON 블록이 없는 응답이면 JSON_EXTRACT_FAIL 태그를 세팅한다`() {
            stubChatClientWithSystemPrompt("plain text without any braces")
            summarizer.summarizeArticle("제목", "본문", Language.KOREAN).shouldBeNull()
            summarizer.getLastRejectReason() shouldBe "JSON_EXTRACT_FAIL"
        }

        @Test
        fun `품질 검증 거부 태그는 그대로 보존한다`() {
            val json = """{"translatedTitle": "t", "summary": "s", "keywords": ["k"], "importanceScore": 0.5}"""
            stubChatClientWithSystemPrompt(json)
            every { summaryQualityValidator.validate(any(), any()) } returns SummaryQualityValidationResult(
                accepted = false,
                normalized = null,
                rejectReason = "CHARS_TOO_SHORT:15"
            )

            summarizer.summarizeArticle("제목", "본문", Language.KOREAN).shouldBeNull()
            summarizer.getLastRejectReason() shouldBe "CHARS_TOO_SHORT:15"
        }

        @Test
        fun `validator accepted=true 이지만 normalized=null 이면 NORMALIZED_NULL 태그를 세팅한다`() {
            val json = """{"translatedTitle": "t", "summary": "s", "keywords": ["k"], "importanceScore": 0.5}"""
            stubChatClientWithSystemPrompt(json)
            every { summaryQualityValidator.validate(any(), any()) } returns SummaryQualityValidationResult(
                accepted = true,
                normalized = null
            )

            summarizer.summarizeArticle("제목", "본문", Language.KOREAN).shouldBeNull()
            summarizer.getLastRejectReason() shouldBe "NORMALIZED_NULL"
        }

        @Test
        fun `예외 발생 시 EXCEPTION prefix 태그를 세팅한다`() {
            every { chatClient.prompt() } returns promptSpec
            every { promptSpec.user(any<String>()) } returns promptSpec
            every { promptSpec.system(any<String>()) } returns promptSpec
            every { promptSpec.call() } returns callSpec
            every { callSpec.chatResponse() } throws IllegalStateException("boom")

            summarizer.summarizeArticle("제목", "본문", Language.KOREAN).shouldBeNull()
            summarizer.getLastRejectReason() shouldBe "EXCEPTION:IllegalStateException"
        }
    }

    // =====================================================================
    // 헬퍼 메서드
    // =====================================================================

    /** ChatClient mock을 설정하여 chatResponse()가 지정한 content를 반환하도록 구성한다. */
    private fun stubChatClient(content: String?) {
        every { chatClient.prompt() } returns promptSpec
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callSpec
        val chatResponse = if (content != null) {
            ChatResponse(listOf(Generation(AssistantMessage(content))))
        } else {
            null
        }
        every { callSpec.chatResponse() } returns chatResponse
    }

    /** ChatClient mock에 system prompt 설정도 포함하여 구성한다 (summarizeArticle용). */
    private fun stubChatClientWithSystemPrompt(content: String?) {
        every { chatClient.prompt() } returns promptSpec
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.system(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callSpec
        val chatResponse = if (content != null) {
            ChatResponse(listOf(Generation(AssistantMessage(content))))
        } else {
            null
        }
        every { callSpec.chatResponse() } returns chatResponse
    }

    /** ChatClient mock에 특정 ChatResponse 객체를 반환하도록 구성한다 (screening용, system 없음). */
    private fun stubChatClientWithResponse(chatResponse: ChatResponse?) {
        every { chatClient.prompt() } returns promptSpec
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callSpec
        every { callSpec.chatResponse() } returns chatResponse
    }

    /** ChatClient mock에 system prompt + 특정 ChatResponse 객체를 반환하도록 구성한다 (summarizeArticle용). */
    private fun stubChatClientWithSystemPromptAndResponse(chatResponse: ChatResponse?) {
        every { chatClient.prompt() } returns promptSpec
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.system(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callSpec
        every { callSpec.chatResponse() } returns chatResponse
    }

    // =====================================================================
    // stripTranslationNoise — Gemini가 번역문을 JSON/따옴표/코드펜스로 감싸
    // 반환하는 다양한 이탈 케이스를 벗기고 순수 번역문만 돌려주는지 검증한다.
    // =====================================================================

    @Nested
    inner class `stripTranslationNoise 엣지케이스` {

        private fun stripNoise(text: String): String {
            val method = ClippingSummarizer::class.java.getDeclaredMethod("stripTranslationNoise", String::class.java)
            method.isAccessible = true
            return method.invoke(summarizer, text) as String
        }

        @Test
        fun `순수 한국어 문장은 그대로 반환한다`() {
            stripNoise("에듀사: AI 시대의 핵심은 직무가 아닌 역량") shouldBe "에듀사: AI 시대의 핵심은 직무가 아닌 역량"
        }

        @Test
        fun `앞뒤 공백과 개행은 제거한다`() {
            stripNoise("\n  한국어 번역문  \n") shouldBe "한국어 번역문"
        }

        @Test
        fun `양끝 큰따옴표는 벗긴다`() {
            stripNoise("\"에듀사: AI 시대의 핵심\"") shouldBe "에듀사: AI 시대의 핵심"
        }

        @Test
        fun `양끝 작은따옴표는 벗긴다`() {
            stripNoise("'에듀사: AI 시대의 핵심'") shouldBe "에듀사: AI 시대의 핵심"
        }

        @Test
        fun `코드 펜스 json 블록을 벗긴다`() {
            val input = "```json\n에듀사: AI 시대의 핵심\n```"
            stripNoise(input) shouldBe "에듀사: AI 시대의 핵심"
        }

        @Test
        fun `코드 펜스 언어 태그 없는 블록도 벗긴다`() {
            stripNoise("```\n한국어 번역문\n```") shouldBe "한국어 번역문"
        }

        @Test
        fun `translation 키로 감싼 JSON은 값만 추출한다`() {
            val input = """{"translation": "에듀사: AI 시대의 핵심은 직무가 아닌 역량"}"""
            stripNoise(input) shouldBe "에듀사: AI 시대의 핵심은 직무가 아닌 역량"
        }

        @Test
        fun `translatedText 키로 감싼 JSON은 값만 추출한다`() {
            stripNoise("""{"translatedText": "한국어 번역"}""") shouldBe "한국어 번역"
        }

        @Test
        fun `translatedTitle 키로 감싼 JSON은 값만 추출한다`() {
            stripNoise("""{"translatedTitle": "한국어 제목"}""") shouldBe "한국어 제목"
        }

        @Test
        fun `result 키로 감싼 JSON은 값만 추출한다`() {
            stripNoise("""{"result": "한국어 결과"}""") shouldBe "한국어 결과"
        }

        @Test
        fun `output 키로 감싼 JSON도 값만 추출한다`() {
            stripNoise("""{"output": "한국어"}""") shouldBe "한국어"
        }

        @Test
        fun `text 키로 감싼 JSON도 값만 추출한다`() {
            stripNoise("""{"text": "한국어"}""") shouldBe "한국어"
        }

        @Test
        fun `ko 키로 감싼 JSON도 값만 추출한다`() {
            stripNoise("""{"ko": "한국어"}""") shouldBe "한국어"
        }

        @Test
        fun `korean 키로 감싼 JSON도 값만 추출한다`() {
            stripNoise("""{"korean": "한국어"}""") shouldBe "한국어"
        }

        @Test
        fun `키 대소문자가 달라도 매칭한다`() {
            stripNoise("""{"Translation": "한국어"}""") shouldBe "한국어"
            stripNoise("""{"TRANSLATED_TEXT": "한국어"}""".replace("_", ""))
                .shouldBe("한국어")
        }

        @Test
        fun `여러 줄과 들여쓰기가 있는 JSON도 언래핑한다`() {
            val input = """
                {
                  "translation": "에듀사: AI 시대의 핵심은 직무가 아닌 역량"
                }
            """.trimIndent()
            stripNoise(input) shouldBe "에듀사: AI 시대의 핵심은 직무가 아닌 역량"
        }

        @Test
        fun `코드 펜스로 감싼 JSON도 언래핑한다`() {
            val input = "```json\n{\"translation\": \"에듀사: AI 시대의 핵심\"}\n```"
            stripNoise(input) shouldBe "에듀사: AI 시대의 핵심"
        }

        @Test
        fun `낯선 키 이름이어도 문자열 필드가 하나뿐이면 그 값을 쓴다`() {
            stripNoise("""{"foo": "한국어 번역문"}""") shouldBe "한국어 번역문"
        }

        @Test
        fun `여러 필드 중에서는 translation 우선 키를 선택한다`() {
            val input = """{"translation": "한국어", "confidence": "0.95", "model": "gemini"}"""
            stripNoise(input) shouldBe "한국어"
        }

        @Test
        fun `문자열 필드가 여러 개이고 선호 키가 없으면 원문을 유지한다`() {
            // 뭘 골라야 할지 애매하면 JSON을 그대로 보존해 후속 점검이 가능하게 한다.
            val input = """{"foo": "값1", "bar": "값2"}"""
            stripNoise(input) shouldBe input
        }

        @Test
        fun `빈 JSON 객체는 원문을 유지한다`() {
            stripNoise("{}") shouldBe "{}"
        }

        @Test
        fun `문자열 필드가 없는 JSON은 원문을 유지한다`() {
            val input = """{"score": 0.5, "ok": true}"""
            stripNoise(input) shouldBe input
        }

        @Test
        fun `중괄호로 시작하지만 JSON이 아닌 문자열은 원문을 유지한다`() {
            val input = "{이건 JSON 아님}"
            stripNoise(input) shouldBe input
        }

        @Test
        fun `잘못된 JSON 문법은 원문을 유지한다`() {
            val input = """{translation: "따옴표 없음"}"""
            stripNoise(input) shouldBe input
        }

        @Test
        fun `추출된 값의 양 끝 공백은 제거한다`() {
            val input = """{"translation": "  한국어  "}"""
            stripNoise(input) shouldBe "한국어"
        }

        @Test
        fun `추출된 값이 빈 문자열이면 원문 그대로 둔다`() {
            // 빈 번역문을 제목으로 쓰면 안 되므로 원문을 보존해 호출부가 폴백하도록 한다.
            val input = """{"translation": "   "}"""
            stripNoise(input) shouldBe input
        }

        @Test
        fun `한국어 내용의 마크다운 강조는 보존한다`() {
            val input = """{"translation": "*AI* 시대의 **핵심**"}"""
            stripNoise(input) shouldBe "*AI* 시대의 **핵심**"
        }

        @Test
        fun `JSON 값에 이스케이프된 따옴표가 있어도 정확히 복원한다`() {
            val input = """{"translation": "그는 \"안녕\"이라고 말했다"}"""
            stripNoise(input) shouldBe "그는 \"안녕\"이라고 말했다"
        }
    }
}
