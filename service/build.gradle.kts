import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.ncorti.ktfmt.gradle") version "0.27.0"
}

group = "com.fortuneavenue"

version = "0.0.1-SNAPSHOT"

java { sourceCompatibility = JavaVersion.VERSION_17 }

repositories { mavenCentral() }

ktfmt {
    kotlinLangStyle()
    removeUnusedImports.set(true)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation("org.jetbrains.exposed:exposed-core:1.4.0")
    implementation("org.jetbrains.exposed:exposed-dao:1.4.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.4.0")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Kills any individual test method that hangs instead of letting it block the whole
    // suite (and `make test`) forever. Reported as a timeout failure for that test only --
    // everything else still runs. Override per-test with @Timeout if a specific test
    // legitimately needs longer.
    systemProperty("junit.jupiter.execution.timeout.default", "10s")
}

tasks.named<BootRun>("bootRun") { jvmArgs("--enable-native-access=ALL-UNNAMED") }

// Fail `./gradlew check` (and anything that depends on it) on unformatted
// Kotlin, same idea as the timeout above: catch it in the build, not review.
tasks.check { dependsOn("ktfmtCheck") }
