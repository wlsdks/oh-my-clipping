package com.clipping.mcpserver.service.digest

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class DigestCandidateSelectionPolicyTest {

    private fun candidate(
        id: String,
        sourceId: String?,
        importance: Double,
        combined: Double = importance,
        title: String = "Title-$id",
        summary: String = "Summary of $id",
        keywords: List<String> = emptyList(),
        createdAt: Instant = Instant.parse("2026-04-15T00:00:00Z"),
    ) = DigestCandidate(
        id = id,
        title = title,
        summary = summary,
        keywords = keywords,
        importanceScore = importance,
        combinedScore = combined,
        sourceId = sourceId,
        sourceLink = "https://example.com/$id",
        createdAt = createdAt,
    )

    @Nested
    inner class `소스 다양성` {
        @Test
        fun `동점 후보는 여러 소스를 포함하도록 선택한다`() {
            val pool = listOf(
                candidate("a1", "A", 0.7),
                candidate("a2", "A", 0.7),
                candidate("a3", "A", 0.7),
                candidate("b1", "B", 0.7),
                candidate("b2", "B", 0.7),
                candidate("c1", "C", 0.7),
                candidate("c2", "C", 0.7),
            )

            val result = DigestCandidateSelectionPolicy().selectWithSoftPenalty(pool, maxItems = 5)

            result shouldHaveSize 5
            val ids = result.map { it.id }.toSet()
            val sourcesRepresented = listOf("A", "B", "C").count { source ->
                pool.filter { it.sourceId == source }.any { it.id in ids }
            }
            sourcesRepresented shouldBe 3
        }

        @Test
        fun `강한 소스 페널티에서도 최종 표시는 중요도 순서로 정렬한다`() {
            val pool = listOf(
                candidate("a1", "A", 0.9),
                candidate("a2", "A", 0.85),
                candidate("b1", "B", 0.8),
                candidate("b2", "B", 0.75),
                candidate("c1", "C", 0.7),
            )

            val result = DigestCandidateSelectionPolicy(lambda = 1.0).selectWithSoftPenalty(pool, maxItems = 5)

            result.map { it.id } shouldBe listOf("a1", "a2", "b1", "b2", "c1")
        }
    }

    @Nested
    inner class `품질 컷과 경계값` {
        @Test
        fun `minRawScore 미만 후보는 채우기용으로도 선택하지 않는다`() {
            val pool = listOf(
                candidate("a", "A", 0.4),
                candidate("b", "B", 0.30),
                candidate("c", "C", 0.29),
            )

            val result = DigestCandidateSelectionPolicy(minRawScore = 0.3).selectWithSoftPenalty(pool, maxItems = 5)

            result.map { it.id }.toSet() shouldBe setOf("a", "b")
        }

        @Test
        fun `빈 후보와 maxItems 0 은 빈 결과를 반환한다`() {
            DigestCandidateSelectionPolicy().selectWithSoftPenalty(emptyList(), maxItems = 5).shouldBeEmpty()
            DigestCandidateSelectionPolicy().selectWithSoftPenalty(
                listOf(candidate("a", "A", 0.8)),
                maxItems = 0
            ).shouldBeEmpty()
        }

        @Test
        fun `중요도 필터를 모두 실패하면 thin-day 폴백으로 전체 후보에서 고른다`() {
            val pool = listOf(
                candidate("a", "A", importance = 0.2, combined = 0.4, title = "Semiconductor recovery"),
                candidate("b", "B", importance = 0.1, combined = 0.35, title = "Automotive battery order"),
            )

            val result = DigestCandidateSelectionPolicy(minRawScore = 0.3).select(
                candidates = pool,
                maxItems = 2,
                minImportanceScore = 0.8
            )

            result.map { it.id } shouldBe listOf("a", "b")
        }
    }

    @Nested
    inner class `중복 제거` {
        @Test
        fun `제목이 유사한 후보는 먼저 온 후보 하나만 남긴다`() {
            val pool = listOf(
                candidate("a", "A", 0.9, title = "TestCorp AI chip launch"),
                candidate("b", "B", 0.8, title = "TestCorp AI chip launch update"),
                candidate("c", "C", 0.7, title = "TestCorp battery partnership"),
            )

            val result = DigestCandidateSelectionPolicy().dedupeCandidates(pool)

            result.map { it.id } shouldBe listOf("a", "c")
        }

        @Test
        fun `요약과 키워드가 거의 같은 후보는 의미 중복으로 제거한다`() {
            val pool = listOf(
                candidate(
                    "a",
                    "A",
                    0.9,
                    title = "TestCorp earnings outlook",
                    summary = "memory semiconductor demand recovery cloud ai server capex",
                    keywords = listOf("memory", "semiconductor", "cloud")
                ),
                candidate(
                    "b",
                    "B",
                    0.8,
                    title = "TestCorp stock outlook",
                    summary = "memory semiconductor demand recovery cloud ai server capex",
                    keywords = listOf("memory", "semiconductor", "cloud")
                ),
            )

            val result = DigestCandidateSelectionPolicy().dedupeCandidates(pool)

            result.map { it.id } shouldBe listOf("a")
        }
    }

    @Nested
    inner class `결정론` {
        @Test
        fun `완전 동점이면 id 오름차순으로 결정한다`() {
            val pool = listOf(
                candidate("b-item", "A", 0.7),
                candidate("a-item", "A", 0.7),
            )

            val result = DigestCandidateSelectionPolicy().selectWithSoftPenalty(pool, maxItems = 1)

            result.single().id shouldBe "a-item"
        }

        @Test
        fun `같은 소스 동점이면 최신 기사를 먼저 선택한다`() {
            val older = Instant.parse("2026-04-14T00:00:00Z")
            val newer = Instant.parse("2026-04-15T00:00:00Z")
            val pool = listOf(
                candidate("old", "A", 0.7, createdAt = older),
                candidate("new", "A", 0.7, createdAt = newer),
            )

            val result = DigestCandidateSelectionPolicy().selectWithSoftPenalty(pool, maxItems = 1)

            result.single().id shouldBe "new"
        }
    }
}
