plugins {
    kotlin("jvm") version "2.3.0"
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
    implementation("org.springframework:spring-web")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

tasks.register("checkErrorTypeBoundaries") {
    group = "verification"
    description = "Ensure shared error types stay free of app, persistence, and adapter dependencies."
    val sourceRoot = layout.projectDirectory.dir("src/main/kotlin").asFile
    inputs.dir(sourceRoot)
    outputs.upToDateWhen { false }

    doLast {
        val forbiddenImports = listOf(
            Regex("""import\s+jakarta\.persistence\.""") to "JPA import",
            Regex("""import\s+com\.clipping\.mcpserver\.model\.""") to "app model import",
            Regex("""import\s+com\.clipping\.mcpserver\.entity\.""") to "entity import",
            Regex("""import\s+com\.clipping\.mcpserver\.repository\.""") to "repository import",
            Regex("""import\s+com\.clipping\.mcpserver\.store\.""") to "store import",
            Regex("""import\s+com\.clipping\.mcpserver\.service\.""") to "service import",
            Regex("""import\s+com\.clipping\.mcpserver\.admin\.""") to "admin adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.user\.""") to "user adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.adapter\.""") to "external adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.rss\.""") to "RSS adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.ai\.""") to "AI adapter import",
        )

        val violations = mutableListOf<String>()
        sourceRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText()
                forbiddenImports.forEach { (pattern, label) ->
                    pattern.findAll(source).forEach { match ->
                        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                        violations += "${file.relativeTo(sourceRoot).path}:$line — forbidden error type dependency ($label)"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            throw GradleException(
                "${violations.size} error type boundary violation(s) detected. " +
                    "Keep shared error types free of app/persistence/adapter dependencies.",
            )
        }

        logger.lifecycle("checkErrorTypeBoundaries: OK (shared error types have no forbidden app dependencies)")
    }
}

tasks.named("check") {
    dependsOn("checkErrorTypeBoundaries")
}
