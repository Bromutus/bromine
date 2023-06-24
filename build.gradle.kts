val kotlin_version: String by project
val kotlin_logging_version: String by project
val log4j_version: String by project
val ktor_version: String by project
val kord_version: String by project
val dotenv_version: String by project

plugins {
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
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
}

dependencies {
    implementation("io.github.oshai:kotlin-logging-jvm:$kotlin_logging_version")
    implementation("org.apache.logging.log4j:log4j-core:$log4j_version")
    implementation("org.apache.logging.log4j:log4j-api:$log4j_version")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j_version")
    implementation("dev.kord:kord-core:$kord_version")
    implementation("io.github.cdimascio:dotenv-kotlin:$dotenv_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-logging-jvm:2.2.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "at.bromutus.bromine.AppKt"
        }
    }
}
