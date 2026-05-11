package com.clipping.mcpserver.store

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 카테고리 × 섹션 별 연속 empty 일수 추적.
 * digest 생성 시 섹션이 비었으면 increment, 매치 발생 시 reset.
 * 3일 이상 누적되면 escalation copy 에 사용.
 */
@Repository
class CategorySectionSilenceLogStore(private val jdbc: JdbcTemplate) {

    /**
     * 연속 empty 일수를 1 증가시키고 현재 누적값을 반환한다.
     * 레코드가 없으면 1로 신규 생성한다.
     */
    fun incrementAndGet(categoryId: String, sectionKey: String): Int {
        // 기존 레코드 업데이트 시도
        val updated = jdbc.update(
            """
            UPDATE category_section_silence_log
               SET consecutive_empty_days = consecutive_empty_days + 1,
                   last_empty_at = CURRENT_TIMESTAMP
             WHERE category_id = ? AND section_key = ?
            """.trimIndent(),
            categoryId, sectionKey
        )
        if (updated == 0) {
            // 레코드 없으면 신규 삽입 (1일차)
            try {
                jdbc.update(
                    """
                    INSERT INTO category_section_silence_log(id, category_id, section_key, consecutive_empty_days)
                    VALUES (?, ?, ?, 1)
                    """.trimIndent(),
                    UUID.randomUUID().toString(), categoryId, sectionKey
                )
                return 1
            } catch (_: DataIntegrityViolationException) {
                jdbc.update(
                    """
                    UPDATE category_section_silence_log
                       SET consecutive_empty_days = consecutive_empty_days + 1,
                           last_empty_at = CURRENT_TIMESTAMP
                     WHERE category_id = ? AND section_key = ?
                    """.trimIndent(),
                    categoryId, sectionKey
                )
            }
        }
        return getConsecutiveEmptyDays(categoryId, sectionKey)
    }

    /**
     * 연속 empty 일수를 0으로 초기화한다.
     * 섹션에 매치 기사가 등장했을 때 호출한다.
     */
    fun reset(categoryId: String, sectionKey: String) {
        jdbc.update(
            """
            UPDATE category_section_silence_log
               SET consecutive_empty_days = 0, last_empty_at = CURRENT_TIMESTAMP
             WHERE category_id = ? AND section_key = ?
            """.trimIndent(),
            categoryId, sectionKey
        )
    }

    /**
     * 현재 연속 empty 일수를 조회한다. 레코드가 없으면 0을 반환한다.
     */
    fun getConsecutiveEmptyDays(categoryId: String, sectionKey: String): Int =
        jdbc.query(
            "SELECT consecutive_empty_days FROM category_section_silence_log WHERE category_id = ? AND section_key = ?",
            { rs, _ -> rs.getInt("consecutive_empty_days") },
            categoryId, sectionKey
        ).firstOrNull() ?: 0
}
