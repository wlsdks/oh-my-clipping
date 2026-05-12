package com.ohmyclipping.adapter.out

import com.ohmyclipping.service.collection.NaverNewsSearchItem
import com.ohmyclipping.service.collection.NaverNewsSearchPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * OSS 기본 [NaverNewsSearchPort] 구현. 운영 환경에서는 SearchCo 검색 API 를 호출하는 구현으로 교체한다.
 *
 * `isConfigured() = false` 를 반환해 호출자가 search 비활성 분기를 타게 한다. 자세한 맥락은 ADR-044 참고.
 */
@Component
class NaverNewsSearchAdapter : NaverNewsSearchPort {

    init {
        log.warn {
            "NaverNewsSearchAdapter stub is active — news search is disabled. " +
                "Provide a production NaverNewsSearchPort bean to override (see ADR-044)."
        }
    }

    override fun isConfigured(): Boolean = false

    override fun searchNews(query: String, display: Int): List<NaverNewsSearchItem> = emptyList()
}
