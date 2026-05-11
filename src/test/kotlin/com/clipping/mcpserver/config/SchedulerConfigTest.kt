package com.clipping.mcpserver.config

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SchedulerConfigTest {

    private val config = SchedulerConfig()

    @Nested
    inner class `기본 스케줄러(sched-)` {

        @Test
        fun `풀 사이즈가 30으로 설정된다`() {
            val scheduler = config.taskScheduler()
            scheduler.poolSize shouldBe 30
        }

        @Test
        fun `스레드 이름 접두사가 sched-이다`() {
            val scheduler = config.taskScheduler()
            scheduler.threadNamePrefix shouldBe "sched-"
        }
    }

    @Nested
    inner class `다이제스트 전용 스케줄러(digest-)` {

        @Test
        fun `풀 사이즈가 5로 설정된다`() {
            val scheduler = config.digestTaskScheduler()
            scheduler.poolSize shouldBe 5
        }

        @Test
        fun `스레드 이름 접두사가 digest-이다`() {
            val scheduler = config.digestTaskScheduler()
            scheduler.threadNamePrefix shouldBe "digest-"
        }
    }

    @Nested
    inner class `풀 분리 검증` {

        @Test
        fun `기본 스케줄러와 다이제스트 스케줄러는 서로 다른 인스턴스이다`() {
            val defaultScheduler = config.taskScheduler()
            val digestScheduler = config.digestTaskScheduler()
            defaultScheduler shouldNotBe digestScheduler
        }

        @Test
        fun `총 스레드 수는 35개이다 (기본 30 + 다이제스트 5)`() {
            val total = config.taskScheduler().poolSize + config.digestTaskScheduler().poolSize
            total shouldBe 35
        }

        @Test
        fun `상수값이 풀 사이즈와 일치한다`() {
            SchedulerConfig.DEFAULT_POOL_SIZE shouldBe 30
            SchedulerConfig.DIGEST_POOL_SIZE shouldBe 5
        }
    }
}
