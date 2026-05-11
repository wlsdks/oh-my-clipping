package com.ohmyclipping.service.digest

import com.ohmyclipping.model.DigestDiffLog
import com.ohmyclipping.store.DigestDiffLogStore
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Shadow mode diff 기록 조회 서비스.
 *
 * Admin 계층이 store 를 직접 의존하지 않도록 위임 계층을 제공한다.
 * 현재는 단순 위임이지만, 추후 페이지네이션 정책·캐시·접근 제어 로직이 필요할 때 이 계층에서 처리한다.
 */
@Service
class DigestDiffService(private val store: DigestDiffLogStore) {

    /**
     * 지정 카테고리의 날짜 범위 내 diff 기록을 최신 순으로 반환한다.
     *
     * @param categoryId 조회 대상 카테고리 ID
     * @param from 시작일 (null 이면 오늘 기준 30일 전)
     * @param to 종료일 (null 이면 오늘)
     * @return 날짜 내림차순으로 정렬된 diff 로그 목록
     */
    fun listForCategory(
        categoryId: String,
        from: LocalDate?,
        to: LocalDate?,
    ): List<DigestDiffLog> {
        val (effectiveFrom, effectiveTo) = resolveDateRange(from, to)
        return store.findByCategoryAndDateRange(categoryId, effectiveFrom, effectiveTo)
    }

    /**
     * 지정 카테고리의 날짜 범위 내 diff 기록을 DB 페이지네이션으로 조회한다.
     */
    fun listForCategory(
        categoryId: String,
        from: LocalDate?,
        to: LocalDate?,
        offset: Int,
        limit: Int,
    ): List<DigestDiffLog> {
        val (effectiveFrom, effectiveTo) = resolveDateRange(from, to)
        // offset/limit 보정은 controller가 담당하고, 서비스는 store에 제한 조회만 위임한다.
        return store.findByCategoryAndDateRange(categoryId, effectiveFrom, effectiveTo, offset, limit)
    }

    /**
     * 지정 카테고리의 날짜 범위 내 diff 기록 총 개수를 반환한다.
     */
    fun countForCategory(
        categoryId: String,
        from: LocalDate?,
        to: LocalDate?,
    ): Int {
        val (effectiveFrom, effectiveTo) = resolveDateRange(from, to)
        return store.countByCategoryAndDateRange(categoryId, effectiveFrom, effectiveTo)
    }

    private fun resolveDateRange(from: LocalDate?, to: LocalDate?): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        // null 입력은 서비스 계층에서 기본값으로 치환하여 store 는 항상 non-null 날짜를 받는다.
        return Pair(from ?: today.minusDays(30), to ?: today)
    }
}
