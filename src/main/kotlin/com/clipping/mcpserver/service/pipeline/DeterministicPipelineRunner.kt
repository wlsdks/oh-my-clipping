package com.clipping.mcpserver.service.pipeline

import com.clipping.mcpserver.service.dto.clipping.PipelineOrchestrationMode
import com.clipping.mcpserver.service.dto.clipping.PipelineRunResult
import com.clipping.mcpserver.service.dto.clipping.PipelineStepStatus
import com.clipping.mcpserver.service.dto.clipping.PipelineStepTrace
import com.clipping.mcpserver.service.pipeline.EnginePipelineActions
import com.clipping.mcpserver.service.pipeline.EnginePipelineRunner
import com.clipping.mcpserver.service.pipeline.EnginePipelineStepStatus
import com.clipping.mcpserver.service.port.ClippingPipelinePort
import org.springframework.stereotype.Service

@Service
class DeterministicPipelineRunner(
    private val clippingPipelinePort: ClippingPipelinePort,
    private val enginePipelineRunner: EnginePipelineRunner = EnginePipelineRunner()
) {

    fun run(
        categoryId: String,
        hoursBack: Int?,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?,
        fallbackReason: String?
    ): PipelineRunResult {
        val engineRun = enginePipelineRunner.run(
            EnginePipelineActions(
                collect = { clippingPipelinePort.collect(categoryId, hoursBack) },
                summarize = { clippingPipelinePort.summarize(categoryId) },
                digest = {
                    clippingPipelinePort.digest(
                        categoryId = categoryId,
                        maxItems = maxItems,
                        unsentOnly = unsentOnly,
                        sendToSlack = sendToSlack,
                        slackChannelId = slackChannelId
                    )
                },
                collectDetail = { result ->
                    "newItems=${result.newItems}, duplicateSkipped=${result.duplicateSkipped}"
                },
                summarizeDetail = { result ->
                    "totalSummarized=${result.totalSummarized}"
                },
                digestDetail = { result ->
                    "selectedCount=${result.selectedCount}, postedToSlack=${result.postedToSlack}"
                }
            )
        )

        return PipelineRunResult(
            collect = engineRun.collect.toCollectResult(),
            summarize = engineRun.summarize.toSummarizeResult(),
            digest = engineRun.digest.toDigestResult(),
            orchestrationMode = PipelineOrchestrationMode.DETERMINISTIC,
            fallbackApplied = fallbackReason != null,
            orchestrationWarnings = listOfNotNull(fallbackReason?.let { "Ralph fallback applied: $it" }),
            stepTraces = engineRun.traces.map { trace ->
                PipelineStepTrace(
                    step = trace.step,
                    status = when (trace.status) {
                        EnginePipelineStepStatus.SUCCEEDED -> PipelineStepStatus.SUCCEEDED
                        EnginePipelineStepStatus.FAILED -> PipelineStepStatus.FAILED
                    },
                    startedAt = trace.startedAt.toString(),
                    endedAt = trace.endedAt.toString(),
                    detail = trace.detail
                )
            }
        )
    }
}
