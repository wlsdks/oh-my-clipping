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

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("checkEngineBoundaries") {
    group = "verification"
    description = "Ensure clipping-engine stays free of Spring/JPA/store/app model dependencies."
    val sourceRoot = layout.projectDirectory.dir("src/main/kotlin").asFile
    inputs.dir(sourceRoot)
    outputs.upToDateWhen { false }

    doLast {
        val forbiddenImports = listOf(
            Regex("""import\s+org\.springframework\.""") to "Spring import",
            Regex("""import\s+jakarta\.persistence\.""") to "JPA import",
            Regex("""import\s+com\.clipping\.mcpserver\.model\.""") to "app model import",
            Regex("""import\s+com\.clipping\.mcpserver\.entity\.""") to "entity import",
            Regex("""import\s+com\.clipping\.mcpserver\.repository\.""") to "repository import",
            Regex("""import\s+com\.clipping\.mcpserver\.store\.""") to "store import",
            Regex("""import\s+com\.clipping\.mcpserver\.config\.""") to "app config import",
            Regex("""import\s+com\.clipping\.mcpserver\.admin\.""") to "admin adapter import",
            Regex("""import\s+com\.clipping\.mcpserver\.user\.""") to "user adapter import",
        )

        val violations = mutableListOf<String>()
        sourceRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val source = file.readText()
                forbiddenImports.forEach { (pattern, label) ->
                    pattern.findAll(source).forEach { match ->
                        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                        violations += "${file.relativeTo(sourceRoot).path}:$line — forbidden engine dependency ($label)"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            throw GradleException(
                "${violations.size} clipping-engine boundary violation(s) detected. " +
                    "Keep engine code free of Spring/JPA/store/app model dependencies.",
            )
        }

        logger.lifecycle("checkEngineBoundaries: OK (clipping-engine has no forbidden app/framework imports)")
    }
}

tasks.named("check") {
    dependsOn("checkEngineBoundaries")
}
