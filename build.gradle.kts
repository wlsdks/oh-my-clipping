plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    kotlin("plugin.jpa") version "2.3.0"
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
    // NOTE: Detekt plugin 은 현재 stable 1.23.8 이 Kotlin 2.0.21 까지만 지원하여 이 프로젝트의
    // Kotlin 2.3.0 과 호환되지 않는다. Detekt 2.0 stable 릴리스 이후 재도입한다.
    // 대체 enforcement: 아래 checkPostgresSpecificSql gradle task + AGENTS.md §1.3 코드 리뷰 체크.
}

group = "com.ohmyclipping"
version = "2.0.0"
description = "oh-my-clipping — an open-source news clipping agent. RSS collection, AI summarisation, and Slack delivery, with an admin UI and an MCP server interface."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.4")
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:error-types"))
    implementation(project(":core:api-models"))
    implementation(project(":ports:workflow"))
    implementation(project(":ports:persistence"))
    implementation(project(":adapters:persistence"))
    implementation(project(":adapters:notification"))
    implementation(project(":modules:admin"))
    implementation(project(":modules:digest-policy"))
    implementation(project(":modules:collection"))
    implementation(project(":modules:source"))
    implementation(project(":modules:digest"))
    implementation(project(":modules:user"))
    implementation(project(":modules:analytics"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring AI MCP Server (SSE via WebFlux)
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webflux")
    runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-aarch_64")

    // Spring AI - Google Gemini
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")

    // RSS parsing
    implementation("com.rometools:rome:2.1.0")
    implementation("org.jsoup:jsoup:1.19.1")

    // Redis (rate limit, notification dedup 영속화)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // Redis 기반 세션 저장소 (SESSION_STORE_TYPE=redis 시 활성화)
    implementation("org.springframework.session:spring-session-data-redis")

    // Kotlin JDSL (타입 안전 Kotlin 쿼리 DSL)
    implementation("com.linecorp.kotlin-jdsl:jpql-dsl:3.5.4")
    implementation("com.linecorp.kotlin-jdsl:jpql-render:3.5.4")
    implementation("com.linecorp.kotlin-jdsl:spring-data-jpa-support:3.5.4")

    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    // 운영 환경 JSON 구조화 로깅 (logback-spring.xml에서 production 프로파일에 활성화)
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // OpenAPI (Swagger UI)
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.5")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("io.mockk:mockk:1.14.5")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testRuntimeOnly("com.h2database:h2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

val frontendDir = layout.projectDirectory.dir("frontend")
val skipFrontendBuild = providers.gradleProperty("skipFrontendBuild")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

fun pnpmCommand(): String =
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "pnpm.cmd" else "pnpm"

fun mainKotlinSourceRoots(): List<File> =
    listOf(layout.projectDirectory.dir("src/main/kotlin").asFile) +
        subprojects.map { it.layout.projectDirectory.dir("src/main/kotlin").asFile }

val installFrontend by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Install dependencies for admin SPA."
    onlyIf { !skipFrontendBuild.get() }
    workingDir(frontendDir)
    inputs.file(frontendDir.file("package.json"))
    inputs.file(frontendDir.file("pnpm-lock.yaml"))
    outputs.dir(frontendDir.dir("node_modules"))
    commandLine(pnpmCommand(), "install", "--frozen-lockfile")
}

val buildFrontend by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Build admin SPA into Spring static resources."
    dependsOn(installFrontend)
    onlyIf { !skipFrontendBuild.get() }
    workingDir(frontendDir)
    inputs.files(
        frontendDir.file("package.json"),
        frontendDir.file("pnpm-lock.yaml"),
        frontendDir.file("vite.config.ts"),
        frontendDir.file("tsconfig.json"),
        frontendDir.file("tsconfig.node.json"),
        frontendDir.file("index.html")
    )
    inputs.dir(frontendDir.dir("src"))
    outputs.dir(layout.projectDirectory.dir("src/main/resources/static"))
    commandLine(pnpmCommand(), "run", "build")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dependsOn(buildFrontend)
    if (System.getProperty("spring.profiles.active").isNullOrBlank() && System.getenv("SPRING_PROFILES_ACTIVE").isNullOrBlank()) {
        systemProperty("spring.profiles.active", "local")
    }
}

tasks.named("bootJar") {
    dependsOn(buildFrontend)
}

tasks.named("build") {
    dependsOn(buildFrontend)
}

tasks.named("processResources") {
    mustRunAfter(buildFrontend)
}

