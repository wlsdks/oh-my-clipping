package com.ohmyclipping.service.digest

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SectionLabelResolverTest {
    private fun org(name: String) = DigestOrganization(name = name)

    @Nested
    inner class `summarizeOrgs` {
        @Test fun `1개`() { summarizeOrgs(listOf(org("MegaCorp"))) shouldBe "MegaCorp" }
        @Test fun `3개`() {
            summarizeOrgs(listOf(org("MegaCorp"), org("ConglomerateCo"), org("현대차"))) shouldBe
                "MegaCorp·ConglomerateCo·현대차"
        }
        @Test fun `5개 overflow`() {
            val orgs = listOf("A", "B", "C", "D", "E").map(::org)
            summarizeOrgs(orgs) shouldBe "A·B·C 외 2개"
        }
        @Test fun `빈 리스트 → 빈 문자열`() {
            summarizeOrgs(emptyList()) shouldBe ""
        }
        @Test fun `공백 조직명은 제외하고 나머지는 trim 한다`() {
            summarizeOrgs(listOf(org(" MegaCorp "), org(" "), org("현대차"))) shouldBe "MegaCorp·현대차"
        }
    }

    @Nested
    inner class `summarizeKeywords` {
        @Test fun `3개`() { summarizeKeywords(listOf("리스킬링", "AI", "L&D")) shouldBe "리스킬링·AI·L&D" }
        @Test fun `5개 overflow`() {
            summarizeKeywords(listOf("A","B","C","D","E")) shouldBe "A·B·C 외 2개"
        }
        @Test fun `공백 키워드는 제외하고 나머지는 trim 한다`() {
            summarizeKeywords(listOf(" AI ", " ", "L&D")) shouldBe "AI·L&D"
        }
    }

    @Nested
    inner class `resolveSectionLabel` {
        @Test fun `CROSSFILTER 1x1 자연어`() {
            resolveSectionLabel(DigestMode.CROSSFILTER, listOf("리스킬링"), listOf(org("MegaCorp")),
                               dualSection = null) shouldBe "📰 MegaCorp의 리스킬링"
        }
        @Test fun `CROSSFILTER 라벨은 공백 입력을 제외하고 trim 한다`() {
            resolveSectionLabel(DigestMode.CROSSFILTER, listOf(" AI ", " "), listOf(org(" MegaCorp ")),
                               dualSection = null) shouldBe "📰 MegaCorp의 AI"
        }
        @Test fun `CROSSFILTER 1xN`() {
            resolveSectionLabel(DigestMode.CROSSFILTER, listOf("리스킬링"),
                listOf(org("MegaCorp"), org("ConglomerateCo"), org("현대차")), dualSection = null
            ) shouldBe "📰 리스킬링 × MegaCorp·ConglomerateCo·현대차"
        }
        @Test fun `CROSSFILTER Nx1`() {
            resolveSectionLabel(DigestMode.CROSSFILTER, listOf("리스킬링", "AI"),
                listOf(org("MegaCorp")), dualSection = null
            ) shouldBe "📰 MegaCorp × 리스킬링·AI"
        }
        @Test fun `TOPIC_ONLY`() {
            resolveSectionLabel(DigestMode.TOPIC_ONLY, listOf("AI"), emptyList(),
                               dualSection = null) shouldBe "📰 AI"
        }
        @Test fun `ACCOUNT_ONLY`() {
            resolveSectionLabel(DigestMode.ACCOUNT_ONLY, emptyList(), listOf(org("A")),
                               dualSection = null) shouldBe "🏢 내 기업 동향"
        }
        @Test fun `DUAL topic`() {
            resolveSectionLabel(DigestMode.DUAL_SECTION, listOf("A"), listOf(org("B")),
                               dualSection = "topic") shouldBe "📰 주제 동향"
        }
        @Test fun `DUAL account`() {
            resolveSectionLabel(DigestMode.DUAL_SECTION, listOf("A"), listOf(org("B")),
                               dualSection = "account") shouldBe "🏢 내 기업"
        }
        @Test fun `DUAL dualSection null이면 EngineInvalidInputException`() {
            shouldThrow<EngineInvalidInputException> {
                resolveSectionLabel(DigestMode.DUAL_SECTION, listOf("A"), listOf(org("B")),
                                   dualSection = null)
            }
        }
    }
}
