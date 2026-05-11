package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.ensureValid
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class SlackBlockKitTemplateService {

    data class DigestTemplateContext(
        val categoryName: String,
        val totalCandidates: Int,
        val selectedCount: Int,
        val topKeywords: String,
        val generatedAtKst: String,
        val channelId: String,
        val itemTitle: String,
        val itemSummary: String,
        val itemSourceLabel: String,
        val itemSourceLink: String,
        val itemKeywords: String,
        val itemImportance: String
    )

    data class RenderedTemplate(
        val blocks: List<Map<String, Any?>>,
        val renderedText: String
    )

    private val mapper = jacksonObjectMapper().apply { findAndRegisterModules() }
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    fun defaultTemplate(): String = DEFAULT_TEMPLATE

    fun supportedPlaceholders(): List<String> = PLACEHOLDER_KEYS.toList()

    fun sampleContext(channelId: String = "C0123456789"): DigestTemplateContext =
        DigestTemplateContext(
            categoryName = "L&D 트렌드",
            totalCandidates = 12,
            selectedCount = 1,
            topKeywords = "학습문화, 리스킬링, AI 활용",
            generatedAtKst = nowKstLabel(),
            channelId = channelId,
            itemTitle = "2026 HRD 리포트: 스킬 기반 학습이 조직 성과에 미친 영향",
            itemSummary = "핵심 사실: 스킬 기반 학습 도입 조직은 교육 참여율이 평균 22% 상승했습니다.\n\n실무 시사점: 직무별 핵심역량과 학습 콘텐츠 연결이 성과 개선에 중요합니다.",
            itemSourceLabel = "example.org",
            itemSourceLink = "https://example.org/insights/2026-hrd-report",
            itemKeywords = "학습문화, 리스킬링, AI 활용",
            itemImportance = "0.87"
        )

    fun renderTemplate(template: String, context: DigestTemplateContext): RenderedTemplate {
        val normalizedTemplate = template.trim()
        ensureValid(normalizedTemplate.isNotBlank()) { "Block Kit 템플릿 JSON이 비어 있습니다." }
        ensureValid(normalizedTemplate.length <= MAX_TEMPLATE_LENGTH) {
            "Block Kit 템플릿은 최대 ${MAX_TEMPLATE_LENGTH}자까지 허용됩니다."
        }

        val renderedJson = applyPlaceholders(
            template = normalizedTemplate,
            values = context.toPlaceholders()
        )
        val unresolved = PLACEHOLDER_PATTERN.find(renderedJson)?.value
        ensureValid(unresolved == null) { "지원하지 않는 플레이스홀더가 있습니다: $unresolved" }

        val root = try {
            mapper.readTree(renderedJson)
        } catch (e: JsonProcessingException) {
            throw InvalidInputException("Block Kit 템플릿 JSON 파싱 실패: ${e.message}")
        }

        ensureValid(root.isArray) { "Block Kit 템플릿 최상위는 JSON 배열이어야 합니다." }
        ensureValid(root.size() in 1..50) { "Block 수는 1~50개 사이여야 합니다." }

        val blocks = root.map { node ->
            val mapped = mapper.convertValue<Map<String, Any?>>(node, mapType)
            ensureValid(mapped["type"] is String) { "각 Block에는 문자열 type 필드가 필요합니다." }
            mapped
        }
        val fallbackText = buildFallbackText(context)

        return RenderedTemplate(
            blocks = blocks,
            renderedText = fallbackText
        )
    }

    private fun applyPlaceholders(template: String, values: Map<String, String>): String {
        var rendered = template
        values.forEach { (key, value) ->
            rendered = rendered.replace("{{${key}}}", jsonEscape(value))
        }
        return rendered
    }

    private fun jsonEscape(raw: String): String {
        val encoded = mapper.writeValueAsString(raw)
        return encoded.removePrefix("\"").removeSuffix("\"")
    }

    private fun buildFallbackText(context: DigestTemplateContext): String =
        buildString {
            append("${context.categoryName} 다이제스트 (${context.selectedCount}/${context.totalCandidates})")
            append("\n")
            append("- ${context.itemTitle}")
            append("\n")
            append("- ${context.itemSummary.replace("\n", " ")}")
            append("\n")
            append("- 원문: ${context.itemSourceLink}")
        }

    private fun nowKstLabel(): String =
        ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss ('KST')"))

    private fun DigestTemplateContext.toPlaceholders(): Map<String, String> =
        mapOf(
            "categoryName" to categoryName,
            "totalCandidates" to totalCandidates.toString(),
            "selectedCount" to selectedCount.toString(),
            "topKeywords" to topKeywords,
            "generatedAtKst" to generatedAtKst,
            "channelId" to channelId,
            "itemTitle" to itemTitle,
            "itemSummary" to itemSummary,
            "itemSourceLabel" to itemSourceLabel,
            "itemSourceLink" to itemSourceLink,
            "itemKeywords" to itemKeywords,
            "itemImportance" to itemImportance
        )

    companion object {
        private const val MAX_TEMPLATE_LENGTH = 12_000
        private val PLACEHOLDER_PATTERN = Regex("\\{\\{[^}]+}}")
        private val PLACEHOLDER_KEYS = listOf(
            "categoryName",
            "totalCandidates",
            "selectedCount",
            "topKeywords",
            "generatedAtKst",
            "channelId",
            "itemTitle",
            "itemSummary",
            "itemSourceLabel",
            "itemSourceLink",
            "itemKeywords",
            "itemImportance"
        )
        private val DEFAULT_TEMPLATE = """
[
  {
    "type": "header",
    "text": {
      "type": "plain_text",
      "text": "{{categoryName}} 다이제스트 ({{selectedCount}}/{{totalCandidates}})",
      "emoji": true
    }
  },
  {
    "type": "context",
    "elements": [
      {
        "type": "mrkdwn",
        "text": "{{generatedAtKst}} · 핵심 키워드 {{topKeywords}}"
      }
    ]
  },
  {
    "type": "section",
    "text": {
      "type": "mrkdwn",
      "text": "*{{itemTitle}}*\n{{itemSummary}}"
    }
  },
  {
    "type": "context",
    "elements": [
      {
        "type": "mrkdwn",
        "text": "{{itemSourceLabel}}"
      }
    ]
  },
  {
    "type": "actions",
    "elements": [
      {
        "type": "button",
        "text": {
          "type": "plain_text",
          "text": "원문 보기",
          "emoji": true
        },
        "url": "{{itemSourceLink}}",
        "action_id": "open_source"
      }
    ]
  }
]
        """.trim()
    }
}
