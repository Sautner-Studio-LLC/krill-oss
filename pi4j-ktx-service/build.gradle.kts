import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.shadow)
}

group = "krill.zone"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Pi4J
    implementation(libs.pi4j.ktx)
    implementation(libs.pi4j.core)
    implementation(libs.pi4j.plugin.raspberrypi)
    implementation(libs.pi4j.plugin.pigpio)

    // gRPC + Protobuf
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.kotlin)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback.classic)

    // javax.annotation is removed from JDK 9+ but required by gRPC generated code
    compileOnly(libs.javax.annotation.api)

    testImplementation(kotlin("test"))
}

val vcat = extensions.getByType<VersionCatalogsExtension>().named("libs")
val protobufVersion  = vcat.findVersion("protobuf").get().requiredVersion
val grpcVersion      = vcat.findVersion("grpc").get().requiredVersion
val grpcKotlinVersion = vcat.findVersion("grpc-kotlin").get().requiredVersion

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    // Merge pi4j plugin ServiceLoader registrations so all providers are visible
    mergeServiceFiles()

    // Strip JAR signing metadata — fat jars cannot carry signatures from upstream jars
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")

    archiveBaseName.set("krill-pi4j")
    archiveVersion.set("")
    archiveClassifier.set("all")

    manifest {
        attributes["Main-Class"] = "krill.zone.MainKt"
    }
}

// Make shadowJar the default build artifact
tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.test {
    useJUnitPlatform()
}