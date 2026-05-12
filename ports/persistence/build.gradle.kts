plugins {
    kotlin("jvm") version "2.3.0"
}

// `:ports:persistence` 와 `:adapters:persistence` 가 default Gradle capability
// (`{group}:{lastPathSegment}:{version}` = `com.ohmyclipping:persistence:2.0.0`) 를 공유하면
// 의존성 substitution 으로 `:adapters:persistence` 가 자기 자신을 참조해 순환 task graph 가
// 발생한다. group 을 distinct 하게 잡아 capability 충돌을 끊는다.
// 운영 publishing 설정이 없으므로 group prefix 변경의 외부 영향은 없다.
group = "com.ohmyclipping.ports"
version = "2.0.0"

base {
    archivesName.set("store-spi")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:api-models"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

tasks.register("checkStoreSpiBoundaries") {
    group = "verification"
    description = "Ensure store SPI module stays free of framework and persistence implementation dependencies."
    val sourceRoot = layout.projectDirectory.dir("src/main/kotlin").asFile
    inputs.dir(sourceRoot)
    outputs.upToDateWhen { false }

    doLast {
        val forbiddenImports = listOf(
            Regex("""import\s+org\.springframework\.""") to "Spring import",
            Regex("""import\s+jakarta\.persistence\.""") to "JPA import",
            Regex("""import\s+com\.ohmyclipping\.entity\.""") to "entity import",
            Regex("""import\s+com\.ohmyclipping\.repository\.""") to "repository import",
            Regex("""import\s+com\.ohmyclipping\.config\.""") to "app config import",
            Regex("""import\s+com\.ohmyclipping\.admin\.""") to "admin adapter import",
            Regex("""import\s+com\.ohmyclipping\.user\.""") to "user adapter import",
            Regex("""import\s+com\.ohmyclipping\.adapter\.""") to "external adapter import",
            Regex("""import\s+com\.ohmyclipping\.rss\.""") to "RSS adapter import",
            Regex("""import\s+com\.ohmyclipping\.ai\.""") to "AI adapter import",
        )

        val violations = mutableListOf<String>()
        sourceRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relativePath = file.relativeTo(sourceRoot).path
                val source = file.readText()
                forbiddenImports.forEach { (pattern, label) ->
                    pattern.findAll(source).forEach { match ->
                        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                        violations += "$relativePath:$line — forbidden store SPI dependency ($label)"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            throw GradleException(
                "${violations.size} store SPI boundary violation(s) detected. " +
                    "Keep store SPIs free of Spring/JPA/entity/repository/adapter dependencies.",
            )
        }

        logger.lifecycle("checkStoreSpiBoundaries: OK (store SPIs have no forbidden framework/persistence imports)")
    }
}

tasks.named("check") {
    dependsOn("checkStoreSpiBoundaries")
}
