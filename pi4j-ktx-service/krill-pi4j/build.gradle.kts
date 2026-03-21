import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.vanniktech)
    `maven-publish`
}

group = "com.krillforge"
version = "0.0.2"

repositories {
    mavenCentral()
}

dependencies {
    // gRPC + Protobuf (api so consumers inherit them)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
    api(libs.protobuf.kotlin)
    api(libs.kotlinx.coroutines.core)

    // Transport — needed to open a ManagedChannel in client code
    implementation(libs.grpc.netty.shaded)

    compileOnly(libs.javax.annotation.api)

    testImplementation(kotlin("test"))
}

val vcat = extensions.getByType<VersionCatalogsExtension>().named("libs")
val protobufVersion   = vcat.findVersion("protobuf").get().requiredVersion
val grpcVersion       = vcat.findVersion("grpc").get().requiredVersion
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
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

// ── Maven Central publishing ───────────────────────────────────────────────────

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId    = "com.krillforge",
        artifactId = "krill-pi4j",
        version    = project.version.toString(),
    )

    pom {
        name        = "Krill Pi4J Client"
        description = "gRPC client library for the krill-pi4j hardware service. " +
            "Lets any JDK 21+ project control Raspberry Pi GPIO, PWM and I2C " +
            "via a local gRPC daemon without requiring JDK 25."
        url         = "https://github.com/bsautner/krill-oss"

        licenses {
            license {
                name = "Apache License 2.0"
                url  = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        developers {
            developer {
                id    = "bsautner"
                name  = "Benjamin Sautner"
                email = "ben@krill.zone"
                url   = "https://krill.zone"
            }
        }

        scm {
            url                 = "https://github.com/bsautner/krill-oss"
            connection          = "scm:git:git://github.com/bsautner/krill-oss.git"
            developerConnection = "scm:git:ssh://git@github.com/bsautner/krill-oss.git"
        }
    }
}
