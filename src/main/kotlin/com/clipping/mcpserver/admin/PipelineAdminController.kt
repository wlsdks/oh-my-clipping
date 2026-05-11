package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.PipelineExecuteRequest
import com.clipping.mcpserver.admin.dto.PipelineExecuteResponse
import com.clipping.mcpserver.admin.dto.PipelineRunResponse
import com.clipping.mcpserver.admin.dto.PipelineRunsPageResponse
import com.clipping.mcpserver.admin.util.AdminTimeRange
import com.clipping.mcpserver.service.pipeline.PipelineExecutionService
import com.clipping.mcpserver.support.PaginationUtils
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 파이프라인 실행 및 이력 조회 API를 제공하는 관리자 컨트롤러.
 * 수동 실행 요청, 실행 이력 목록/상세 조회, 최근 실행 조회 기능을 담당한다.
 */
@RestController
@RequestMapping("/api/admin/pipeline")
class PipelineAdminController(
    private val executionService: PipelineExecutionService
) {

    /**
     * 파이프라인을 비동기로 실행한다.
     * 즉시 202 Accepted를 반환하고, 실제 실행은 백그라운드에서 진행된다.
     *
     * @param request 실행 파라미터 (카테고리 ID, 수집 범위 등)
     * @return 생성된 실행 ID
     */
    @PostMapping("/execute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun execute(
        @RequestBody request: PipelineExecuteRequest
    ): PipelineExecuteResponse {
        // 비동기 실행을 시작하고 runId를 반환한다.
        val runId = executionService.startExecution(
            categoryId = request.categoryId,
            triggeredBy = "admin",
            hoursBack = request.hoursBack,
            maxItems = request.maxItems,
            unsentOnly = request.unsentOnly,
            sendToSlack = request.sendToSlack,
            slackChannelId = request.slackChannelId,
            ralphLoopEnabled = request.ralphLoopEnabled,
            ralphLoopMaxIterations = request.ralphLoopMaxIterations,
            ralphLoopStopPhrase = request.ralphLoopStopPhrase
        )
        return PipelineExecuteResponse(runId = runId)
    }

    /**
     * 파이프라인 실행 이력을 페이지네이션으로 조회한다.
     * 카테고리, 상태, 기간, 페르소나로 필터링할 수 있다.
     *
     * @param categoryId 카테고리 ID 필터 (선택)
     * @param status 실행 상태 필터 (선택, 잘못된 값은 무시)
     * @param from 시작 날짜 필터 (선택, yyyy-MM-dd)
     * @param to 종료 날짜 필터 (선택, yyyy-MM-dd)
     * @param personaId 페르소나 ID 필터 (선택, 해당 페르소나 카테고리에 해당하는 실행만 반환)
     * @param within 최근 기간 필터 (선택, "1d" 또는 "7d"). startedAt 기준으로 필터링한다.
     * @param page 페이지 번호 (기본 0)
     * @param size 페이지 크기 (기본 30)
     */
    @GetMapping("/runs")
    fun listRuns(
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) personaId: String?,
        @RequestParam(required = false) within: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int
    ): PipelineRunsPageResponse {
        val safeSize = size.coerceIn(1, 200)
        // 음수 페이지가 DB OFFSET 음수로 전달되지 않도록 첫 페이지로 보정한다.
        val safePage = page.coerceAtLeast(0)
        val offset = PaginationUtils.safeOffset(safePage, safeSize)

        // 페르소나 필터는 빈 문자열을 null로 정규화해 기존 동작을 유지한다.
        val normalizedPersonaId = personaId?.trim()?.takeIf { it.isNotBlank() }
        // within 파라미터를 KST 달력 기준 Instant 하한으로 변환한다. 잘못된 값이면 즉시 예외를 던진다.
        val since = AdminTimeRange.parseWithin(within)

        // 필터 조건으로 실행 이력을 조회한다.
        val runs = executionService.findRuns(
            categoryId = categoryId,
            status = status,
            since = since,
            offset = offset,
            limit = safeSize,
            personaId = normalizedPersonaId
        )

        // 총 건수를 조회한다.
        val totalCount = executionService.countRuns(
            categoryId = categoryId,
            status = status,
            since = since,
            personaId = normalizedPersonaId
        )

        // 각 실행에 대한 stepTrace를 함께 조회하여 응답을 구성한다.
        val content = runs.map { run ->
            val traces = executionService.findStepTraces(run.id)
            PipelineRunResponse.from(run, traces)
        }

        return PipelineRunsPageResponse(
            content = content,
            totalCount = totalCount.toLong(),
            page = safePage,
            size = safeSize
        )
    }

    /**
     * 특정 파이프라인 실행의 상세 정보를 조회한다.
     * stepTrace 목록을 포함한다.
     *
     * @param runId 실행 ID
     * @throws NoSuchElementException 실행 ID가 존재하지 않을 경우
     */
    @GetMapping("/runs/{runId}")
    fun getRunDetail(
        @PathVariable runId: String
    ): PipelineRunResponse {
        // 실행 레코드를 조회한다. 없으면 404를 반환한다.
        val run = executionService.findById(runId)
            ?: throw NoSuchElementException("파이프라인 실행을 찾을 수 없습니다: $runId")

        // 해당 실행의 단계 추적 목록을 조회한다.
        val traces = executionService.findStepTraces(runId)
        return PipelineRunResponse.from(run, traces)
    }

    /**
     * 카테고리의 최근 파이프라인 실행을 조회한다.
     * categoryId가 없으면 null을 반환한다.
     *
     * @param categoryId 카테고리 ID (선택)
     * @return 최근 실행 정보 또는 null
     */
    @GetMapping("/latest")
    fun getLatestRun(
        @RequestParam(required = false) categoryId: String?
    ): org.springframework.http.ResponseEntity<PipelineRunResponse> {
        if (categoryId.isNullOrBlank()) return org.springframework.http.ResponseEntity.noContent().build()

        // 가장 최근 실행 1건을 조회한다.
        val latestRun = executionService.findLatestByCategoryId(categoryId, limit = 1)
            .firstOrNull() ?: return org.springframework.http.ResponseEntity.noContent().build()

        // 단계 추적 목록을 함께 조회한다.
        val traces = executionService.findStepTraces(latestRun.id)
        return org.springframework.http.ResponseEntity.ok(PipelineRunResponse.from(latestRun, traces))
    }
}
