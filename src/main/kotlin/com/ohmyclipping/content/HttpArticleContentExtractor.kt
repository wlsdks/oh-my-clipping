package com.ohmyclipping.content

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.model.Language
import com.ohmyclipping.security.UrlSafetyValidator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.HttpURLConnection

private val log = KotlinLogging.logger {}
private val KOREAN_PATTERN = Regex("[가-힣]")

@Component
class HttpArticleContentExtractor(
    private val properties: ClippingMcpServerProperties,
    private val urlSafetyValidator: UrlSafetyValidator
) : ArticleContentExtractor {

    override fun extract(url: String): ExtractedArticle? {
        return try {
            val safeUri = urlSafetyValidator.validatePublicHttpUrl(url)
            val safeUrl = safeUri.toString()
            val conn = safeUri.toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = properties.pageConnectionTimeoutMs
            conn.readTimeout = properties.pageReadTimeoutMs
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "ClippingBot/1.0")

            if (conn.responseCode !in 200..299) return null
            val contentType = conn.contentType?.lowercase().orEmpty()
            if (!contentType.contains("text/html") && !contentType.contains("application/xhtml+xml")) return null

            val html = conn.inputStream.bufferedReader().use { it.readText() }
            parseHtml(safeUrl, html)
        } catch (e: IOException) {
            log.debug(e) { "Failed to extract article content from $url" }
            null
        } catch (e: IllegalArgumentException) {
            log.debug(e) { "Failed to extract article content from $url" }
            null
        } catch (e: SecurityException) {
            log.debug(e) { "Failed to extract article content from $url" }
            null
        }
    }

    internal fun parseHtml(baseUrl: String, html: String): ExtractedArticle? {
        val doc = Jsoup.parse(html, baseUrl)
        doc.select("script,style,noscript,svg,canvas,iframe").remove()

        val title = doc.title().ifBlank {
            doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        }.ifBlank {
            doc.selectFirst("h1")?.text().orEmpty()
        }.ifBlank {
            "Untitled"
        }.trim()

        val text = sequenceOf("article", "main", "body")
            .mapNotNull { selector -> doc.selectFirst(selector)?.text() }
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(properties.maxExtractedContentLength)
            ?: return null

        return ExtractedArticle(
            title = title,
            content = text,
            language = detectLanguage("$title $text")
        )
    }

    private fun detectLanguage(text: String): Language {
        val koreanCount = KOREAN_PATTERN.findAll(text).count()
        return if (koreanCount > 5) Language.KOREAN else Language.FOREIGN
    }
}
