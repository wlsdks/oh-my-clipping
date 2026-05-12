package com.ohmyclipping.adapter.out

import com.ohmyclipping.config.SlaEscalationProperties
import com.ohmyclipping.content.ArticleContentExtractor
import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.service.OrganizationService
import com.ohmyclipping.service.port.CollectionArticleExtractorPort
import com.ohmyclipping.service.port.CollectionExtractedArticle
import com.ohmyclipping.service.port.CollectionRuntimeSettings
import com.ohmyclipping.service.port.CollectionRuntimeSettingsPort
import com.ohmyclipping.service.port.CollectionUrlSafetyPort
import com.ohmyclipping.service.port.SourceOrganization
import com.ohmyclipping.service.port.SourceOrganizationPort
import com.ohmyclipping.service.port.SourceSlaSettings
import com.ohmyclipping.service.port.SourceSlaSettingsPort
import com.ohmyclipping.service.port.SourceUrlSafetyPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI

/**
 * `ports/workflow` 모듈 포트들이 도메인 서비스에 닿도록 위임하는 root app 어댑터 모음.
 *
 * 모듈 재구성(ADR-040)으로 collection/source 서비스가 자체 모듈로 분리됐지만, 이들이 의존하는
 * URL 검증/콘텐츠 추출/조직 조회/SLA 설정 같은 인프라/도메인 서비스는 여전히 root app 에 있다.
 * 본 어댑터들은 cross-module wiring 을 끊지 않게 port 인터페이스에 root app 의 구현을 위임한다.
 *
 * OSS sanitization 시 이 어댑터 모음이 누락돼 656건의 `@SpringBootTest` 가 ApplicationContext 로딩에
 * 실패했다. 자세한 맥락은 ADR-044 참고.
 */

@Component
class CollectionRuntimeSettingsAdapter(
    @Value("\${clipping.collection.max-content-length:200000}") private val maxContentLength: Int,
) : CollectionRuntimeSettingsPort {

    override fun currentCollectionSettings(): CollectionRuntimeSettings =
        CollectionRuntimeSettings(maxContentLength = maxContentLength)
}

@Component
class CollectionArticleExtractorAdapter(
    private val articleContentExtractor: ArticleContentExtractor,
) : CollectionArticleExtractorPort {

    override fun extract(url: String): CollectionExtractedArticle? =
        articleContentExtractor.extract(url)?.let { extracted ->
            CollectionExtractedArticle(
                title = extracted.title,
                content = extracted.content,
                language = extracted.language.name,
            )
        }
}

@Component
class CollectionUrlSafetyAdapter(
    private val urlSafetyValidator: UrlSafetyValidator,
) : CollectionUrlSafetyPort {

    override fun validatePublicHttpUrl(rawUrl: String): URI =
        urlSafetyValidator.validatePublicHttpUrl(rawUrl)
}

@Component
class SourceUrlSafetyAdapter(
    private val urlSafetyValidator: UrlSafetyValidator,
) : SourceUrlSafetyPort {

    override fun validatePublicHttpUrl(rawUrl: String): URI =
        urlSafetyValidator.validatePublicHttpUrl(rawUrl)
}

@Component
class SourceSlaSettingsAdapter(
    private val slaProperties: SlaEscalationProperties,
) : SourceSlaSettingsPort {

    override fun currentSourceSlaSettings(): SourceSlaSettings =
        SourceSlaSettings(
            enabled = slaProperties.enabled,
            sourceRequestStaleDays = slaProperties.sourceRequestStaleDays,
        )
}

@Component
class SourceOrganizationAdapter(
    private val organizationService: OrganizationService,
) : SourceOrganizationPort {

    override fun findSourceOrganizationsByCategoryId(categoryId: String): List<SourceOrganization> =
        organizationService.findByCategoryId(categoryId).map { org ->
            SourceOrganization(name = org.name)
        }
}
