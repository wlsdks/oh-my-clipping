package com.clipping.mcpserver.tool

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * admin_*_async 도구의 enqueue 및 상태 조회 흐름을 검증한다.
 *
 * PR-04 이전 명칭(`clip_*_async`)은 `admin_*_async`로 바뀌었으며,
 * 파일명은 기존 테스트 클래스 이력을 유지하기 위해 남겨 두었다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ClipAsyncToolsTest {

    @Autowired
    lateinit var toolCallbackProvider: ToolCallbackProvider

    @Test
    fun `admin_collect_async should enqueue job and admin_job_status should return pending`() {
        val collectTool = toolCallbackProvider.toolCallbacks
            .first { it.toolDefinition.name() == "admin_collect_async" }
        val statusTool = toolCallbackProvider.toolCallbacks
            .first { it.toolDefinition.name() == "admin_job_status" }

        val enqueueResult = collectTool.call("{}")
        enqueueResult.shouldContain("\"status\":\"PENDING\"")
        enqueueResult.shouldContain("\"jobType\":\"COLLECT\"")

        val jobId = enqueueResult.substringAfter("\"jobId\":\"").substringBefore("\"")
        val statusResult = statusTool.call(
            """
            {"jobId":"$jobId"}
            """.trimIndent(),
        )

        statusResult.shouldContain("\"id\":\"$jobId\"")
        statusResult.shouldContain("\"status\":\"PENDING\"")
    }

    @Test
    fun `admin_summarize_async should enqueue summarize job`() {
        val summarizeTool = toolCallbackProvider.toolCallbacks
            .first { it.toolDefinition.name() == "admin_summarize_async" }

        val enqueueResult = summarizeTool.call("{}")
        enqueueResult.shouldContain("\"status\":\"PENDING\"")
        enqueueResult.shouldContain("\"jobType\":\"SUMMARIZE\"")
    }
}
