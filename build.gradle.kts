val kotlinVersion: String by project
val kotlinLoggingVersion: String by project
val log4jVersion: String by project
val ktorVersion: String by project
val kordVersion: String by project
val scrimageVersion: String by project
val kamlVersion: String by project

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

group = "at.bromutus"
version = "0.0.1"
application {
    mainClass.set("at.bromutus.bromine.AppKt")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")
    implementation("dev.kord:kord-core:$kordVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    implementation("com.sksamuel.scrimage:scrimage-core:$scrimageVersion")
    implementation("com.charleskorn.kaml:kaml:$kamlVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "at.bromutus.bromine.AppKt"
        }
    }
}
