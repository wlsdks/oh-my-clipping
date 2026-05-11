package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.service.dto.pipeline.PipelineRunEntity
import com.clipping.mcpserver.service.dto.pipeline.PipelineRunStatus
import com.clipping.mcpserver.service.dto.clipping.PipelineStepStatus
import com.clipping.mcpserver.service.dto.pipeline.PipelineStepTraceEntity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * pipeline_step_traces 스키마가 실제 step 이름 길이를 수용하는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcPipelineRunStoreSchemaIntegrationTest {

    @Autowired lateinit var pipelineRunStore: PipelineRunStore
    @Autowired lateinit var categoryStore: CategoryStore

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        categoryId = categoryStore.save(
            Category(id = "", name = "PipelineRunStoreSchema-${System.nanoTime()}")
        ).id
    }

    @Test
    fun `saveStepTrace는 긴 반복 step 이름도 손실 없이 저장한다`() {
        val run = pipelineRunStore.save(
            PipelineRunEntity(
                categoryId = categoryId,
                categoryName = "schema-test",
                triggeredBy = "test",
                status = PipelineRunStatus.RUNNING,
                orchestrationMode = "RALPH",
                startedAt = Instant.now()
            )
        )

        val trace = pipelineRunStore.saveStepTrace(
            PipelineStepTraceEntity(
                runId = run.id,
                step = "ITERATION_12_CRITIC_REVIEW",
                status = PipelineStepStatus.SUCCEEDED,
                startedAt = Instant.now(),
                endedAt = Instant.now(),
                detail = "critic step"
            )
        )

        val saved = pipelineRunStore.findStepTracesByRunId(run.id).single()
        saved.id shouldBe trace.id
        saved.step shouldBe "ITERATION_12_CRITIC_REVIEW"
        saved.status shouldBe PipelineStepStatus.SUCCEEDED
    }
}
