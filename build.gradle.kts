val kotlin_version: String by project
val ktor_version: String by project
val kord_version: String by project
val dotenv_version: String by project

plugins {
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
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
    implementation("dev.kord:kord-core:$kord_version")
    implementation("io.github.cdimascio:dotenv-kotlin:$dotenv_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}