plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    kotlin("plugin.jpa") version "2.3.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.clipping.mcpserver"
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
    implementation(project(":clipping-api-models"))
    implementation(project(":clipping-domain"))
    implementation(project(":clipping-error-types"))
    implementation(project(":clipping-pipeline-models"))
    implementation(project(":clipping-store-spi"))
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
            Regex("""import\s+com\.clipping\.mcpserver\.admin\.""") to "admin adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.user\.""") to "user adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.config\.""") to "app config import",
            Regex("""import\s+com\.clipping\.mcpserver\.adapter\.""") to "external adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.rss\.""") to "RSS adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.ai\.""") to "AI adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.mcp\.""") to "MCP adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.observability\.""") to "observability import",
            Regex("""import\s+com\.clipping\.mcpserver\.security\.""") to "security import",
        )

        val violations = mutableListOf<String>()
        sourceRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relativePath = file.relativeTo(sourceRoot).path
                if (!relativePath.startsWith("com/clipping/mcpserver/entity/") &&
                    !relativePath.startsWith("com/clipping/mcpserver/repository/") &&
                    !relativePath.startsWith("com/clipping/mcpserver/store/")
                ) {
                    violations += "$relativePath — persistence module may contain only entity/repository/store packages"
                }

                val source = file.readText()
                Regex("""import\s+com\.clipping\.mcpserver\.service\.(?!dto\.|analytics\.dto\.|pipeline\.)""")
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
