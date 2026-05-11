package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.BatchSummary
import java.time.Instant

interface DigestCandidateStore {

    /**
     * Digest 선택용 후보 조회. batch_summaries 필드와 각 기사의 rss_source_id 를
     * LEFT JOIN 으로 함께 반환한다. rss_items 가 삭제된 경우 source_id 는 null.
     *
     * @return Pair of (후보 summary 리스트, summaryId -> rssSourceId 또는 source_link 호스트 사이드카 맵)
     */
    fun findDigestCandidatesWithSource(
        categoryId: String,
        since: Instant,
        limit: Int,
    ): Pair<List<BatchSummary>, Map<String, String?>>
}

