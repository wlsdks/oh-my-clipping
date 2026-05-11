package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.ClassifyRequest
import com.ohmyclipping.service.KeywordEntityService
import com.ohmyclipping.service.dto.analytics.KeywordEntityItem
import com.ohmyclipping.service.dto.analytics.KeywordEntityResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 키워드 엔티티 분류 관리 컨트롤러.
 * 키워드를 엔티티 카테고리별로 분류 조회하거나 수동 등록한다.
 */
@RestController
@RequestMapping("/api/admin/keyword-entities")
class KeywordEntityAdminController(
    private val service: KeywordEntityService,
) {

    /** 기간 내 분류된 키워드 목록을 조회한다. */
    @GetMapping
    fun getClassified(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(required = false) categoryId: Long?,
    ): KeywordEntityResponse = service.getClassifiedKeywords(days, categoryId)

    /** 키워드를 수동으로 엔티티 분류에 등록한다. */
    @PostMapping
    fun classify(
        @RequestBody req: ClassifyRequest
    ): KeywordEntityItem =
        service.classifyKeyword(req.keyword, req.category)
}
