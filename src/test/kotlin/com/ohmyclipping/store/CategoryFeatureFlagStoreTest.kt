package com.ohmyclipping.store

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryFeatureFlagStoreTest(
    @Autowired private val flagStore: CategoryFeatureFlagStore,
    @Autowired private val jdbc: JdbcTemplate
) {

    @Test
    fun `find absent returns false`() {
        val categoryId = insertTestCategory()
        flagStore.isAccountBasedDigestEnabled(categoryId) shouldBe false
    }

    @Test
    fun `setEnabled true persists and reads back true`() {
        val categoryId = insertTestCategory()
        flagStore.setAccountBasedDigestEnabled(categoryId, true)
        flagStore.isAccountBasedDigestEnabled(categoryId) shouldBe true
    }

    @Test
    fun `setEnabled toggle updates existing row`() {
        val categoryId = insertTestCategory()
        flagStore.setAccountBasedDigestEnabled(categoryId, true)
        flagStore.setAccountBasedDigestEnabled(categoryId, false)
        flagStore.isAccountBasedDigestEnabled(categoryId) shouldBe false
    }

    @Test
    fun `isShadowModeEnabled 미저장이면 false`() {
        val categoryId = insertTestCategory()
        flagStore.isShadowModeEnabled(categoryId) shouldBe false
    }

    @Test
    fun `setShadowModeEnabled true 저장 후 isShadow true + enabled_at 기록`() {
        val categoryId = insertTestCategory()
        flagStore.setShadowModeEnabled(categoryId, true)

        flagStore.isShadowModeEnabled(categoryId) shouldBe true
        (flagStore.getShadowEnabledAt(categoryId) != null) shouldBe true
    }

    @Test
    fun `setShadowModeEnabled 재활성 시 shadow_enabled_at 덮어쓰지 않음`() {
        val categoryId = insertTestCategory()
        flagStore.setShadowModeEnabled(categoryId, true)
        val firstEnabledAt = flagStore.getShadowEnabledAt(categoryId)

        flagStore.setShadowModeEnabled(categoryId, false)
        flagStore.setShadowModeEnabled(categoryId, true)

        flagStore.getShadowEnabledAt(categoryId) shouldBe firstEnabledAt
    }

    @Test
    fun `setShadowModeEnabled 은 기존 account_based_digest_enabled 값을 보존한다`() {
        val categoryId = insertTestCategory()
        flagStore.setAccountBasedDigestEnabled(categoryId, true)
        flagStore.setShadowModeEnabled(categoryId, true)

        flagStore.isAccountBasedDigestEnabled(categoryId) shouldBe true
        flagStore.isShadowModeEnabled(categoryId) shouldBe true
    }

    /**
     * batch_categories 에 JDBC 로 직접 삽입하고 id 를 반환한다.
     * JPA save() 후 flush 없이 JDBC FK 를 체크하면 트랜잭션 내 visibility 문제가 생기므로
     * 처음부터 JDBC 로 삽입하는 패턴을 사용한다 (ReviewPolicyQueryHelperTest 동일 패턴).
     */
    private fun insertTestCategory(): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO batch_categories (id, name, is_active, created_at, updated_at, system_updated_at)
               VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            id, "cff-test-${System.nanoTime()}"
        )
        return id
    }
}
