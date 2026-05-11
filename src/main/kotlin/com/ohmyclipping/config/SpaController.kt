package com.ohmyclipping.config

import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

// SPA fallback: /, /admin/**, /user/** 경로를 index.html 로 포워딩
@RestController
class SpaController {

    private val indexHtml: String by lazy {
        ClassPathResource("static/index.html").inputStream.bufferedReader(Charsets.UTF_8).readText()
    }

    @GetMapping(value = ["/", "/login", "/signup", "/admin/**", "/user/**"], produces = [MediaType.TEXT_HTML_VALUE])
    fun spa(): Mono<String> = Mono.just(indexHtml)
}
