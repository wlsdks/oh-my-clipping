package com.clipping.mcpserver.service.source

import com.clipping.mcpserver.service.port.SourceOrganization
import com.clipping.mcpserver.service.port.SourceOrganizationPort
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.RssSourceStore
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.time.Duration
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

private val log = KotlinLogging.logger {}

/**
 * 카테고리의 rss_sources 를 digest mode 기반으로 동기화한다.
 *
 * - 신규 승인 / 조직 편집 / 키워드 편집 후 호출.
 * - auto_generated 는 delta 교체, manual 은 보존.
 * - categoryId 단위 ReentrantLock 으로 race 방지 (H2 호환).
 *
 * 동시성 설계:
 * - [syncSourcesForCategory] 는 @Transactional 없이 lock 을 획득한 뒤
 *   self-proxy 를 통해 [syncSourcesForCategoryInTx] 를 호출한다.
 * - lock 이 트랜잭션 경계 바깥에 있으므로, 두 번째 스레드는 첫 번째
 *   트랜잭션이 완전히 커밋된 후에야 lock 을 획득할 수 있다.
 *
 * 메모리 안전성:
 * - categoryId 별 lock 은 Caffeine Cache 에 보관 (maximumSize=10_000, expireAfterAccess=30분).
 * - ConcurrentHashMap 대비 TTL/size 상한으로 무한 증가를 방지한다.
 * - expireAfterAccess 는 마지막 접근 시점 기준이므로, 진행 중인 lock holder 가
 *   [lockFor] 를 통해 주기적으로 접근하는 한 evict 되지 않는다.
 */
