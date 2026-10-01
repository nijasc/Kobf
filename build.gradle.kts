plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "dev.kobf"
version = "0.1.0"

repositories { mavenCentral() }

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
    implementation("org.ow2.asm:asm-util:9.7.1")
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    testImplementation(kotlin("test"))
    testImplementation("org.ow2.asm:asm:9.7.1")
}

application {
    mainClass = "dev.kobf.MainKt"
    applicationName = "kobf"
}

kotlin {
    jvmToolchain(21)
}

tasks.test { useJUnitPlatform() }