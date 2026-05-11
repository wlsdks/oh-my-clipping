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
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

tasks.register("checkAppPortBoundaries") {
    group = "verification"
    description = "Ensure app port module stays free of Spring/JPA/store/app model dependencies."
    val sourceRoot = layout.projectDirectory.dir("src/main/kotlin").asFile
    inputs.dir(sourceRoot)
    outputs.upToDateWhen { false }

    doLast {
        val forbiddenImports = listOf(
            Regex("""import\s+org\.springframework\.""") to "Spring import",
            Regex("""import\s+jakarta\.persistence\.""") to "JPA import",
            Regex("""import\s+com\.ohmyclipping\.model\.""") to "app model import",
            Regex("""import\s+com\.ohmyclipping\.entity\.""") to "entity import",
            Regex("""import\s+com\.ohmyclipping\.repository\.""") to "repository import",
            Regex("""import\s+com\.ohmyclipping\.store\.""") to "store import",
            Regex("""import\s+com\.ohmyclipping\.config\.""") to "app config import",
            Regex("""import\s+com\.ohmyclipping\.admin\.""") to "admin adapter import",
            Regex("""import\s+com\.ohmyclipping\.user\.""") to "user adapter import",
        )

        val violations = mutableListOf<String>()
        sourceRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText()
                forbiddenImports.forEach { (pattern, label) ->
                    pattern.findAll(source).forEach { match ->
                        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                        violations += "${file.relativeTo(sourceRoot).path}:$line — forbidden app port dependency ($label)"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            throw GradleException(
                "${violations.size} app port boundary violation(s) detected. " +
                    "Keep app ports free of Spring/JPA/store/app model dependencies.",
            )
        }

        logger.lifecycle("checkAppPortBoundaries: OK (app ports have no forbidden app/framework imports)")
    }
}

tasks.named("check") {
    dependsOn("checkAppPortBoundaries")
}
