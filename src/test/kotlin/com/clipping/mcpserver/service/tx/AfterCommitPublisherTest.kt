package com.clipping.mcpserver.service.tx

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
class AfterCommitPublisherTest {

    @Test
    fun `TX 밖에서 호출 시 즉시 실행된다`() {
        var executed = false
        publishAfterCommit { executed = true }
        assertThat(executed).isTrue
    }

    @Test
    @Transactional
    fun `TX 내에서 호출 시 TX 종료 전에는 실행되지 않는다`() {
        var executed = false
        publishAfterCommit { executed = true }
        // @Transactional 래퍼가 테스트 메서드 종료 후 TX를 종료하므로,
        // 이 시점에서는 아직 afterCommit 콜백이 실행되지 않아야 한다.
        assertThat(executed).isFalse
    }
}
