import org.jetbrains.compose.desktop.application.dsl.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.targets.js.webpack.*
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.*
import proguard.gradle.*

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.proguard.gradle)
    }
}

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
}


kotlin {
    jvm("desktop")

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            freeCompilerArgs += "-Xbinary=bundleId=krill.zone"
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "ComposeApp.js"
                val fastWasm = providers.gradleProperty("fastWasm").map { it.toBoolean() }.getOrElse(false)
                mode = if (fastWasm) KotlinWebpackConfig.Mode.DEVELOPMENT else KotlinWebpackConfig.Mode.PRODUCTION
                sourceMaps = !fastWasm
            }
        }
        binaries.executable()
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
    
    sourceSets {
        androidMain {
            dependencies {
                api(libs.compose.ui.tooling)
                implementation(libs.bundles.media3)
                implementation(compose.preview)
                implementation(libs.slf4j.simple)
                implementation(libs.androidx.activity.compose)
                implementation(libs.ktor.client.cio)


            }
        }

        val commonMain by getting {
            dependencies {

                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(projects.generated)
                implementation(projects.shared)
                implementation(libs.kermit)
                implementation(libs.runtime)
                implementation(libs.foundation)
                implementation(libs.material3)
                implementation(libs.ui)
                implementation(libs.components.resources)
                implementation(libs.ui.tooling.preview)
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.kotlinx.datetime)
                implementation(libs.bundles.ktorClient)
                implementation(libs.kotlinx.coroutines)
                implementation(libs.settings)
                implementation(libs.bundles.coil)
                implementation(libs.koalaplot)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.logback.classic)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
        }
        getByName("iosX64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        getByName("iosArm64Main").dependsOn(iosMain)
    }
}



android {
    namespace = "krill.zone.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

compose {
    resources {
        publicResClass = true
        packageOfResClass = "krill.composeapp.generated.resources"
        generateResClass = always
    }
    desktop {
        application {
            mainClass = "krill.zone.MainKt"
            buildTypes.release.proguard {
                version.set(libs.versions.proguardGradle.get())
                configurationFiles.from("proguard-rules-desktop.pro")
            }
            nativeDistributions {
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                packageName = "krill-desktop"
                packageVersion = "1.0.756"
                vendor = "Krill"
                description = "Krill Desktop Control Application"

                linux {
                    debMaintainer = "Benjamin Sautner <ben@krill.zone>"
                }
            }
        }
    }
}


composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}


