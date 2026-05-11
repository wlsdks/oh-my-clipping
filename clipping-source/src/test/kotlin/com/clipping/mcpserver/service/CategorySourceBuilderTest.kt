package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.service.port.SourceOrganization
import com.clipping.mcpserver.service.port.SourceOrganizationPort
import com.clipping.mcpserver.service.source.CategorySourceBuilder
import com.clipping.mcpserver.service.source.DigestMode
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.RssSourceStore
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain as kContain
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CategorySourceBuilderTest {
    private val rssStore = mockk<RssSourceStore>(relaxed = true)
    private val ruleStore = mockk<CategoryRuleStore>(relaxed = true)
    private val organizationPort = mockk<SourceOrganizationPort>(relaxed = true)

    // selfProvider 가 builder 자신을 반환하도록 설정 (실제 Spring 컨텍스트 없이 unit 테스트).
    private val selfProvider = mockk<ObjectProvider<CategorySourceBuilder>>()

    private val builder = CategorySourceBuilder(
        rssSourceStore = rssStore,
        categoryRuleStore = ruleStore,
        organizationPort = organizationPort,
        maxCombinations = 30,
        selfProvider = selfProvider
    )

    init {
        // selfProvider.getObject() 가 builder 를 반환 — self-proxy 역할.
        every { selfProvider.getObject() } returns builder
    }

    private fun stubOrg(name: String, aliases: List<String> = emptyList()) =
        SourceOrganization(name = name, aliases = aliases)

    private fun stubSource(id: String, url: String, origin: String, categoryId: String = "cat-1") =
        RssSource(
            id = id, categoryId = categoryId, name = "n", url = url,
            origin = origin,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )

    @Nested
    inner class `buildUrls` {
        @Test
        fun `TOPIC_ONLY 3 keyword 3 URL`() {
            val urls = builder.buildUrls(
                DigestMode.TOPIC_ONLY,
                keywords = listOf("AI", "리스킬링", "L&D"),
                orgs = emptyList()
            )
            urls shouldHaveSize 3
            urls[0].url kContain "q=AI"
            urls[1].url kContain "%EB%A6%AC%EC%8A%A4%ED%82%AC%EB%A7%81"  // 리스킬링 URL-encoded
        }

        @Test
        fun `ACCOUNT_ONLY 2 org 2 URL with alias OR`() {
            val orgs = listOf(
                stubOrg("MegaCorp", listOf("SEC")),
                stubOrg("ConglomerateCo")
            )
            val urls = builder.buildUrls(
                DigestMode.ACCOUNT_ONLY,
                keywords = emptyList(),
                orgs = orgs
            )
            urls shouldHaveSize 2
            urls[0].url kContain "OR"          // alias OR 조합
            urls[1].url kContain "ConglomerateCo"
        }

        @Test
        fun `CROSSFILTER NxM with cap 30`() {
            val keywords = List(10) { "k$it" }
            val orgs = List(10) { stubOrg("o$it") }
            val urls = builder.buildUrls(
                DigestMode.CROSSFILTER,
                keywords, orgs
            )
            urls shouldHaveSize 30  // 100 조합 → 30 cap
        }

        @Test
        fun `DUAL_SECTION = CROSSFILTER + ACCOUNT (합집합, cap 30)`() {
            val orgs = listOf(stubOrg("A"), stubOrg("B"))
            val keywords = listOf("k1", "k2")
            val urls = builder.buildUrls(
                DigestMode.DUAL_SECTION,
                keywords, orgs
            )
            // 4 cross (2x2) + 2 account = 6
            urls shouldHaveSize 6
        }

        @Test
        fun `CROSSFILTER 1x1`() {
            val urls = builder.buildUrls(
                DigestMode.CROSSFILTER,
                keywords = listOf("리스킬링"),
                orgs = listOf(stubOrg("MegaCorp"))
            )
            urls shouldHaveSize 1
            urls[0].name kContain "MegaCorp"
        }
    }

    @Nested
    inner class `reconcileSources` {
        @Test
        fun `신규 target INSERT, 사라진 auto 만 DELETE, manual preserve`() {
            val categoryId = "cat-1"
            every { rssStore.findByCategoryIdAndOrigin(categoryId, "auto_generated") } returns listOf(
                stubSource(id = "s1", url = "old-auto.com", origin = "auto_generated")
            )
            every { rssStore.existsByCategoryIdAndUrl(categoryId, "new-auto.com") } returns false

            builder.reconcileSources(categoryId, listOf(
                CategorySourceBuilder.GeneratedSource(url = "new-auto.com", name = "new")
            ))

            // 신규 insert
            verify {
                rssStore.insert(
                    id = any(), categoryId = categoryId, sourceUrl = "new-auto.com",
                    sourceName = "new", origin = "auto_generated"
                )
            }
            // 사라진 auto 삭제
            verify { rssStore.delete("s1") }
            // manual 조회 안 함
            verify(exactly = 0) { rssStore.findByCategoryIdAndOrigin(categoryId, "manual") }
        }

        @Test
        fun `manual 로 이미 있는 url 은 중복 INSERT skip`() {
            val categoryId = "cat-1"
            every { rssStore.findByCategoryIdAndOrigin(categoryId, "auto_generated") } returns emptyList()
            every { rssStore.existsByCategoryIdAndUrl(categoryId, "shared.com") } returns true  // manual 선점

            builder.reconcileSources(categoryId, listOf(
                CategorySourceBuilder.GeneratedSource(url = "shared.com", name = "n")
            ))

            verify(exactly = 0) {
                rssStore.insert(any(), any(), "shared.com", any(), any())
            }
        }

        @Test
        fun `기존 auto URL 과 target URL 일치 시 재사용 (INSERT도 DELETE도 안함)`() {
            val categoryId = "cat-1"
            every { rssStore.findByCategoryIdAndOrigin(categoryId, "auto_generated") } returns listOf(
                stubSource(id = "s1", url = "keep.com", origin = "auto_generated")
            )
            // existsByCategoryIdAndUrl 은 중복 체크용으로 호출되지 않음 (이미 existing에 있으므로 skip)
            every { rssStore.existsByCategoryIdAndUrl(categoryId, "keep.com") } returns true

            builder.reconcileSources(categoryId, listOf(
                CategorySourceBuilder.GeneratedSource(url = "keep.com", name = "n")
            ))

            verify(exactly = 0) { rssStore.insert(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { rssStore.delete(any()) }
        }
    }

    @Nested
    inner class `동시성 - 어노테이션 배치 검증` {

        @Test
        fun `syncSourcesForCategory 는 @Transactional 이 없어야 한다`() {
            // lock 이 트랜잭션 바깥에 있어야 dirty-read 창이 없음.
            val annotations = CategorySourceBuilder::class.java
                .getMethod("syncSourcesForCategory", String::class.java)
                .annotations
            annotations.none { it is Transactional }.shouldBeTrue()
        }

        @Test
        fun `syncSourcesForCategoryInTx 는 @Transactional 이 있어야 한다`() {
            // 실제 DB 작업은 트랜잭션 안에서 이뤄진다.
            val annotations = CategorySourceBuilder::class.java
                .getMethod("syncSourcesForCategoryInTx", String::class.java)
                .annotations
            annotations.any { it is Transactional }.shouldBeTrue()
        }

        @Test
        fun `syncSourcesForCategory 는 selfProvider 를 통해 syncSourcesForCategoryInTx 를 호출한다`() {
            val categoryId = "cat-lock"
            every { ruleStore.findIncludeKeywords(categoryId) } returns listOf("AI")
            every { organizationPort.findSourceOrganizationsByCategoryId(categoryId) } returns emptyList()

            builder.syncSourcesForCategory(categoryId)

            // selfProvider.getObject() 가 정확히 1회 호출됐어야 한다.
            verify(exactly = 1) { selfProvider.getObject() }
        }

        @Test
        fun `같은 categoryId 의 두 번째 호출은 첫 번째 unlock 후에야 실행된다`() {
            val categoryId = "cat-seq"
            // thread1 이 lock 내부(InTx 직전)에 도달했음을 알리는 래치.
            val thread1Locked = CountDownLatch(1)
            // thread1 이 InTx 를 완료해도 된다는 신호.
            val thread1Release = CountDownLatch(1)

            val slowSelfProvider = mockk<ObjectProvider<CategorySourceBuilder>>()
            val slowBuilder = CategorySourceBuilder(
                rssSourceStore = rssStore,
                categoryRuleStore = ruleStore,
                organizationPort = organizationPort,
                maxCombinations = 30,
                selfProvider = slowSelfProvider
            )

            // getObject() 는 두 스레드 모두 호출 — 첫 번째 호출만 blocking.
            var firstCall = true
            every { slowSelfProvider.getObject() } answers {
                if (firstCall) {
                    firstCall = false
                    thread1Locked.countDown()            // thread2 가 lock 경쟁하도록 알림.
                    thread1Release.await(3, TimeUnit.SECONDS) // 해제 신호를 기다림.
                }
                slowBuilder
            }

            val executor = Executors.newFixedThreadPool(2)

            val thread1 = executor.submit {
                slowBuilder.syncSourcesForCategory(categoryId)
            }

            // thread1 이 lock 을 잡을 때까지 대기.
            thread1Locked.await(2, TimeUnit.SECONDS)

            // thread2 시작 — thread1 이 lock 을 아직 보유 중이므로 block 됨.
            val thread2Started = CountDownLatch(1)
            val thread2 = executor.submit {
                thread2Started.countDown()
                slowBuilder.syncSourcesForCategory(categoryId)
            }

            // thread2 가 lock 대기 상태에 들어갈 시간을 준다.
            thread2Started.await(2, TimeUnit.SECONDS)
            Thread.sleep(30)

            // 이 시점에서 thread1 의 InTx 는 아직 시작도 안 됐음 (release 대기 중).
            // thread2 도 lock 을 못 잡은 상태. getObject() 호출 횟수는 1 이어야 한다.
            verify(exactly = 1) { slowSelfProvider.getObject() }

            // thread1 InTx 완료 허용.
            thread1Release.countDown()
            thread1.get(3, TimeUnit.SECONDS)
            thread2.get(3, TimeUnit.SECONDS)
            executor.shutdown()

            // thread1 + thread2 모두 완료 → getObject() 총 2회 호출.
            verify(exactly = 2) { slowSelfProvider.getObject() }
        }
    }
}
