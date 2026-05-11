package com.ohmyclipping.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.flywaydb.core.api.MigrationInfo
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.callback.Context
import org.flywaydb.core.api.callback.Event
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class FlywayMigrationFailureLoggerTest {

    private val logger = FlywayMigrationFailureLogger()
    private val slf4jLogger = LoggerFactory.getLogger(FlywayMigrationFailureLogger::class.java) as LogbackLogger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.list.clear()
        appender.start()
        slf4jLogger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        slf4jLogger.detachAppender(appender)
    }

    private fun contextFor(version: String, description: String, script: String): Context {
        val info = mockk<MigrationInfo>()
        every { info.version } returns MigrationVersion.fromVersion(version)
        every { info.description } returns description
        every { info.script } returns script

        val context = mockk<Context>()
        every { context.migrationInfo } returns info
        return context
    }

    @Nested
    inner class `supports 메서드` {

        @Test
        fun `AFTER_MIGRATE_ERROR 이벤트를 처리한다`() {
            logger.supports(Event.AFTER_MIGRATE_ERROR, null) shouldBe true
        }

        @Test
        fun `AFTER_EACH_MIGRATE_ERROR 이벤트를 처리한다`() {
            logger.supports(Event.AFTER_EACH_MIGRATE_ERROR, null) shouldBe true
        }

        @Test
        fun `AFTER_EACH_MIGRATE 성공 이벤트도 처리한다 (debug 용)`() {
            logger.supports(Event.AFTER_EACH_MIGRATE, null) shouldBe true
        }

        @Test
        fun `BEFORE_MIGRATE 같은 관심없는 이벤트는 처리하지 않는다`() {
            logger.supports(Event.BEFORE_MIGRATE, null) shouldBe false
        }
    }

    @Nested
    inner class `handle 메서드` {

        @Test
        fun `AFTER_MIGRATE_ERROR 이벤트에서 ERROR 레벨로 version 과 description 을 기록한다`() {
            val context = contextFor("125", "add_user_index", "V125__add_user_index.sql")

            logger.handle(Event.AFTER_MIGRATE_ERROR, context)

            val event = appender.list.firstOrNull { it.level == Level.ERROR }
            event shouldNotBe null
            event!!.formattedMessage shouldContain "V125"
            event.formattedMessage shouldContain "add_user_index"
            event.formattedMessage shouldContain "V125__add_user_index.sql"
        }

        @Test
        fun `AFTER_EACH_MIGRATE_ERROR 이벤트에서 ERROR 레벨로 기록하고 운영 안내 메시지를 포함한다`() {
            val context = contextFor("126", "backfill_orders", "V126__backfill_orders.sql")

            logger.handle(Event.AFTER_EACH_MIGRATE_ERROR, context)

            val event = appender.list.firstOrNull { it.level == Level.ERROR }
            event shouldNotBe null
            event!!.formattedMessage shouldContain "V126"
            event.formattedMessage shouldContain "flyway repair"
        }

        @Test
        fun `AFTER_EACH_MIGRATE 성공 이벤트는 DEBUG 레벨로만 기록한다`() {
            val context = contextFor("127", "add_column", "V127__add_column.sql")

            logger.handle(Event.AFTER_EACH_MIGRATE, context)

            appender.list.none { it.level == Level.ERROR } shouldBe true
            appender.list.none { it.level == Level.WARN } shouldBe true
        }

        @Test
        fun `context 가 null 이어도 안전하게 처리한다`() {
            logger.handle(Event.AFTER_MIGRATE_ERROR, null)

            val event = appender.list.firstOrNull { it.level == Level.ERROR }
            event shouldNotBe null
            event!!.formattedMessage shouldContain "V?"
        }
    }

    @Test
    fun `canHandleInTransaction 은 true 를 반환한다`() {
        logger.canHandleInTransaction(Event.AFTER_MIGRATE_ERROR, null) shouldBe true
    }

    @Test
    fun `getCallbackName 은 클래스 식별자를 반환한다`() {
        logger.callbackName shouldBe "FlywayMigrationFailureLogger"
    }
}
