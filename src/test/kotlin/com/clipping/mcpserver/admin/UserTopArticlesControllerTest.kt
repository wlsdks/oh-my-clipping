package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.TopArticlesService
import com.clipping.mcpserver.store.BatchSummaryStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 사용자용 주요 기사 컨트롤러의 날짜 범위 전달을 검증한다.
 */
class UserTopArticlesControllerTest {

    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val service = TopArticlesService(batchSummaryStore)
    private val controller = UserTopArticlesController(service)
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `from과 to가 있으면 요청한 정확한 날짜 범위로 조회한다`() {
        every {
            batchSummaryStore.findTopArticles(any(), any(), isNull(), any(), any(), any(), any())
        } returns emptyList()

        controller.getTopArticles(
            from = "2026-01-01",
            to = "2026-01-31",
            days = 7,
            limit = 10,
            categoryId = null,
            sentiment = null,
            eventType = null,
            keyword = null,
            date = null
        )

        verify(exactly = 1) {
            batchSummaryStore.findTopArticles(
                LocalDate.parse("2026-01-01").atStartOfDay(zone).toInstant(),
                LocalDate.parse("2026-02-01").atStartOfDay(zone).toInstant(),
                null,
                null,
                null,
                null,
                10
            )
        }
    }
}
