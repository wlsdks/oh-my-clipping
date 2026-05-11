package com.ohmyclipping.entity

import com.ohmyclipping.model.Category
import com.ohmyclipping.service.dto.pipeline.PipelineRunEntity as PipelineRunModel
import com.ohmyclipping.service.dto.pipeline.PipelineRunStatus
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.PipelineRunStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * PipelineRunEntity의 slack_thread_ts, slack_payload_json 컬럼 매핑을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PipelineRunEntityTest {

    @Autowired lateinit var pipelineRunStore: PipelineRunStore
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var runRepository: com.ohmyclipping.repository.PipelineRunRepository

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        categoryId = categoryStore.save(
            Category(id = "", name = "EntityTest-${System.nanoTime()}")
        ).id
    }

    @Test
    fun `slack_thread_ts와 slack_payload_json을 저장하고 읽을 수 있다`() {
        // given: run을 저장한 후 JPA 엔티티를 직접 수정하여 신규 컬럼을 설정한다.
        val run = pipelineRunStore.save(
            PipelineRunModel(
                categoryId = categoryId,
                categoryName = "entity-test",
                triggeredBy = "test",
                status = PipelineRunStatus.RUNNING,
                orchestrationMode = "DETERMINISTIC",
                startedAt = java.time.Instant.now()
            )
        )

        // JPA 엔티티를 꺼내 신규 컬럼 값을 직접 설정한다.
        val entity = runRepository.findById(run.id).orElseThrow()
        entity.slackThreadTs = "1713327300.001234"
        entity.slackPayloadJson = """{"blocks":[]}"""
        runRepository.save(entity)
        runRepository.flush()

        // when: 새로 조회한다.
        val loaded = runRepository.findById(run.id).orElseThrow()

        // then
        loaded.slackThreadTs shouldBe "1713327300.001234"
        loaded.slackPayloadJson shouldNotBe null
        loaded.slackPayloadJson!!.contains("blocks") shouldBe true
    }

    @Test
    fun `slack_thread_ts와 slack_payload_json 기본값은 null이다`() {
        // given
        val run = pipelineRunStore.save(
            PipelineRunModel(
                categoryId = categoryId,
                categoryName = "entity-default-test",
                triggeredBy = "test",
                status = PipelineRunStatus.RUNNING,
                orchestrationMode = "DETERMINISTIC",
                startedAt = java.time.Instant.now()
            )
        )

        // when
        val entity = runRepository.findById(run.id).orElseThrow()

        // then: 기존 생성 코드를 건드리지 않아도 기본값은 null이다.
        entity.slackThreadTs shouldBe null
        entity.slackPayloadJson shouldBe null
    }
}
