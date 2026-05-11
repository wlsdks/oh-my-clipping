package com.ohmyclipping.admin

import com.ohmyclipping.service.DbSizeMetricsService
import com.ohmyclipping.service.dto.DbSizeSnapshot
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * DB 크기 메트릭을 조회하는 관리자 전용 컨트롤러.
 *
 * Spring Security 의 ROLE_ADMIN 요구는 SecurityConfig 에서 URL 패턴 "/api/admin" 하위로
 * 일괄 처리되므로 별도 @PreAuthorize 없이 동일하게 보호된다.
 */
@RestController
@RequestMapping("/api/admin/ops")
class DbMetricsAdminController(
    private val dbSizeMetricsService: DbSizeMetricsService
) {

    /**
     * DB 크기 스냅샷을 반환한다.
     *
     * 5분 캐시를 기본 사용하며, forceRefresh=true 시 캐시를 무시하고 즉시 재조회한다.
     * forceRefresh 는 관리자 수동 확인 목적이므로 남용 방지가 서비스 레이어 캐시로 충분하다.
     *
     * @param forceRefresh true 이면 캐시를 건너뛰고 실시간 조회한다 (기본값 false).
     * @return DB 크기 스냅샷 (200 OK)
     */
    @GetMapping("/db-metrics")
    fun get(
        @RequestParam(required = false, defaultValue = "false") forceRefresh: Boolean
    ): ResponseEntity<DbSizeSnapshot> {
        val snapshot = dbSizeMetricsService.snapshot(forceRefresh = forceRefresh)
        return ResponseEntity.ok(snapshot)
    }
}