tasks.withType<Test> {
    useJUnitPlatform {
        excludeTags("stress")
    }
    // 전체 테스트 스위트(@SpringBootTest 다수 포함)가 메모리 압박을 받지 않도록 충분한 힙을 보장한다.
    maxHeapSize = "3g"
    jvmArgs("-XX:MaxMetaspaceSize=1g", "-XX:+HeapDumpOnOutOfMemoryError")
    // 외부 환경변수가 테스트 DB/Redis 설정을 오염시키지 않도록 시스템 프로퍼티로 강제 지정한다.
    systemProperty("spring.profiles.active", "test")
    systemProperty("spring.datasource.url", "jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
    systemProperty("spring.datasource.username", "sa")
    systemProperty("spring.datasource.password", "")
    systemProperty("spring.datasource.driver-class-name", "org.h2.Driver")
    // H2 테스트 환경에선 PostgreSQL 전용 마이그레이션(db/migration-pg)은 제외한다.
    // application.yml 의 기본값(db/migration,db/migration-pg)을 시스템 프로퍼티로 override.
    systemProperty("spring.flyway.locations", "classpath:db/migration")
    systemProperty("spring.ai.google.genai.api-key", "test-key")
}

tasks.register<Test>("stressTest") {
    group = "verification"
    description = "실제 외부 API를 호출할 수 있는 stress 태그 테스트를 실행한다."
    useJUnitPlatform {
        includeTags("stress")
    }
    maxHeapSize = "3g"
    jvmArgs("-XX:MaxMetaspaceSize=1g", "-XX:+HeapDumpOnOutOfMemoryError")
    systemProperty("spring.profiles.active", "test")
    systemProperty("spring.datasource.url", "jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
    systemProperty("spring.datasource.username", "sa")
    systemProperty("spring.datasource.password", "")
    systemProperty("spring.datasource.driver-class-name", "org.h2.Driver")
    systemProperty("spring.flyway.locations", "classpath:db/migration")
    System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("spring.ai.google.genai.api-key", it)
    }
    shouldRunAfter(tasks.test)
}

// --------------------------------------------------------------------------------------------
// PostgreSQL 전용 SQL 스캐너 — AGENTS.md §1.5 "DB 방언 누수 금지" 규칙을 소스 레벨에서 검사
//
// 배경:
//   - 운영 DB 는 PostgreSQL, 테스트 DB 는 H2(MODE=PostgreSQL) 이다.
//   - H2 가 지원하지 않는 PG 전용 문법(INTERVAL 리터럴, ON CONFLICT DO UPDATE, ::jsonb 캐스트)
//     이 소스에 섞이면 프로덕션에서만 동작하거나 테스트가 우회되어 회귀가 누락된다.
//
// Detekt 를 써서 처리하는 것이 이상적이지만 현재 Detekt 1.23.x 는 Kotlin 2.0.21 까지만 지원한다.
// 이 프로젝트의 Kotlin 2.3.0 과 호환되는 stable Detekt 가 나올 때까지, grep 기반 gradle task 로
// 최소한의 벤더 누수 방지 게이트를 유지한다.
// --------------------------------------------------------------------------------------------
tasks.register("checkPostgresSpecificSql") {
    group = "verification"
    description = "Kotlin 소스에서 H2 가 지원하지 않는 PostgreSQL 전용 SQL 패턴을 스캔한다."
    val sourceRoots = mainKotlinSourceRoots()
    inputs.files(sourceRoots)
    outputs.upToDateWhen { false } // 항상 실행 (매우 가벼움)

    doLast {
        // INTERVAL 리터럴: H2 MODE=PostgreSQL 에서 파싱 실패 → Java 산술로 대체해야 함
        val intervalPattern = Regex("""INTERVAL\s+'\d+[^']*'""", RegexOption.IGNORE_CASE)
        // ON CONFLICT (col) DO UPDATE: H2 는 컬럼 없는 ON CONFLICT DO NOTHING 만 지원
        val onConflictUpsertPattern = Regex(
            """ON\s+CONFLICT\s*\([^)]+\)\s*DO\s+UPDATE""",
            RegexOption.IGNORE_CASE,
        )
        // jsonb 캐스트: PostgreSQL 전용 타입. H2 에서는 문자열로 취급되어 동작 불일치
        val jsonbCastPattern = Regex("""::jsonb\b""", RegexOption.IGNORE_CASE)

        val patterns = listOf(
            "INTERVAL literal" to intervalPattern,
            "ON CONFLICT DO UPDATE" to onConflictUpsertPattern,
            "::jsonb cast" to jsonbCastPattern,
        )

        // Kotlin 소스의 주석(// 라인 주석, /* */ 블록 주석, KDoc)을 제거한 뒤 패턴을 검사한다.
        // 주석 내부의 설명용 SQL 인용구(예: "H2 에선 INTERVAL '180 days' 불가")에 걸리면 false positive 가 된다.
        val lineCommentPattern = Regex("""//[^\n]*""")
        val blockCommentPattern = Regex("""/\*[\s\S]*?\*/""")

        val violations = mutableListOf<String>()
        sourceRoots
            .filter { it.exists() }
            .forEach { sourceRoot ->
                sourceRoot.walk()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        val text = file.readText()
                        // 라인 번호 정합성을 위해 주석을 같은 길이의 공백(개행은 유지)으로 치환한다.
                        val stripped = text
                            .replace(blockCommentPattern) { match ->
                                match.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
                            }
                            .replace(lineCommentPattern) { match ->
                                " ".repeat(match.value.length)
                            }
                        patterns.forEach { (label, pattern) ->
                            pattern.findAll(stripped).forEach { match ->
                                val line = stripped.substring(0, match.range.first).count { it == '\n' } + 1
                                val rel = file.relativeTo(projectDir).path
                                violations += "$rel:$line — PG-only SQL ($label): ${match.value}"
                            }
                        }
                    }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            throw GradleException(
                "${violations.size} PostgreSQL-only SQL pattern(s) detected. " +
                    "Replace with vendor-neutral SQL or Java arithmetic (see AGENTS.md §1.5).",
            )
        } else {
            logger.lifecycle("checkPostgresSpecificSql: OK (no PG-only SQL patterns found)")
        }
    }
}

