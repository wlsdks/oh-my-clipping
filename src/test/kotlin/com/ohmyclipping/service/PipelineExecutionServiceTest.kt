package com.ohmyclipping.service

import com.ohmyclipping.service.pipeline.PipelineExecutionService

import com.ohmyclipping.model.Category
import com.ohmyclipping.service.dto.pipeline.PipelineRunEntity
import com.ohmyclipping.service.dto.pipeline.PipelineRunStatus
import com.ohmyclipping.service.event.PipelineRunRequestedEvent
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.PipelineRunStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

class PipelineExecutionServiceTest {

    private val pipelineRunStore = mockk<PipelineRunStore>()
    private val categoryService = mockk<CategoryService>()
    private val sourceStore = mockk<com.ohmyclipping.store.RssSourceStore>()
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val categoryStore = mockk<CategoryStore>()

    private val service = PipelineExecutionService(
        pipelineRunStore = pipelineRunStore,
        categoryService = categoryService,
        sourceStore = sourceStore,
        applicationEventPublisher = applicationEventPublisher,
        categoryStore = categoryStore
    )

    @Nested
    inner class `startExecution 메서드` {

        @Test
        fun `카테고리가 존재하면 해당 이름으로 RUNNING 레코드를 생성하고 실행 이벤트를 발행한다`() {
            // 카테고리 조회 설정
            every { categoryService.findById("cat-1") } returns Category(
                id = "cat-1",
                name = "경제 뉴스"
            )
            every { sourceStore.listApproved("cat-1") } returns listOf(mockk(relaxed = true))
            every { pipelineRunStore.countRunningByCategoryId("cat-1") } returns 0

            // 저장 시 ID가 생성된 엔티티를 반환
            val savedSlot = slot<PipelineRunEntity>()
            every { pipelineRunStore.save(capture(savedSlot)) } answers {
                savedSlot.captured.copy(id = "run-001")
            }

            val result = service.startExecution(
                categoryId = "cat-1",
                triggeredBy = "admin",
                hoursBack = 24,
                maxItems = 10,
                unsentOnly = true,
                sendToSlack = true,
                slackChannelId = "C123",
                ralphLoopEnabled = null,
                ralphLoopMaxIterations = null,
                ralphLoopStopPhrase = null
            )

            // 반환된 runId 검증
            result shouldBe "run-001"

            // 저장된 엔티티의 필드 검증
            savedSlot.captured.categoryId shouldBe "cat-1"
            savedSlot.captured.categoryName shouldBe "경제 뉴스"
            savedSlot.captured.triggeredBy shouldBe "admin"
            savedSlot.captured.status shouldBe PipelineRunStatus.RUNNING
            savedSlot.captured.orchestrationMode shouldBe "PENDING"

            // 비동기 실행은 AFTER_COMMIT 이벤트로 위임한다.
            val eventSlot = slot<PipelineRunRequestedEvent>()
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(capture(eventSlot))
            }
            eventSlot.captured.runId shouldBe "run-001"
            eventSlot.captured.categoryId shouldBe "cat-1"
            eventSlot.captured.hoursBack shouldBe 24
            eventSlot.captured.maxItems shouldBe 10
            eventSlot.captured.unsentOnly shouldBe true
            eventSlot.captured.sendToSlack shouldBe true
            eventSlot.captured.slackChannelId shouldBe "C123"
        }

