plugins {
    kotlin("jvm") version "2.3.0"
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

dependencies {
    implementation(project(":core:api-models"))
    implementation(project(":core:domain"))

    implementation("jakarta.validation:jakarta.validation-api:3.1.1")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

tasks.register("checkAdminModelBoundaries") {
    group = "verification"
    description = "Ensure admin DTOs stay free of Spring/JPA/store/root implementation dependencies."
    val sourceRoot = layout.projectDirectory.dir("src/main/kotlin").asFile
    inputs.dir(sourceRoot)
    outputs.upToDateWhen { false }

    doLast {
        val forbiddenImports = listOf(
            Regex("""import\s+org\.springframework\.""") to "Spring import",
            Regex("""import\s+jakarta\.persistence\.""") to "JPA import",
            Regex("""import\s+com\.ohmyclipping\.entity\.""") to "entity import",
            Regex("""import\s+com\.ohmyclipping\.repository\.""") to "repository import",
            Regex("""import\s+com\.ohmyclipping\.store\.""") to "store import",
            Regex("""import\s+com\.ohmyclipping\.config\.""") to "app config import",
            Regex("""import\s+com\.ohmyclipping\.adapter\.""") to "external adapter import",
            Regex("""import\s+com\.ohmyclipping\.ai\.""") to "AI adapter import",
            Regex("""import\s+com\.ohmyclipping\.rss\.""") to "RSS adapter import",
            Regex("""import\s+com\.ohmyclipping\.service\.(?!dto\.)""") to "application service import",
        )

        val violations = mutableListOf<String>()
        sourceRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText()
                forbiddenImports.forEach { (pattern, label) ->
                    pattern.findAll(source).forEach { match ->
                        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                        violations += "${file.relativeTo(sourceRoot).path}:$line — forbidden admin model dependency ($label)"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            throw GradleException(
                "${violations.size} admin model boundary violation(s) detected. " +
                    "Keep admin DTOs free of framework, store, and service implementation dependencies.",
            )
        }

        logger.lifecycle("checkAdminModelBoundaries: OK (admin DTOs have no forbidden implementation imports)")
    }
}

tasks.named("check") {
    dependsOn("checkAdminModelBoundaries")
}
