package com.clipping.mcpserver

import com.clipping.mcpserver.config.AppProperties
import com.clipping.mcpserver.config.ClippingFeatureFlags
import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.config.DartProperties
import com.clipping.mcpserver.config.NaverProperties
import com.clipping.mcpserver.config.SecurityProperties
import com.clipping.mcpserver.config.SlaEscalationProperties
import com.clipping.mcpserver.config.SlackProperties
import com.clipping.mcpserver.service.analytics.AnalyticsGrowthProperties
import com.clipping.mcpserver.service.analytics.AnalyticsRiskProperties
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
