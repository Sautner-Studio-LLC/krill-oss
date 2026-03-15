@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.dsl.*

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.proguard.gradle)
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.shadow)
}

group = "krill.zone"
version = "1.0.756"

kotlin {
    jvm {

        mainRun {
            mainClass.set("krill.zone.ApplicationKt")
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvmToolchain(21)

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Empty commonMain - all dependencies are JVM-specific
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(projects.shared)
                implementation(projects.generated)
                implementation(projects.aws.apigwServerLoggingLambda) {
                    exclude(group = "org.slf4j", module = "slf4j-simple")
                }
                implementation(libs.mqtt)
                implementation(libs.bundles.pi4j)
                implementation(libs.koin.ktor)
                implementation(libs.koin.slf4j)
                implementation(libs.kermit.jvm)
                implementation(libs.kotlinx.datetime)
                implementation(libs.bundles.ktorServer)
                implementation(libs.bundles.ktorClient)
                implementation(libs.bundles.ktorClientJvm)

                implementation(libs.serial)
                implementation(libs.plot)
                implementation(libs.logback.classic)
                
                // Database - Exposed ORM and H2
                implementation(libs.bundles.exposed)

            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.testJvm)
                implementation(kotlin("test"))
                runtimeOnly(libs.junit.launcher)
            }
        }
    }
}



tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    val fastWasm = providers.gradleProperty("fastWasm").map { it.toBoolean() }.getOrElse(false)

    // Configure which JAR to use as input - use the jvm JAR from KMP
    val jvmJar = tasks.named("jvmJar")
    dependsOn(jvmJar)

    // Fix Pi4J ServiceLoader: Multiple Pi4J JARs (pi4j-plugin-gpiod, pi4j-plugin-raspberrypi)
    // each declare their plugin in META-INF/services/com.pi4j.extension.Plugin.
    // With DuplicatesStrategy.EXCLUDE, only the first one survives — silently dropping
    // the GpioDPlugin. Without it, Pi4J falls back to memory-mapped GPIO (RpiDigitalOutput)
    // which doesn't work on Raspberry Pi 5 (RP1 chip requires gpiod chardev interface).
    // Solution: generate a pre-merged service file and add it first so it wins the EXCLUDE race.
    val mergedServicesDir = layout.buildDirectory.dir("merged-services")
    doFirst {
        val outDir = mergedServicesDir.get().asFile
        val serviceFile = File(outDir, "META-INF/services/com.pi4j.extension.Plugin")
        serviceFile.parentFile.mkdirs()
        serviceFile.writeText(
            listOf(
                "com.pi4j.plugin.gpiod.GpioDPlugin",
                "com.pi4j.plugin.raspberrypi.RaspberryPiPlugin",
            ).joinToString("\n") + "\n"
        )
    }
    from(mergedServicesDir)  // Added first so our merged file wins with EXCLUDE

    from(jvmJar.map { (it as Jar).archiveFile })
    configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Remove version from JAR name - produce server-all.jar
    archiveBaseName.set("server")
    archiveVersion.set("")
    archiveClassifier.set("all")

    manifest {
        attributes["Main-Class"] = "krill.zone.ApplicationKt"
    }


    if (fastWasm) {
        enabled = false // Disable ShadowJar for debug builds
    } else {
        isZip64 = true

        minimize {
            exclude(dependency("io.ktor:.*:.*"))
            exclude(dependency("io.netty:.*:.*"))  // Netty loads classes dynamically
            exclude(dependency("ch.qos.logback:.*:.*"))
            exclude(dependency("com.pi4j:.*:.*"))
            exclude(dependency("com.h2database:.*:.*"))  // H2 driver is loaded by name via Class.forName
            exclude(dependency("org.jetbrains.exposed:.*:.*"))  // Exposed uses ServiceLoader
        }
    }
}

// ProGuard task - obfuscate the fat JAR
tasks.register<proguard.gradle.ProGuardTask>("proguard") {
    group = "build"
    description = "Obfuscates the server fat JAR using ProGuard"

    dependsOn(tasks.named("shadowJar"))

    val shadowJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
    injars(shadowJar.map { it.outputs.files })

    outjars(layout.buildDirectory.file("libs/server-min.jar"))

    // Resolve Java runtime libraries for ProGuard.
    // ProGuard MUST have the JDK runtime (java.lang.Object etc.) or it will fail
    // with IncompleteClassHierarchyException. We check multiple sources because
    // self-hosted CI runners may have JAVA_HOME vs java.home mismatches.
    val javaHomeProp = System.getProperty("java.home") ?: ""
    val javaHomeEnv  = System.getenv("JAVA_HOME") ?: ""

    // Try all candidate directories for jmods
    val jmodsCandidates = listOfNotNull(
        javaHomeEnv.takeIf { it.isNotEmpty() }?.let { file("$it/jmods") },
        javaHomeEnv.takeIf { it.isNotEmpty() }?.let { file("$it/../jmods") },
        javaHomeProp.takeIf { it.isNotEmpty() }?.let { file("$it/jmods") },
        javaHomeProp.takeIf { it.isNotEmpty() }?.let { file("$it/../jmods") },
    ).distinctBy { it.canonicalPath }

    val jmodsDir = jmodsCandidates.firstOrNull { it.isDirectory && (it.listFiles()?.isNotEmpty() == true) }

    if (jmodsDir != null) {
        val jmods = jmodsDir.listFiles()?.filter { it.extension == "jmod" }?.sorted() ?: emptyList()
        jmods.forEach { libraryjars(it.absolutePath) }
        println("ProGuard: loaded ${jmods.size} jmods from ${jmodsDir.canonicalPath}")
    } else {
        // Fallback: lib/modules (always present on JDK 9+, even without jmods/)
        val modulesCandidates = listOfNotNull(
            javaHomeEnv.takeIf { it.isNotEmpty() }?.let { file("$it/lib/modules") },
            javaHomeProp.takeIf { it.isNotEmpty() }?.let { file("$it/lib/modules") },
        ).distinctBy { it.canonicalPath }
        val modulesFile = modulesCandidates.firstOrNull { it.exists() }
        if (modulesFile != null) {
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), modulesFile.absolutePath)
            println("ProGuard: using lib/modules from ${modulesFile.canonicalPath}")
        } else {
            println("ProGuard: ERROR - Cannot find Java runtime libraries!")
            println("  java.home system property: $javaHomeProp")
            println("  JAVA_HOME env variable:    $javaHomeEnv")
            println("  Searched jmods dirs:        ${jmodsCandidates.map { "${it.absolutePath} (exists=${it.exists()})" }}")
            println("  Searched modules files:     ${modulesCandidates.map { "${it.absolutePath} (exists=${it.exists()})" }}")
            throw GradleException("Cannot find Java runtime libraries for ProGuard. See output above.")
        }
    }

    configuration("proguard-rules.pro")

    printmapping(layout.buildDirectory.file("libs/server-mapping.txt"))
    printseeds(layout.buildDirectory.file("libs/server-seeds.txt"))
    printusage(layout.buildDirectory.file("libs/server-usage.txt"))

    // Enable verbose output to see what ProGuard is doing
    verbose()
}

tasks.withType<Test> {
    useJUnitPlatform()  // REQUIRED for JUnit 5
}


