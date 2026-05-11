package com.ohmyclipping.migration

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * V137 (category_feature_flags 확장: legend + shadow) + V138 (digest_diff_log) 스모크 테스트.
 *
 * V137 은 Phase D-1 의 컬럼 추가 4 종을 검증하고, V138 은 테이블 + UNIQUE 제약을 검증한다.
 */
@SpringBootTest
class V137V138MigrationTest(@Autowired private val jdbc: JdbcTemplate) {

    @Test
    fun `V137 - category_feature_flags 에 legend + shadow 컬럼 추가`() {
        val cols = jdbc.queryForList(
            """
            SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME)='CATEGORY_FEATURE_FLAGS'
            """.trimIndent(),
            String::class.java,
        ).map { it.uppercase() }

        cols shouldContainAll listOf(
            "DUAL_LEGEND_SHOWN_AT",
            "DUAL_LEGEND_DISPLAY_COUNT",
            "SHADOW_MODE_ENABLED",
            "SHADOW_ENABLED_AT",
        )
    }

    @Test
    fun `V138 - digest_diff_log 테이블 + 컬럼 구성`() {
        val cols = jdbc.queryForList(
            """
            SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME)='DIGEST_DIFF_LOG'
            """.trimIndent(),
            String::class.java,
        ).map { it.uppercase() }

        cols shouldContainAll listOf(
            "ID",
            "CATEGORY_ID",
            "DIGEST_DATE",
            "LEGACY_SUMMARY",
            "NEW_SUMMARY",
            "NEW_MODE",
            "SECTIONS_COUNT",
            "ARTICLES_COUNT",
            "CROSS_MATCH_COUNT",
            "CREATED_AT",
        )
    }

    @Test
    fun `V138 - digest_diff_log UNIQUE (category_id, digest_date) 로 중복 insert 방지`() {
        // 테스트용 카테고리 확보 — 실제 batch_categories row 가 필요 (FK)
        val catId = "v138-test-${System.nanoTime()}"
        jdbc.update(
            """
            INSERT INTO batch_categories(id, name, slack_channel_id)
            VALUES (?, ?, ?)
            """.trimIndent(),
            catId, "v138-test-$catId", "#v138-test",
        )
        try {
            val id1 = "ddl-${System.nanoTime()}-1"
            val id2 = "ddl-${System.nanoTime()}-2"
            val date = java.sql.Date.valueOf("2026-04-22")

            // 최초 insert 는 성공
            jdbc.update(
                "INSERT INTO digest_diff_log(id, category_id, digest_date) VALUES (?, ?, ?)",
                id1, catId, date,
            )

            // 같은 (category_id, digest_date) 로 두번째 insert 는 UNIQUE 위반
            val threw = try {
                jdbc.update(
                    "INSERT INTO digest_diff_log(id, category_id, digest_date) VALUES (?, ?, ?)",
                    id2, catId, date,
                )
                false
            } catch (e: org.springframework.dao.DataIntegrityViolationException) {
                true
            }
            threw shouldBe true
        } finally {
            jdbc.update("DELETE FROM digest_diff_log WHERE category_id = ?", catId)
            jdbc.update("DELETE FROM batch_categories WHERE id = ?", catId)
        }
    }
}
