package com.ohmyclipping.service

import com.ohmyclipping.analytics.SourceQualityQueryHelper
import com.ohmyclipping.service.dto.admin.ContentLeversSummary
import org.springframework.stereotype.Service
import java.time.Instant

/** 콘텐츠 레버 대시보드 (레버 3 소스 품질) 집계. */
@Service
class AnalyticsContentLeversService(
    private val source: SourceQualityQueryHelper,
) {

    fun summary(from: Instant, to: Instant): ContentLeversSummary {
        // 소스 품질 단일 집계를 응답으로 래핑
        return ContentLeversSummary(
            sourceQuality = source.sourceQuality(from, to),
        )
    }
}
