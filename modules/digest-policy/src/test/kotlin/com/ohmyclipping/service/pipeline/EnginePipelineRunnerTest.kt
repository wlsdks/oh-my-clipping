package com.ohmyclipping.service.pipeline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class EnginePipelineRunnerTest {

    @Test
    fun `run should execute collect summarize digest in deterministic order`() {
        val calls = mutableListOf<String>()
        val runner = EnginePipelineRunner(fixedClock())

        val result = runner.run(
            EnginePipelineActions(
                collect = {
                    calls += "collect"
                    Collect(newItems = 2, duplicateSkipped = 1)
                },
                summarize = {
                    calls += "summarize"
                    Summarize(totalSummarized = 2)
                },
                digest = {
                    calls += "digest"
                    Digest(selectedCount = 1, postedToSlack = false)
                },
                collectDetail = { "newItems=${it.newItems}, duplicateSkipped=${it.duplicateSkipped}" },
                summarizeDetail = { "totalSummarized=${it.totalSummarized}" },
                digestDetail = { "selectedCount=${it.selectedCount}, postedToSlack=${it.postedToSlack}" }
            )
        )

        calls shouldContainExactly listOf("collect", "summarize", "digest")
        result.traces.map { it.step } shouldContainExactly listOf("COLLECT", "SUMMARIZE", "DIGEST")
        result.traces.map { it.status }.toSet() shouldBe setOf(EnginePipelineStepStatus.SUCCEEDED)
        result.traces.map { it.detail } shouldContainExactly listOf(
            "newItems=2, duplicateSkipped=1",
            "totalSummarized=2",
            "selectedCount=1, postedToSlack=false"
        )
    }

    @Test
    fun `run should stop and record failed step when collect fails`() {
        val calls = mutableListOf<String>()
        val runner = EnginePipelineRunner(fixedClock())

        val exception = shouldThrow<IllegalStateException> {
            runner.run(
                EnginePipelineActions(
                    collect = {
                        calls += "collect"
                        throw IllegalStateException("collect failed")
                    },
                    summarize = {
                        calls += "summarize"
                        Summarize(totalSummarized = 1)
                    },
                    digest = {
                        calls += "digest"
                        Digest(selectedCount = 1, postedToSlack = false)
                    },
                    collectDetail = { "unused" },
                    summarizeDetail = { "unused" },
                    digestDetail = { "unused" }
                )
            )
        }

        exception.message shouldBe "collect failed"
        calls shouldContainExactly listOf("collect")
    }

    @Test
    fun `run should not fail when success detail builder fails`() {
        val calls = mutableListOf<String>()
        val runner = EnginePipelineRunner(fixedClock())

        val result = runner.run(
            EnginePipelineActions(
                collect = {
                    calls += "collect"
                    Collect(newItems = 2, duplicateSkipped = 1)
                },
                summarize = {
                    calls += "summarize"
                    Summarize(totalSummarized = 2)
                },
                digest = {
                    calls += "digest"
                    Digest(selectedCount = 1, postedToSlack = false)
                },
                collectDetail = { throw IllegalStateException("detail failed") },
                summarizeDetail = { "totalSummarized=${it.totalSummarized}" },
                digestDetail = { "selectedCount=${it.selectedCount}, postedToSlack=${it.postedToSlack}" }
            )
        )

        calls shouldContainExactly listOf("collect", "summarize", "digest")
        result.traces.map { it.status }.toSet() shouldBe setOf(EnginePipelineStepStatus.SUCCEEDED)
        result.traces.first().detail shouldBe "detail unavailable: IllegalStateException"
    }

    @Test
    fun `run should cap success detail length`() {
        val runner = EnginePipelineRunner(fixedClock())
        val longDetail = "x".repeat(300)

        val result = runner.run(
            EnginePipelineActions(
                collect = { Collect(newItems = 2, duplicateSkipped = 1) },
                summarize = { Summarize(totalSummarized = 2) },
                digest = { Digest(selectedCount = 1, postedToSlack = false) },
                collectDetail = { longDetail },
                summarizeDetail = { "ok" },
                digestDetail = { "ok" }
            )
        )

        result.traces.first().detail?.length shouldBe 240
    }

    private fun fixedClock(): Clock =
        Clock.fixed(Instant.parse("2026-05-02T00:00:00Z"), ZoneOffset.UTC)

    private data class Collect(
        val newItems: Int,
        val duplicateSkipped: Int
    )

    private data class Summarize(
        val totalSummarized: Int
    )

    private data class Digest(
        val selectedCount: Int,
        val postedToSlack: Boolean
    )
}
