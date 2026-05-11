plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    kotlin("plugin.jpa") version "2.3.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ohmyclipping"
version = "2.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.9")
    }
}

dependencies {
    implementation(project(":core:api-models"))
    implementation(project(":core:domain"))
    implementation(project(":core:error-types"))
    implementation(project(":core:pipeline-models"))
    implementation(project(":ports:persistence"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("org.springframework:spring-jdbc")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("checkPersistenceBoundaries") {
    group = "verification"
    description = "Ensure persistence module contains only JPA entities, Spring Data repositories, and store implementations."
    val sourceRoot = layout.projectDirectory.dir("src/main/kotlin").asFile
    inputs.dir(sourceRoot)
    outputs.upToDateWhen { false }

    doLast {
        val forbiddenImports = listOf(
            Regex("""import\s+com\.ohmyclipping\.admin\.""") to "admin adapter import",
            Regex("""import\s+com\.ohmyclipping\.user\.""") to "user adapter import",
            Regex("""import\s+com\.ohmyclipping\.config\.""") to "app config import",
            Regex("""import\s+com\.ohmyclipping\.adapter\.""") to "external adapter import",
            Regex("""import\s+com\.ohmyclipping\.rss\.""") to "RSS adapter import",
            Regex("""import\s+com\.ohmyclipping\.ai\.""") to "AI adapter import",
            Regex("""import\s+com\.ohmyclipping\.mcp\.""") to "MCP adapter import",
            Regex("""import\s+com\.ohmyclipping\.observability\.""") to "observability import",
            Regex("""import\s+com\.ohmyclipping\.security\.""") to "security import",
        )

        val violations = mutableListOf<String>()
        sourceRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relativePath = file.relativeTo(sourceRoot).path
                if (!relativePath.startsWith("com/ohmyclipping/entity/") &&
                    !relativePath.startsWith("com/ohmyclipping/repository/") &&
                    !relativePath.startsWith("com/ohmyclipping/store/")
                ) {
                    violations += "$relativePath — persistence module may contain only entity/repository/store packages"
                }

                val source = file.readText()
                Regex("""import\s+com\.ohmyclipping\.service\.(?!dto\.|analytics\.dto\.|pipeline\.)""")
                    .findAll(source)
                    .forEach { match ->
                        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                        violations += "$relativePath:$line — forbidden persistence dependency (service implementation import)"
                    }
                forbiddenImports.forEach { (pattern, label) ->
                    pattern.findAll(source).forEach { match ->
                        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                        violations += "$relativePath:$line — forbidden persistence dependency ($label)"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            throw GradleException(
                "${violations.size} persistence boundary violation(s) detected. " +
                    "Keep persistence free of app service/config/adapter dependencies.",
            )
        }

        logger.lifecycle("checkPersistenceBoundaries: OK (persistence contains only entity/repository/store implementation code)")
    }
}

tasks.named("check") {
    dependsOn("checkPersistenceBoundaries")
}
