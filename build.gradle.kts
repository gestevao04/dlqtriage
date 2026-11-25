plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
}

group = "dev"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val detekt by configurations.creating

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(libs.spring.kafka)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.junit.platform.launcher)
    detekt(libs.detekt.cli)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

sourceSets {
    create("integrationTest") {
        kotlin.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += output + compileClasspath
    }
    create("evals") {
        kotlin.srcDir("src/evals/kotlin")
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += output + compileClasspath
    }
    create("sandbox") {
        kotlin.srcDir("src/sandbox/kotlin")
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += output + compileClasspath
    }
}

sourceSets["test"].compileClasspath += sourceSets["evals"].output
sourceSets["test"].runtimeClasspath += sourceSets["evals"].output

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])
configurations["evalsImplementation"].extendsFrom(configurations["implementation"])
configurations["evalsRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])
configurations["sandboxImplementation"].extendsFrom(configurations["implementation"])
configurations["sandboxRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])

tasks.register<Test>("integrationTest") {
    description = "Runs PostgreSQL and Kafka Testcontainers integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.register<JavaExec>("evalRun") {
    description = "Runs the deterministic classifier evaluation against the checked-in dataset."
    group = "verification"
    dependsOn(tasks.named("evalsClasses"))
    classpath = sourceSets["evals"].runtimeClasspath
    mainClass.set("dev.dlqtriage.evals.EvaluationHarnessKt")
    args("evals/dataset/baseline.jsonl", "evals/reports/baseline.json")
}

tasks.register<JavaExec>("sandboxRun") {
    description = "Publishes synthetic failures to the configured DLQ topic."
    group = "application"
    dependsOn(tasks.named("sandboxClasses"))
    classpath = sourceSets["sandbox"].runtimeClasspath
    mainClass.set("dev.dlqtriage.sandbox.SyntheticFailureGeneratorKt")
}

tasks.register<JavaExec>("detekt") {
    description = "Runs detekt static analysis."
    group = "verification"
    classpath = detekt
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(
        "--input",
        "src/main/kotlin,src/test/kotlin,src/integrationTest/kotlin,src/evals/kotlin,src/sandbox/kotlin",
    )
}
