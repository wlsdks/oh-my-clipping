package com.ohmyclipping

import com.ohmyclipping.config.AppProperties
import com.ohmyclipping.config.ClippingFeatureFlags
import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.config.DartProperties
import com.ohmyclipping.config.NaverProperties
import com.ohmyclipping.config.SecurityProperties
import com.ohmyclipping.config.SlaEscalationProperties
import com.ohmyclipping.config.SlackProperties
import com.ohmyclipping.service.analytics.AnalyticsGrowthProperties
import com.ohmyclipping.service.analytics.AnalyticsRiskProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableConfigurationProperties(
    AppProperties::class,
    ClippingFeatureFlags::class,
    ClippingMcpServerProperties::class,
    DartProperties::class,
    NaverProperties::class,
    SecurityProperties::class,
    SlaEscalationProperties::class,
    SlackProperties::class,
    AnalyticsRiskProperties::class,
    AnalyticsGrowthProperties::class
)
class ClippingMcpServerApplication

fun main(args: Array<String>) {
    runApplication<ClippingMcpServerApplication>(*args)
}
