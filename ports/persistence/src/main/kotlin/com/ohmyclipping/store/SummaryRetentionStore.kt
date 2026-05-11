package com.ohmyclipping.store

import java.time.Instant

interface SummaryRetentionStore {

    fun countByItemOlderThan(cutoff: Instant, categoryId: String? = null): Int

    fun deleteByItemOlderThan(cutoff: Instant, categoryId: String? = null): Int

    /**
     * cutoff 이전에 생성된 배치 요약을 chunk 단위로 삭제한다. 단, 다음 anchor 중 하나라도 존재하면 제외:
     * - summary_feedback.summary_id (사용자가 피드백을 남긴 기사)
     * - bookmarked_articles.summary_id (사용자 북마크)
     *
     * 스케줄러가 반환값이 0이 될 때까지 반복 호출한다.
     */
    fun deleteOlderThanExcludingAnchored(cutoff: Instant, limit: Int): Int
}

