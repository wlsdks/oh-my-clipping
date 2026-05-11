package com.ohmyclipping.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 시간 의존성을 주입하기 위한 Clock 빈.
 * 테스트에서 Clock.fixed로 교체 가능하게 하기 위함이다.
 */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}