@Service
class CategorySourceBuilder(
    private val rssSourceStore: RssSourceStore,
    private val categoryRuleStore: CategoryRuleStore,
    private val organizationPort: SourceOrganizationPort,
    @Value("\${clipping.collection.max-combinations:30}")
    private val maxCombinations: Int,
    private val selfProvider: ObjectProvider<CategorySourceBuilder>
) {
    /** URL 생성 결과 — url 과 사람이 읽을 수 있는 name 포함. */
    data class GeneratedSource(val url: String, val name: String)

    /**
     * categoryId 별 ReentrantLock 보관소.
     * - maximumSize=10_000: 카테고리 수 상한 (초과 시 LRU evict)
     * - expireAfterAccess=30분: 장기간 미사용 categoryId lock 자동 해제
     */
    private val lockCache: Cache<String, ReentrantLock> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(Duration.ofMinutes(30))
        .build()

    /**
     * 외부 진입점 — @Transactional 없음. Lock 을 획득한 뒤 self-proxy 를 통해
     * [syncSourcesForCategoryInTx] 를 호출하여 @Transactional AOP 가 적용되도록 한다.
     *
     * lock 이 트랜잭션보다 먼저 획득되고 커밋 후에 해제되므로 dirty-read 창이 없다.
     */
    fun syncSourcesForCategory(categoryId: String) {
        // Caffeine Cache.get() 은 atomic getOrLoad — 같은 key 에 대해 ReentrantLock 단일 인스턴스를 보장한다.
        val lock = lockFor(categoryId)
        lock.lock()
        try {
            // 프록시를 통한 호출 — @Transactional 이 적용됨.
            selfProvider.getObject().syncSourcesForCategoryInTx(categoryId)
        } finally {
            lock.unlock()
        }
    }

    /**
     * 실제 DB 읽기/쓰기 — @Transactional 적용. [syncSourcesForCategory] 에서만 호출한다.
     *
     * keywords 와 orgs 가 모두 비어 있으면 skip 하고 경고만 남긴다.
     */
    @Transactional
    fun syncSourcesForCategoryInTx(categoryId: String) {
        val keywords = categoryRuleStore.findIncludeKeywords(categoryId)
        val orgs = organizationPort.findSourceOrganizationsByCategoryId(categoryId)
        if (keywords.isEmpty() && orgs.isEmpty()) {
            log.warn { "syncSourcesForCategory skipped — no keywords or orgs for $categoryId" }
            return
        }
        val mode = resolveDigestMode(keywords.size, orgs.size)
        val targets = buildUrls(mode, keywords, orgs)
        reconcileSources(categoryId, targets)
    }

    /**
     * digest mode 에 따라 Google News RSS URL 목록을 생성한다.
     *
     * - TOPIC_ONLY: 키워드별 1개씩
     * - ACCOUNT_ONLY: 조직별 1개 (이름 + alias OR 조합)
     * - CROSSFILTER: 조직 × 키워드 교차 (maxCombinations 상한)
     * - DUAL_SECTION: CROSSFILTER + ACCOUNT_ONLY 합집합 (maxCombinations 상한)
     */
    fun buildUrls(
        mode: DigestMode,
        keywords: List<String>,
        orgs: List<SourceOrganization>
    ): List<GeneratedSource> {
        return when (mode) {
            DigestMode.TOPIC_ONLY -> keywords.map { kw ->
                GeneratedSource(url = googleNewsUrl(kw), name = kw)
            }

            DigestMode.ACCOUNT_ONLY -> orgs.map { org ->
                // alias 포함 OR 쿼리로 조직 관련 뉴스를 폭넓게 수집한다.
                val terms = (listOf(org.name) + org.aliases).distinct()
                val query = terms.joinToString(" OR ") { "\"$it\"" }
                GeneratedSource(url = googleNewsUrl(query), name = org.name)
            }

            DigestMode.CROSSFILTER -> {
                // 조직 × 키워드 교차 조합 생성 후 cap 적용.
                val combos = orgs.flatMap { org -> keywords.map { kw -> org to kw } }
                combos.take(maxCombinations).map { (org, kw) ->
                    GeneratedSource(
                        url = googleNewsUrl("\"${org.name}\" $kw"),
                        name = "${org.name} × $kw"
                    )
                }
            }

            DigestMode.DUAL_SECTION -> {
                // CROSSFILTER + ACCOUNT_ONLY 합집합, 전체에 cap 적용.
                val cross = buildUrls(DigestMode.CROSSFILTER, keywords, orgs)
                val account = buildUrls(DigestMode.ACCOUNT_ONLY, emptyList(), orgs)
                (cross + account).take(maxCombinations)
            }
        }
    }

    /**
     * 생성된 target URL 목록과 DB 의 auto_generated 소스를 비교하여 delta 동기화한다.
     *
     * - 신규 URL: insert (단, manual 로 선점된 URL 은 skip)
     * - 사라진 auto URL: delete
     * - 일치하는 URL: 재사용 (아무 작업 없음)
     * - manual 소스: 절대 건드리지 않음
     */
    fun reconcileSources(categoryId: String, targets: List<GeneratedSource>) {
        val existing = rssSourceStore.findByCategoryIdAndOrigin(categoryId, "auto_generated")
        val existingByUrl = existing.associateBy { it.url }
        val targetByUrl = targets.associateBy { it.url }

        // 신규 target 에 대해 INSERT — 이미 존재하는 URL(auto 또는 manual)은 skip.
        targets.filterNot { existingByUrl.containsKey(it.url) }.forEach { target ->
            if (rssSourceStore.existsByCategoryIdAndUrl(categoryId, target.url)) return@forEach
            rssSourceStore.insert(
                id = UUID.randomUUID().toString(),
                categoryId = categoryId,
                sourceUrl = target.url,
                sourceName = target.name,
                origin = "auto_generated"
            )
        }

        // target 에서 사라진 auto 소스만 삭제 — manual 소스는 건드리지 않음.
        existing.filterNot { targetByUrl.containsKey(it.url) }.forEach {
            rssSourceStore.delete(it.id)
        }
    }

    /**
     * categoryId 에 해당하는 ReentrantLock 을 반환한다.
     * Caffeine Cache.get() 은 동일 key 에 대해 단일 인스턴스를 원자적으로 제공하므로 race-free.
     */
    private fun lockFor(categoryId: String): ReentrantLock =
        lockCache.get(categoryId) { ReentrantLock() }!!

    /** Google News RSS 검색 URL 을 생성한다. */
    private fun googleNewsUrl(query: String): String {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8)
        return "https://news.google.com/rss/search?q=$encoded&hl=ko&gl=KR&ceid=KR:ko"
    }
}

enum class DigestMode {
    TOPIC_ONLY,
    ACCOUNT_ONLY,
    CROSSFILTER,
    DUAL_SECTION,
}

fun resolveDigestMode(keywordCount: Int, organizationCount: Int): DigestMode = when {
    keywordCount == 0 && organizationCount == 0 ->
        throw IllegalStateException("digest requires at least one keyword or org")
    organizationCount == 0 -> DigestMode.TOPIC_ONLY
    keywordCount == 0 -> DigestMode.ACCOUNT_ONLY
    keywordCount == 1 || organizationCount == 1 -> DigestMode.CROSSFILTER
    else -> DigestMode.DUAL_SECTION
}
