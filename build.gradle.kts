plugins {
    kotlin("jvm") version "1.8.22"
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
    implementation("dev.kord:kord-core:0.9.0")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.8.22")
}