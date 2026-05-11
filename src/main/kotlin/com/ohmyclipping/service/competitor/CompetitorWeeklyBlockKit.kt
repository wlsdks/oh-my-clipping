package com.ohmyclipping.service.competitor

import com.ohmyclipping.service.dto.CompetitorTimelineItem
import com.ohmyclipping.service.dto.SovResponse
import com.ohmyclipping.service.port.CompetitorWeeklyInsight

/**
 * 경쟁사 주간 요약 Slack Block Kit 메시지를 조립한다.
 *
 * Slack Block Kit 제약:
 * - 블록 최대 50개
 * - section text 최대 3000자
 * - 경쟁사 수가 많을 때 블록 한도를 초과하지 않도록 경쟁사 10개까지만 표시한다.
 */
object CompetitorWeeklyBlockKit {

    private const val MAX_COMPETITORS = 10
    private const val MAX_SOV_ITEMS = 10
    private const val MAX_ARTICLES_PER_COMPETITOR = 3

    /**
     * 경쟁사 주간 요약 Slack 메시지 블록을 조립한다.
     *
     * @param sov Share of Voice 응답 (null이면 SOV 섹션 생략)
     * @param topArticles 경쟁사별 상위 기사 맵 (key: 경쟁사명)
     * @param aiInsight AI 생성 주간 인사이트 (null이면 인사이트 섹션 생략)
     * @param periodLabel 발송 기간 레이블 (예: "2026-04-05 ~ 2026-04-11")
     * @param webUrl 웹 상세 페이지 URL (null/빈 문자열이면 CTA 링크 생략)
     * @return Pair(fallback 텍스트, Block Kit 블록 리스트)
     */
    fun build(
        sov: SovResponse?,
        topArticles: Map<String, List<CompetitorTimelineItem>>,
        aiInsight: CompetitorWeeklyInsight?,
        periodLabel: String,
        webUrl: String? = null
    ): Pair<String, List<Map<String, Any?>>> {
        val blocks = mutableListOf<Map<String, Any?>>()

        // 헤더 블록 추가
        blocks += header(":trophy: 경쟁사 주간 요약 ($periodLabel)")

        // SOV 섹션 — shares가 비어있으면 생략
        if (sov != null && sov.shares.isNotEmpty()) {
            val sovText = buildSovText(sov)
            blocks += section(":bar_chart: *Share of Voice*\n$sovText")
        }

        blocks += divider()

        // 경쟁사별 상위 기사 섹션 — 블록 한도 초과 방지를 위해 최대 10개까지만 표시
        var competitorCount = 0
        for ((name, articles) in topArticles) {
            if (articles.isEmpty() || competitorCount >= MAX_COMPETITORS) continue
            val articleText = articles.take(MAX_ARTICLES_PER_COMPETITOR)
                .joinToString("\n") { "• <${it.sourceLink}|${it.title}>" }
            blocks += section("*$name*\n$articleText")
            competitorCount++
        }

        // AI 인사이트 섹션 — null이면 생략
        if (aiInsight != null) {
            blocks += divider()
            val insightText = buildInsightText(aiInsight)
            blocks += section(":bulb: *AI 인사이트*\n$insightText")
        }

        // CTA 링크 — webUrl이 유효할 때만 추가
        if (!webUrl.isNullOrBlank()) {
            blocks += section(":link: <$webUrl|웹에서 전체 보기>")
        }

        val totalArticles = topArticles.values.sumOf { it.size }
        val fallback = "경쟁사 주간 요약 ($periodLabel) — ${totalArticles}건"
        return fallback to blocks
    }

    /** SOV 섹션 텍스트를 조립한다. 최대 MAX_SOV_ITEMS개까지만 표시한다. */
    private fun buildSovText(sov: SovResponse): String =
        sov.shares.take(MAX_SOV_ITEMS).joinToString("\n") { item ->
            val pct = String.format("%.0f", item.share * 100)
            val deltaText = item.shareDelta?.let { d ->
                val sign = if (d >= 0) "+" else ""
                val arrow = when {
                    d > 0.001 -> " :arrow_up:"
                    d < -0.001 -> " :arrow_down:"
                    else -> ""
                }
                " ${sign}${String.format("%.0f", d * 100)}%p$arrow"
            } ?: ""
            "*${item.name}*: ${item.count}건 (${pct}%)$deltaText"
        }

    /** AI 인사이트 텍스트를 조립한다. 경쟁사 하이라이트와 전체 인사이트를 결합한다. */
    private fun buildInsightText(aiInsight: CompetitorWeeklyInsight): String {
        val highlights = aiInsight.competitorHighlights.joinToString("\n") {
            "• *${it.name}*: ${it.highlight}"
        }
        return "$highlights\n\n${aiInsight.weeklyInsight}"
    }

    private fun header(text: String): Map<String, Any?> = mapOf(
        "type" to "header",
        "text" to mapOf("type" to "plain_text", "text" to text, "emoji" to true)
    )

    private fun section(text: String): Map<String, Any?> = mapOf(
        "type" to "section",
        "text" to mapOf("type" to "mrkdwn", "text" to text)
    )

    private fun divider(): Map<String, Any?> = mapOf("type" to "divider")
}