tasks.register<Zip>("wasmZip") {
    val devDist = tasks.named<Sync>("wasmJsBrowserDevelopmentExecutableDistribution")

    dependsOn(devDist)

    archiveFileName.set("wasm-archive.zip")
    destinationDirectory.set(layout.buildDirectory.dir("wasm"))

    val distDir = devDist.map { it.destinationDir }

    from(distDir)
    inputs.dir(distDir).withPathSensitivity(PathSensitivity.RELATIVE)

    isReproducibleFileOrder = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("wasmZip") {
    val devDist = tasks.named<Sync>("wasmJsBrowserDevelopmentExecutableDistribution")

    dependsOn(devDist)
}

tasks.withType<BinaryenExec>().configureEach {
    val fastWasm = providers.gradleProperty("fastWasm").map { it.toBoolean() }.getOrElse(false)

    onlyIf { !fastWasm }
}

// Mark WASM tasks as incompatible with configuration cache due to Kotlin plugin limitations
// See: https://youtrack.jetbrains.com/issue/KT-50848
tasks.withType<KotlinWebpack>().configureEach {
    notCompatibleWithConfigurationCache("Kotlin/JS webpack tasks capture Project instances")
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
    notCompatibleWithConfigurationCache("Kotlin/JS test tasks capture Project instances")
}

// Desktop fat JAR for Debian packaging (Architecture: all - works on amd64, arm64, etc.)
tasks.register<Jar>("desktopFatJar") {
    group = "build"
    description = "Creates a fat JAR for the desktop application for Debian packaging"

    archiveBaseName.set("krill-desktop")
    archiveClassifier.set("")
    archiveVersion.set("")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    // Use the desktop JAR as the base
    val desktopJar = tasks.named<Jar>("desktopJar")
    dependsOn(desktopJar)

    // Set the main class
    manifest {
        attributes["Main-Class"] = "krill.zone.MainKt"
    }

    // Include the desktop JAR contents
    from(desktopJar.map { zipTree(it.archiveFile) })

    // Include all runtime dependencies for the desktop target
    from({
        configurations.getByName("desktopRuntimeClasspath").map {
            if (it.isDirectory) it else zipTree(it)
        }
    })

    // Handle duplicate files
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Exclude unnecessary files
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("META-INF/NOTICE*", "META-INF/LICENSE*")
    exclude("META-INF/INDEX.LIST")
    exclude("META-INF/versions/*/module-info.class")
}


tasks.register<ProGuardTask>("desktopProguard") {
    group = "build"
    description = "Runs ProGuard on the desktop fat JAR for production builds (CURRENTLY DISABLED)"

    val fastWasm = providers.gradleProperty("fastWasm").map { it.toBoolean() }.getOrElse(false)
    
    // Depend on desktopFatJar
    dependsOn(tasks.named("desktopFatJar"))
    
    // Configuration file
    configuration("proguard-rules-desktop.pro")
    
    // Input/Output JARs
    injars(layout.buildDirectory.file("libs/krill-desktop.jar"))
    outjars(layout.buildDirectory.file("libs/krill-desktop-min.jar"))
    
    // Resolve Java runtime libraries for ProGuard (same logic as server module).
    val javaHomeProp = System.getProperty("java.home") ?: ""
    val javaHomeEnv  = System.getenv("JAVA_HOME") ?: ""
    val jmodsCandidates = listOfNotNull(
        javaHomeEnv.takeIf { it.isNotEmpty() }?.let { file("$it/jmods") },
        javaHomeEnv.takeIf { it.isNotEmpty() }?.let { file("$it/../jmods") },
        javaHomeProp.takeIf { it.isNotEmpty() }?.let { file("$it/jmods") },
        javaHomeProp.takeIf { it.isNotEmpty() }?.let { file("$it/../jmods") },
    ).distinctBy { it.canonicalPath }
    val jmodsDir = jmodsCandidates.firstOrNull { it.isDirectory && (it.listFiles()?.isNotEmpty() == true) }
    if (jmodsDir != null) {
        jmodsDir.listFiles()?.filter { it.extension == "jmod" }?.sorted()?.forEach { libraryjars(it.absolutePath) }
    } else {
        val modulesFile = listOfNotNull(
            javaHomeEnv.takeIf { it.isNotEmpty() }?.let { file("$it/lib/modules") },
            javaHomeProp.takeIf { it.isNotEmpty() }?.let { file("$it/lib/modules") },
        ).firstOrNull { it.exists() }
        if (modulesFile != null) {
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), modulesFile.absolutePath)
        } else {
            throw GradleException("Cannot find Java runtime libraries for ProGuard. java.home=$javaHomeProp JAVA_HOME=$javaHomeEnv")
        }
    }

    // Output mapping file for debugging
    printmapping(layout.buildDirectory.file("libs/desktop-mapping.txt"))
    printseeds(layout.buildDirectory.file("libs/desktop-seeds.txt"))
    printusage(layout.buildDirectory.file("libs/desktop-usage.txt"))
    
    // Only run for production builds
    onlyIf { !fastWasm }
    
    doFirst {
        println("🔒 Running ProGuard obfuscation on desktop JAR...")
    }
    
    doLast {
        val minJar = layout.buildDirectory.file("libs/krill-desktop-min.jar").get().asFile
        val fatJar = layout.buildDirectory.file("libs/krill-desktop.jar").get().asFile
        if (minJar.exists() && fatJar.exists()) {
            val reduction = ((1 - minJar.length().toDouble() / fatJar.length()) * 100).toInt()
            println("✅ ProGuard complete: ${fatJar.length() / 1024}KB → ${minJar.length() / 1024}KB (${reduction}% reduction)")
        }
    }
}

// Task to build production-ready desktop JAR
tasks.register("desktopProductionJar") {
    group = "build"
    description = "Builds production desktop JAR with ProGuard obfuscation"
    
    dependsOn(tasks.named("desktopProguard"))
    
    doLast {
        val minJar = layout.buildDirectory.file("libs/krill-desktop-min.jar").get().asFile
        if (minJar.exists()) {
            println("📦 Production desktop JAR ready: ${minJar.absolutePath}")
        } else {
            println("⚠️ Warning: ProGuard output not found. Using unobfuscated JAR.")
        }
    }
}
