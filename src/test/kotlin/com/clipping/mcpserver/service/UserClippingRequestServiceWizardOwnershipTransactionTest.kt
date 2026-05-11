package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.UserClippingRequest
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.UserClippingRequestStore
import com.clipping.mcpserver.store.UserOwnedCategoryStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * 위자드 소유권 등록 중 마지막 요청 저장이 실패해도 owner 매핑이 남지 않는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserClippingRequestServiceWizardOwnershipTransactionTest {

    @Autowired
    lateinit var service: UserClippingRequestService

    @Autowired
    lateinit var adminUserStore: AdminUserStore

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var userOwnedCategoryStore: UserOwnedCategoryStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @MockitoBean
    lateinit var requestStore: UserClippingRequestStore

    private lateinit var user: AdminUser
    private lateinit var category: Category

    @BeforeEach
    fun setup() {
        val suffix = System.nanoTime()
        user = adminUserStore.save(
            AdminUser(
                id = "",
                username = "w$suffix@t.io",
                passwordHash = "pw",
                role = AccountRole.USER
            )
        )
        category = categoryStore.save(
            Category(
                id = "",
                name = "wizard-tx-category-$suffix"
            )
        )
        doReturn(emptyList<UserClippingRequest>())
            .`when`(requestStore)
            .listByRequesterUserId(user.id)
        doThrow(IllegalStateException("request save failure"))
            .`when`(requestStore)
            .save(anyRequest())
    }

    @AfterEach
    fun cleanup() {
        if (this::user.isInitialized && this::category.isInitialized) {
            jdbc.update("DELETE FROM clipping_user_owned_categories WHERE user_id = ? AND category_id = ?", user.id, category.id)
            categoryStore.delete(category.id)
            jdbc.update("DELETE FROM admin_users WHERE id = ?", user.id)
        }
    }

    @Test
    fun `register wizard ownership should rollback owner mapping when request save fails`() {
        assertThrows(IllegalStateException::class.java) {
            service.registerWizardOwnership(
                requesterUsername = user.username,
                requestName = "wizard-tx-request",
                sourceName = "wizard-tx-source",
                sourceUrl = "https://example.com/wizard-tx.xml",
                slackChannelId = "C_TX_WIZARD",
                personaName = "실무 요약",
                personaPrompt = "핵심만 정리",
                summaryStyle = null,
                targetAudience = null,
                selectedPresetId = null,
                categoryId = category.id,
                personaId = null,
                sourceId = null
            )
        }

        assertFalse(userOwnedCategoryStore.exists(user.id, category.id))
    }

    private fun anyRequest(): UserClippingRequest {
        any(UserClippingRequest::class.java)
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T
}
