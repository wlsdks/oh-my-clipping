package com.clipping.mcpserver.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * CORS 설정.
 *
 * 허용 오리진은 환경변수 `CORS_ALLOWED_ORIGINS`로 쉼표 구분하여 지정한다.
 * `*`을 지정하면 모든 오리진을 허용한다 (테스트 환경용).
 * 미설정 시 로컬 개발 서버(localhost:3000, localhost:5173)만 허용한다.
 */
@Configuration
class WebConfig(
    @Value("\${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}")
    private val allowedOriginsRaw: String
) {

    @Bean
    fun corsConfigurer() = object : WebFluxConfigurer {
        override fun addCorsMappings(registry: CorsRegistry) {
            val origins = allowedOriginsRaw
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toTypedArray()

            // 모든 경로에 CORS 적용 (API, 로그인, 회원가입 등)
            val mapping = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .exposedHeaders("Location", "X-Request-Id")
                .maxAge(3600) // preflight 캐시 1시간

            if (origins.singleOrNull() == "*") {
                mapping.allowedOriginPatterns("*")
                    .allowCredentials(true)
            } else {
                mapping.allowedOrigins(*origins)
                    .allowCredentials(true)
            }
        }
    }
}
