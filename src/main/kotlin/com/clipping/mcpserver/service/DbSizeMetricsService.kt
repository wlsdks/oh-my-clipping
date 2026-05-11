package com.clipping.mcpserver.service

import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.service.dto.DailyGrowthSummary
import com.clipping.mcpserver.service.dto.DbSizeSnapshot
import com.clipping.mcpserver.service.dto.RetentionEligibleSummary
import com.clipping.mcpserver.service.dto.TableSizeEntry
import com.clipping.mcpserver.service.port.OpsLogNotifier
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

private val log = KotlinLogging.logger {}

/**
 * DB 크기 메트릭을 조회하고 캐싱하는 서비스.
 *
 * - PostgreSQL 환경: pg_database_size / pg_total_relation_size 로 실제 크기 조회
 * - H2 환경(테스트): stub 스냅샷 반환 (H2에서 PG 시스템 함수 미지원)
 * - 5분 단순 AtomicReference 캐시 — 새 Caffeine 캐시 설정 없이 경량하게 처리
 * - Micrometer Gauge: db_size.bytes, db_size.alert_level (ok=0, warning=1, critical=2)
 *
 * 임계 수준:
 * - ok: 사용률 < 80%
 * - warning: 80% ≤ 사용률 < 95%
 * - critical: 사용률 ≥ 95%
 */
