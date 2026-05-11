package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.digest.*

import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.config.AppProperties
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.DigestCandidateStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.SlackChannelDailySendCountStore
import com.clipping.mcpserver.store.SummaryFeedbackStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment
import java.time.Instant

/**
 * DigestService.selectWithSoftPenalty 알고리즘의 행위 시나리오 15개를 검증한다.
 *
 * - 소스 다양성 유도 (fair-share greedy)
 * - raw score floor (thin-day 품질 컷)
 * - null source 버킷 처리
 * - 결정론적 tie-breaking
 * - lambda=0 / lambda=1 극단값
 * - 빈 pool, maxItems=0 경계 케이스
 */
class DigestServiceFairShareTest {

    // -- helpers --

    private fun makeService(lambda: Double = 0.15, minRawScore: Double = 0.3): DigestService {
        val env = mockk<Environment>()
        every { env.getProperty("clipping.digest.fair_share.lambda", "0.15") } returns lambda.toString()
        every { env.getProperty("clipping.digest.fair_share.min_raw_score", "0.3") } returns minRawScore.toString()
        return DigestService(
            categoryStore = mockk<CategoryStore>(relaxed = true),
            summaryStore = mockk<BatchSummaryStore>(relaxed = true),
            digestCandidateStore = mockk<DigestCandidateStore>(relaxed = true),
            runtimeSettingService = mockk<RuntimeSettingService>(relaxed = true),
            appProperties = AppProperties(),
            applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true),
            slackMessageSender = mockk<com.clipping.mcpserver.service.port.SlackDeliveryPort>(relaxed = true),
            slackChannelDailySendCountStore = mockk<SlackChannelDailySendCountStore>(relaxed = true),
            adminReviewQueueService = mockk<AdminReviewQueueService>(relaxed = true),
            summaryFeedbackStore = mockk<SummaryFeedbackStore>(relaxed = true),
            slackBlockKitTemplateService = mockk<SlackBlockKitTemplateService>(relaxed = true),
            digestDeliveryFinalizationService = mockk<DigestDeliveryFinalizationService>(relaxed = true),
            statsService = mockk<StatsService>(relaxed = true),
            summarizer = mockk<LlmSummarizationPort>(relaxed = true),
            environment = env,
            featureFlagsService = mockk<FeatureFlagsService>(relaxed = true),
            digestPreviewService = mockk<DigestPreviewService>(relaxed = true),
            categoryDigestStateService = mockk<CategoryDigestStateService>(relaxed = true),
            digestDiffLogStore = mockk<com.clipping.mcpserver.store.DigestDiffLogStore>(relaxed = true),
        )
    }

    private fun makeSummary(
        id: String,
        importance: Float = 0.7f,
        sourceLink: String = "https://example.com/$id",
    ) = BatchSummary(
        id = id,
        originalTitle = "Title-$id",
        summary = "Summary of $id",
        importanceScore = importance,
        sourceLink = sourceLink,
        categoryId = "cat-test",
        rssItemId = "item-$id",
        createdAt = Instant.parse("2026-04-15T00:00:00Z"),
    )

    private fun candidate(
        id: String,
        sourceId: String?,
        importance: Double,
        combined: Double = importance,
        createdAt: Instant = Instant.parse("2026-04-15T00:00:00Z"),
    ) = DigestService.RankedCandidate(
        summary = makeSummary(id, importance = importance.toFloat()),
        rssSourceId = sourceId,
        combinedScore = combined,
        importanceScore = importance,
        createdAt = createdAt,
        id = id,
    )

    // -- scenarios --

    @Nested
    inner class `소스 다양성 - fair-share` {

        @Test
        fun `시나리오1 3소스 동점 maxItems5 최소3소스 포함`() {
            // 3개 소스, 각 2~3건 동점 — 페널티가 다양성을 유도해야 한다
            val pool = listOf(
                candidate("a1", "A", 0.7),
                candidate("a2", "A", 0.7),
                candidate("a3", "A", 0.7),
                candidate("b1", "B", 0.7),
                candidate("b2", "B", 0.7),
                candidate("c1", "C", 0.7),
                candidate("c2", "C", 0.7),
            )
            val result = makeService().selectWithSoftPenalty(pool, 5)
            result shouldHaveSize 5
            // 각 소스가 최소 1건씩 포함돼야 한다
            val ids = result.map { it.id }.toSet()
            val sourcesRepresented = listOf("A", "B", "C").count { source ->
                pool.filter { it.rssSourceId == source }.any { it.id in ids }
            }
            sourcesRepresented shouldBe 3
        }

        @Test
        fun `시나리오2 1소스고점수 다른소스저점수 A독점방지`() {
            // A가 5건 모두 0.9점, B/C는 0.5점 — lambda=0.15 이면 0.9-0.15*4=0.3 vs 0.5 이므로 B/C도 포함돼야 함
            val pool = listOf(
                candidate("a1", "A", 0.9),
                candidate("a2", "A", 0.9),
                candidate("a3", "A", 0.9),
                candidate("a4", "A", 0.9),
                candidate("a5", "A", 0.9),
                candidate("b1", "B", 0.5),
                candidate("c1", "C", 0.5),
            )
            val result = makeService(lambda = 0.15).selectWithSoftPenalty(pool, 5)
            result shouldHaveSize 5
            val ids = result.map { it.id }.toSet()
            // B와 C 중 적어도 하나는 포함돼야 한다
            val nonAIncluded = ids.any { it == "b1" || it == "c1" }
            nonAIncluded shouldBe true
        }

        @Test
        fun `시나리오3 5소스 maxItems3 상위3소스 각1건씩`() {
            // 5개 소스, 점수 차이 있음 — 상위 3건이 서로 다른 소스에서 나와야 함
            val pool = listOf(
                candidate("a1", "A", 0.9),
                candidate("b1", "B", 0.8),
                candidate("c1", "C", 0.7),
                candidate("d1", "D", 0.4),
                candidate("e1", "E", 0.4),
            )
            val result = makeService().selectWithSoftPenalty(pool, 3)
            result shouldHaveSize 3
            val selectedIds = result.map { it.id }.toSet()
            selectedIds shouldBe setOf("a1", "b1", "c1")
        }

        @Test
        fun `시나리오4 1소스뿐 maxItems5 모두같은소스`() {
            val pool = listOf(
                candidate("a1", "A", 0.9),
                candidate("a2", "A", 0.8),
                candidate("a3", "A", 0.7),
                candidate("a4", "A", 0.6),
                candidate("a5", "A", 0.5),
            )
            val result = makeService().selectWithSoftPenalty(pool, 5)
            result shouldHaveSize 5
            // 단일 소스이므로 5건 모두 A에서 나온다
            result.all { it.id.startsWith("a") } shouldBe true
        }
    }

    @Nested
    inner class `Null source 버킷` {

        @Test
        fun `시나리오5 Null소스 가상버킷 공유 페널티 적용`() {
            // null source 3건 + real source 3건
            // null들은 NULL_SOURCE_KEY 버킷으로 묶여 페널티 공유
            val pool = listOf(
                candidate("x1", null, 0.8),
                candidate("x2", null, 0.8),
                candidate("x3", null, 0.8),
                candidate("r1", "R", 0.7),
                candidate("r2", "R", 0.7),
                candidate("r3", "R", 0.7),
            )
            val result = makeService(lambda = 0.2).selectWithSoftPenalty(pool, 4)
            result shouldHaveSize 4
            // null 소스가 모든 슬롯을 독점하지 않아야 한다 (페널티 덕분에 R도 포함)
            val nullIds = result.map { it.id }.count { it.startsWith("x") }
            val realIds = result.map { it.id }.count { it.startsWith("r") }
            // 각 그룹에서 최소 1건씩
            (nullIds >= 1) shouldBe true
            (realIds >= 1) shouldBe true
        }

        @Test
        fun `시나리오13 모든후보 null source NPE없음`() {
            val pool = listOf(
                candidate("x", null, 0.8),
                candidate("y", null, 0.7),
                candidate("z", null, 0.6),
            )
            val result = makeService().selectWithSoftPenalty(pool, 3)
            result shouldHaveSize 3
        }
    }

    @Nested
    inner class `Pool 크기 경계 케이스` {

        @Test
        fun `시나리오6 pool이 maxItems보다 작으면 모두 반환`() {
            val pool = listOf(
                candidate("a", "A", 0.8),
                candidate("b", "B", 0.7),
            )
            val result = makeService().selectWithSoftPenalty(pool, 5)
            result shouldHaveSize 2
        }

        @Test
        fun `시나리오14 빈pool 빈결과`() {
            val result = makeService().selectWithSoftPenalty(emptyList(), 5)
            result.shouldBeEmpty()
        }

        @Test
        fun `시나리오15 maxItems0 빈결과`() {
            val pool = listOf(candidate("a", "A", 0.8))
            val result = makeService().selectWithSoftPenalty(pool, 0)
            result.shouldBeEmpty()
        }
    }

    @Nested
    inner class `결정론적 tie-breaking` {

        @Test
        fun `시나리오7 완전동점 동일결과 두번실행`() {
            // id 기준 tiebreaker — 같은 입력에서 항상 같은 결과
            val pool = listOf(
                candidate("b-item", "A", 0.7),
                candidate("a-item", "A", 0.7),
            )
            val s = makeService()
            val result1 = s.selectWithSoftPenalty(pool, 1).map { it.id }
            val result2 = s.selectWithSoftPenalty(pool, 1).map { it.id }
            result1 shouldBe result2
        }

        @Test
        fun `시나리오7b id tiebreaker 사전순 더 작은 id 선택`() {
            // 동점에서 id ascending tiebreaker — "a-item" < "b-item"
            val pool = listOf(
                candidate("b-item", "A", 0.7),
                candidate("a-item", "A", 0.7),
            )
            val result = makeService().selectWithSoftPenalty(pool, 1)
            result shouldHaveSize 1
            result.first().id shouldBe "a-item"
        }
    }

    @Nested
    inner class `Lambda 극단값` {

        @Test
        fun `시나리오8 lambda0 다양성유도없음 단일소스독점가능`() {
            val s = makeService(lambda = 0.0)
            // 모든 점수가 같으면 페널티가 0이므로 어떤 소스든 가능 (결과 5건이면 충분)
            val pool = listOf(
                candidate("a1", "A", 0.9),
                candidate("a2", "A", 0.9),
                candidate("a3", "A", 0.9),
                candidate("b1", "B", 0.5),
                candidate("c1", "C", 0.5),
            )
            val result = s.selectWithSoftPenalty(pool, 3)
            result shouldHaveSize 3
            // lambda=0 이면 A가 상위 3건을 독점할 수 있다
            result.all { it.id.startsWith("a") } shouldBe true
        }

        @Test
        fun `시나리오11 lambda1 강한페널티 첫3번은 모두다른소스`() {
            // lambda=1.0: 같은 소스 2번째 effectiveScore = score - 1.0*1 로 크게 낮아짐
            // 하지만 combinedScore 자체는 minRawScore 이상이면 탈락하지 않음 — 소프트 패널티
            // 따라서 첫 3번 선택은 반드시 서로 다른 소스에서 나와야 한다 (A, B, C)
            val s = makeService(lambda = 1.0)
            val pool = listOf(
                candidate("a1", "A", 0.9),
                candidate("a2", "A", 0.85),
                candidate("b1", "B", 0.8),
                candidate("b2", "B", 0.75),
                candidate("c1", "C", 0.7),
            )
            val result = s.selectWithSoftPenalty(pool, 5)
            // 5건 모두 반환 (combinedScore 0.75+ >= minRawScore 0.3)
            result shouldHaveSize 5
            // display 재정렬 기준: importanceScore desc → a1, a2, b1, b2, c1
            // greedy 선택 순서: a1(A,eff=0.9), b1(B,eff=0.8), c1(C,eff=0.7), a2(A,eff=0.85-1=−0.15), b2(B,eff=0.75-1=−0.25)
            // 하지만 a2.combinedScore=0.85 >= 0.3 → floor 통과, b2=0.75 → 통과
            val ids = result.map { it.id }
            // display 재정렬: 중요도 내림차순 → a1(0.9), a2(0.85), b1(0.8), b2(0.75), c1(0.7)
            ids shouldBe listOf("a1", "a2", "b1", "b2", "c1")
        }
    }

    @Nested
    inner class `Raw score floor` {

        @Test
        fun `시나리오12 minRawScore0_3 경계 0_30통과 0_29탈락`() {
            val s = makeService(minRawScore = 0.3)
            val pool = listOf(
                candidate("a", "A", 0.4),   // 통과
                candidate("b", "B", 0.30),  // 경계 = 통과 (>= 기준)
                candidate("c", "C", 0.29),  // 탈락 (< 0.30)
            )
            val result = s.selectWithSoftPenalty(pool, 5)
            val ids = result.map { it.id }.toSet()
            ("c" !in ids) shouldBe true   // 0.29는 탈락
            ("a" in ids) shouldBe true    // 0.40은 통과
            ("b" in ids) shouldBe true    // 0.30은 통과
        }
    }

    @Nested
    inner class `피드백 부스트` {

        @Test
        fun `시나리오9 combinedScore가 importanceScore보다 크면 부스트된 후보가 선택됨`() {
            // 피드백 부스트로 combinedScore > importanceScore
            val pool = listOf(
                candidate("low-raw", "A", importance = 0.4, combined = 0.75),  // 부스트 적용
                candidate("high-raw", "B", importance = 0.6, combined = 0.6),  // 부스트 없음
            )
            val result = makeService().selectWithSoftPenalty(pool, 1)
            result shouldHaveSize 1
            // combinedScore 기준으로 greedy 선택 — 0.75 > 0.6 이므로 low-raw 먼저 선택
            result.first().id shouldBe "low-raw"
        }
    }

    @Nested
    inner class `Display 재정렬` {

        @Test
        fun `최종결과는 importanceScore내림차순으로정렬`() {
            // greedy 선택 순서와 무관하게 display 재정렬은 importanceScore 기준
            val pool = listOf(
                candidate("a", "A", importance = 0.5, combined = 0.9),  // greedy 먼저 선택됨
                candidate("b", "B", importance = 0.8, combined = 0.8),
                candidate("c", "C", importance = 0.6, combined = 0.7),
            )
            val result = makeService().selectWithSoftPenalty(pool, 3)
            result shouldHaveSize 3
            // display 재정렬: 0.8, 0.6, 0.5 순서
            result.map { it.importanceScore } shouldBe listOf(0.8f, 0.6f, 0.5f)
        }
    }

    @Nested
    inner class `createdAt tiebreaker` {

        @Test
        fun `시나리오10 같은소스 동점 최신기사 우선선택`() {
            val older = Instant.parse("2026-04-14T00:00:00Z")
            val newer = Instant.parse("2026-04-15T00:00:00Z")
            val pool = listOf(
                candidate("old", "A", 0.7, createdAt = older),
                candidate("new", "A", 0.7, createdAt = newer),
            )
            val result = makeService().selectWithSoftPenalty(pool, 1)
            result shouldHaveSize 1
            // 최신 기사 우선 (createdAt desc)
            result.first().id shouldBe "new"
        }
    }
}
