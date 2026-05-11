package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.UserClippingRequestEntity
import com.clipping.mcpserver.model.UserClippingRequestStatus
import com.clipping.mcpserver.repository.UserClippingRequestRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

/**
 * 사용자 클리핑 요청 목록의 제한 조회 테스트.
 * 관리자 목록 화면에서 전체 row를 로드한 뒤 자르지 않도록 repository 위임을 검증한다.
 */
class JpaUserClippingRequestStoreListRecentTest {

    private val repository = mockk<UserClippingRequestRepository>()
    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val store = JpaUserClippingRequestStore(repository, jdbc)

    @Test
    fun `listAll은 상태 필터가 있어도 Pageable 조건 조회로 안전 상한을 DB에 적용한다`() {
        val entity = requestEntity(id = "request-status", status = "APPROVED")
        val pageable = PageRequest.of(0, 10000)
        every { repository.findByStatusOrderByCreatedAtDesc("APPROVED", pageable) } returns listOf(entity)

        val result = store.listAll(UserClippingRequestStatus.APPROVED)

        result.map { it.id } shouldBe listOf("request-status")
        verify(exactly = 1) { repository.findByStatusOrderByCreatedAtDesc("APPROVED", pageable) }
        verify(exactly = 0) { repository.findByStatus("APPROVED") }
    }

    @Test
    fun `listAll은 상태 필터가 없어도 findAll 대신 Pageable 최신순 조회를 사용한다`() {
        val entity = requestEntity(id = "request-all", status = "PENDING")
        val pageable = PageRequest.of(0, 10000)
        every { repository.findAllByOrderByCreatedAtDesc(pageable) } returns listOf(entity)

        val result = store.listAll(null)

        result.map { it.id } shouldBe listOf("request-all")
        verify(exactly = 1) { repository.findAllByOrderByCreatedAtDesc(pageable) }
        verify(exactly = 0) { repository.findAll() }
    }

    @Test
    fun `상태 필터가 있으면 Pageable 조건 조회로 최근 요청만 가져온다`() {
        val entity = requestEntity(id = "request-1", status = "PENDING")
        val pageable = PageRequest.of(0, 50)
        every { repository.findByStatusOrderByCreatedAtDesc("PENDING", pageable) } returns listOf(entity)

        val result = store.listRecent(UserClippingRequestStatus.PENDING, 50)

        result.map { it.id } shouldBe listOf("request-1")
        verify(exactly = 1) { repository.findByStatusOrderByCreatedAtDesc("PENDING", pageable) }
        verify(exactly = 0) { repository.findByStatus("PENDING") }
    }

    @Test
    fun `상태 필터가 없으면 Pageable 전체 최신순 조회를 사용한다`() {
        val entity = requestEntity(id = "request-2", status = "APPROVED")
        val pageable = PageRequest.of(0, 20)
        every { repository.findAllByOrderByCreatedAtDesc(pageable) } returns listOf(entity)

        val result = store.listRecent(null, 20)

        result.map { it.id } shouldBe listOf("request-2")
        verify(exactly = 1) { repository.findAllByOrderByCreatedAtDesc(pageable) }
        verify(exactly = 0) { repository.findAll() }
    }

    @Test
    fun `승인 구독 사용자 ID는 카테고리 조건 DISTINCT 조회로 가져온다`() {
        val categoryIds = setOf("cat-1", "cat-2")
        every { repository.findApprovedRequesterIdsByCategoryIds(categoryIds) } returns listOf("user-1", "user-2")

        val result = store.findApprovedRequesterIdsByCategoryIds(categoryIds)

        result shouldBe setOf("user-1", "user-2")
        verify(exactly = 1) { repository.findApprovedRequesterIdsByCategoryIds(categoryIds) }
        verify(exactly = 0) { repository.findByStatus(any()) }
    }

    @Test
    fun `승인 구독 사용자 ID 조회는 카테고리가 없으면 DB를 호출하지 않는다`() {
        val result = store.findApprovedRequesterIdsByCategoryIds(emptySet())

        result shouldBe emptySet()
        verify(exactly = 0) { repository.findApprovedRequesterIdsByCategoryIds(any()) }
    }

