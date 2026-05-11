package com.ohmyclipping.migration

import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.springframework.stereotype.Component
import java.sql.Connection

private val log = KotlinLogging.logger {}

/**
 * V139: batch_summaries.rss_item_id FK 를 RESTRICT 에서 ON DELETE SET NULL 로 변환.
 *
 * 순수 SQL 로 구현하면 H2 에서 V4 가 생성한 익명 FK(CONSTRAINT_N 형태)를
 * DROP 할 수 없다 — H2 는 PG 관례 이름(batch_summaries_rss_item_id_fkey)이 아닌
 * 고유 번호를 자동 할당하기 때문이다. 이 Java 마이그레이션은 INFORMATION_SCHEMA 를
 * 통해 테이블 FK 이름을 동적으로 조회하고, 잔여 RESTRICT FK 를 모두 제거한다.
 *
 * PG 에서는 V4 가 PG 관례 이름(batch_summaries_rss_item_id_fkey)으로 FK 를 생성하므로
 * 이름 기반 DROP 이 가능하지만, 이 코드는 양쪽 모두 INFORMATION_SCHEMA 방식으로
 * 일관성 있게 처리한다.
 *
 * Spring Boot FlywayAutoConfiguration 이 `JavaMigration` 빈을 Flyway 에 자동 주입한다.
 */
@Component
class V139__RetentionPrepBatchSummariesRssItemFk : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val conn = context.connection

        // Phase 0: 백업 스냅샷 생성
        createBackupSnapshot(conn)

        // Phase 1: rss_item_id 컬럼을 nullable 로 전환
        conn.createStatement().use { stmt ->
            stmt.execute("ALTER TABLE batch_summaries ALTER COLUMN rss_item_id DROP NOT NULL")
        }

        // Phase 2: batch_summaries → rss_items 참조 FK 전체 제거 (익명 FK 포함).
        // fk_batch_summaries_rss_item_category(복합 FK)도 이 단계에서 함께 제거된다.
        // 복합 FK 제거는 retention 안전성을 위한 의도적 결정 — ADR-033 참고.
        dropAllRssItemFks(conn)

        // Phase 3: 명시적 이름 + ON DELETE SET NULL 으로 FK 재생성
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                ALTER TABLE batch_summaries
                  ADD CONSTRAINT fk_batch_summaries_rss_item
                  FOREIGN KEY (rss_item_id)
                  REFERENCES rss_items(id)
                  ON DELETE SET NULL
                """.trimIndent()
            )
        }

        // Phase 4: bookmarked_articles anti-join 전용 인덱스 추가 (retention DELETE 성능)
        conn.createStatement().use { stmt ->
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_bookmarked_articles_summary_id ON bookmarked_articles(summary_id)"
            )
        }

        log.info {
            "V139: batch_summaries.rss_item_id FK → ON DELETE SET NULL 전환 완료"
        }
    }

    /**
     * 백업 스냅샷 테이블 생성. 이미 존재하면 skip (IF NOT EXISTS).
     * 복구 목적이므로 id, rss_item_id, category_id, created_at 만 보관한다.
     */
    private fun createBackupSnapshot(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS _v139_backup_batch_summaries_rss_item_ids AS
                  SELECT id, rss_item_id, category_id, created_at
                  FROM batch_summaries
                """.trimIndent()
            )
        }
    }

    /**
     * batch_summaries 테이블에서 rss_items(id) 를 참조하는 모든 FK 이름을 조회하고
     * 하나씩 DROP 한다. H2 익명 FK(CONSTRAINT_N) 와 PG 관례 이름 모두 처리한다.
     *
     * H2 INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS 는 FK 이름만 제공하므로
     * TABLE_CONSTRAINTS 와 KEY_COLUMN_USAGE 를 조인해 컬럼 단위로 필터링한다.
     */
    private fun dropAllRssItemFks(conn: Connection) {
        // fk_batch_summaries_rss_item_category 는 phase 2 에서 함께 제거한다.
        // fk_batch_summaries_rss_item 은 아직 존재하지 않으므로 제외 목록 불필요.
        val fkNames = findFksReferencingRssItems(conn)

        for (fkName in fkNames) {
            log.debug { "V139: DROP FK $fkName from batch_summaries" }
            conn.createStatement().use { stmt ->
                // 일부 DB 에서는 IF EXISTS 미지원이므로 예외 흡수 후 계속 진행
                try {
                    stmt.execute("ALTER TABLE batch_summaries DROP CONSTRAINT IF EXISTS \"$fkName\"")
                } catch (ex: Exception) {
                    log.warn { "V139: DROP CONSTRAINT $fkName 실패(무시): ${ex.message}" }
                }
            }
        }
    }

    /**
     * JDBC DatabaseMetaData.getImportedKeys() 로 batch_summaries → rss_items 를
     * 참조하는 모든 FK 이름을 반환한다.
     *
     * DatabaseMetaData API 는 H2/PostgreSQL/모든 JDBC 드라이버에서 동일하게 동작하므로
     * INFORMATION_SCHEMA SQL 조인보다 더 안전하다. FK_NAME 컬럼이 null 인 경우는 skip 한다.
     */
    private fun findFksReferencingRssItems(conn: Connection): List<String> {
        return try {
            val names = mutableListOf<String>()
            // getImportedKeys: batch_summaries 가 참조하는 모든 부모 테이블의 FK 목록 반환
            // catalog=null, schema=null 로 전체 스키마 검색
            val metaData = conn.metaData
            metaData.getImportedKeys(null, null, "batch_summaries").use { rs ->
                while (rs.next()) {
                    val pkTable = rs.getString("PKTABLE_NAME")
                    val fkColumn = rs.getString("FKCOLUMN_NAME")
                    val fkName = rs.getString("FK_NAME")
                    // rss_items 를 참조하는 rss_item_id 컬럼 FK 만 수집
                    if (pkTable.equals("rss_items", ignoreCase = true) &&
                        fkColumn.equals("rss_item_id", ignoreCase = true) &&
                        fkName != null
                    ) {
                        names.add(fkName)
                    }
                }
            }
            // H2 가 대소문자 보존 여부에 따라 "batch_summaries" 를 찾지 못할 수 있다.
            // 못 찾으면 대문자로 재시도한다.
            if (names.isEmpty()) {
                metaData.getImportedKeys(null, null, "BATCH_SUMMARIES").use { rs ->
                    while (rs.next()) {
                        val pkTable = rs.getString("PKTABLE_NAME")
                        val fkColumn = rs.getString("FKCOLUMN_NAME")
                        val fkName = rs.getString("FK_NAME")
                        if (pkTable.uppercase() == "RSS_ITEMS" &&
                            fkColumn.uppercase() == "RSS_ITEM_ID" &&
                            fkName != null
                        ) {
                            names.add(fkName)
                        }
                    }
                }
            }
            log.debug { "V139: batch_summaries 의 rss_item_id FK 목록: $names" }
            names
        } catch (ex: Exception) {
            log.warn { "V139: FK 목록 조회 실패 (${ex.message}) — 이름 기반 fallback 사용" }
            // Fallback: PG 관례 이름 + 복합 FK 이름을 직접 지정 (PG 에서 확실히 동작)
            listOf("batch_summaries_rss_item_id_fkey", "fk_batch_summaries_rss_item_category")
        }
    }
}
