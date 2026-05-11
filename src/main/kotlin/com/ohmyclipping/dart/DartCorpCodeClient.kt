package com.ohmyclipping.dart

import com.ohmyclipping.config.DartProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

/**
 * DART corpCode.xml API 클라이언트.
 * ZIP 파일을 다운로드하고 내부 XML을 파싱하여 기업 목록을 반환한다.
 */
@Component
class DartCorpCodeClient(
    private val dartProperties: DartProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * DART API에서 전체 기업 코드 목록을 다운로드한다.
     * API 키가 비어있으면 빈 리스트를 반환한다.
     *
     * @return 상장/비상장 포함 전체 기업 리스트
     */
    fun fetchAllCompanies(): List<DartCompany> {
        if (dartProperties.apiKey.isBlank()) {
            log.warn("DART API 키가 설정되지 않아 기업 코드 다운로드를 건너뜁니다.")
            return emptyList()
        }

        val url = "${dartProperties.corpCodeUrl}?crtfc_key=${dartProperties.apiKey}"
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = dartProperties.connectTimeoutMs
        conn.readTimeout = dartProperties.readTimeoutMs
        conn.requestMethod = "GET"

        try {
            val status = conn.responseCode
            if (status != 200) {
                log.error("DART corpCode API 응답 오류: status={}", status)
                return emptyList()
            }

            // ZIP 스트림에서 첫 번째 엔트리(CORPCODE.xml)를 파싱
            val companies = mutableListOf<DartCompany>()
            ZipInputStream(BufferedInputStream(conn.inputStream)).use { zis ->
                val entry = zis.nextEntry
                if (entry != null) {
                    val factory = SAXParserFactory.newInstance()
                    val parser = factory.newSAXParser()
                    parser.parse(zis, CorpCodeSaxHandler(companies))
                }
            }

            log.info("DART 기업 코드 다운로드 완료: 총 {}건", companies.size)
            return companies
        } catch (e: IOException) {
            log.error("DART 기업 코드 다운로드 실패: {}", e.message, e)
            return emptyList()
        } catch (e: ParserConfigurationException) {
            log.error("DART 기업 코드 다운로드 실패: {}", e.message, e)
            return emptyList()
        } catch (e: SAXException) {
            log.error("DART 기업 코드 다운로드 실패: {}", e.message, e)
            return emptyList()
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * CORPCODE.xml SAX 파서 핸들러.
 * <list> 하위 각 <list> 요소에서 corp_code, corp_name, stock_code를 추출한다.
 */
private class CorpCodeSaxHandler(
    private val result: MutableList<DartCompany>
) : DefaultHandler() {

    private var inList = false
    private var currentTag = ""
    private var corpCode = ""
    private var corpName = ""
    private var stockCode = ""
    private val textBuffer = StringBuilder()

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        val tag = qName.lowercase()
        if (tag == "list") {
            inList = true
            corpCode = ""
            corpName = ""
            stockCode = ""
        }
        currentTag = tag
        textBuffer.setLength(0)
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (inList) {
            textBuffer.append(ch, start, length)
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        val tag = qName.lowercase()
        if (inList) {
            val text = textBuffer.toString().trim()
            when (tag) {
                "corp_code" -> corpCode = text
                "corp_name" -> corpName = text
                "stock_code" -> stockCode = text
                "list" -> {
                    // 기업 코드와 이름이 있는 항목만 추가
                    if (corpCode.isNotBlank() && corpName.isNotBlank()) {
                        result.add(DartCompany(corpCode, corpName, stockCode))
                    }
                    inList = false
                }
            }
        }
        textBuffer.setLength(0)
    }
}
