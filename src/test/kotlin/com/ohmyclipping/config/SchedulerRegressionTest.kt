package com.ohmyclipping.config

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.test.context.ActiveProfiles

/**
 * SchedulingEnablerConfig(matchIfMissing=true) 의 회귀 잠금 테스트.
 * application-test.yml 은 scheduler.enabled=false 로 설정하므로
 * 이 테스트에서만 명시적으로 true 로 덮어 써 스케줄러 빈 등록을 검증한다.
 */
@SpringBootTest(properties = ["clipping.scheduler.enabled=true"])
@ActiveProfiles("test")
class SchedulerRegressionTest {

    @Autowired(required = false)
    var scheduledTaskHolder: ScheduledTaskHolder? = null

    @Autowired
    lateinit var ctx: ApplicationContext

    @Test
    fun `기본 상태에서 ScheduledTaskHolder가 존재`() {
        scheduledTaskHolder shouldNotBe null
    }

    @Test
    fun `기본 상태에서 scheduled task가 1개 이상 등록`() {
        scheduledTaskHolder!!.scheduledTasks.shouldNotBeEmpty()
    }
}
