package com.ohmyclipping.service.pipeline

import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.dto.pipeline.PipelineRunEntity
import com.ohmyclipping.service.dto.pipeline.PipelineRunStatus
import com.ohmyclipping.service.dto.admin.PipelineStartCommand
import com.ohmyclipping.service.event.PipelineRunRequestedEvent
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.PipelineRunStore
import com.ohmyclipping.store.RssSourceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * 파이프라인 실행 요청을 접수하고 커밋 이후 비동기 실행 이벤트를 발행하는 서비스.
 * RUNNING 상태의 run 레코드를 먼저 저장한 뒤, AFTER_COMMIT 리스너가 워커 실행을 맡는다.
 */
@Service
class PipelineExecutionService(
    private val pipelineRunStore: PipelineRunStore,
    private val categoryService: CategoryService,
    private val sourceStore: RssSourceStore,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val categoryStore: CategoryStore
) {

    /**
     * 실행 이력을 필터/페이징으로 조회한다.
     *
     * @param since null이 아니면 createdAt이 이 시각 이후인 건만 반환한다 (within 파라미터 전달용)
     * @param personaId 페르소나 필터 — 지정 시, 해당 페르소나에 연결된 활성 카테고리에서 발생한
     *                  실행만 반환한다. null/blank이면 필터링하지 않는다.
     *
     * 페르소나가 지정된 경우 카테고리 ID 집합을 구해 store에 `categoryIds`로 전달하여
     * SQL `IN` 절로 필터링한다. 페이지네이션은 DB 레벨에서 그대로 적용되므로
     * content 크기와 totalCount가 일관되게 유지된다.
     */
    @Transactional(readOnly = true)
    fun findRuns(
        categoryId: String? = null,
        status: String? = null,
        since: Instant? = null,
        offset: Int = 0,
        limit: Int = 20,
        personaId: String? = null
    ): List<PipelineRunEntity> {
        // 페르소나 필터가 없으면 기존 동작을 그대로 유지한다.
        if (personaId.isNullOrBlank()) {
            return pipelineRunStore.findAll(
                categoryId = categoryId,
                status = status,
                since = since,
                offset = offset,
                limit = limit
            )
        }

        // 페르소나 카테고리가 없으면 결과가 비어있음이 확정된다.
        val personaCategoryIds = categoryStore.findActiveByPersonaId(personaId).map { it.id }.toSet()
        if (personaCategoryIds.isEmpty()) return emptyList()

        // categoryId 필터가 함께 왔는데 페르소나 카테고리에 포함되지 않으면 즉시 빈 결과를 반환한다.
        if (categoryId != null && categoryId !in personaCategoryIds) return emptyList()

        // store에 categoryIds를 전달하여 SQL IN 절로 필터링한다. 페이지네이션은 DB에서 처리된다.
        return pipelineRunStore.findAll(
            categoryId = categoryId,
            status = status,
            since = since,
            offset = offset,
            limit = limit,
            categoryIds = personaCategoryIds
        )
    }

    /**
     * 실행 이력 총 건수를 조회한다.
     *
     * @param since null이 아니면 createdAt이 이 시각 이후인 건만 집계한다
     * @param personaId 페르소나 필터 — 지정 시, 해당 페르소나에 연결된 활성 카테고리에서 발생한
     *                  실행만 카운트한다. null/blank이면 필터링하지 않는다.
     */
    @Transactional(readOnly = true)
    fun countRuns(
        categoryId: String? = null,
        status: String? = null,
        since: Instant? = null,
        personaId: String? = null
    ): Int {
        // 페르소나 필터가 없으면 기존 동작을 그대로 유지한다.
        if (personaId.isNullOrBlank()) {
            return pipelineRunStore.countAll(categoryId = categoryId, status = status, since = since)
        }

        // 페르소나 카테고리가 없으면 카운트는 0이다.
        val personaCategoryIds = categoryStore.findActiveByPersonaId(personaId).map { it.id }.toSet()
        if (personaCategoryIds.isEmpty()) return 0

        if (categoryId != null && categoryId !in personaCategoryIds) return 0

        // store에 categoryIds를 넘겨 한 번의 COUNT 쿼리로 합산한다.
        return pipelineRunStore.countAll(
            categoryId = categoryId,
            status = status,
            since = since,
            categoryIds = personaCategoryIds
        )
    }

    /** 단일 실행 상세를 조회한다 */
    @Transactional(readOnly = true)
    fun findById(runId: String) = pipelineRunStore.findById(runId)

    /** 카테고리별 최근 실행을 조회한다 */
    @Transactional(readOnly = true)
    fun findLatestByCategoryId(categoryId: String, limit: Int = 1) =
        pipelineRunStore.findLatestByCategoryId(categoryId, limit)

    /** 실행의 단계별 추적 기록을 조회한다 */
    @Transactional(readOnly = true)
    fun findStepTraces(runId: String) = pipelineRunStore.findStepTracesByRunId(runId)

    /**
     * 기존 호출자 호환용 오버로드.
     * 내부적으로 PipelineStartCommand로 변환하여 위임한다.
     */
    @Transactional
    fun startExecution(
        categoryId: String,
        triggeredBy: String,
        hoursBack: Int?,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?,
        ralphLoopEnabled: Boolean?,
        ralphLoopMaxIterations: Int?,
        ralphLoopStopPhrase: String?
    ): String = startExecution(
        PipelineStartCommand(
            categoryId = categoryId,
            triggeredBy = triggeredBy,
            hoursBack = hoursBack,
            maxItems = maxItems,
            unsentOnly = unsentOnly,
            sendToSlack = sendToSlack,
            slackChannelId = slackChannelId,
            ralphLoopEnabled = ralphLoopEnabled,
            ralphLoopMaxIterations = ralphLoopMaxIterations,
            ralphLoopStopPhrase = ralphLoopStopPhrase
        )
    )

    /**
     * 파이프라인 실행을 접수하고 커밋 이후 비동기 워커가 실행되도록 이벤트를 발행한다.
     * run 레코드 저장과 실행 요청 발행을 하나의 트랜잭션으로 묶어 선행 데이터 없는 실행을 막는다.
     *
     * @param command 파이프라인 실행 파라미터를 묶은 커맨드 객체
     * @return 생성된 파이프라인 실행 ID
     */
    @Transactional
    fun startExecution(command: PipelineStartCommand): String {
        // 카테고리명 조회 (없으면 기본값으로 대체)
        val category = categoryService.findById(command.categoryId)
        val categoryName = category?.name ?: "(삭제된 카테고리)"

        // 카테고리가 존재하면 활성 소스 유무를 검증한다
        if (category != null) {
            val approvedSources = sourceStore.listApproved(command.categoryId)
            if (approvedSources.isEmpty()) {
                throw com.ohmyclipping.error.InvalidInputException(
                    "해당 카테고리에 승인된 소스가 없습니다. 소스를 먼저 추가해 주세요."
                )
            }
        }

        // 동일 카테고리에 이미 실행 중인 파이프라인이 있으면 중복 실행을 방지한다
        val runningCount = pipelineRunStore.countRunningByCategoryId(command.categoryId)
        if (runningCount > 0) {
            throw com.ohmyclipping.error.ConflictException(
                message = "해당 카테고리에 이미 실행 중인 파이프라인이 있습니다. 완료 후 다시 시도해 주세요."
            )
        }

        // RUNNING 상태의 실행 레코드 생성
        val run = pipelineRunStore.save(
            PipelineRunEntity(
                categoryId = command.categoryId,
                categoryName = categoryName,
                triggeredBy = command.triggeredBy,
                status = PipelineRunStatus.RUNNING,
                orchestrationMode = "PENDING",
                startedAt = Instant.now()
            )
        )
        log.info { "파이프라인 실행 레코드 생성: runId=${run.id}, categoryId=${command.categoryId}" }

        // 워커 실행은 커밋 이후 이벤트 리스너가 맡아 미커밋 상태를 읽는 경쟁을 막는다
        applicationEventPublisher.publishEvent(
            PipelineRunRequestedEvent(
                runId = run.id,
                categoryId = command.categoryId,
                hoursBack = command.hoursBack,
                maxItems = command.maxItems,
                unsentOnly = command.unsentOnly,
                sendToSlack = command.sendToSlack,
                slackChannelId = command.slackChannelId,
                ralphLoopEnabled = command.ralphLoopEnabled,
                ralphLoopMaxIterations = command.ralphLoopMaxIterations,
                ralphLoopStopPhrase = command.ralphLoopStopPhrase
            )
        )

        return run.id
    }
}
