package com.ohmyclipping.content

import com.ohmyclipping.model.Language

data class ExtractedArticle(
    val title: String,
    val content: String,
    val language: Language
)

interface ArticleContentExtractor {
    fun extract(url: String): ExtractedArticle?
}
