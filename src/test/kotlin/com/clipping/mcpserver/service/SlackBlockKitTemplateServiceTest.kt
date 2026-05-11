package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SlackBlockKitTemplateServiceTest {

    private val service = SlackBlockKitTemplateService()

    @Nested
    inner class `defaultTemplate` {

        @Test
        fun `기본 템플릿은 비어 있지 않은 JSON 배열이다`() {
            val template = service.defaultTemplate()

            template.trim().startsWith("[") shouldBe true
            template.trim().endsWith("]") shouldBe true
            template.isNotBlank() shouldBe true
        }

        @Test
        fun `기본 템플릿에 필수 플레이스홀더가 포함되어 있다`() {
            val template = service.defaultTemplate()

            (template.contains("{{categoryName}}")) shouldBe true
            (template.contains("{{itemTitle}}")) shouldBe true
            (template.contains("{{itemSummary}}")) shouldBe true
            (template.contains("{{itemSourceLink}}")) shouldBe true
        }
    }

    @Nested
    inner class `supportedPlaceholders` {

        @Test
        fun `12개의 지원 플레이스홀더를 반환한다`() {
            val placeholders = service.supportedPlaceholders()

            placeholders shouldHaveSize 12
            placeholders shouldContain "categoryName"
            placeholders shouldContain "itemTitle"
            placeholders shouldContain "itemImportance"
        }
    }

    @Nested
    inner class `sampleContext` {

        @Test
        fun `기본 채널 ID로 샘플 컨텍스트를 생성한다`() {
            val context = service.sampleContext()

            context.channelId shouldBe "C0123456789"
            context.categoryName shouldBe "L&D 트렌드"
            context.totalCandidates shouldBe 12
            context.selectedCount shouldBe 1
        }

        @Test
        fun `커스텀 채널 ID를 적용한다`() {
            val context = service.sampleContext(channelId = "C9999999999")

            context.channelId shouldBe "C9999999999"
        }
    }

    @Nested
    inner class `renderTemplate` {

        private val context = service.sampleContext()

        @Test
        fun `기본 템플릿과 샘플 컨텍스트로 정상 렌더링된다`() {
            val result = service.renderTemplate(service.defaultTemplate(), context)

            result.blocks.isNotEmpty() shouldBe true
            (result.renderedText.contains("L&D 트렌드 다이제스트")) shouldBe true
            (result.renderedText.contains(context.itemTitle)) shouldBe true
        }

        @Test
        fun `렌더링된 블록에 플레이스홀더가 남아 있지 않다`() {
            val result = service.renderTemplate(service.defaultTemplate(), context)
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val blocksJson = mapper.writeValueAsString(result.blocks)

            // mustache 패턴 {{key}}가 남아 있지 않아야 한다
            val placeholderPattern = Regex("\\{\\{[^}]+}}")
            (placeholderPattern.containsMatchIn(blocksJson)) shouldBe false
        }

        @Test
        fun `각 블록에 type 필드가 포함되어 있다`() {
            val result = service.renderTemplate(service.defaultTemplate(), context)

            result.blocks.forEach { block ->
                (block["type"] is String) shouldBe true
            }
        }

        @Test
        fun `빈 템플릿이면 InvalidInputException이 발생한다`() {
            val exception = shouldThrow<InvalidInputException> {
                service.renderTemplate("   ", context)
            }
            (exception.message.contains("비어 있습니다")) shouldBe true
        }

        @Test
        fun `최대 길이를 초과하면 InvalidInputException이 발생한다`() {
            val longTemplate = "x".repeat(12_001)

            val exception = shouldThrow<InvalidInputException> {
                service.renderTemplate(longTemplate, context)
            }
            (exception.message.contains("최대")) shouldBe true
        }

        @Test
        fun `지원하지 않는 플레이스홀더가 있으면 InvalidInputException이 발생한다`() {
            val template = """
            [
              {
                "type": "section",
                "text": {
                  "type": "mrkdwn",
                  "text": "{{unsupported_placeholder}}"
                }
              }
            ]
            """.trimIndent()

            val exception = shouldThrow<InvalidInputException> {
                service.renderTemplate(template, context)
            }
            (exception.message.contains("지원하지 않는 플레이스홀더")) shouldBe true
            (exception.message.contains("unsupported_placeholder")) shouldBe true
        }

        @Test
        fun `유효하지 않은 JSON이면 InvalidInputException이 발생한다`() {
            val exception = shouldThrow<InvalidInputException> {
                service.renderTemplate("{not valid json", context)
            }
            (exception.message.contains("JSON 파싱 실패")) shouldBe true
        }

        @Test
        fun `최상위가 배열이 아니면 InvalidInputException이 발생한다`() {
            val objectTemplate = """{"type": "section", "text": {"type": "mrkdwn", "text": "hello"}}"""

            val exception = shouldThrow<InvalidInputException> {
                service.renderTemplate(objectTemplate, context)
            }
            (exception.message.contains("JSON 배열이어야 합니다")) shouldBe true
        }

        @Test
        fun `빈 배열이면 InvalidInputException이 발생한다`() {
            val exception = shouldThrow<InvalidInputException> {
                service.renderTemplate("[]", context)
            }
            (exception.message.contains("Block 수는 1~50개")) shouldBe true
        }

        @Test
        fun `블록에 type 필드가 없으면 InvalidInputException이 발생한다`() {
            val noTypeTemplate = """[{"text": {"type": "mrkdwn", "text": "hello"}}]"""

            val exception = shouldThrow<InvalidInputException> {
                service.renderTemplate(noTypeTemplate, context)
            }
            (exception.message.contains("type 필드가 필요합니다")) shouldBe true
        }

        @Test
        fun `51개 블록이면 InvalidInputException이 발생한다`() {
            val manyBlocks = (1..51).joinToString(",") {
                """{"type": "section", "text": {"type": "mrkdwn", "text": "item $it"}}"""
            }
            val template = "[$manyBlocks]"

            val exception = shouldThrow<InvalidInputException> {
                service.renderTemplate(template, context)
            }
            (exception.message.contains("Block 수는 1~50개")) shouldBe true
        }

        @Test
        fun `fallback 텍스트에 카테고리명과 아이템 제목이 포함된다`() {
            val result = service.renderTemplate(service.defaultTemplate(), context)

            (result.renderedText.contains("L&D 트렌드")) shouldBe true
            (result.renderedText.contains(context.itemTitle)) shouldBe true
            (result.renderedText.contains(context.itemSourceLink)) shouldBe true
        }

        @Test
        fun `JSON 특수 문자가 포함된 컨텍스트도 정상 렌더링된다`() {
            val specialContext = context.copy(
                itemTitle = "제목에 \"따옴표\"와 \\백슬래시가 포함",
                itemSummary = "줄바꿈\n포함 요약"
            )

            val template = """
            [
              {
                "type": "section",
                "text": {
                  "type": "mrkdwn",
                  "text": "{{itemTitle}} - {{itemSummary}}"
                }
              }
            ]
            """.trimIndent()

            val result = service.renderTemplate(template, specialContext)

            result.blocks shouldHaveSize 1
            result.blocks[0]["type"] shouldBe "section"
        }

        @Test
        fun `단일 블록 템플릿도 정상 렌더링된다`() {
            val singleBlock = """
            [
              {
                "type": "section",
                "text": {
                  "type": "mrkdwn",
                  "text": "{{categoryName}}: {{itemTitle}}"
                }
              }
            ]
            """.trimIndent()

            val result = service.renderTemplate(singleBlock, context)

            result.blocks shouldHaveSize 1
        }
    }
}
