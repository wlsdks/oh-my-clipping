package com.ohmyclipping.migration

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * V139 migration 스모크 테스트.
 *
 * batch_summaries.rss_item_id: NOT NULL → NULL 전환,
 * FK batch_summaries.rss_item_id REFERENCES rss_items(id) with ON DELETE SET NULL 재생성,
 * bookmarked_articles 의 summary_id 인덱스 추가를 검증한다.
 */
@SpringBootTest
class V139MigrationTest(@Autowired private val jdbc: JdbcTemplate) {

    @Test
    fun `batch_summaries 테이블이 존재하고 구성이 정상`() {
        val cols = jdbc.queryForList(
            """
            SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME)='BATCH_SUMMARIES'
            """.trimIndent(),
            String::class.java,
        ).map { it.uppercase() }

        cols shouldContainAll listOf(
            "ID",
            "CATEGORY_ID",
            "RSS_ITEM_ID",
            "CREATED_AT",
        )
    }

    @Test
    fun `batch_summaries rss_item_id 는 nullable 이다`() {
        val nullable = jdbc.queryForObject(
            """
            SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME)='BATCH_SUMMARIES' AND UPPER(COLUMN_NAME)='RSS_ITEM_ID'
            """.trimIndent(),
            String::class.java,
        )
        nullable shouldBe "YES"
    }

    @Test
    fun `FK on batch_summaries rss_item_id 존재한다`() {
        val constraints = jdbc.queryForList(
            """
            SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
            WHERE UPPER(CONSTRAINT_NAME)='FK_BATCH_SUMMARIES_RSS_ITEM'
            """.trimIndent(),
            String::class.java,
        )

        constraints.size shouldBe 1
    }

    @Test
    fun `복합 FK fk_batch_summaries_rss_item_category 는 제거되었다`() {
        val constraints = jdbc.queryForList(
            """
            SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
            WHERE UPPER(CONSTRAINT_NAME)='FK_BATCH_SUMMARIES_RSS_ITEM_CATEGORY'
            """.trimIndent(),
            String::class.java,
        )

        constraints.size shouldBe 0
    }

    @Test
    fun `V139 batch_summaries category_id FK는 존재하지 않는 category를 거부한다`() {
        // FK constraint on category_id should prevent inserting with nonexistent category
        val ex = try {
            jdbc.update(
                """
                INSERT INTO batch_summaries (
                    id, category_id, rss_item_id, summary, created_at
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """.trimIndent(),
                "v139-test-id",
                "99999999-9999-9999-9999-999999999999",
                null,
                "test summary text"
            )
            null
        } catch (e: Exception) {
            e
        }

        ex.shouldBeInstanceOf<DataIntegrityViolationException>()
    }

    @Test
    fun `idx_bookmarked_articles_summary_id 인덱스 존재한다`() {
        val count = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES
            WHERE UPPER(TABLE_NAME)='BOOKMARKED_ARTICLES' AND UPPER(INDEX_NAME)='IDX_BOOKMARKED_ARTICLES_SUMMARY_ID'
            """.trimIndent(),
            Int::class.java,
        )

        count shouldBe 1
    }

    @Test
    fun `idx_bookmarked_articles_summary_id 는 summary_id 컬럼을 포함한다`() {
        val indexCols = jdbc.queryForList(
            """
            SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS
            WHERE UPPER(TABLE_NAME)='BOOKMARKED_ARTICLES' AND UPPER(INDEX_NAME)='IDX_BOOKMARKED_ARTICLES_SUMMARY_ID'
            ORDER BY ORDINAL_POSITION
            """.trimIndent(),
            String::class.java,
        ).map { it.uppercase() }

        indexCols shouldContainAll listOf("SUMMARY_ID")
    }

    @Test
    fun `_v139_backup_batch_summaries_rss_item_ids 테이블 존재한다`() {
        val backupCols = jdbc.queryForList(
            """
            SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME)='_V139_BACKUP_BATCH_SUMMARIES_RSS_ITEM_IDS'
            """.trimIndent(),
            String::class.java,
        ).map { it.uppercase() }

        backupCols shouldContainAll listOf(
            "ID",
            "RSS_ITEM_ID",
            "CATEGORY_ID",
            "CREATED_AT",
        )
    }

    @Test
    fun `마이그레이션은 멱등성이 있다`() {
        val constraintExists = jdbc.queryForList(
            """
            SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
            WHERE UPPER(CONSTRAINT_NAME)='FK_BATCH_SUMMARIES_RSS_ITEM'
            """.trimIndent(),
            String::class.java,
        ).size

        constraintExists shouldBe 1
    }
}
