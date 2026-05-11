package com.ohmyclipping.service

import com.ohmyclipping.service.digest.*

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Digest 모듈의 모든 일자 계산이 KST 명시 ZoneId 를 사용하는지 검증하는 회귀 가드.
 *
 * 과거 DigestService.kt:275, 426, 846 에서 system 기본 timezone 에 의존하는
 * `LocalDate.now()` 가 사용돼 비-KST JVM 에서 잘못된 발송일을 기록하는 버그가 있었다
 * (페르소나 분석 Slice 1 사전 검증에서 발견).
 *
 * 본 테스트는 소스 파일 정적 검사 형태의 sentinel 로, 같은 버그가 재발하면 즉시 실패한다.
 * DigestService 가 DigestRenderer 로 분할된 뒤에도 양쪽 모두 KST-aware 여야 하므로
 * 두 소스 파일을 함께 검사한다.
 */
class DigestServiceTimezoneRegressionTest {

    private val digestModuleSources = listOf(
        "src/main/kotlin/com/ohmyclipping/service/digest/DigestService.kt",
        "src/main/kotlin/com/ohmyclipping/service/digest/DigestRenderer.kt",
        "src/main/kotlin/com/ohmyclipping/service/digest/DigestSelectionService.kt",
    )

    @Test
    fun `Digest 모듈 소스에는 인자 없는 LocalDate now 가 등장해서는 안 된다`() {
        // 인자 없는 `LocalDate.now()` 호출은 system 기본 timezone 에 의존하므로 금지.
        val forbiddenPattern = Regex("""\bLocalDate\.now\s*\(\s*\)""")
        val violations = digestModuleSources
            .map(Paths::get)
            .filter(Files::exists)
            .flatMap { path ->
                val source = Files.readString(path)
                forbiddenPattern.findAll(source).map { path.toString() to it.range.first }
            }

        assertThat(violations)
            .withFailMessage(
                "Digest 모듈에서 인자 없는 LocalDate.now() 호출이 ${violations.size}건 발견됐습니다: $violations. " +
                    "모든 일자 계산은 ZonedDateTime.now(ZoneId.of(\"Asia/Seoul\")).toLocalDate() 또는 " +
                    "동등한 KST-aware 호출을 사용해야 합니다."
            )
            .isEmpty()
    }

    @Test
    fun `Digest 모듈에서 KST ZoneId 가 최소 한 번은 참조돼야 한다`() {
        val existingSources = digestModuleSources
            .map(Paths::get)
            .filter(Files::exists)

        assertThat(existingSources)
            .withFailMessage("DigestService.kt 는 반드시 존재해야 합니다")
            .isNotEmpty

        val joined = existingSources.joinToString("\n") { Files.readString(it) }

        assertThat(joined)
            .withFailMessage(
                "Digest 모듈에서 java.time.ZoneId import 가 없습니다. " +
                    "KST(Asia/Seoul) 경계로 일자 계산을 하려면 ZoneId 를 참조해야 합니다."
            )
            .contains("import java.time.ZoneId")

        assertThat(joined)
            .withFailMessage(
                "Digest 모듈에서 ZoneId.of(\"Asia/Seoul\") 사용이 없습니다. " +
                    "시스템 기본 timezone 대신 반드시 KST 를 명시해야 합니다."
            )
            .contains("ZoneId.of(\"Asia/Seoul\")")
    }
}
