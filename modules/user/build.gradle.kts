plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
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
    implementation(project(":ports:workflow"))
    implementation(project(":core:domain"))
    implementation(project(":modules:digest-policy"))
    implementation(project(":core:error-types"))
    implementation(project(":adapters:notification"))
    implementation(project(":modules:source"))
    implementation(project(":ports:persistence"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("jakarta.validation:jakarta.validation-api:3.1.1")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.14.5")
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

tasks.register("checkUserApplicationBoundaries") {
    group = "verification"
    description = "Ensure user application module does not depend on root app implementation packages."
    val sourceRoot = layout.projectDirectory.dir("src/main/kotlin").asFile
    inputs.dir(sourceRoot)
    outputs.upToDateWhen { false }

    doLast {
        val forbiddenImports = listOf(
            Regex("""import\s+jakarta\.persistence\.""") to "JPA import",
            Regex("""import\s+com\.ohmyclipping\.admin\.""") to "admin adapter import",
            Regex("""import\s+com\.ohmyclipping\.adapter\.""") to "external adapter import",
            Regex("""import\s+com\.ohmyclipping\.ai\.""") to "AI adapter import",
            Regex("""import\s+com\.ohmyclipping\.config\.""") to "root config import",
            Regex("""import\s+com\.ohmyclipping\.content\.""") to "content adapter import",
            Regex("""import\s+com\.ohmyclipping\.entity\.""") to "entity import",
            Regex("""import\s+com\.ohmyclipping\.observability\.""") to "observability import",
            Regex("""import\s+com\.ohmyclipping\.repository\.""") to "repository import",
            Regex("""import\s+com\.ohmyclipping\.rss\.""") to "RSS adapter import",
            Regex("""import\s+com\.ohmyclipping\.security\.""") to "security import",
            Regex("""import\s+com\.ohmyclipping\.service\.(?!dto\.|port\.|notification\.|source\.|event\.)""") to "root service import",
            Regex("""import\s+com\.ohmyclipping\.support\.""") to "root support import",
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
                        violations += "${file.relativeTo(sourceRoot).path}:$line — forbidden user application dependency ($label)"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            throw GradleException(
                "${violations.size} user application boundary violation(s) detected. " +
                    "Keep user application code behind ports and shared SPIs.",
            )
        }

        logger.lifecycle("checkUserApplicationBoundaries: OK (user application module has no root app implementation imports)")
    }
}

tasks.named("check") {
    dependsOn("checkUserApplicationBoundaries")
}
