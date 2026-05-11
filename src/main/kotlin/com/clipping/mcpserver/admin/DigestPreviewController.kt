package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.digest.DigestPreviewService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * 다이제스트 모드 미리보기 및 dry-run API.
 *
 * FE 위자드에서 모드 전환 시 현재 mode 를 확인하고(Task 23),
 * E2E 다이제스트 검증(Task 25)에서 Block Kit 결과를 Slack 전송 없이 확인하는 데 사용한다.
 */
@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
class DigestPreviewController(
    private val previewService: DigestPreviewService,
) {

    /**
     * 카테고리의 현재 다이제스트 모드와 키워드/조직 수를 반환한다.
     *
     * @param categoryId 대상 카테고리 ID
     * @return currentMode (null 이면 설정 미완료), keywordCount, orgCount, changed(FE 에서 관리)
     */
    @GetMapping("/{categoryId}/digest-mode-preview")
    fun previewMode(@PathVariable categoryId: String): Mono<Map<String, Any?>> {
        val snap = previewService.previewModeForCategory(categoryId)
        return Mono.just(
            mapOf(
                "currentMode" to snap.currentMode,
                "keywordCount" to snap.keywordCount,
                "orgCount" to snap.orgCount,
                // FE 가 이전 mode 와 비교해 changed 를 판단한다 — 서버는 항상 false 반환
                "changed" to false,
            )
        )
    }

    /**
     * 최근 기사를 기반으로 Block Kit JSON 을 생성하고 반환한다. Slack 미발송.
     *
     * @param categoryId 대상 카테고리 ID
     * @return mode 이름, blocks(Block Kit JSON 배열 문자열)
     */
    @PostMapping("/{categoryId}/digest-dry-run")
    fun dryRun(@PathVariable categoryId: String): Mono<Map<String, Any?>> {
        val result = previewService.dryRunForCategory(categoryId)
        return Mono.just(
            mapOf(
                "mode" to result.mode,
                "blocks" to result.blocks,
            )
        )
    }
}
