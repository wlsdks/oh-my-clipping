package com.clipping.mcpserver.store

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.SummaryFeedback
import com.clipping.mcpserver.repository.SummaryFeedbackRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

/**
 * JpaSummaryFeedbackStore.normalizeType() 경로 단위 테스트.
 * Tier 2 Group A — IllegalArgumentException → InvalidInputException 도메인 예외 전환 검증.
 */
class JpaSummaryFeedbackStoreNormalizeTypeTest {

    private val repository = mockk<SummaryFeedbackRepository>(relaxed = true)
    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val store = JpaSummaryFeedbackStore(repository, jdbc)

    @Nested
    inner class `upsert 메서드 — 피드백 타입 정규화` {

        @Test
        fun `허용되지 않은 피드백 타입은 InvalidInputException 을 던진다`() {
            val feedback = SummaryFeedback(
                id = "",
                summaryId = "sum-1",
                feedbackType = "LOVE",
                userId = "user-1",
                createdAt = Instant.now()
            )

            // IllegalArgumentException 이 아닌 도메인 예외가 나와야 한다 (CLAUDE §1.3)
            val ex = shouldThrow<InvalidInputException> { store.upsert(feedback) }
            ex.message shouldContain "LOVE"
        }

        @Test
        fun `빈 문자열 피드백 타입은 InvalidInputException 을 던진다`() {
            val feedback = SummaryFeedback(
                id = "",
                summaryId = "sum-1",
                feedbackType = "",
                userId = "user-1",
                createdAt = Instant.now()
            )

            shouldThrow<InvalidInputException> { store.upsert(feedback) }
        }

        @Test
        fun `소문자 임의 문자열도 InvalidInputException 을 던진다`() {
            val feedback = SummaryFeedback(
                id = "",
                summaryId = "sum-1",
                feedbackType = "hate",
                userId = "user-1",
                createdAt = Instant.now()
            )

            // 정규화는 uppercase 후 허용 리스트 검사 — "HATE" 는 허용되지 않음
            shouldThrow<InvalidInputException> { store.upsert(feedback) }
        }
    }
}