@Service
class DbSizeMetricsService(
    private val namedJdbc: NamedParameterJdbcTemplate,
    private val dataSource: DataSource,
    private val runtimeSettingService: RuntimeSettingService,
    private val meterRegistry: MeterRegistry,
    private val opsLogNotifier: OpsLogNotifier,
    private val clippingMetrics: ClippingMetrics
) {

    companion object {
        /** Neon free tier 한계 1 GB (1,073,741,824 bytes). */
        const val LIMIT_BYTES = 1_073_741_824L

        private const val CACHE_TTL_MINUTES = 5L
        private const val TOP_TABLE_LIMIT = 10
        private const val WARNING_THRESHOLD = 0.80
        private const val CRITICAL_THRESHOLD = 0.95

        /** Micrometer Gauge alert_level 인코딩: ok=0, warning=1, critical=2. */
        private const val GAUGE_LEVEL_OK = 0.0
        private const val GAUGE_LEVEL_WARNING = 1.0
        private const val GAUGE_LEVEL_CRITICAL = 2.0

        /** 일별 성장 추정: batch_summary 1건당 평균 바이트 추정치 (참고용). */
        private const val BYTES_PER_SUMMARY_ESTIMATE = 2_048L

        // ── 히스테리시스 임계 ─────────────────────────────────────────────────
        /** critical 진입 임계 (95%). ok/warning → critical 전환 조건. */
        private const val ALERT_CRITICAL_THRESHOLD = 95.0
        /** critical 해제 임계 (90%). critical → ok 전환 조건. */
        private const val ALERT_RESET_THRESHOLD = 90.0

        private const val ALERT_LEVEL_OK = "ok"
        private const val ALERT_LEVEL_WARNING = "warning"
        private const val ALERT_LEVEL_CRITICAL = "critical"
    }

    /** 캐시 저장소 — snapshot + 캡처 시각을 한 쌍으로 보관. */
    private data class CachedSnapshot(val snapshot: DbSizeSnapshot, val cachedAt: Instant)

    private val cache = AtomicReference<CachedSnapshot?>(null)

    /**
     * DB 제품명 감지 결과를 lazy로 캐싱한다.
     * 최초 1회만 Connection을 열어 검사하고, 이후 재시작 전까지 재사용한다.
     */
    private val isH2: Boolean by lazy { detectDbProductName().uppercase().contains("H2") }

    // Gauge 가 AtomicLong 을 참조하도록 선언 — snapshot 갱신 시 함께 업데이트.
    private val gaugeDbSizeBytes = AtomicLong(0L)
    private val gaugeAlertLevel = AtomicLong(0L)

    init {
        // Micrometer Gauge 등록: db_size.bytes
        Gauge.builder("db_size.bytes", gaugeDbSizeBytes) { it.get().toDouble() }
            .description("Current database size in bytes")
            .register(meterRegistry)

        // Micrometer Gauge 등록: db_size.alert_level (ok=0, warning=1, critical=2)
        Gauge.builder("db_size.alert_level", gaugeAlertLevel) { it.get().toDouble() }
            .description("DB size alert level (0=ok, 1=warning, 2=critical)")
            .register(meterRegistry)
    }

    /**
     * DB 크기 스냅샷을 반환한다.
     * 5분 이내 캐시가 있으면 재사용하고, forceRefresh=true 이면 캐시를 무시한다.
     *
     * @param forceRefresh true 이면 캐시를 건너뛰고 즉시 재조회한다.
     * @return DB 크기 스냅샷
     */
    fun snapshot(forceRefresh: Boolean = false): DbSizeSnapshot {
        // forceRefresh 가 아니면 캐시 유효성 먼저 확인한다 (잠금 없이 빠르게 읽는다).
        if (!forceRefresh) {
            val cached = cache.get()
            if (cached != null && !isCacheExpired(cached.cachedAt)) {
                log.debug { "DB size snapshot cache hit (age=${ChronoUnit.SECONDS.between(cached.cachedAt, Instant.now())}s)" }
                return cached.snapshot
            }
        }

        // 캐시 미스 또는 forceRefresh 시: synchronized 블록으로 동시 첫 진입을 막아 중복 발송을 방지한다.
        return synchronized(this) {
            // 잠금 획득 후 재확인 — 다른 스레드가 이미 갱신했을 수 있다.
            if (!forceRefresh) {
                val cached = cache.get()
                if (cached != null && !isCacheExpired(cached.cachedAt)) {
                    return@synchronized cached.snapshot
                }
            }

            // DB 제품명에 따라 실제 조회 또는 stub 를 선택한다.
            val fresh = if (isH2) buildH2Stub() else buildPostgresSnapshot()

            // 캐시에 저장하고 Gauge 를 갱신한다.
            cache.set(CachedSnapshot(snapshot = fresh, cachedAt = Instant.now()))
            updateGauges(fresh)

            // 히스테리시스 기반 임계 알림 처리: critical 진입 시 알림 발송, 중복 발송 억제.
            evaluateAndFireHysteresisAlert(fresh)

            log.debug { "DB size snapshot refreshed: ${fresh.databaseSizeMegabytes}MB (${fresh.thresholdLevel})" }
            fresh
        }
    }

    // ── 내부 구현 ──

    /**
     * DB 제품명을 반환한다. DatabaseMetaData.getDatabaseProductName 사용.
     * lazy 필드 `isH2` 에서 최초 1회만 호출된다.
     */
    private fun detectDbProductName(): String {
        return dataSource.connection.use { conn ->
            conn.metaData.databaseProductName
        }
    }

    /** 캐시 만료 여부를 확인한다 (5분 TTL). */
    private fun isCacheExpired(cachedAt: Instant): Boolean {
        return Instant.now().isAfter(cachedAt.plus(CACHE_TTL_MINUTES, ChronoUnit.MINUTES))
    }

    /** PostgreSQL 환경에서 실제 크기를 조회한다. */
    private fun buildPostgresSnapshot(): DbSizeSnapshot {
        val dbSizeBytes = queryDatabaseSizeBytes()
        val topTables = queryTopTables(dbSizeBytes)
        val retentionSettings = runtimeSettingService.current()
        val retentionEligible = queryRetentionEligible(retentionSettings)
        val dailyGrowth = queryDailyGrowth()
        val pctOfLimit = (dbSizeBytes.toDouble() / LIMIT_BYTES) * 100.0

        return DbSizeSnapshot(
            databaseSizeBytes = dbSizeBytes,
            databaseSizeMegabytes = dbSizeBytes / (1024 * 1024),
            databaseSizePercentOfLimit = pctOfLimit.coerceIn(0.0, 100.0),
            limitBytes = LIMIT_BYTES,
            thresholdLevel = computeThresholdLevel(pctOfLimit),
            topTables = topTables,
            retentionEligible = retentionEligible,
            dailyGrowth = dailyGrowth,
            lastRefreshedAt = Instant.now()
        )
    }

    /** pg_database_size(current_database()) 로 전체 DB 크기를 조회한다. */
    private fun queryDatabaseSizeBytes(): Long {
        return namedJdbc.jdbcTemplate.queryForObject(
            "SELECT pg_database_size(current_database())",
            Long::class.java
        ) ?: 0L
    }

    /** pg_total_relation_size 로 상위 10개 테이블의 크기를 조회한다. */
    private fun queryTopTables(dbSizeBytes: Long): List<TableSizeEntry> {
        val sql = """
            SELECT
                relname AS table_name,
                pg_total_relation_size(c.oid) AS total_bytes,
                GREATEST(reltuples::bigint, 0) AS row_estimate
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind = 'r'
              AND n.nspname = 'public'
            ORDER BY total_bytes DESC
            LIMIT $TOP_TABLE_LIMIT
        """.trimIndent()

        return namedJdbc.jdbcTemplate.query(sql) { rs, _ ->
            val bytes = rs.getLong("total_bytes")
            val pct = if (dbSizeBytes > 0) (bytes.toDouble() / dbSizeBytes) * 100.0 else 0.0
            TableSizeEntry(
                table = rs.getString("table_name"),
                bytes = bytes,
                rows = rs.getLong("row_estimate"),
                pctOfDb = pct.coerceIn(0.0, 100.0)
            )
        }
    }

    /**
     * 보존 정책 cutoff 를 기준으로 삭제 가능한 행 수를 COUNT 한다.
     * DELETE 대신 COUNT 만 수행하므로 부작용 없다.
     */
    private fun queryRetentionEligible(settings: RuntimeSettingService.RuntimeSettings): RetentionEligibleSummary {
        val rssItemsCutoff = Instant.now().minus(settings.retentionRssItemsDays.toLong(), ChronoUnit.DAYS)
        val batchSummaryCutoff = Instant.now().minus(settings.retentionBatchSummariesDays.toLong(), ChronoUnit.DAYS)

        // rss_items: cutoff 이전 행 수 집계 (created_at = 수집 시각, retention 스케줄러와 동일 기준)
        val rssItemCount = namedJdbc.jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rss_items WHERE created_at < ?",
            Long::class.java,
            java.sql.Timestamp.from(rssItemsCutoff)
        ) ?: 0L

        // batch_summaries: anchor(summary_feedback, bookmarked_articles) 제외한 삭제 후보
        val batchSummaryCount = namedJdbc.jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM batch_summaries bs
            WHERE bs.created_at < ?
              AND NOT EXISTS (
                  SELECT 1 FROM summary_feedback sf WHERE sf.summary_id = bs.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM bookmarked_articles ba WHERE ba.summary_id = bs.id
              )
            """.trimIndent(),
            Long::class.java,
            java.sql.Timestamp.from(batchSummaryCutoff)
        ) ?: 0L

        // 해방 예정 바이트: rss_items 평균 1KB + batch_summary 평균 2KB 추정
        val projectedBytes = (rssItemCount * 1_024L) + (batchSummaryCount * BYTES_PER_SUMMARY_ESTIMATE)

        return RetentionEligibleSummary(
            rssItemsOlderThanCutoff = rssItemCount,
            batchSummariesOlderThanCutoffExcludingAnchored = batchSummaryCount,
            projectedBytesFreed = projectedBytes
        )
    }

    /**
     * 최근 7일간 일별 batch_summaries 생성 건수를 집계해 증가량을 추정한다.
     * created_at 기반이므로 DB 레벨 집계 — 실제 바이트가 아닌 건수 × 추정 바이트.
     */
    private fun queryDailyGrowth(): DailyGrowthSummary {
        // day_offset: 0 = 오늘, 6 = 6일 전
        // cutoff 는 Java 에서 계산해 파라미터로 전달 — INTERVAL 리터럴 (§1.3.2 금지) 회피
        val cutoff = java.sql.Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS))
        val sql = """
            SELECT day_offset, summary_count
            FROM (
                SELECT
                    CAST(FLOOR(
                        (CAST(EXTRACT(EPOCH FROM NOW()) AS BIGINT) -
                         CAST(EXTRACT(EPOCH FROM created_at) AS BIGINT)) / 86400
                    ) AS INTEGER) AS day_offset,
                    COUNT(*) AS summary_count
                FROM batch_summaries
                WHERE created_at >= ?
                GROUP BY day_offset
            ) sub
            WHERE day_offset BETWEEN 0 AND 6
            ORDER BY day_offset ASC
        """.trimIndent()

        val byOffset = namedJdbc.jdbcTemplate.query(sql, { rs, _ ->
            rs.getInt("day_offset") to rs.getLong("summary_count")
        }, cutoff).toMap()

        // day-6 ~ day-0 순서 (index 0 = 가장 오래된 날)
        val lastSevenDaysBytes = (6 downTo 0).map { offset ->
            (byOffset[offset] ?: 0L) * BYTES_PER_SUMMARY_ESTIMATE
        }

        val avgDailyBytes = if (lastSevenDaysBytes.isEmpty()) 0L
        else lastSevenDaysBytes.sum() / lastSevenDaysBytes.size

        return DailyGrowthSummary(
            lastSevenDaysBytes = lastSevenDaysBytes,
            avgDailyBytes = avgDailyBytes
        )
    }

    /**
     * 사용률(%) → 임계 수준 문자열 변환.
     * - ok: < 80%
     * - warning: 80% ≤ pct < 95%
     * - critical: ≥ 95%
     */
    private fun computeThresholdLevel(pctOfLimit: Double): String {
        val ratio = pctOfLimit / 100.0
        return when {
            ratio >= CRITICAL_THRESHOLD -> "critical"
            ratio >= WARNING_THRESHOLD -> "warning"
            else -> "ok"
        }
    }

    /** Gauge AtomicLong 을 최신 스냅샷 값으로 갱신한다. */
    private fun updateGauges(snapshot: DbSizeSnapshot) {
        gaugeDbSizeBytes.set(snapshot.databaseSizeBytes)
        val levelValue = when (snapshot.thresholdLevel) {
            "critical" -> GAUGE_LEVEL_CRITICAL
            "warning" -> GAUGE_LEVEL_WARNING
            else -> GAUGE_LEVEL_OK
        }
        gaugeAlertLevel.set(levelValue.toLong())
    }

    // ── 히스테리시스 알림 ──

    /**
     * DB 크기 임계 수준을 이전 상태와 비교해 엣지 트리거 방식으로 Slack 알림을 결정한다.
     *
     * 규칙:
     * - 이전 = ok/warning이고 현재 pct ≥ 95% → critical 전환, Slack 알림 발송
     * - 이전 = critical이고 현재 pct < 90% → ok 초기화 (90-95% 억제 구간은 critical 유지)
     * - warning 전환(80..95%)은 Slack 알림을 발송하지 않는다.
     *
     * @param snapshot 최신 스냅샷 (pct, size 정보 포함)
     */
    internal fun evaluateAndFireHysteresisAlert(snapshot: DbSizeSnapshot) {
        // 이전 알림 수준을 DB 에서 읽어온다 (서비스 재시작 후에도 상태 유지).
        val previousLevel = runtimeSettingService.getStringSetting(
            RuntimeSettingService.KEY_DB_SIZE_ALERT_LAST_LEVEL
        ) ?: ALERT_LEVEL_OK

        val pct = snapshot.databaseSizePercentOfLimit
        val newLevel = computeHysteresisLevel(previousLevel, pct)

        // 상태가 바뀌었을 때만 DB 에 저장해 불필요한 write 를 줄인다.
        if (newLevel != previousLevel) {
            runtimeSettingService.setStringSetting(
                RuntimeSettingService.KEY_DB_SIZE_ALERT_LAST_LEVEL, newLevel
            )
        }

        // ok/warning → critical 전환 시에만 Slack 알림을 발송한다.
        if (previousLevel != ALERT_LEVEL_CRITICAL && newLevel == ALERT_LEVEL_CRITICAL) {
            fireDbSizeCriticalAlert(snapshot)
        }
    }

    /**
     * 히스테리시스 규칙에 따라 새 수준을 계산한다.
     *
     * @param previousLevel 이전 수준 ("ok" | "warning" | "critical")
     * @param pct 현재 DB 사용률 (0..100)
     * @return 새 수준 문자열
     */
    internal fun computeHysteresisLevel(previousLevel: String, pct: Double): String {
        return if (previousLevel == ALERT_LEVEL_CRITICAL) {
            // critical 상태: 90% 미만으로 떨어져야 해제 (90-95% 구간은 억제)
            if (pct < ALERT_RESET_THRESHOLD) ALERT_LEVEL_OK else ALERT_LEVEL_CRITICAL
        } else {
            // ok/warning 상태: 95% 이상이면 critical 전환
            when {
                pct >= ALERT_CRITICAL_THRESHOLD -> ALERT_LEVEL_CRITICAL
                pct >= WARNING_THRESHOLD * 100.0 -> ALERT_LEVEL_WARNING
                else -> ALERT_LEVEL_OK
            }
        }
    }

    /**
     * DB 크기 95% 임계 초과 알림을 OpsLogNotifier 를 통해 발송한다.
     * opsLogsEnabled kill switch, Silent Hours 판정, admin 버튼 URL은 OpsLogNotifier 구현체가 처리한다.
     *
     * @param snapshot 현재 DB 크기 스냅샷
     */
    private fun fireDbSizeCriticalAlert(snapshot: DbSizeSnapshot) {
        val sizeMb = snapshot.databaseSizeMegabytes
        val limitMb = snapshot.limitBytes / (1024 * 1024)
        val pct = snapshot.databaseSizePercentOfLimit

        // OpsLogNotifier 를 통해 kill switch / Silent Hours / 메타데이터를 자동으로 적용한다.
        opsLogNotifier.postDbSizeCritical(
            databaseSizeMegabytes = sizeMb,
            limitMegabytes = limitMb,
            percent = pct,
        )

        // 메트릭 카운터를 증가시켜 알림 발송 빈도를 추적한다.
        clippingMetrics.recordDbSizeAlertFired(ALERT_LEVEL_CRITICAL)

        log.warn { "DB 크기 임계 초과 알림 발송 요청: ${sizeMb}MB / ${limitMb}MB (${"%.1f".format(pct)}%)" }
    }

    // ── H2 stub ──

    /**
     * H2 환경에서 반환할 stub 스냅샷.
     * 실제 pg_database_size 가 없으므로 0 값으로 채운다.
     */
    private fun buildH2Stub(): DbSizeSnapshot {
        val stubTables = listOf(
            TableSizeEntry("batch_summaries", bytes = 1_024L, rows = 10L, pctOfDb = 10.0),
            TableSizeEntry("rss_items", bytes = 512L, rows = 5L, pctOfDb = 5.0)
        )
        return DbSizeSnapshot(
            databaseSizeBytes = 0L,
            databaseSizeMegabytes = 0L,
            databaseSizePercentOfLimit = 0.0,
            limitBytes = LIMIT_BYTES,
            thresholdLevel = "ok",
            topTables = stubTables,
            retentionEligible = RetentionEligibleSummary(
                rssItemsOlderThanCutoff = 0L,
                batchSummariesOlderThanCutoffExcludingAnchored = 0L,
                projectedBytesFreed = 0L
            ),
            dailyGrowth = DailyGrowthSummary(
                lastSevenDaysBytes = List(7) { 0L },
                avgDailyBytes = 0L
            ),
            lastRefreshedAt = Instant.now()
        )
    }
}
