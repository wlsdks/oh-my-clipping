package com.clipping.mcpserver.migration

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun columnExists(table: String, column: String): Boolean {
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?",
            Int::class.java,
            table.uppercase(),
            column.uppercase()
        )
        return (count ?: 0) > 0
    }

    private fun tableExists(table: String): Boolean {
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = ?",
            Int::class.java,
            table.uppercase()
        )
        return (count ?: 0) > 0
    }

    private fun indexExists(indexName: String): Boolean {
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE UPPER(INDEX_NAME) = ?",
            Int::class.java,
            indexName.uppercase()
        )
        return (count ?: 0) > 0
    }

    private fun constraintExists(table: String, constraint: String): Boolean {
        val count = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
            WHERE UPPER(TABLE_NAME) = ? AND UPPER(CONSTRAINT_NAME) = ?
            """.trimIndent(),
            Int::class.java,
            table.uppercase(),
            constraint.uppercase()
        )
        return (count ?: 0) > 0
    }

    private fun singleColumnUniqueConstraintExists(table: String, column: String): Boolean {
        val count = jdbc.queryForObject(
            """
            SELECT COUNT(*)
              FROM (
                SELECT tc.CONSTRAINT_NAME
                  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                  JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                    ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                   AND tc.TABLE_NAME = kcu.TABLE_NAME
                 WHERE UPPER(tc.TABLE_NAME) = ?
                   AND UPPER(tc.CONSTRAINT_TYPE) = 'UNIQUE'
                 GROUP BY tc.CONSTRAINT_NAME
                HAVING COUNT(*) = 1
                   AND UPPER(MAX(kcu.COLUMN_NAME)) = ?
              ) t
            """.trimIndent(),
            Int::class.java,
            table.uppercase(),
            column.uppercase()
        )
        return (count ?: 0) > 0
    }

    // V6: rss_sources approval/reliability columns
    @Test
    fun `V6 should add crawl_approved column to rss_sources`() {
        columnExists("rss_sources", "crawl_approved") shouldBe true
    }

    @Test
    fun `V6 should add approved_by column to rss_sources`() {
        columnExists("rss_sources", "approved_by") shouldBe true
    }

    @Test
    fun `V6 should add approved_at column to rss_sources`() {
        columnExists("rss_sources", "approved_at") shouldBe true
    }

    @Test
    fun `V6 should add verification_status column to rss_sources`() {
        columnExists("rss_sources", "verification_status") shouldBe true
    }

    @Test
    fun `V6 should add reliability_score column to rss_sources`() {
        columnExists("rss_sources", "reliability_score") shouldBe true
    }

    @Test
    fun `V6 should add last_crawl_error column to rss_sources`() {
        columnExists("rss_sources", "last_crawl_error") shouldBe true
    }

    @Test
    fun `V6 should add crawl_fail_count column to rss_sources`() {
        columnExists("rss_sources", "crawl_fail_count") shouldBe true
    }

    // V7: clipping_personas table
    @Test
    fun `V7 should create clipping_personas table`() {
        tableExists("clipping_personas") shouldBe true
    }

    @Test
    fun `V7 clipping_personas should have all expected columns`() {
        val expected = listOf(
            "id", "name", "description", "system_prompt", "summary_style",
            "target_audience", "max_items", "language", "is_active",
            "created_at", "updated_at"
        )
        expected.forEach { col ->
            columnExists("clipping_personas", col) shouldBe true
        }
    }

    // V8: batch_summaries importance_score
    @Test
    fun `V8 should add importance_score column to batch_summaries`() {
        columnExists("batch_summaries", "importance_score") shouldBe true
    }

    // V9: clipping_stats table
    @Test
    fun `V9 should create clipping_stats table`() {
        tableExists("clipping_stats") shouldBe true
    }

    @Test
    fun `V9 clipping_stats should have all expected columns`() {
        val expected = listOf(
            "id", "category_id", "stat_date", "items_collected",
            "items_summarized", "items_sent", "top_keywords",
            "avg_importance_score", "created_at"
        )
        expected.forEach { col ->
            columnExists("clipping_stats", col) shouldBe true
        }
    }

    // V10: batch_categories max_items + persona_id
    @Test
    fun `V10 should add max_items column to batch_categories`() {
        columnExists("batch_categories", "max_items") shouldBe true
    }

    @Test
    fun `V10 should add persona_id column to batch_categories`() {
        columnExists("batch_categories", "persona_id") shouldBe true
    }

    // V11: daily_summaries topic_keywords
    @Test
    fun `V11 should add topic_keywords column to daily_summaries`() {
        columnExists("daily_summaries", "topic_keywords") shouldBe true
    }

    @Test
    fun `V11 should allow null overall_summary in daily_summaries`() {
        // Insert a row with null overall_summary — should not throw
        jdbc.update(
            """INSERT INTO batch_categories (id, name) VALUES ('test-cat', 'Test')"""
        )
        jdbc.update(
            """INSERT INTO daily_summaries (id, title, summary_date, overall_summary, category_id)
               VALUES ('test-ds', 'Test', CURRENT_DATE, NULL, 'test-cat')"""
        )
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM daily_summaries WHERE id = 'test-ds'",
            Int::class.java
        )
        count shouldBe 1
    }

    // V14: original_contents table
    @Test
    fun `V14 should create original_contents table`() {
        tableExists("original_contents") shouldBe true
    }

    @Test
    fun `V14 original_contents should have all expected columns`() {
        val expected = listOf(
            "id", "rss_item_id", "source_link", "title",
            "markdown", "content_hash", "created_at", "updated_at"
        )
        expected.forEach { col ->
            columnExists("original_contents", col) shouldBe true
        }
    }

    // V15: clipping_retention_policies table
    @Test
    fun `V15 should create clipping_retention_policies table`() {
        tableExists("clipping_retention_policies") shouldBe true
    }

    @Test
    fun `V15 clipping_retention_policies should have all expected columns`() {
        val expected = listOf(
            "id", "category_id", "keep_days", "is_enabled", "created_at", "updated_at"
        )
        expected.forEach { col ->
            columnExists("clipping_retention_policies", col) shouldBe true
        }
    }

    // V16: rss_sources policy columns + llm_runs table
    @Test
    fun `V16 should add policy columns to rss_sources`() {
        val expected = listOf(
            "legal_basis",
            "summary_allowed",
            "fulltext_allowed",
            "terms_reviewed_at",
            "review_notes"
        )
        expected.forEach { col ->
            columnExists("rss_sources", col) shouldBe true
        }
    }

    @Test
    fun `V16 should create llm_runs table`() {
        tableExists("llm_runs") shouldBe true
    }

    @Test
    fun `V16 llm_runs should have all expected columns`() {
        val expected = listOf(
            "id", "category_id", "rss_item_id",
            "model", "prompt_version", "input_hash",
            "input_chars", "output_chars", "status",
            "error_message", "duration_ms", "tokens_in",
            "tokens_out", "created_at"
        )
        expected.forEach { col ->
            columnExists("llm_runs", col) shouldBe true
        }
    }

    // V17: admin_users table
    @Test
    fun `V17 should create admin_users table`() {
        tableExists("admin_users") shouldBe true
    }

    @Test
    fun `V17 admin_users should have all expected columns`() {
        val expected = listOf(
            "id",
            "username",
            "password_hash",
            "display_name",
            "is_active",
            "last_login_at",
            "created_at",
            "updated_at"
        )
        expected.forEach { col ->
            columnExists("admin_users", col) shouldBe true
        }
    }

    @Test
    fun `V33 should create user owned setup tables`() {
        tableExists("clipping_user_owned_personas") shouldBe true
        tableExists("clipping_user_owned_categories") shouldBe true
        tableExists("clipping_user_owned_sources") shouldBe true
    }

    @Test
    fun `V33 user owned setup tables should have expected columns`() {
        listOf("user_id", "persona_id", "created_at").forEach { col ->
            columnExists("clipping_user_owned_personas", col) shouldBe true
        }
        listOf("user_id", "category_id", "created_at").forEach { col ->
            columnExists("clipping_user_owned_categories", col) shouldBe true
        }
        listOf("user_id", "source_id", "created_at").forEach { col ->
            columnExists("clipping_user_owned_sources", col) shouldBe true
        }
    }

    // V18: clipping_runtime_settings table
    @Test
    fun `V18 should create clipping_runtime_settings table`() {
        tableExists("clipping_runtime_settings") shouldBe true
    }

    @Test
    fun `V18 clipping_runtime_settings should have all expected columns`() {
        val expected = listOf(
            "setting_key",
            "setting_value",
            "updated_at"
        )
        expected.forEach { col ->
            columnExists("clipping_runtime_settings", col) shouldBe true
        }
    }

    // V19: clipping_runtime_settings_audits table
    @Test
    fun `V19 should create clipping_runtime_settings_audits table`() {
        tableExists("clipping_runtime_settings_audits") shouldBe true
    }

    @Test
    fun `V19 clipping_runtime_settings_audits should have all expected columns`() {
        val expected = listOf(
            "id",
            "setting_key",
            "old_value",
            "new_value",
            "action",
            "changed_by",
            "changed_at"
        )
        expected.forEach { col ->
            columnExists("clipping_runtime_settings_audits", col) shouldBe true
        }
    }

    // V22: account role + user clipping requests
    @Test
    fun `V22 should add role column to admin_users`() {
        columnExists("admin_users", "role") shouldBe true
    }

    @Test
    fun `V22 should create clipping_user_requests table`() {
        tableExists("clipping_user_requests") shouldBe true
    }

    @Test
    fun `V22 clipping_user_requests should have all expected columns`() {
        val expected = listOf(
            "id",
            "requester_user_id",
            "request_name",
            "source_name",
            "source_url",
            "slack_channel_id",
            "persona_name",
            "persona_prompt",
            "summary_style",
            "target_audience",
            "request_note",
            "status",
            "review_note",
            "reviewed_by_user_id",
            "reviewed_at",
            "approved_category_id",
            "approved_persona_id",
            "approved_source_id",
            "created_at",
            "updated_at"
        )
        expected.forEach { col ->
            columnExists("clipping_user_requests", col) shouldBe true
        }
    }

    @Test
    fun `V23 should add approval columns to admin_users`() {
        val expected = listOf(
            "department",
            "approval_status",
            "approval_note",
            "approved_by_user_id",
            "approved_at"
        )
        expected.forEach { col ->
            columnExists("admin_users", col) shouldBe true
        }
    }

    @Test
    fun `V24 should create clipping_category_rules table`() {
        tableExists("clipping_category_rules") shouldBe true
    }

    @Test
    fun `V24 clipping_category_rules should have all expected columns`() {
        // V111에서 version → revision으로 리네이밍되었고 system_updated_at이 추가되었다.
        val expected = listOf(
            "category_id",
            "include_keywords",
            "exclude_keywords",
            "risk_tags",
            "include_threshold",
            "review_threshold",
            "uncertain_to_review",
            "auto_exclude_enabled",
            "revision",
            "updated_by",
            "updated_at"
        )
        expected.forEach { col ->
            columnExists("clipping_category_rules", col) shouldBe true
        }
    }

    @Test
    fun `V56 should drop clipping_category_region_policies table`() {
        tableExists("clipping_category_region_policies") shouldBe false
    }

    @Test
    fun `V26 should create clipping_review_items table`() {
        tableExists("clipping_review_items") shouldBe true
    }

    @Test
    fun `V26 clipping_review_items should have all expected columns`() {
        val expected = listOf(
            "summary_id",
            "category_id",
            "status",
            "reason",
            "reviewed_by",
            "reviewed_at",
            "created_at",
            "updated_at"
        )
        expected.forEach { col ->
            columnExists("clipping_review_items", col) shouldBe true
        }
    }

    @Test
    fun `V27 should create clipping_review_item_audits table`() {
        tableExists("clipping_review_item_audits") shouldBe true
    }

    @Test
    fun `V27 clipping_review_item_audits should have all expected columns`() {
        val expected = listOf(
            "id",
            "summary_id",
            "category_id",
            "from_status",
            "to_status",
            "reason",
            "reviewed_by",
            "reviewed_at",
            "created_at"
        )
        expected.forEach { col ->
            columnExists("clipping_review_item_audits", col) shouldBe true
        }
    }

    @Test
    fun `V28 should create clipping_trend_snapshots table`() {
        tableExists("clipping_trend_snapshots") shouldBe true
    }

    @Test
    fun `V28 clipping_trend_snapshots should have all expected columns`() {
        val expected = listOf(
            "id",
            "period_type",
            "snapshot_from",
            "snapshot_to",
            "category_id",
            "category_name",
            "region_type",
            "title",
            "summary",
            "key_signals",
            "action_items",
            "source_count",
            "item_count",
            "status",
            "generated_by",
            "published_at",
            "created_at",
            "updated_at"
        )
        expected.forEach { col ->
            columnExists("clipping_trend_snapshots", col) shouldBe true
        }
    }

    @Test
    fun `V28 should create clipping_trend_visual_cards table`() {
        tableExists("clipping_trend_visual_cards") shouldBe true
    }

    @Test
    fun `V28 clipping_trend_visual_cards should have all expected columns`() {
        val expected = listOf(
            "id",
            "snapshot_id",
            "card_type",
            "title",
            "summary",
            "panels",
            "review_status",
            "review_note",
            "generated_by",
            "reviewed_by",
            "reviewed_at",
            "published",
            "created_at",
            "updated_at"
        )
        expected.forEach { col ->
            columnExists("clipping_trend_visual_cards", col) shouldBe true
        }
    }

    @Test
    fun `V31 clipping_stats should have operational KPI columns`() {
        val expected = listOf(
            "items_duplicates",
            "slack_send_attempts",
            "slack_send_successes"
        )
        expected.forEach { col ->
            columnExists("clipping_stats", col) shouldBe true
        }
    }

    @Test
    fun `V34 should normalize legacy request generated names`() {
        val requesterUserId = "mig-user-v34"
        val creatorRequestId = "12345678-0000-0000-0000-000000000001"
        val reusedRequestId = "87654321-0000-0000-0000-000000000001"
        val categoryId = "mig-cat-v34"
        val personaId = "mig-persona-v34"

        jdbc.update(
            """
            INSERT INTO admin_users (id, username, password_hash, role)
            VALUES (?, ?, ?, 'USER')
            """.trimIndent(),
            requesterUserId,
            "mig-user-v34",
            "hash"
        )
        jdbc.update(
            "INSERT INTO batch_categories (id, name) VALUES (?, ?)",
            categoryId,
            "레거시 요청명-12345678"
        )
        jdbc.update(
            """
            INSERT INTO clipping_personas (id, name, system_prompt)
            VALUES (?, ?, ?)
            """.trimIndent(),
            personaId,
            "레거시 페르소나-12345678",
            "prompt"
        )
        jdbc.update(
            """
            INSERT INTO clipping_user_requests (
                id, requester_user_id, request_name, source_name, source_url, slack_channel_id,
                persona_name, persona_prompt, status, approved_category_id, approved_persona_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', ?, ?)
            """.trimIndent(),
            creatorRequestId,
            requesterUserId,
            "레거시 요청명",
            "Tech Source",
            "https://example.com/rss.xml",
            "C123TEST01",
            "레거시 페르소나",
            "prompt",
            categoryId,
            personaId
        )
        jdbc.update(
            """
            INSERT INTO clipping_user_requests (
                id, requester_user_id, request_name, source_name, source_url, slack_channel_id,
                persona_name, persona_prompt, status, approved_persona_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', ?)
            """.trimIndent(),
            reusedRequestId,
            requesterUserId,
            "재사용 요청",
            "Tech Source",
            "https://example.com/rss.xml",
            "C123TEST01",
            "레거시 페르소나-12345678",
            "prompt",
            personaId
        )

        // 최신 마이그레이션 SQL을 다시 실행해 정규화 로직이 실제 데이터에 적용되는지 검증한다.
        ResourceDatabasePopulator(ClassPathResource("db/migration/V34__normalize_legacy_request_generated_names.sql"))
            .execute(jdbc.dataSource ?: error("dataSource missing"))

        jdbc.queryForObject(
            "SELECT name FROM batch_categories WHERE id = ?",
            String::class.java,
            categoryId
        ) shouldBe "레거시 요청명"
        jdbc.queryForObject(
            "SELECT name FROM clipping_personas WHERE id = ?",
            String::class.java,
            personaId
        ) shouldBe "레거시 페르소나"
        jdbc.queryForObject(
            "SELECT persona_name FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            reusedRequestId
        ) shouldBe "레거시 페르소나"
    }

    @Test
    fun `V58 should create report_delivery_log table`() {
        tableExists("report_delivery_log") shouldBe true
    }

    @Test
    fun `V58 report_delivery_log should have all expected columns`() {
        val expected = listOf(
            "id", "report_type", "period_key", "channel_id", "status",
            "snapshot_id", "slack_message_ts", "error_message", "created_at", "updated_at"
        )
        expected.forEach { col ->
            columnExists("report_delivery_log", col) shouldBe true
        }
    }

    @Test
    fun `V61 delivery_log should reject unsupported status`() {
        // V115 에서 delivery_log.category_id → batch_categories(id) FK 를 추가했으므로
        // status 제약 위반을 검증하려면 유효한 카테고리를 먼저 삽입해야 한다.
        jdbc.update(
            """
            INSERT INTO batch_categories (id, name, is_active, created_at, updated_at)
            VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v61-category-status",
            "V61 Status Category"
        )

        assertThrows<DataAccessException> {
            jdbc.update(
                """
                INSERT INTO delivery_log (
                    id, category_id, channel_id, delivery_date, delivery_hour, status, item_count
                ) VALUES (?, ?, ?, CURRENT_DATE, ?, ?, ?)
                """.trimIndent(),
                "delivery-invalid-status",
                "v61-category-status",
                "C123TEST01",
                9,
                "UNKNOWN",
                0
            )
        }
    }

    @Test
    fun `V144 delivery_log should allow no content notification status`() {
        jdbc.update(
            """
            INSERT INTO batch_categories (id, name, is_active, created_at, updated_at)
            VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v144-category-status",
            "V144 Status Category"
        )

        jdbc.update(
            """
            INSERT INTO delivery_log (
                id, category_id, channel_id, delivery_date, delivery_hour, status, item_count
            ) VALUES (?, ?, ?, CURRENT_DATE, ?, ?, ?)
            """.trimIndent(),
            "delivery-no-content-status",
            "v144-category-status",
            "C123TEST144",
            9,
            "NOTIFIED_NO_CONTENT",
            0
        )

        jdbc.queryForObject(
            "SELECT status FROM delivery_log WHERE id = ?",
            String::class.java,
            "delivery-no-content-status"
        ) shouldBe "NOTIFIED_NO_CONTENT"
    }

    @Test
    fun `V61 delivery_log should default retry_attempted to false`() {
        // V115 에서 delivery_log.category_id → batch_categories(id) FK 가 걸려있으므로
        // 유효한 카테고리 row 를 먼저 삽입하여 FK 위반을 방지한다.
        jdbc.update(
            """
            INSERT INTO batch_categories (id, name, is_active, created_at, updated_at)
            VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v61-category-retry",
            "V61 Retry Category"
        )

        jdbc.update(
            """
            INSERT INTO delivery_log (
                id, category_id, channel_id, delivery_date, delivery_hour, status, item_count
            ) VALUES (?, ?, ?, CURRENT_DATE, ?, ?, ?)
            """.trimIndent(),
            "delivery-default-retry",
            "v61-category-retry",
            "C123TEST02",
            10,
            "RESERVED",
            0
        )

        jdbc.queryForObject(
            "SELECT retry_attempted FROM delivery_log WHERE id = ?",
            Boolean::class.java,
            "delivery-default-retry"
        ) shouldBe false
    }

    @Test
    fun `V61 should create hot path indexes`() {
        indexExists("idx_delivery_log_retry_created") shouldBe true
        indexExists("idx_delivery_log_category_created") shouldBe true
        indexExists("idx_delivery_log_category_status_date_created") shouldBe true
        indexExists("idx_clipping_user_requests_requester_category_status") shouldBe true
        indexExists("idx_rss_items_category_processed_created") shouldBe true
        indexExists("idx_rss_sources_collect_ready") shouldBe true
        indexExists("idx_user_delivery_schedules_hour_updated") shouldBe true
        indexExists("idx_user_delivery_schedules_updated_at") shouldBe true
    }

    @Test
    fun `V140 should create user request digest fan-out index`() {
        indexExists("idx_clipping_user_requests_status_category_created") shouldBe true
    }

    @Test
    fun `V145 should create rss source category url index`() {
        indexExists("idx_rss_sources_category_url") shouldBe true
    }

    @Test
    fun `V146 should remove rss item global link unique constraint`() {
        indexExists("uq_rss_items_link_category") shouldBe true
        singleColumnUniqueConstraintExists("rss_items", "link") shouldBe false
    }

    @Test
    fun `V141 should create top articles importance index`() {
        indexExists("idx_batch_summaries_importance_created") shouldBe true
    }

    @Test
    fun `V62 should create batch summary rss item index`() {
        indexExists("idx_batch_summaries_rss_item_category") shouldBe true
    }

    @Test
    fun `V63 should create summary item category constraints`() {
        constraintExists("rss_items", "uq_rss_items_id_category") shouldBe true
        // V63 created compound FK, but V139 removes it for retention logic.
        // Verify compound FK is removed (V139 by design)
        constraintExists("batch_summaries", "fk_batch_summaries_rss_item_category") shouldBe false
        // V139 ensures the single category_id FK still exists from V4 inline definition
        // (verified in V139MigrationTest via fk_batch_summaries_category existence check)
    }

    @Test
    fun `V63 should reject batch summary category mismatch`() {
        jdbc.update(
            """
            INSERT INTO batch_categories (id, name, is_active, created_at, updated_at)
            VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v63-category-a",
            "V63 Category A"
        )
        jdbc.update(
            """
            INSERT INTO batch_categories (id, name, is_active, created_at, updated_at)
            VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v63-category-b",
            "V63 Category B"
        )
        jdbc.update(
            """
            INSERT INTO rss_items (
                id, title, content, link, language, is_processed, category_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v63-rss-item",
            "V63 item",
            "V63 content",
            "https://example.com/v63-item",
            "FOREIGN",
            true,
            "v63-category-a"
        )

        // V63 created compound FK that enforced category match, but V139 removes it.
        // Now INSERT with mismatched category (b) and rss_item (a) is allowed.
        // Category integrity is still enforced separately via FK on category_id.
        val inserted = jdbc.update(
            """
            INSERT INTO batch_summaries (
                id, original_title, summary, source_link, is_sent_to_slack, category_id, rss_item_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v63-summary-mismatch",
            "V63 mismatch",
            "V63 mismatch summary",
            "https://example.com/v63-item",
            false,
            "v63-category-b",
            "v63-rss-item"
        )
        inserted shouldBe 1  // Should succeed (no compound FK constraint)
    }

    @Test
    fun `V75 should add summary_id column to user_events`() {
        columnExists("user_events", "summary_id") shouldBe true
    }

    @Test
    fun `V75 should create idx_user_events_summary_id index`() {
        indexExists("idx_user_events_summary_id") shouldBe true
    }

    @Test
    fun `V75 should create idx_user_events_type_summary_created composite index`() {
        indexExists("idx_user_events_type_summary_created") shouldBe true
    }

    // V76: weekly_persona_snapshot 테이블
    @Test
    fun `V76 should create weekly_persona_snapshot table with uq_wps_week_persona constraint`() {
        tableExists("weekly_persona_snapshot") shouldBe true
        constraintExists("weekly_persona_snapshot", "uq_wps_week_persona") shouldBe true
    }

    // V77: weekly_persona_subscription_state 테이블
    // V118 에서 user_id → category_id 로 리네임됐다 (구독은 persona+category 조합이라 의미 정합).
    @Test
    fun `V77 should create weekly_persona_subscription_state table with composite PK`() {
        tableExists("weekly_persona_subscription_state") shouldBe true
        listOf("week_start", "persona_id", "category_id", "state", "delivery_opportunities",
            "delivered_count", "clicks_in_week", "bookmarks_in_week").forEach { col ->
            columnExists("weekly_persona_subscription_state", col) shouldBe true
        }
    }

    // V78: persona_batch_run 테이블
    @Test
    fun `V78 should create persona_batch_run table with uq_pbr_run_id constraint`() {
        tableExists("persona_batch_run") shouldBe true
        constraintExists("persona_batch_run", "uq_pbr_run_id") shouldBe true
    }

    // V111: 낙관적 잠금 기반 system_updated_at 컬럼 + version → revision 리네이밍
    @Test
    fun `V111 should add system_updated_at column to optimistic-locked tables`() {
        columnExists("clipping_personas", "system_updated_at") shouldBe true
        columnExists("batch_categories", "system_updated_at") shouldBe true
        columnExists("rss_sources", "system_updated_at") shouldBe true
        columnExists("clipping_category_rules", "system_updated_at") shouldBe true
    }

    @Test
    fun `V111 should rename clipping_category_rules version column to revision`() {
        columnExists("clipping_category_rules", "revision") shouldBe true
        columnExists("clipping_category_rules", "version") shouldBe false
    }

    // V112: entity_revision_history — 4 도메인 통합 변경 이력 테이블
    @Test
    fun `V112 should create entity_revision_history table`() {
        tableExists("entity_revision_history") shouldBe true
    }

    @Test
    fun `V112 entity_revision_history should have all expected columns`() {
        val expected = listOf(
            "id", "resource_type", "resource_id", "revision_number",
            "editor_id", "editor_display_name", "changed_fields", "snapshot", "created_at"
        )
        expected.forEach { col ->
            columnExists("entity_revision_history", col) shouldBe true
        }
    }

    @Test
    fun `V112 should create resource lookup and created-at indexes`() {
        indexExists("idx_entity_revision_resource") shouldBe true
        indexExists("idx_entity_revision_created") shouldBe true
    }

    // V114: clipping_review_item_audits 3년 retention 성능 인덱스
    @Test
    fun `V114 should create created_at index on clipping_review_item_audits`() {
        indexExists("idx_review_item_audits_created_at") shouldBe true
    }

    // V128 / V129: 부서-팀 조직도 정식화
    @Test
    fun `V128 should create departments table with name_normalized unique`() {
        tableExists("departments") shouldBe true
        listOf(
            "id", "name", "name_normalized", "display_order",
            "is_active", "created_at", "updated_at"
        ).forEach { col ->
            columnExists("departments", col) shouldBe true
        }
        indexExists("idx_departments_active_order") shouldBe true
    }

    @Test
    fun `V128 should create teams table with department_id cascade FK`() {
        tableExists("teams") shouldBe true
        listOf(
            "id", "department_id", "name", "name_normalized",
            "display_order", "is_active", "created_at", "updated_at"
        ).forEach { col ->
            columnExists("teams", col) shouldBe true
        }
        indexExists("idx_teams_dept_active_order") shouldBe true
    }

    @Test
    fun `V129 should add department_id and team_id FK columns to admin_users`() {
        columnExists("admin_users", "department_id") shouldBe true
        columnExists("admin_users", "team_id") shouldBe true
        indexExists("idx_admin_users_department") shouldBe true
        indexExists("idx_admin_users_team") shouldBe true
    }

    @Test
    fun `V129 should create fk_admin_users_department constraint`() {
        constraintExists("admin_users", "fk_admin_users_department") shouldBe true
        constraintExists("admin_users", "fk_admin_users_team") shouldBe true
    }

    // V133: competitor_watchlist → organizations 백필
    @Test
    fun `V133 should backfill organization rows for existing competitor_watchlist entries`() {
        // V133 migration 이후, 기존 competitor_watchlist 의 각 name 에 대해
        // organizations 테이블에 COMPETITOR type 의 row 가 존재해야 한다.
        //
        // 테스트 DB 에는 bootstrap 데이터로 competitor_watchlist 가 비어있을 수 있으므로
        // 여기서는 먼저 row 를 삽입한 뒤 migration SQL 을 재실행하여 결과를 검증한다.
        jdbc.update(
            """
            INSERT INTO competitor_watchlist (id, name, aliases, tier, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v131-comp-1",
            "V133 TestCompA",
            "[]",
            "DIRECT"
        )
        jdbc.update(
            """
            INSERT INTO competitor_watchlist (id, name, aliases, tier, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v131-comp-2",
            "V133 TestCompB",
            "[]",
            "DIRECT"
        )

        // migration 을 멱등 호출한다 — 이미 적용돼 있어도 NOT EXISTS 가드로 재적용 가능.
        ResourceDatabasePopulator(
            ClassPathResource("db/migration/V133__sync_competitors_to_organizations.sql")
        ).execute(jdbc.dataSource ?: error("dataSource missing"))

        val countA = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM organizations
             WHERE tenant_id = 'default' AND name = ? AND type = 'COMPETITOR'
            """.trimIndent(),
            Int::class.java,
            "V133 TestCompA"
        )
        val countB = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM organizations
             WHERE tenant_id = 'default' AND name = ? AND type = 'COMPETITOR'
            """.trimIndent(),
            Int::class.java,
            "V133 TestCompB"
        )

        countA shouldBe 1
        countB shouldBe 1

        // 재실행해도 중복 생성되지 않아야 한다 (NOT EXISTS 멱등 가드).
        ResourceDatabasePopulator(
            ClassPathResource("db/migration/V133__sync_competitors_to_organizations.sql")
        ).execute(jdbc.dataSource ?: error("dataSource missing"))

        val countAAfterRerun = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM organizations
             WHERE tenant_id = 'default' AND name = ?
            """.trimIndent(),
            Int::class.java,
            "V133 TestCompA"
        )
        countAAfterRerun shouldBe 1
    }

    // V132: clipping_category_rules 에 자동 EXCLUDE 대상 event_type 블랙리스트 컬럼 추가
    @Test
    fun `V132 should add exclude_event_types column to clipping_category_rules`() {
        columnExists("clipping_category_rules", "exclude_event_types") shouldBe true
    }

    @Test
    fun `V132 exclude_event_types should default to empty JSON array`() {
        // 기본값 '[]' 이 실제로 저장되는지 확인. FK 가 있는 category_id 컬럼을 위해
        // 먼저 batch_categories row 를 생성한 뒤 규칙 row 를 insert 한다.
        jdbc.update(
            """
            INSERT INTO batch_categories (id, name, is_active, created_at, updated_at)
            VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v131-default-cat",
            "V132 Default Category"
        )
        jdbc.update(
            """
            INSERT INTO clipping_category_rules (category_id, updated_at)
            VALUES (?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            "v131-default-cat"
        )
        val stored = jdbc.queryForObject(
            "SELECT exclude_event_types FROM clipping_category_rules WHERE category_id = ?",
            String::class.java,
            "v131-default-cat"
        )
        stored shouldBe "[]"
    }
}
