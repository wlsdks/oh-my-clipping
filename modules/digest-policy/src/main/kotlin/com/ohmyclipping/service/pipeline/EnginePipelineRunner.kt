package com.ohmyclipping.service.pipeline

import java.time.Clock
import java.time.Instant

class EnginePipelineRunner(
    private val clock: Clock = Clock.systemUTC()
) {

    fun <C, S, D> run(actions: EnginePipelineActions<C, S, D>): EnginePipelineRun<C, S, D> {
        val traces = mutableListOf<EnginePipelineStepTrace>()
        val collect = traceStep(
            step = "COLLECT",
            traces = traces,
            action = actions.collect,
            detailBuilder = actions.collectDetail
        )
        val summarize = traceStep(
            step = "SUMMARIZE",
            traces = traces,
            action = actions.summarize,
            detailBuilder = actions.summarizeDetail
        )
        val digest = traceStep(
            step = "DIGEST",
            traces = traces,
            action = actions.digest,
            detailBuilder = actions.digestDetail
        )

        return EnginePipelineRun(
            collect = collect,
            summarize = summarize,
            digest = digest,
            traces = traces.toList()
        )
    }

    private fun <T> traceStep(
        step: String,
        traces: MutableList<EnginePipelineStepTrace>,
        action: () -> T,
        detailBuilder: (T) -> String?
    ): T {
        val startedAt = Instant.now(clock)
        return try {
            val result = action()
            traces += EnginePipelineStepTrace(
                step = step,
                status = EnginePipelineStepStatus.SUCCEEDED,
                startedAt = startedAt,
                endedAt = Instant.now(clock),
                detail = safeDetail(result, detailBuilder)
            )
            result
        } catch (e: RuntimeException) {
            traces += EnginePipelineStepTrace(
                step = step,
                status = EnginePipelineStepStatus.FAILED,
                startedAt = startedAt,
                endedAt = Instant.now(clock),
                detail = e.message?.let(::sanitizeDetail)
            )
            throw e
        }
    }

    private fun <T> safeDetail(result: T, detailBuilder: (T) -> String?): String? =
        try {
            detailBuilder(result)?.let(::sanitizeDetail)
        } catch (e: RuntimeException) {
            "detail unavailable: ${e::class.simpleName ?: "RuntimeException"}"
        }

    private fun sanitizeDetail(detail: String): String {
        val redacted = if (VALUE_SECRET_PATTERN.containsMatchIn(detail)) {
            VALUE_SECRET_PATTERN.replace(detail) { match ->
                val keyName = match.groupValues[1]
                "$keyName=***REDACTED***"
            }
        } else {
            detail
        }
        return redacted.take(MAX_STEP_DETAIL_LENGTH)
    }

    private companion object {
        const val MAX_STEP_DETAIL_LENGTH = 240

        val VALUE_SECRET_PATTERN: Regex = Regex(
            "(password|passwd|secret|token|apikey|api_key|authorization|bearer|credential|credentials|privatekey|private_key)" +
                "([\"':= ]+)" +
                "(?:bearer\\s+)?" +
                "[^\"',\\s]+",
            RegexOption.IGNORE_CASE,
        )
    }
}

data class EnginePipelineActions<C, S, D>(
    val collect: () -> C,
    val summarize: () -> S,
    val digest: () -> D,
    val collectDetail: (C) -> String?,
    val summarizeDetail: (S) -> String?,
    val digestDetail: (D) -> String?
)

data class EnginePipelineRun<C, S, D>(
    val collect: C,
    val summarize: S,
    val digest: D,
    val traces: List<EnginePipelineStepTrace>
)

data class EnginePipelineStepTrace(
    val step: String,
    val status: EnginePipelineStepStatus,
    val startedAt: Instant,
    val endedAt: Instant,
    val detail: String? = null
)

enum class EnginePipelineStepStatus {
    SUCCEEDED,
    FAILED
}
