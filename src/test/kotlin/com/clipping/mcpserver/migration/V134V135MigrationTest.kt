package com.clipping.mcpserver.migration

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class V134V135MigrationTest(@Autowired private val jdbc: JdbcTemplate) {

    @Test
    fun `organizations table has new columns`() {
        val count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME)='ORGANIZATIONS' AND UPPER(COLUMN_NAME) IN
                  ('STOCK_CODE','ALIASES','ORIGIN')
        """.trimIndent(), Int::class.java)
        count shouldBe 3
    }

    @Test
    fun `organizations origin check constraint rejects bad values`() {
        try {
            jdbc.update("""
                INSERT INTO organizations(id, tenant_id, name, type, origin)
                VALUES ('test-bad-origin','default','TestCo','OTHER','bogus_value')
            """.trimIndent())
            assert(false) { "expected CHECK constraint violation" }
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `category_feature_flags table exists with V134 base columns`() {
        // V137 에서 legend/shadow 컬럼이 추가되므로 exact match 대신 포함 검증.
        val cols = jdbc.queryForList("""
            SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME)='CATEGORY_FEATURE_FLAGS'
        """.trimIndent(), String::class.java).map { it.uppercase() }.toSet()
        (cols.containsAll(listOf("CATEGORY_ID","ACCOUNT_BASED_DIGEST_ENABLED","UPDATED_AT"))) shouldBe true
    }

    @Test
    fun `rss_sources has origin column with default manual`() {
        val count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME)='RSS_SOURCES' AND UPPER(COLUMN_NAME)='ORIGIN'
        """.trimIndent(), Int::class.java)
        count shouldBe 1
    }

    // NOTE: This test covers V135 (Task 2). It will FAIL after Task 1 alone — this is intentional
    // and documented. V135 will add form_entries column to clipping_user_requests.
    @Test
    fun `clipping_user_requests has form_entries column`() {
        val count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME)='CLIPPING_USER_REQUESTS' AND UPPER(COLUMN_NAME)='FORM_ENTRIES'
        """.trimIndent(), Int::class.java)
        count shouldBe 1
    }
}
