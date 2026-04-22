@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.shadow)
    alias(libs.plugins.vanniktech)
    `maven-publish`
}

group = "com.krillforge"
version = "0.0.2"

kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    androidLibrary {
        namespace = "krill.zone.sdk"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask { enabled = false }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KrillSdk"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// ── Fat jar for JVM target (standalone distribution, not the Maven Central artifact) ──
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    val jvmJar = tasks.named("jvmJar")
    dependsOn(jvmJar)
    from(jvmJar.map { (it as Jar).archiveFile })
    configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    archiveBaseName.set("krill-sdk")
    archiveVersion.set("")
    archiveClassifier.set("all")
}

// ── Maven Central publishing ───────────────────────────────────────────────────

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId    = "com.krillforge",
        artifactId = "krill-sdk",
        version    = project.version.toString(),
    )

    pom {
        name        = "Krill SDK"
        description = "Shared Kotlin Multiplatform client SDK for the Krill home-automation swarm. " +
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
