plugins {
    kotlin("jvm") version "2.3.0"
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

dependencies {
    implementation(project(":clipping-domain"))
    implementation(project(":clipping-api-models"))
    implementation(project(":clipping-pipeline-models"))
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
            Regex("""import\s+com\.clipping\.mcpserver\.entity\.""") to "entity import",
            Regex("""import\s+com\.clipping\.mcpserver\.repository\.""") to "repository import",
            Regex("""import\s+com\.clipping\.mcpserver\.config\.""") to "app config import",
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