        @Test
        fun `카테고리가 존재하지 않으면 categoryName을 삭제된 카테고리로 대체한다`() {
            // 존재하지 않는 카테고리 (소스 체크 건너뜀)
            every { categoryService.findById("missing-cat") } returns null
            every { pipelineRunStore.countRunningByCategoryId("missing-cat") } returns 0

            val savedSlot = slot<PipelineRunEntity>()
            every { pipelineRunStore.save(capture(savedSlot)) } answers {
                savedSlot.captured.copy(id = "run-002")
            }

            val result = service.startExecution(
                categoryId = "missing-cat",
                triggeredBy = "scheduler",
                hoursBack = null,
                maxItems = null,
                unsentOnly = null,
                sendToSlack = null,
                slackChannelId = null,
                ralphLoopEnabled = null,
                ralphLoopMaxIterations = null,
                ralphLoopStopPhrase = null
            )

            result shouldBe "run-002"
            savedSlot.captured.categoryName shouldBe "(삭제된 카테고리)"
            savedSlot.captured.triggeredBy shouldBe "scheduler"

            val eventSlot = slot<PipelineRunRequestedEvent>()
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(capture(eventSlot))
            }
            eventSlot.captured.runId shouldBe "run-002"
            eventSlot.captured.categoryId shouldBe "missing-cat"
            eventSlot.captured.hoursBack shouldBe null
            eventSlot.captured.sendToSlack shouldBe null
        }

        @Test
        fun `Ralph 루프 오버라이드 파라미터가 실행 이벤트에 그대로 전달된다`() {
            every { categoryService.findById("cat-r") } returns Category(
                id = "cat-r",
                name = "Ralph 테스트"
            )
            every { sourceStore.listApproved("cat-r") } returns listOf(mockk(relaxed = true))
            every { pipelineRunStore.countRunningByCategoryId("cat-r") } returns 0

            val savedSlot = slot<PipelineRunEntity>()
            every { pipelineRunStore.save(capture(savedSlot)) } answers {
                savedSlot.captured.copy(id = "run-003")
            }

            service.startExecution(
                categoryId = "cat-r",
                triggeredBy = "admin",
                hoursBack = 48,
                maxItems = 5,
                unsentOnly = false,
                sendToSlack = false,
                slackChannelId = null,
                ralphLoopEnabled = true,
                ralphLoopMaxIterations = 3,
                ralphLoopStopPhrase = "충분합니다"
            )

            val eventSlot = slot<PipelineRunRequestedEvent>()
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(capture(eventSlot))
            }
            eventSlot.captured.runId shouldBe "run-003"
            eventSlot.captured.categoryId shouldBe "cat-r"
            eventSlot.captured.ralphLoopEnabled shouldBe true
            eventSlot.captured.ralphLoopMaxIterations shouldBe 3
            eventSlot.captured.ralphLoopStopPhrase shouldBe "충분합니다"
        }
    }

    @Nested
    inner class `findRuns 메서드` {

        @Test
        fun `필터 파라미터가 store에 그대로 전달된다`() {
            val expected = listOf(
                PipelineRunEntity(
                    id = "run-1",
                    categoryId = "cat-1",
                    categoryName = "테스트",
                    triggeredBy = "admin",
                    status = PipelineRunStatus.SUCCEEDED,
                    orchestrationMode = "SEQUENTIAL",
                    startedAt = Instant.now()
                )
            )
            every {
                pipelineRunStore.findAll(
                    categoryId = "cat-1",
                    status = "SUCCEEDED",
                    offset = 10,
                    limit = 5
                )
            } returns expected

            val result = service.findRuns(
                categoryId = "cat-1",
                status = "SUCCEEDED",
                offset = 10,
                limit = 5
            )

            result shouldBe expected
        }
    }

    @Nested
    inner class `findById 메서드` {

        @Test
        fun `존재하는 runId 조회 시 엔티티를 반환한다`() {
            val run = PipelineRunEntity(
                id = "run-x",
                categoryId = "cat-1",
                categoryName = "테스트",
                triggeredBy = "admin",
                status = PipelineRunStatus.RUNNING,
                orchestrationMode = "PENDING",
                startedAt = Instant.now()
            )
            every { pipelineRunStore.findById("run-x") } returns run

            service.findById("run-x") shouldBe run
        }

        @Test
        fun `존재하지 않는 runId 조회 시 null을 반환한다`() {
            every { pipelineRunStore.findById("no-such-run") } returns null

            service.findById("no-such-run") shouldBe null
        }
    }

    @Nested
    inner class `countRuns 메서드` {

        @Test
        fun `필터 조건으로 총 건수를 조회한다`() {
            every { pipelineRunStore.countAll(categoryId = "cat-1", status = "RUNNING") } returns 42

            service.countRuns(categoryId = "cat-1", status = "RUNNING") shouldBe 42
        }
    }

    @Nested
    inner class `findRuns 페르소나 필터` {

        private fun makeRun(id: String, categoryId: String): PipelineRunEntity = PipelineRunEntity(
            id = id,
            categoryId = categoryId,
            categoryName = "테스트",
            triggeredBy = "admin",
            status = PipelineRunStatus.SUCCEEDED,
            orchestrationMode = "SEQUENTIAL",
            startedAt = Instant.now()
        )

        private val personaCatA = Category(id = "cat-A", name = "분석가 A", personaId = "persona-xyz")
        private val personaCatB = Category(id = "cat-B", name = "분석가 B", personaId = "persona-xyz")

        @Test
        fun `personaId가 null이면 store에 categoryIds 파라미터를 넘기지 않는다`() {
            val expected = listOf(makeRun("run-1", "cat-1"))
            every {
                pipelineRunStore.findAll(
                    categoryId = null,
                    status = null,
                    offset = 0,
                    limit = 20
                )
            } returns expected

            val result = service.findRuns(personaId = null)

            result shouldBe expected
            verify(exactly = 0) { categoryStore.findActiveByPersonaId(any()) }
        }

        @Test
        fun `personaId 지정 시 store에 categoryIds 집합이 전달된다`() {
            every { categoryStore.findActiveByPersonaId("persona-xyz") } returns
                listOf(personaCatA, personaCatB)
            // store가 이미 IN 절로 필터하여 반환한다고 가정한다.
            every {
                pipelineRunStore.findAll(
                    categoryId = null,
                    status = null,
                    offset = 0,
                    limit = 20,
                    categoryIds = setOf("cat-A", "cat-B")
                )
            } returns listOf(makeRun("run-1", "cat-A"), makeRun("run-3", "cat-B"))

            val result = service.findRuns(personaId = "persona-xyz", offset = 0, limit = 20)

            result.map { it.id } shouldBe listOf("run-1", "run-3")
            verify(exactly = 1) {
                pipelineRunStore.findAll(
                    categoryId = null,
                    status = null,
                    offset = 0,
                    limit = 20,
                    categoryIds = setOf("cat-A", "cat-B")
                )
            }
        }

        @Test
        fun `페이지네이션은 store의 offset, limit 파라미터로 그대로 위임된다`() {
            // 스토어 SQL 페이지네이션이 깨지지 않아야 함을 검증한다 (메모리 drop/take 금지).
            every { categoryStore.findActiveByPersonaId("persona-xyz") } returns listOf(personaCatA)
            val pageRows = listOf(
                makeRun("run-41", "cat-A"),
                makeRun("run-42", "cat-A"),
                makeRun("run-43", "cat-A")
            )
            // offset=20, limit=20로 호출 시 store에도 동일한 페이지 파라미터로 전달되어야 한다.
            every {
                pipelineRunStore.findAll(
                    categoryId = null,
                    status = null,
                    offset = 20,
                    limit = 20,
                    categoryIds = setOf("cat-A")
                )
            } returns pageRows

            val result = service.findRuns(personaId = "persona-xyz", offset = 20, limit = 20)

            result shouldBe pageRows
            verify(exactly = 1) {
                pipelineRunStore.findAll(
                    categoryId = any(),
                    status = any(),
                    offset = 20,
                    limit = 20,
                    categoryIds = setOf("cat-A")
                )
            }
        }

        @Test
        fun `personaId에 연결된 활성 카테고리가 없으면 빈 리스트를 반환한다`() {
            every { categoryStore.findActiveByPersonaId("persona-empty") } returns emptyList()

            val result = service.findRuns(personaId = "persona-empty")

            result shouldBe emptyList()
            verify(exactly = 0) {
                pipelineRunStore.findAll(any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `personaId가 공백이면 필터로 취급하지 않는다`() {
            val expected = listOf(makeRun("run-x", "cat-x"))
            every {
                pipelineRunStore.findAll(
                    categoryId = null,
                    status = null,
                    offset = 0,
                    limit = 20
                )
            } returns expected

            val result = service.findRuns(personaId = "   ")

            result shouldBe expected
            verify(exactly = 0) { categoryStore.findActiveByPersonaId(any()) }
        }

        @Test
        fun `categoryId 필터가 페르소나 집합 밖이면 빈 결과를 반환한다`() {
            every { categoryStore.findActiveByPersonaId("persona-xyz") } returns
                listOf(personaCatA, personaCatB)

            val result = service.findRuns(
                categoryId = "cat-unrelated",
                personaId = "persona-xyz"
            )

            result shouldBe emptyList()
            verify(exactly = 0) {
                pipelineRunStore.findAll(any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `categoryId가 페르소나 집합 안이면 store에 둘 다 전달한다`() {
            every { categoryStore.findActiveByPersonaId("persona-xyz") } returns
                listOf(personaCatA, personaCatB)
            every {
                pipelineRunStore.findAll(
                    categoryId = "cat-A",
                    status = null,
                    offset = 0,
                    limit = 20,
                    categoryIds = setOf("cat-A", "cat-B")
                )
            } returns listOf(makeRun("run-9", "cat-A"))

            val result = service.findRuns(
                categoryId = "cat-A",
                personaId = "persona-xyz"
            )

            result.map { it.id } shouldBe listOf("run-9")
        }
    }

    @Nested
    inner class `countRuns 페르소나 필터` {

        private val personaCatA = Category(id = "cat-A", name = "분석가 A", personaId = "persona-xyz")
        private val personaCatB = Category(id = "cat-B", name = "분석가 B", personaId = "persona-xyz")

        @Test
        fun `personaId가 null이면 store에 categoryIds 파라미터를 넘기지 않는다`() {
            every {
                pipelineRunStore.countAll(categoryId = "cat-1", status = "RUNNING")
            } returns 7

            val result = service.countRuns(
                categoryId = "cat-1",
                status = "RUNNING",
                personaId = null
            )

            result shouldBe 7
            verify(exactly = 0) { categoryStore.findActiveByPersonaId(any()) }
        }

        @Test
        fun `personaId 지정 시 store를 한 번만 호출하여 categoryIds로 집계한다`() {
            every { categoryStore.findActiveByPersonaId("persona-xyz") } returns
                listOf(personaCatA, personaCatB)
            every {
                pipelineRunStore.countAll(
                    categoryId = null,
                    status = null,
                    categoryIds = setOf("cat-A", "cat-B")
                )
            } returns 7

            val result = service.countRuns(personaId = "persona-xyz")

            result shouldBe 7
            // N+1 호출을 방지한다: 카테고리 개수와 무관하게 정확히 1회만 호출해야 한다.
            verify(exactly = 1) {
                pipelineRunStore.countAll(
                    categoryId = null,
                    status = null,
                    categoryIds = setOf("cat-A", "cat-B")
                )
            }
        }

        @Test
        fun `personaId에 연결된 활성 카테고리가 없으면 0을 반환한다`() {
            every { categoryStore.findActiveByPersonaId("persona-empty") } returns emptyList()

            service.countRuns(personaId = "persona-empty") shouldBe 0
            verify(exactly = 0) { pipelineRunStore.countAll(any(), any(), any()) }
        }

        @Test
        fun `categoryId가 페르소나 집합 밖이면 0을 반환한다`() {
            every { categoryStore.findActiveByPersonaId("persona-xyz") } returns
                listOf(personaCatA, personaCatB)

            val result = service.countRuns(
                categoryId = "cat-unrelated",
                personaId = "persona-xyz"
            )

            result shouldBe 0
            verify(exactly = 0) { pipelineRunStore.countAll(any(), any(), any()) }
        }
    }
}