    @Test
    fun `개인 스케줄 카테고리 판단은 사용자 조건 DISTINCT 조회로 가져온다`() {
        val requesterIds = setOf("user-1", "user-2")
        every { repository.findApprovedCategoryIdsByRequesterIds(requesterIds) } returns listOf("cat-1", "cat-2")

        val result = store.findApprovedCategoryIdsByRequesterIds(requesterIds)

        result shouldBe setOf("cat-1", "cat-2")
        verify(exactly = 1) { repository.findApprovedCategoryIdsByRequesterIds(requesterIds) }
        verify(exactly = 0) { repository.findByStatus(any()) }
    }

    @Test
    fun `개인 스케줄 카테고리 판단은 사용자가 없으면 DB를 호출하지 않는다`() {
        val result = store.findApprovedCategoryIdsByRequesterIds(emptySet())

        result shouldBe emptySet()
        verify(exactly = 0) { repository.findApprovedCategoryIdsByRequesterIds(any()) }
    }

    @Test
    fun `다이제스트 fan-out은 지정 카테고리 승인 구독만 조회한다`() {
        val entity = requestEntity(id = "request-category", status = "APPROVED")
        every {
            repository.findByStatusAndApprovedCategoryIdOrderByCreatedAtDesc("APPROVED", "cat-1")
        } returns listOf(entity)

        val result = store.listApprovedByCategoryId("cat-1")

        result.map { it.id } shouldBe listOf("request-category")
        verify(exactly = 1) {
            repository.findByStatusAndApprovedCategoryIdOrderByCreatedAtDesc("APPROVED", "cat-1")
        }
        verify(exactly = 0) { repository.findByStatus("APPROVED") }
    }

    @Test
    fun `다이제스트 fan-out 카테고리가 비어 있으면 DB를 호출하지 않는다`() {
        val result = store.listApprovedByCategoryId(" ")

        result shouldBe emptyList()
        verify(exactly = 0) { repository.findByStatusAndApprovedCategoryIdOrderByCreatedAtDesc(any(), any()) }
    }

    @Test
    fun `구독 한도 검증은 PENDING APPROVED 상태 count 쿼리를 사용한다`() {
        every {
            repository.countByRequesterUserIdAndStatusIn("user-1", listOf("PENDING", "APPROVED"))
        } returns 4

        val result = store.countActiveSubscriptionsByRequesterUserId("user-1")

        result shouldBe 4
        verify(exactly = 1) { repository.countByRequesterUserIdAndStatusIn("user-1", listOf("PENDING", "APPROVED")) }
        verify(exactly = 0) { repository.findByRequesterUserId(any()) }
    }

    @Test
    fun `월간 생성 한도 검증은 생성 시각과 유효 상태 count 쿼리를 사용한다`() {
        val since = Instant.parse("2026-04-01T00:00:00Z")
        every {
            repository.countByRequesterUserIdAndCreatedAtAfterAndStatusIn(
                "user-1",
                since,
                listOf("PENDING", "APPROVED")
            )
        } returns 5

        val result = store.countCreatedSinceByRequesterUserId("user-1", since)

        result shouldBe 5
        verify(exactly = 1) {
            repository.countByRequesterUserIdAndCreatedAtAfterAndStatusIn(
                "user-1",
                since,
                listOf("PENDING", "APPROVED")
            )
        }
        verify(exactly = 0) { repository.findByRequesterUserId(any()) }
    }

    @Test
    fun `즉시 구독 중복 검증은 requester category status exists 쿼리를 사용한다`() {
        every {
            repository.existsByRequesterUserIdAndApprovedCategoryIdAndStatus("user-1", "cat-1", "APPROVED")
        } returns true

        val result = store.existsApprovedByRequesterUserIdAndCategoryId("user-1", "cat-1")

        result shouldBe true
        verify(exactly = 1) {
            repository.existsByRequesterUserIdAndApprovedCategoryIdAndStatus("user-1", "cat-1", "APPROVED")
        }
        verify(exactly = 0) { repository.findByRequesterUserId(any()) }
    }

    /**
     * 목록 조회 테스트 fixture를 만든다.
     * 필수 컬럼만 채워 JPA entity -> domain model 변환을 검증한다.
     */
    private fun requestEntity(id: String, status: String): UserClippingRequestEntity {
        val now = Instant.parse("2026-04-26T00:00:00Z")
        return UserClippingRequestEntity(
            id = id,
            requesterUserId = "user-1",
            requestName = "테스트 요청",
            sourceName = "테스트 소스",
            sourceUrl = "https://example.com/rss",
            slackChannelId = "C123",
            personaName = "테스트 페르소나",
            personaPrompt = "요약",
            status = status,
            createdAt = now,
            updatedAt = now,
        )
    }
}
