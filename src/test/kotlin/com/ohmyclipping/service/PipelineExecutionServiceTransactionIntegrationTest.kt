package com.ohmyclipping.service

import com.ohmyclipping.service.pipeline.PipelineAsyncWorker
import com.ohmyclipping.service.pipeline.PipelineExecutionService

import com.ohmyclipping.model.Category
import com.ohmyclipping.service.dto.pipeline.PipelineRunStatus
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.PipelineRunStore
import com.ohmyclipping.store.RssSourceStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.reset
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate

/**
 * 파이프라인 실행 접수와 비동기 시작이 트랜잭션 경계를 지키는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PipelineExecutionServiceTransactionIntegrationTest {

    @Autowired
    lateinit var pipelineExecutionService: PipelineExecutionService

    @Autowired
    lateinit var pipelineRunStore: PipelineRunStore

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var sourceStore: RssSourceStore

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @MockitoBean
    lateinit var pipelineAsyncWorker: PipelineAsyncWorker

    private lateinit var category: Category

    @BeforeEach
    fun setup() {
        reset(pipelineAsyncWorker)
        category = categoryStore.save(
            Category(
                id = "",
                name = "pipeline-tx-${System.nanoTime()}"
            )
        )
        // 파이프라인 실행에는 최소 1개의 승인된(active+approved+verified) 소스가 필요하다
        sourceStore.save(
            RssSource(
                id = "",
                categoryId = category.id,
                name = "test-source",
                url = "https://example.com/rss",
                crawlApproved = true,
                verificationStatus = "VERIFIED"
            )
        )
    }

    @AfterEach
    fun cleanup() {
        if (this::category.isInitialized) {
            jdbc.update("DELETE FROM pipeline_runs WHERE category_id = ?", category.id)
            jdbc.update("DELETE FROM rss_sources WHERE category_id = ?", category.id)
            categoryStore.delete(category.id)
        }
    }

    @Test
    fun `startExecution은 커밋 후에만 비동기 워커를 호출한다`() {
        val runId = pipelineExecutionService.startExecution(
            categoryId = category.id,
            triggeredBy = "admin",
            hoursBack = 24,
            maxItems = 10,
            unsentOnly = true,
            sendToSlack = false,
            slackChannelId = "C_PIPELINE_TX",
            ralphLoopEnabled = true,
            ralphLoopMaxIterations = 2,
            ralphLoopStopPhrase = "충분"
        )

        val savedRun = pipelineRunStore.findById(runId)
        assertEquals(PipelineRunStatus.RUNNING, savedRun?.status)

        verify(pipelineAsyncWorker, timeout(1000)).execute(
            runId,
            category.id,
            24,
            10,
            true,
            false,
            "C_PIPELINE_TX",
            true,
            2,
            "충분"
        )
    }

    @Test
    fun `외부 트랜잭션이 롤백되면 run 저장과 워커 호출이 함께 취소된다`() {
        assertThrows(IllegalStateException::class.java) {
            transactionTemplate.executeWithoutResult {
                pipelineExecutionService.startExecution(
                    categoryId = category.id,
                    triggeredBy = "admin",
                    hoursBack = 12,
                    maxItems = 5,
                    unsentOnly = true,
                    sendToSlack = true,
                    slackChannelId = "C_PIPELINE_TX",
                    ralphLoopEnabled = null,
                    ralphLoopMaxIterations = null,
                    ralphLoopStopPhrase = null
                )
                throw IllegalStateException("force rollback")
            }
        }

        assertEquals(0, pipelineRunStore.countAll(categoryId = category.id, status = null))
        verifyNoInteractions(pipelineAsyncWorker)
    }
}
