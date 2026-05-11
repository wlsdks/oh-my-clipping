package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.KnownNewsSource

/**
 * 주요 뉴스사이트 매핑 테이블 접근 인터페이스.
 */
interface KnownNewsSourceStore {
    /** 전체 목록을 조회한다. */
    fun listAll(): List<KnownNewsSource>

    /** 이름, 별칭, 도메인으로 검색한다. */
    fun search(query: String): List<KnownNewsSource>
}
