package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.digest.*

import com.clipping.mcpserver.config.AppProperties
import com.clipping.mcpserver.model.Organization
import com.clipping.mcpserver.model.OrganizationType
import com.clipping.mcpserver.service.digest.BadgedArticle
import com.clipping.mcpserver.service.digest.DigestArticle
import com.clipping.mcpserver.service.digest.DigestMode
import com.clipping.mcpserver.service.digest.DigestSectionResult
import com.clipping.mcpserver.service.digest.EscalationCopy
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class DigestRendererAccountBasedTest {

    private val renderer = DigestRenderer(AppProperties(baseUrl = "http://localhost:8086"))

    private fun org(name: String) = Organization(
        id = "x-$name", tenantId = "default", name = name,
        type = OrganizationType.CUSTOMER,
        domain = null, description = null, stockCode = null,
        aliases = emptyList(), origin = null,
        createdAt = Instant.now(), updatedAt = Instant.now()
    )

    private fun article(id: String, kw: Boolean = false, org: Boolean = false) =
        DigestArticle(
            id = id, title = "T-$id", summary = "요약-$id",
            matchesKeyword = kw, matchesOrganization = org
        )

    @Nested
    inner class `section block with ⭐ badge suffix` {
        @Test
        fun `badged true → 제목 뒤 ⭐ suffix`() {
            val block = renderer.renderAccountSectionBlock(
                title = "MegaCorp DS, AI 교육 전면 확대",
                summary = "요약 본문",
                badged = true
            )
            block shouldContain "MegaCorp DS, AI 교육 전면 확대"
            block shouldContain "⭐"
            // stripLeadingDecoration 회귀 방지: ⭐ 가 line head 에 오지 않아야 함
            // 즉 "⭐ " 로 시작하는 라인이 block 안에 없어야 함
            val lines = block.split("\\n")
            lines.none { it.trimStart().startsWith("⭐") } shouldBe true
        }

        @Test
        fun `badged false → ⭐ 없음`() {
            val block = renderer.renderAccountSectionBlock(
                title = "제목", summary = "요약", badged = false
            )
            block shouldNotContain "⭐"
        }
    }

    @Nested
    inner class `전체 account-based digest 렌더` {
        @Test
        fun `DUAL 섹션 사이 divider block`() {
            val sections = listOf(
                DigestSectionResult(
                    "topic", listOf(
                        BadgedArticle(article("a1", kw = true, org = true), badged = true)
                    )
                ),
                DigestSectionResult(
                    "account", listOf(
                        BadgedArticle(article("a2", org = true), badged = false)
                    )
                )
            )
            val output = renderer.renderAccountBasedDigest(
                sections = sections,
                mode = DigestMode.DUAL_SECTION,
                keywords = listOf("리스킬링", "AI"),
                orgs = listOf(org("MegaCorp"), org("ConglomerateCo")),
                dualLegendShown = false
            )
            output shouldContain """"type":"divider""""
        }

        @Test
        fun `DUAL 첫 사용 시 legend context block`() {
            val sections = listOf(
                DigestSectionResult("topic", listOf(BadgedArticle(article("a1"), false))),
                DigestSectionResult("account", listOf(BadgedArticle(article("a2"), false)))
            )
            val output = renderer.renderAccountBasedDigest(
                sections = sections, mode = DigestMode.DUAL_SECTION,
                keywords = listOf("AI"), orgs = listOf(org("A")),
                dualLegendShown = false
            )
            output shouldContain "⭐ = 주제와 기업이 모두 일치하는 뉴스"
        }

        @Test
        fun `legend 본 적 있으면 (dualLegendShown=true) 전체 텍스트 재노출 없음 + footer link 은 유지`() {
            val sections = listOf(
                DigestSectionResult("topic", listOf(BadgedArticle(article("a"), false))),
                DigestSectionResult("account", listOf(BadgedArticle(article("b"), false)))
            )
            val output = renderer.renderAccountBasedDigest(
                sections = sections, mode = DigestMode.DUAL_SECTION,
                keywords = listOf("AI"), orgs = listOf(org("A")),
                dualLegendShown = true
            )
            // 전체 legend 텍스트는 생략
            output shouldNotContain "⭐ = 주제와 기업이 모두 일치하는 뉴스"
            // footer link 는 항상 유지
            output shouldContain "⭐ 표기 자세히 보기"
            output shouldContain "/help/legend"
        }

        @Test
        fun `단일 섹션 (CROSSFILTER) 은 legend 도 footer link 도 없음`() {
            val sections = listOf(
                DigestSectionResult("cross", listOf(BadgedArticle(article("a"), false)))
            )
            val output = renderer.renderAccountBasedDigest(
                sections = sections, mode = DigestMode.CROSSFILTER,
                keywords = listOf("AI"), orgs = listOf(org("A")),
                dualLegendShown = false
            )
            output shouldNotContain "⭐ = 주제와 기업이 모두 일치하는 뉴스"
            output shouldNotContain "/help/legend"
        }

        @Test
        fun `DUAL 처음 사용 (dualLegendShown=false) 은 legend + footer link 둘 다 포함`() {
            val sections = listOf(
                DigestSectionResult("topic", listOf(BadgedArticle(article("a"), false))),
                DigestSectionResult("account", listOf(BadgedArticle(article("b"), false)))
            )
            val output = renderer.renderAccountBasedDigest(
                sections = sections, mode = DigestMode.DUAL_SECTION,
                keywords = listOf("AI"), orgs = listOf(org("A")),
                dualLegendShown = false
            )
            output shouldContain "⭐ = 주제와 기업이 모두 일치하는 뉴스"
            output shouldContain "/help/legend"
        }

        @Test
        fun `footer link 은 appProperties baseUrl 을 사용한다`() {
            val customRenderer = DigestRenderer(AppProperties(baseUrl = "https://clipping.example.com/"))
            val sections = listOf(
                DigestSectionResult("topic", listOf(BadgedArticle(article("a"), false))),
                DigestSectionResult("account", listOf(BadgedArticle(article("b"), false)))
            )
            val output = customRenderer.renderAccountBasedDigest(
                sections = sections, mode = DigestMode.DUAL_SECTION,
                keywords = listOf("AI"), orgs = listOf(org("A")),
                dualLegendShown = true
            )
            // trailing slash 가 있어도 중복되지 않아야 함
            output shouldContain "https://clipping.example.com/help/legend"
        }

        @Test
        fun `빈 섹션 → context block footer (escalation copy)`() {
            val sections = listOf(
                DigestSectionResult("topic", listOf(BadgedArticle(article("t1", kw = true), false))),
                DigestSectionResult("account", emptyList())
            )
            val output = renderer.renderAccountBasedDigest(
                sections = sections, mode = DigestMode.DUAL_SECTION,
                keywords = listOf("AI"), orgs = listOf(org("A")),
                dualLegendShown = true,
                emptyCopies = mapOf("account" to EscalationCopy(text = "오늘 기업 관련 뉴스는 없었어요"))
            )
            output shouldContain "오늘 기업 관련 뉴스는 없었어요"
        }

        @Test
        fun `header 블록은 resolveSectionLabel 결과 포함`() {
            val sections = listOf(
                DigestSectionResult("cross", listOf(BadgedArticle(article("a"), false)))
            )
            val output = renderer.renderAccountBasedDigest(
                sections = sections, mode = DigestMode.CROSSFILTER,
                keywords = listOf("리스킬링"),
                orgs = listOf(org("MegaCorp")),
                dualLegendShown = true
            )
            // CROSSFILTER with 1 org + 1 keyword → "📰 MegaCorp의 리스킬링"
            output shouldContain "MegaCorp의 리스킬링"
        }
    }
}
