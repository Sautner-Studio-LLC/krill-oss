plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

group = "krill.zone"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Client library (proto stubs + gRPC interfaces)
    implementation(project(":krill-pi4j"))

    // Pi4J hardware
    implementation(libs.pi4j.ktx)
    implementation(libs.pi4j.core)
    implementation(libs.pi4j.plugin.raspberrypi)
    implementation(libs.pi4j.plugin.pigpio)

    // gRPC transport
    implementation(libs.grpc.netty.shaded)

    // Logging
    implementation(libs.logback.classic)

    compileOnly(libs.javax.annotation.api)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    archiveBaseName.set("krill-pi4j")
    archiveVersion.set("")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "krill.zone.MainKt"
    }
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.test {
    useJUnitPlatform()
}
