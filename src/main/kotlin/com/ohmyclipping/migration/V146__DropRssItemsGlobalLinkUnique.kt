package com.ohmyclipping.migration

import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.springframework.stereotype.Component
import java.sql.Connection

private val v146Log = KotlinLogging.logger {}

/**
 * V146: rss_items.link 의 legacy 전역 UNIQUE 제약을 제거한다.
 *
 * V95 는 `(link, category_id)` 복합 unique index 로 정책을 바꿨지만, H2 에서는 V3 의
 * inline `link UNIQUE` 제약 이름이 DB가 자동 생성한 이름이라 남을 수 있다.
 * 이 마이그레이션은 information_schema 를 조회해 `rss_items(link)` 단일 컬럼 unique
 * 제약/인덱스를 제거하고, 카테고리 스코프 unique index 를 보장한다.
 */
@Component
class V146__DropRssItemsGlobalLinkUnique : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val conn = context.connection

        val uniqueConstraints = findSingleColumnLinkUniqueConstraints(conn)
        for (constraint in uniqueConstraints) {
            dropConstraint(conn, constraint)
        }

        val uniqueIndexes = findSingleColumnLinkUniqueIndexes(conn)
            .filterNot { it.equals("uq_rss_items_link_category", ignoreCase = true) }
        for (index in uniqueIndexes) {
            dropIndex(conn, index)
        }

        conn.createStatement().use { stmt ->
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_rss_items_link_category ON rss_items(link, category_id)")
        }
        v146Log.info {
            "V146: rss_items global link unique cleanup complete " +
                "(constraints=$uniqueConstraints, indexes=$uniqueIndexes)"
        }
    }

    private fun findSingleColumnLinkUniqueConstraints(conn: Connection): List<String> =
        runCatching {
            val sql = """
                SELECT tc.CONSTRAINT_NAME
                  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                  JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                    ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                   AND tc.TABLE_NAME = kcu.TABLE_NAME
                 WHERE LOWER(tc.TABLE_NAME) = 'rss_items'
                   AND UPPER(tc.CONSTRAINT_TYPE) = 'UNIQUE'
                 GROUP BY tc.CONSTRAINT_NAME
                HAVING COUNT(*) = 1
                   AND LOWER(MAX(kcu.COLUMN_NAME)) = 'link'
            """.trimIndent()
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(rs.getString(1))
                    }
                }
            }
        }.getOrElse { ex ->
            v146Log.warn { "V146: unique constraint lookup failed: ${ex.message}" }
            listOf("rss_items_link_key")
        }

    private fun findSingleColumnLinkUniqueIndexes(conn: Connection): List<String> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        return runCatching {
            conn.metaData.getIndexInfo(null, null, "rss_items", true, false).use { rs ->
                while (rs.next()) {
                    if (rs.getBoolean("NON_UNIQUE")) continue
                    val indexName = rs.getString("INDEX_NAME") ?: continue
                    val columnName = rs.getString("COLUMN_NAME") ?: continue
                    grouped.getOrPut(indexName) { mutableListOf() }.add(columnName)
                }
            }
            if (grouped.isEmpty()) {
                conn.metaData.getIndexInfo(null, null, "RSS_ITEMS", true, false).use { rs ->
                    while (rs.next()) {
                        if (rs.getBoolean("NON_UNIQUE")) continue
                        val indexName = rs.getString("INDEX_NAME") ?: continue
                        val columnName = rs.getString("COLUMN_NAME") ?: continue
                        grouped.getOrPut(indexName) { mutableListOf() }.add(columnName)
                    }
                }
            }
            grouped
                .filter { (_, columns) -> columns.size == 1 && columns.single().equals("link", ignoreCase = true) }
                .keys
                .toList()
        }.getOrElse { ex ->
            v146Log.warn { "V146: unique index lookup failed: ${ex.message}" }
            emptyList()
        }
    }

    private fun dropConstraint(conn: Connection, name: String) {
        conn.createStatement().use { stmt ->
            runCatching {
                stmt.execute("ALTER TABLE rss_items DROP CONSTRAINT IF EXISTS \"$name\"")
            }.onFailure { quotedFailure ->
                runCatching {
                    stmt.execute("ALTER TABLE rss_items DROP CONSTRAINT IF EXISTS $name")
                }.onFailure { unquotedFailure ->
                    v146Log.warn {
                        "V146: failed to drop constraint $name: " +
                            "${quotedFailure.message}; ${unquotedFailure.message}"
                    }
                }
            }
        }
    }

    private fun dropIndex(conn: Connection, name: String) {
        conn.createStatement().use { stmt ->
            runCatching {
                stmt.execute("DROP INDEX IF EXISTS \"$name\"")
            }.onFailure { quotedFailure ->
                runCatching {
                    stmt.execute("DROP INDEX IF EXISTS $name")
                }.onFailure { unquotedFailure ->
                    v146Log.warn {
                        "V146: failed to drop index $name: " +
                            "${quotedFailure.message}; ${unquotedFailure.message}"
                    }
                }
            }
        }
    }
}
