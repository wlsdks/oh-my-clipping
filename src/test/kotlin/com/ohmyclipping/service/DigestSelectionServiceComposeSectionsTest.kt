package com.ohmyclipping.service

import com.ohmyclipping.service.digest.DigestArticle
import com.ohmyclipping.service.digest.EngineInvalidInputException
import com.ohmyclipping.service.digest.DigestMode
import com.ohmyclipping.service.digest.composeSections
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DigestSelectionServiceComposeSectionsTest {

    private fun article(id: String, kw: Boolean = false, org: Boolean = false): DigestArticle =
        DigestArticle(
            id = id,
            title = id,
            summary = "",
            matchesKeyword = kw,
            matchesOrganization = org
        )

    @Nested
    inner class `composeSections` {
        @Test
        fun `TOPIC_ONLY 1 섹션 no badge`() {
            val arts = listOf(article("a", kw = true), article("b", kw = true))
            val sections = composeSections(DigestMode.TOPIC_ONLY, arts, budget = 5)
            sections shouldHaveSize 1
            sections[0].kind shouldBe "topic"
            sections[0].articles.all { !it.badged } shouldBe true
        }

        @Test
        fun `ACCOUNT_ONLY 1 섹션 no badge`() {
            val arts = listOf(article("a", org = true))
            val sections = composeSections(DigestMode.ACCOUNT_ONLY, arts, budget = 5)
            sections shouldHaveSize 1
            sections[0].kind shouldBe "account"
            sections[0].articles[0].badged shouldBe false
        }

        @Test
        fun `CROSSFILTER 1 섹션 no badge (전체가 교차이므로)`() {
            val arts = listOf(article("a", kw = true, org = true))
            val sections = composeSections(DigestMode.CROSSFILTER, arts, budget = 5)
            sections shouldHaveSize 1
            sections[0].kind shouldBe "cross"
            sections[0].articles[0].badged shouldBe false
        }

        @Test
        fun `DUAL budget=5 → topic 3건 + account 섹션은 남은 org 매치만큼 (이 경우 1건)`() {
            val arts = List(10) { i -> article("a$i", kw = true, org = i < 4) }
            val sections = composeSections(DigestMode.DUAL_SECTION, arts, budget = 5)
            sections shouldHaveSize 2
            sections[0].kind shouldBe "topic"
            sections[0].articles.size shouldBe 3
            sections[1].kind shouldBe "account"
            sections[1].articles.size shouldBe 1  // org 매치 4 - topic 3 = 1
            sections[1].articles[0].article.id shouldBe "a3"
        }

        @Test
        fun `DUAL account 섹션은 비-org 기사로 패딩 안함 (라벨 정합성)`() {
            val arts = listOf(
                article("topic1", kw = true, org = false),
                article("topic2", kw = true, org = false),
                article("both",   kw = true, org = true),   // crossMatch → topic 에만
                // org-only 기사 없음 — account 후보 0
            )
            val sections = composeSections(DigestMode.DUAL_SECTION, arts, budget = 5)
            val account = sections.first { it.kind == "account" }
            account.articles shouldHaveSize 0  // 패딩 없음
        }

        @Test
        fun `DUAL budget=3 → topic 2건 + account 1건`() {
            val arts = List(10) { i -> article("a$i", kw = true, org = i < 4) }
            val sections = composeSections(DigestMode.DUAL_SECTION, arts, budget = 3)
            sections[0].articles.size shouldBe 2
            sections[1].articles.size shouldBe 1
        }

        @Test
        fun `DUAL budget=1 → CROSSFILTER downgrade (섹션 1개)`() {
            val arts = listOf(article("a", kw = true, org = true))
            val sections = composeSections(DigestMode.DUAL_SECTION, arts, budget = 1)
            sections shouldHaveSize 1
            sections[0].kind shouldBe "cross"
        }

        @Test
        fun `DUAL topic 섹션의 crossMatch 에만 ⭐ badge`() {
            val arts = listOf(
                article("a", kw = true, org = true),  // crossMatch
                article("b", kw = true, org = false)  // topic only
            )
            val sections = composeSections(DigestMode.DUAL_SECTION, arts, budget = 3)
            val topic = sections.first { it.kind == "topic" }
            topic.articles.first { it.article.id == "a" }.badged shouldBe true
            topic.articles.first { it.article.id == "b" }.badged shouldBe false
        }

        @Test
        fun `DUAL account 섹션은 topic 에 이미 선택된 기사 제외`() {
            val arts = listOf(
                article("a", kw = true, org = true),  // topic 에 들어감
                article("b", kw = true, org = true),  // topic 에 들어감
                article("c", kw = true, org = true),  // topic 에 들어감
                article("d", kw = false, org = true)  // account 전용
            )
            val sections = composeSections(DigestMode.DUAL_SECTION, arts, budget = 5)
            val topicIds = sections.first { it.kind == "topic" }.articles.map { it.article.id }.toSet()
            val accountIds = sections.first { it.kind == "account" }.articles.map { it.article.id }.toSet()
            (topicIds intersect accountIds) shouldBe emptySet<String>()
            // account 는 d 만 (a/b/c 는 topic 에 3건 모두 들어갔으므로)
            accountIds shouldBe setOf("d")
        }

        @Test
        fun `빈 articles — 섹션은 존재하되 articles empty`() {
            val sections = composeSections(DigestMode.TOPIC_ONLY, emptyList(), budget = 5)
            sections shouldHaveSize 1
            sections[0].articles shouldHaveSize 0
        }

        @Test
        fun `budget 이 0 이하면 EngineInvalidInputException 으로 거부된다`() {
            shouldThrow<EngineInvalidInputException> {
                composeSections(DigestMode.TOPIC_ONLY, emptyList(), budget = 0)
            }
        }
    }
}
