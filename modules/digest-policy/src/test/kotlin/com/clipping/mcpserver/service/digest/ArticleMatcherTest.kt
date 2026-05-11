package com.clipping.mcpserver.service.digest

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ArticleMatcherTest {

    private fun stubOrg(name: String, aliases: List<String>) =
        DigestOrganization(name = name, aliases = aliases)

    @Nested
    inner class `Hangul 경계 매칭` {
        @Test fun `MegaCorp 키워드는 MegaCorp 단독 단어에 match`() {
            matchesKeyword("MegaCorp 리스킬링 확대", "MegaCorp") shouldBe true
        }
        @Test fun `MegaCorp 키워드는 MegaCorp 에 NOT match (접미 Hangul)`() {
            matchesKeyword("MegaCorp발표", "MegaCorp") shouldBe false
        }
        @Test fun `MegaCorp 키워드는 우리MegaCorp 에 NOT match (접두 Hangul)`() {
            matchesKeyword("우리MegaCorp 직원", "MegaCorp") shouldBe false
        }
        @Test fun `AI 키워드는 AI 교육 에 match (영문 뒤 공백)`() {
            matchesKeyword("AI 교육 확대", "AI") shouldBe true
        }
        @Test fun `AI 키워드는 AIM 에 NOT match (영문 접미)`() {
            matchesKeyword("AIM Intelligence", "AI") shouldBe false
        }
        @Test fun `대소문자 무시`() {
            matchesKeyword("ai 교육", "AI") shouldBe true
        }
        @Test fun `한글+영문 혼합 L&D 매치`() {
            matchesKeyword("기업 L&D 강화", "L&D") shouldBe true
        }
        @Test fun `빈 텍스트는 unmatch`() {
            matchesKeyword("", "AI") shouldBe false
        }
    }

    @Nested
    inner class `조직 매칭` {
        @Test fun `name substring match`() {
            val org = stubOrg(name = "MegaCorp", aliases = emptyList())
            matchesOrganization("MegaCorp 실적 발표", org) shouldBe true
        }
        @Test fun `alias 토큰 경계 match`() {
            val org = stubOrg(name = "MegaCorp", aliases = listOf("SEC", "MegaCorp"))
            matchesOrganization("SEC 주식 급등", org) shouldBe true
        }
        @Test fun `alias 접미 오매칭 방지`() {
            val org = stubOrg(name = "MegaCorp", aliases = listOf("MegaCorp"))
            matchesOrganization("MegaCorp생명 뉴스", org) shouldBe false
        }
        @Test fun `org name 도 토큰 경계 (MegaCorp alias ≠ MegaCorp공업)`() {
            val org = stubOrg(name = "MegaCorp", aliases = emptyList())
            matchesOrganization("MegaCorp공업 보도자료", org) shouldBe false
        }
        @Test fun `빈 aliases`() {
            val org = stubOrg(name = "ConglomerateCo", aliases = emptyList())
            matchesOrganization("ConglomerateCo 실적", org) shouldBe true
        }
    }
}
