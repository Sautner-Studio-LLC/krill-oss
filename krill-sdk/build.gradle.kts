plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.vanniktech)
    `maven-publish`
}

group = "krill.zone"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

// ── Fat jar (standalone distribution, not the Maven Central artifact) ──────────

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    archiveBaseName.set("krill-sdk")
    archiveVersion.set("")
    archiveClassifier.set("all")
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

// ── Maven Central publishing ───────────────────────────────────────────────────

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId    = "krill.zone",
        artifactId = "krill-sdk",
        version    = project.version.toString(),
    )

    pom {
        name        = "Krill SDK"
        description = "Shared Kotlin/JVM client SDK for the Krill home-automation swarm. " +
            "Provides common data models and utilities for building integrations against a Krill server."
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
