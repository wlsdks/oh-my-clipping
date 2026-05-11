package com.ohmyclipping.service

import com.ohmyclipping.model.RuntimeSetting
import com.ohmyclipping.model.RuntimeSettingAudit
import com.ohmyclipping.store.RuntimeSettingAuditStore
import com.ohmyclipping.store.RuntimeSettingStore
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class RuntimeSettingServiceTransactionTest {

    @Autowired
    lateinit var runtimeSettingService: RuntimeSettingService

    @Autowired
    lateinit var runtimeSettingStore: RuntimeSettingStore

    @MockitoBean
    lateinit var runtimeSettingAuditStore: RuntimeSettingAuditStore

    @BeforeEach
    fun setup() {
        runtimeSettingStore.deleteAll()
    }

    @Test
    fun `update should rollback setting writes when audit save fails`() {
        doThrow(IllegalStateException("audit save failure"))
            .`when`(runtimeSettingAuditStore)
            .saveAll(anyAuditList())

        assertThrows(IllegalStateException::class.java) {
            runtimeSettingService.update(
                update = RuntimeSettingService.RuntimeSettingsUpdate(defaultHoursBack = 36),
                changedBy = "tx-test"
            )
        }

        assertNull(runtimeSettingStore.findByKey("default_hours_back"))
    }

    @Test
    fun `resetAll should rollback delete when audit save fails`() {
        runtimeSettingStore.save(RuntimeSetting(key = "default_hours_back", value = "24"))
        doThrow(IllegalStateException("audit save failure"))
            .`when`(runtimeSettingAuditStore)
            .saveAll(anyAuditList())

        assertThrows(IllegalStateException::class.java) {
            runtimeSettingService.resetAll(changedBy = "tx-test")
        }

        assertNotNull(runtimeSettingStore.findByKey("default_hours_back"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun anyAuditList(): List<RuntimeSettingAudit> =
        anyList<RuntimeSettingAudit>() as List<RuntimeSettingAudit>
}
