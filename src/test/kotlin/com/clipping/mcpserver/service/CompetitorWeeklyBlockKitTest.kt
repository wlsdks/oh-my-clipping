package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.CompetitorHighlight
import com.clipping.mcpserver.service.port.CompetitorWeeklyInsight
import com.clipping.mcpserver.service.competitor.CompetitorWeeklyBlockKit
import com.clipping.mcpserver.service.dto.CompetitorTimelineItem
import com.clipping.mcpserver.service.dto.SovPeriod
import com.clipping.mcpserver.service.dto.SovResponse
import com.clipping.mcpserver.service.dto.SovShareItem
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CompetitorWeeklyBlockKitTest {

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private fun makeSovResponse(vararg names: String): SovResponse {
        val total = names.size * 10
        val shares = names.mapIndexed { idx, name ->
            SovShareItem(
                competitorId = "c-$idx",
                name = name,
                count = 10,
                share = 1.0 / names.size,
                shareDelta = if (idx == 0) 0.05 else null
            )
        }
        return SovResponse(
            period = SovPeriod("2026-04-05", "2026-04-11"),
            totalArticles = total,
            shares = shares
        )
    }

    private fun makeArticle(title: String, competitorName: String, score: Float = 0.8f) =
        CompetitorTimelineItem(
            summaryId = "s-$title",
            competitorId = "cid",
            competitorName = competitorName,
            title = title,
            summary = "요약",
            keywords = emptyList(),
            sourceLink = "https://example.com/$title",
            importanceScore = score,
            eventType = null,
            sentiment = null,
            createdAt = "2026-04-10T12:00:00Z"
        )

    private fun makeInsight(vararg names: String) = CompetitorWeeklyInsight(
        competitorHighlights = names.map { CompetitorHighlight(it, "$it 주요 동향 요약") },
        weeklyInsight = "이번 주 전체 경쟁 환경 인사이트"
    )

    // ── 테스트 ─────────────────────────────────────────────────────────────────

    @Nested
    inner class `전체 데이터 포함 메시지` {

        @Test
        fun `SOV + 기사 + AI 인사이트가 모두 포함된 메시지를 생성한다`() {
            val sov = makeSovResponse("A사", "B사")
            val topArticles = mapOf(
                "A사" to listOf(makeArticle("기사1", "A사"), makeArticle("기사2", "A사")),
                "B사" to listOf(makeArticle("기사3", "B사"))
            )
            val insight = makeInsight("A사", "B사")

            val (fallback, blocks) = CompetitorWeeklyBlockKit.build(
                sov = sov,
                topArticles = topArticles,
                aiInsight = insight,
                periodLabel = "2026-04-05 ~ 2026-04-11",
                webUrl = "https://app.example.com/competitor"
            )

            // fallback 텍스트 포함 확인
            fallback shouldContain "2026-04-05 ~ 2026-04-11"
            fallback shouldContain "건"

            // 헤더 블록 존재 확인
            val header = blocks.first()
            header["type"] shouldBe "header"
            @Suppress("UNCHECKED_CAST")
            (header["text"] as Map<String, Any?>)["text"].toString() shouldContain "경쟁사 주간 요약"

            // SOV 섹션 텍스트에 경쟁사명 포함 확인
            val sovBlock = blocks.find { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("Share of Voice")
            }
            requireNotNull(sovBlock) { "SOV 섹션이 없습니다" }
            @Suppress("UNCHECKED_CAST")
            val sovText = (sovBlock["text"] as Map<String, Any?>)["text"].toString()
            sovText shouldContain "A사"
            sovText shouldContain "B사"
            sovText shouldContain ":arrow_up:"

            // 기사 섹션 확인 (A사, B사)
            val articleBlocks = blocks.filter { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    run {
                        val text = (block["text"] as? Map<String, Any?>)?.get("text").toString()
                        text.contains("기사1") || text.contains("기사3")
                    }
            }
            articleBlocks.size shouldBe 2

            // AI 인사이트 섹션 확인
            val insightBlock = blocks.find { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("AI 인사이트")
            }
            requireNotNull(insightBlock) { "인사이트 섹션이 없습니다" }
            @Suppress("UNCHECKED_CAST")
            val insightText = (insightBlock["text"] as Map<String, Any?>)["text"].toString()
            insightText shouldContain "이번 주 전체 경쟁 환경 인사이트"

            // CTA 링크 섹션 확인
            val ctaBlock = blocks.find { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("웹에서 전체 보기")
            }
            requireNotNull(ctaBlock) { "CTA 링크 섹션이 없습니다" }
        }
    }

    @Nested
    inner class `SOV 생략 케이스` {

        @Test
        fun `SOV가 null이면 SOV 섹션이 생략된다`() {
            val topArticles = mapOf("A사" to listOf(makeArticle("기사1", "A사")))
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = null,
                topArticles = topArticles,
                aiInsight = null,
                periodLabel = "2026-04-05 ~ 2026-04-11"
            )

            val hasSovBlock = blocks.any { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("Share of Voice")
            }
            hasSovBlock shouldBe false
        }

        @Test
        fun `SOV shares가 빈 리스트면 SOV 섹션이 생략된다`() {
            val emptySov = SovResponse(
                period = SovPeriod("2026-04-05", "2026-04-11"),
                totalArticles = 0,
                shares = emptyList()
            )
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = emptySov,
                topArticles = emptyMap(),
                aiInsight = null,
                periodLabel = "2026-04-05 ~ 2026-04-11"
            )

            val hasSovBlock = blocks.any { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("Share of Voice")
            }
            hasSovBlock shouldBe false
        }
    }

    @Nested
    inner class `AI 인사이트 생략 케이스` {

        @Test
        fun `AI 인사이트가 null이면 인사이트 섹션이 생략된다`() {
            val topArticles = mapOf("A사" to listOf(makeArticle("기사1", "A사")))
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = null,
                topArticles = topArticles,
                aiInsight = null,
                periodLabel = "2026-04-05 ~ 2026-04-11"
            )

            val hasInsightBlock = blocks.any { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("AI 인사이트")
            }
            hasInsightBlock shouldBe false
        }
    }

    @Nested
    inner class `빈 기사 케이스` {

        @Test
        fun `기사가 비어있으면 헤더와 divider만 생성한다`() {
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = null,
                topArticles = emptyMap(),
                aiInsight = null,
                periodLabel = "2026-04-05 ~ 2026-04-11"
            )

            // 헤더 1개 + divider 1개만 존재
            blocks shouldHaveSize 2
            blocks[0]["type"] shouldBe "header"
            blocks[1]["type"] shouldBe "divider"
        }

        @Test
        fun `articles가 비어있는 경쟁사 항목은 섹션이 생성되지 않는다`() {
            val topArticles = mapOf(
                "A사" to emptyList(),
                "B사" to listOf(makeArticle("기사1", "B사"))
            )
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = null,
                topArticles = topArticles,
                aiInsight = null,
                periodLabel = "2026-04-05 ~ 2026-04-11"
            )

            // A사 섹션은 없고 B사 섹션만 존재
            val hasA = blocks.any { block ->
                @Suppress("UNCHECKED_CAST")
                (block["text"] as? Map<String, Any?>)?.get("text").toString().let {
                    it.startsWith("*A사*")
                }
            }
            hasA shouldBe false

            val hasB = blocks.any { block ->
                @Suppress("UNCHECKED_CAST")
                (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("기사1")
            }
            hasB shouldBe true
        }
    }

    @Nested
    inner class `블록 한도 안전성` {

        @Test
        fun `경쟁사 10개 초과 시 10개만 표시한다`() {
            // 경쟁사 15개 생성
            val topArticles = (1..15).associate { i ->
                "경쟁사$i" to listOf(makeArticle("기사$i", "경쟁사$i"))
            }
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = null,
                topArticles = topArticles,
                aiInsight = null,
                periodLabel = "2026-04-05 ~ 2026-04-11"
            )

            // 경쟁사 섹션은 최대 10개
            val competitorSections = blocks.filter { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    (block["text"] as? Map<String, Any?>)?.get("text").toString().let {
                        it.startsWith("*경쟁사")
                    }
            }
            competitorSections.size shouldBe 10

            // 전체 블록 수는 50개 이하
            (blocks.size <= 50) shouldBe true
        }
    }

    @Nested
    inner class `CTA 링크` {

        @Test
        fun `webUrl이 있으면 CTA 링크가 포함된다`() {
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = null,
                topArticles = emptyMap(),
                aiInsight = null,
                periodLabel = "2026-04-05 ~ 2026-04-11",
                webUrl = "https://app.example.com/competitor"
            )

            val ctaBlock = blocks.find { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("웹에서 전체 보기")
            }
            requireNotNull(ctaBlock) { "CTA 링크 블록이 없습니다" }
            @Suppress("UNCHECKED_CAST")
            val ctaText = (ctaBlock["text"] as Map<String, Any?>)["text"].toString()
            ctaText shouldContain "https://app.example.com/competitor"
        }

        @Test
        fun `webUrl이 null이면 CTA 링크가 생략된다`() {
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = null,
                topArticles = emptyMap(),
                aiInsight = null,
                periodLabel = "2026-04-05 ~ 2026-04-11",
                webUrl = null
            )

            val hasCtaBlock = blocks.any { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("웹에서 전체 보기")
            }
            hasCtaBlock shouldBe false
        }

        @Test
        fun `webUrl이 빈 문자열이면 CTA 링크가 생략된다`() {
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = null,
                topArticles = emptyMap(),
                aiInsight = null,
                periodLabel = "2026-04-05 ~ 2026-04-11",
                webUrl = ""
            )

            val hasCtaBlock = blocks.any { block ->
                block["type"] == "section" &&
                    @Suppress("UNCHECKED_CAST")
                    (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("웹에서 전체 보기")
            }
            hasCtaBlock shouldBe false
        }
    }

    @Nested
    inner class `SOV 델타 표시` {

        @Test
        fun `shareDelta가 양수면 arrow_up 이모지가 포함된다`() {
            val sov = SovResponse(
                period = SovPeriod("2026-04-05", "2026-04-11"),
                totalArticles = 10,
                shares = listOf(
                    SovShareItem("c1", "A사", 10, 1.0, shareDelta = 0.05)
                )
            )
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = sov,
                topArticles = emptyMap(),
                aiInsight = null,
                periodLabel = "테스트 기간"
            )

            val sovText = blocks.find { block ->
                @Suppress("UNCHECKED_CAST")
                (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("Share of Voice")
            }?.let {
                @Suppress("UNCHECKED_CAST")
                (it["text"] as Map<String, Any?>)["text"].toString()
            } ?: ""

            sovText shouldContain ":arrow_up:"
        }

        @Test
        fun `shareDelta가 음수면 arrow_down 이모지가 포함된다`() {
            val sov = SovResponse(
                period = SovPeriod("2026-04-05", "2026-04-11"),
                totalArticles = 10,
                shares = listOf(
                    SovShareItem("c1", "A사", 10, 1.0, shareDelta = -0.05)
                )
            )
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = sov,
                topArticles = emptyMap(),
                aiInsight = null,
                periodLabel = "테스트 기간"
            )

            val sovText = blocks.find { block ->
                @Suppress("UNCHECKED_CAST")
                (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("Share of Voice")
            }?.let {
                @Suppress("UNCHECKED_CAST")
                (it["text"] as Map<String, Any?>)["text"].toString()
            } ?: ""

            sovText shouldContain ":arrow_down:"
        }

        @Test
        fun `shareDelta가 null이면 delta 표시가 생략된다`() {
            val sov = SovResponse(
                period = SovPeriod("2026-04-05", "2026-04-11"),
                totalArticles = 10,
                shares = listOf(
                    SovShareItem("c1", "A사", 10, 1.0, shareDelta = null)
                )
            )
            val (_, blocks) = CompetitorWeeklyBlockKit.build(
                sov = sov,
                topArticles = emptyMap(),
                aiInsight = null,
                periodLabel = "테스트 기간"
            )

            val sovText = blocks.find { block ->
                @Suppress("UNCHECKED_CAST")
                (block["text"] as? Map<String, Any?>)?.get("text").toString().contains("Share of Voice")
            }?.let {
                @Suppress("UNCHECKED_CAST")
                (it["text"] as Map<String, Any?>)["text"].toString()
            } ?: ""

            sovText shouldNotContain ":arrow_up:"
            sovText shouldNotContain ":arrow_down:"
        }
    }
}