// --------------------------------------------------------------------------------------------
// broad catch baseline scanner — AGENTS.md §1.3 "광범위 catch 최소화" 규칙을 회귀 방지 게이트로 유지
//
// 현재 코드에는 외부 API, 필터, 캐시, 스케줄러 boundary 보호막 성격의 catch (Exception) 이 남아 있다.
// 즉시 전부 제거하면 운영 흐름이 바뀔 수 있으므로, baseline 이하로만 허용해 새 broad catch 유입을 막고
// 추후 배치에서 파일별 count 를 줄일 때 baseline 을 함께 낮춘다.
// --------------------------------------------------------------------------------------------
tasks.register("checkBroadExceptionBaseline") {
    group = "verification"
    description = "Kotlin 소스의 catch (Exception) 사용량이 baseline보다 늘지 않았는지 검사한다."
    val sourceRoots = mainKotlinSourceRoots()
    val baselineFile = layout.projectDirectory.file("config/quality/broad-exception-baseline.txt").asFile
    inputs.files(sourceRoots)
    inputs.file(baselineFile)
    outputs.upToDateWhen { false }

    doLast {
        if (!baselineFile.exists()) {
            throw GradleException("Missing broad catch baseline: ${baselineFile.relativeTo(projectDir).path}")
        }

        val broadCatchPattern = Regex(
            """catch\s*\(\s*(?:e|_|ex)\s*:\s*(?:java\.lang\.)?Exception\s*\)""",
        )
        val baseline = baselineFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val parts = line.split("=", limit = 2)
                require(parts.size == 2) { "Invalid broad exception baseline line: $line" }
                parts[0].trim() to parts[1].trim().toInt()
            }

        val actual = mutableMapOf<String, Int>()
        sourceRoots
            .filter { it.exists() }
            .forEach { root ->
                root.walk()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        val count = broadCatchPattern.findAll(file.readText()).count()
                        if (count > 0) {
                            actual[file.relativeTo(projectDir).path] = count
                        }
                    }
            }

        val violations = mutableListOf<String>()
        actual.forEach { (path, count) ->
            val allowed = baseline[path]
            when {
                allowed == null -> violations += "$path — new catch(Exception) usage: $count"
                count > allowed -> violations += "$path — catch(Exception) count increased: $allowed -> $count"
            }
        }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            throw GradleException(
                "${violations.size} broad exception baseline violation(s) detected. " +
                    "Narrow the catch or intentionally update config/quality/broad-exception-baseline.txt.",
            )
        }

        val baselineTotal = baseline.values.sum()
        val actualTotal = actual.values.sum()
        val reducedFiles = baseline
            .filter { (path, allowed) -> (actual[path] ?: 0) < allowed }
            .map { (path, allowed) -> "$path: $allowed -> ${actual[path] ?: 0}" }
        reducedFiles.forEach { logger.lifecycle("checkBroadExceptionBaseline: reduced $it") }
        logger.lifecycle("checkBroadExceptionBaseline: OK ($actualTotal/$baselineTotal catch(Exception) usages)")
    }
}

// check 수명 주기에 정적 분석을 포함시켜 ./gradlew check 한 번으로 일괄 검증되도록 한다.
tasks.named("check") {
    dependsOn(
        "checkPostgresSpecificSql",
        "checkBroadExceptionBaseline",
        ":core:api-models:checkApiModelBoundaries",
        ":core:domain:checkDomainBoundaries",
        ":core:error-types:checkErrorTypeBoundaries",
        ":ports:workflow:checkAppPortBoundaries",
        ":ports:persistence:checkStoreSpiBoundaries",
        ":adapters:persistence:checkPersistenceBoundaries",
        ":adapters:notification:checkNotificationBoundaries",
        ":modules:admin:checkAdminModelBoundaries",
        ":modules:digest-policy:checkEngineBoundaries",
        ":modules:collection:checkCollectionBoundaries",
        ":modules:source:checkSourceBoundaries",
        ":modules:digest:checkDigestApplicationBoundaries",
        ":modules:user:checkUserApplicationBoundaries",
        ":modules:analytics:checkAnalyticsApplicationBoundaries",
    )
}
